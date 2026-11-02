(() => {
  'use strict';

  const AUTO_REFRESH_MS = 5 * 60 * 1000;
  const MONTHLY_USAGE_TTL_MS = 6 * 60 * 60 * 1000;
  const AVG_DAYS_PER_MONTH = 30.44;
  const LOW_THRESHOLD = 150.0;
  const LOW_BALANCE_THRESHOLD = 100.0;

  const PROVIDERS = {
    NESCO: { label: 'NESCO Electric', hint: 'Meter number', forcedMeterType: null },
    DESCO: { label: 'DESCO Electric', hint: 'Account number', forcedMeterType: 'PREPAID' },
    PGCL: { label: 'PGCL Gas', hint: 'Customer code', forcedMeterType: 'POSTPAID' },
  };

  function resolveMeterType(provider, meterType) {
    return PROVIDERS[provider].forcedMeterType || meterType || 'PREPAID';
  }

  const METERS_KEY = 'billboard.meters';
  const MONTHLY_CACHE_KEY = 'billboard.monthlyCache';
  const NOTIFIED_KEY = 'billboard.notified';

  // ---- persistence -------------------------------------------------------

  function loadMeterInfos() {
    try {
      const json = localStorage.getItem(METERS_KEY);
      return json ? JSON.parse(json) : [];
    } catch {
      return [];
    }
  }
  function saveMeterInfos(list) {
    try {
      localStorage.setItem(METERS_KEY, JSON.stringify(list));
    } catch {}
    pushSavedMeters(list);
  }
  // Server-side copy in web/server/meters.json is the source of truth;
  // localStorage is only a fallback if the server file is unavailable.
  async function fetchSavedMeters() {
    try {
      const res = await fetch('/api/meters');
      if (!res.ok) return null;
      const list = await res.json();
      return Array.isArray(list) ? list : null;
    } catch {
      return null;
    }
  }
  async function pushSavedMeters(list) {
    try {
      await fetch('/api/meters', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(list),
      });
    } catch {}
  }
  function loadMonthlyCache() {
    try {
      const json = localStorage.getItem(MONTHLY_CACHE_KEY);
      return json ? JSON.parse(json) : {};
    } catch {
      return {};
    }
  }
  function saveMonthlyCache(cache) {
    localStorage.setItem(MONTHLY_CACHE_KEY, JSON.stringify(cache));
  }
  function loadNotified() {
    try {
      const json = localStorage.getItem(NOTIFIED_KEY);
      return json ? JSON.parse(json) : {};
    } catch {
      return {};
    }
  }
  function saveNotified(map) {
    localStorage.setItem(NOTIFIED_KEY, JSON.stringify(map));
  }

  // ---- formatting ---------------------------------------------------------

  function formatTaka(amount) {
    if (amount === null || amount === undefined || Number.isNaN(amount)) return '—';
    return '৳ ' + Number(amount).toFixed(2);
  }
  function formatDaysRemaining(days) {
    if (days === null || days === undefined) return 'Calculating…';
    if (days < 1) return 'Less than a day';
    const rounded = Math.round(days);
    return `~${rounded} day${rounded === 1 ? '' : 's'}`;
  }
  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
  function dash(v) {
    return v === null || v === undefined || v === '' ? '—' : esc(v);
  }
  function postpaidDueSummary(data) {
    const dueBills = (data && data.dueBills) || [];
    if (dueBills.length === 0) return { message: 'No due bills', isDue: false };
    const total = dueBills.reduce((sum, b) => sum + (b.totalBillTaka || 0), 0);
    return {
      message: `${formatTaka(total)} due (${dueBills.length} bill${dueBills.length === 1 ? '' : 's'})`,
      isDue: true,
    };
  }

  function estimateDaysRemaining(balance, monthlyUsage) {
    if (balance === null || balance === undefined || balance <= 0) return null;
    const recent = (monthlyUsage || [])
      .slice(0, 3)
      .map((r) => r.totalUsage)
      .filter((v) => v !== null && v !== undefined && v > 0);
    if (recent.length === 0) return null;
    const avgDaily = (recent.reduce((a, b) => a + b, 0) / recent.length) / AVG_DAYS_PER_MONTH;
    if (avgDaily <= 0) return null;
    return balance / avgDaily;
  }
  function estimateDescoDaysRemaining(balance, monthlyConsumption) {
    if (balance === null || balance === undefined || balance <= 0) return null;
    const recent = (monthlyConsumption || [])
      .slice(0, 3)
      .map((r) => r.consumedTaka)
      .filter((v) => v !== null && v !== undefined && v > 0);
    if (recent.length === 0) return null;
    const avgDaily = (recent.reduce((a, b) => a + b, 0) / recent.length) / AVG_DAYS_PER_MONTH;
    if (avgDaily <= 0) return null;
    return balance / avgDaily;
  }

  // ---- state ---------------------------------------------------------------

  /** @type {Map<string, object>} keyed by meter number */
  const meters = new Map();
  let isInitialLoading = true;
  let statusText = '';
  let currentDetailsMeter = null;

  function newMeterState(meter, nickname, provider, billingType) {
    return {
      meter,
      nickname,
      provider,
      billingType: resolveMeterType(provider, billingType),
      isLoading: true,
      error: null,
      name: null,
      fatherOrHusbandName: null,
      address: null,
      mobile: null,
      office: null,
      feederName: null,
      consumerNo: null,
      meterSerialNo: null,
      approvedLoadKw: null,
      tariff: null,
      meterType: null,
      meterStatus: null,
      installDate: null,
      minRechargeAmount: null,
      lastRecharge: null,
      monthlyUsage: [],
      balance: null,
      balanceAsOf: null,
      lastRechargeDate: null,
      lastRechargeAmount: null,
      predictionDaysRemaining: null,
      descoData: null,
      pgclData: null,
      nescoPostpaidData: null,
    };
  }

  // ---- notifications ---------------------------------------------------------

  function checkAndNotify(meterNo, nickname, balance) {
    const notified = loadNotified();
    if (balance === null || balance === undefined) return;
    if (balance < LOW_BALANCE_THRESHOLD) {
      if (!notified[meterNo]) {
        if (window.Notification && Notification.permission === 'granted') {
          new Notification('Low balance', {
            body: `${nickname} [${meterNo}] has low balance. Please recharge.`,
          });
        }
        notified[meterNo] = true;
        saveNotified(notified);
      }
    } else if (notified[meterNo]) {
      notified[meterNo] = false;
      saveNotified(notified);
    }
  }

  // ---- networking ---------------------------------------------------------

  async function fetchJson(url) {
    const res = await fetch(url);
    const body = await res.json().catch(() => null);
    if (!res.ok) {
      throw new Error((body && body.error) || `Request failed (HTTP ${res.status})`);
    }
    return body;
  }

  async function refreshMonthlyUsageIfStale(meterNo, force) {
    const cache = loadMonthlyCache();
    const cached = cache[meterNo];
    const isStale = !cached || Date.now() - cached.fetchedAt > MONTHLY_USAGE_TTL_MS;
    if (!force && !isStale) return;
    try {
      const rows = await fetchJson(`/api/nesco/monthly?meter=${encodeURIComponent(meterNo)}`);
      cache[meterNo] = { rows, fetchedAt: Date.now() };
      saveMonthlyCache(cache);
      const state = meters.get(meterNo);
      if (state) {
        state.monthlyUsage = rows;
        state.predictionDaysRemaining = estimateDaysRemaining(state.balance, rows);
        renderDashboard();
        if (currentDetailsMeter === meterNo) renderDetails(meterNo);
      }
    } catch {
      // Leave previously cached monthly usage (if any) in place; retried next cycle.
    }
  }

  async function refreshOne(meterNo) {
    const state = meters.get(meterNo);
    if (!state) return;
    state.isLoading = true;
    state.error = null;
    renderDashboard();

    try {
      if (state.provider === 'NESCO' && state.billingType === 'POSTPAID') {
        const data = await fetchJson(`/api/nesco/postpaid?meter=${encodeURIComponent(meterNo)}`);
        Object.assign(state, { isLoading: false, nescoPostpaidData: data, error: null });
      } else if (state.provider === 'NESCO') {
        const data = await fetchJson(`/api/nesco?meter=${encodeURIComponent(meterNo)}`);
        Object.assign(state, {
          isLoading: false,
          name: data.name,
          fatherOrHusbandName: data.fatherOrHusbandName,
          address: data.address,
          mobile: data.mobile,
          office: data.office,
          feederName: data.feederName,
          consumerNo: data.consumerNo,
          meterSerialNo: data.meterNo,
          approvedLoadKw: data.approvedLoadKw,
          tariff: data.tariff,
          meterType: data.meterType,
          meterStatus: data.meterStatus,
          installDate: data.installDate,
          minRechargeAmount: data.minRechargeAmount,
          balance: data.balance,
          balanceAsOf: data.balanceAsOf,
          lastRecharge: data.lastRecharge,
          lastRechargeDate: data.lastRecharge ? data.lastRecharge.date : null,
          lastRechargeAmount: data.lastRecharge ? data.lastRecharge.rechargeAmount : null,
          predictionDaysRemaining: estimateDaysRemaining(data.balance, state.monthlyUsage),
          error: null,
        });
        checkAndNotify(meterNo, state.nickname, data.balance);
        refreshMonthlyUsageIfStale(meterNo, false);
      } else if (state.provider === 'DESCO') {
        const data = await fetchJson(`/api/desco?account=${encodeURIComponent(meterNo)}`);
        Object.assign(state, {
          isLoading: false,
          balance: data.balance,
          balanceAsOf: data.readingTime,
          lastRechargeDate: data.lastRecharge ? data.lastRecharge.rechargeDate : null,
          lastRechargeAmount: data.lastRecharge ? data.lastRecharge.totalAmount : null,
          predictionDaysRemaining: estimateDescoDaysRemaining(data.balance, data.monthlyConsumption),
          descoData: data,
          error: null,
        });
        checkAndNotify(meterNo, state.nickname, data.balance);
      } else if (state.provider === 'PGCL') {
        const data = await fetchJson(`/api/pgcl?customer=${encodeURIComponent(meterNo)}`);
        Object.assign(state, { isLoading: false, pgclData: data, error: null });
      }
    } catch (e) {
      state.isLoading = false;
      state.error = e.message || 'Failed to fetch data';
    }
    renderDashboard();
    if (currentDetailsMeter === meterNo) renderDetails(meterNo);
  }

  async function refreshAll() {
    if (meters.size === 0) return;
    await Promise.all(Array.from(meters.keys()).map((m) => refreshOne(m)));
    statusText = 'Updated: ' + new Date().toLocaleTimeString();
    renderDashboard();
  }

  let autoRefreshTimer = null;
  function startAutoRefresh() {
    if (autoRefreshTimer) clearInterval(autoRefreshTimer);
    autoRefreshTimer = setInterval(refreshAll, AUTO_REFRESH_MS);
  }

  // ---- add / remove meter ---------------------------------------------------

  function addMeter(meterNo, nickname, provider, billingType, onSuccess, onError) {
    if (!meterNo.trim()) {
      onError(`Please enter ${PROVIDERS[provider].hint.toLowerCase()}.`);
      return;
    }
    if (meters.has(meterNo)) {
      onError('This meter is already in your list.');
      return;
    }
    const resolvedType = resolveMeterType(provider, billingType);

    (async () => {
      try {
        let nick;
        const state = newMeterState(meterNo, '', provider, resolvedType);
        state.isLoading = false;

        if (provider === 'NESCO' && resolvedType === 'POSTPAID') {
          const data = await fetchJson(`/api/nesco/postpaid?meter=${encodeURIComponent(meterNo)}`);
          nick = nickname || data.name || meterNo;
          Object.assign(state, { nescoPostpaidData: data });
        } else if (provider === 'NESCO') {
          const data = await fetchJson(`/api/nesco?meter=${encodeURIComponent(meterNo)}`);
          nick = nickname || data.name || meterNo;
          Object.assign(state, {
            name: data.name,
            fatherOrHusbandName: data.fatherOrHusbandName,
            address: data.address,
            mobile: data.mobile,
            office: data.office,
            feederName: data.feederName,
            consumerNo: data.consumerNo,
            meterSerialNo: data.meterNo,
            approvedLoadKw: data.approvedLoadKw,
            tariff: data.tariff,
            meterType: data.meterType,
            meterStatus: data.meterStatus,
            installDate: data.installDate,
            minRechargeAmount: data.minRechargeAmount,
            balance: data.balance,
            balanceAsOf: data.balanceAsOf,
            lastRecharge: data.lastRecharge,
            lastRechargeDate: data.lastRecharge ? data.lastRecharge.date : null,
            lastRechargeAmount: data.lastRecharge ? data.lastRecharge.rechargeAmount : null,
          });
        } else if (provider === 'DESCO') {
          const data = await fetchJson(`/api/desco?account=${encodeURIComponent(meterNo)}`);
          nick = nickname || data.customerName || meterNo;
          Object.assign(state, {
            balance: data.balance,
            balanceAsOf: data.readingTime,
            lastRechargeDate: data.lastRecharge ? data.lastRecharge.rechargeDate : null,
            lastRechargeAmount: data.lastRecharge ? data.lastRecharge.totalAmount : null,
            predictionDaysRemaining: estimateDescoDaysRemaining(data.balance, data.monthlyConsumption),
            descoData: data,
          });
        } else {
          const data = await fetchJson(`/api/pgcl?customer=${encodeURIComponent(meterNo)}`);
          nick = nickname || data.customerName || meterNo;
          Object.assign(state, { pgclData: data });
        }

        state.nickname = nick;
        meters.set(meterNo, state);
        const infos = loadMeterInfos();
        infos.push({ meter: meterNo, nickname: nick, provider, billingType: resolvedType });
        saveMeterInfos(infos);

        renderDashboard();
        onSuccess();

        if (provider === 'NESCO' && resolvedType === 'PREPAID') refreshMonthlyUsageIfStale(meterNo, true);
      } catch (e) {
        onError(`Could not verify ${PROVIDERS[provider].hint.toLowerCase()}: ${e.message || 'unknown error'}`);
      }
    })();
  }

  function removeMeter(meterNo) {
    meters.delete(meterNo);
    saveMeterInfos(loadMeterInfos().filter((m) => m.meter !== meterNo));
    const cache = loadMonthlyCache();
    delete cache[meterNo];
    saveMonthlyCache(cache);
    if (currentDetailsMeter === meterNo) showDashboard();
    renderDashboard();
  }

  // ---- rendering: dashboard ---------------------------------------------------

  const dashboardEmptyEl = document.getElementById('dashboardEmpty');
  const dashboardLoadingEl = document.getElementById('dashboardLoading');
  const meterListEl = document.getElementById('meterList');
  const statusTextEl = document.getElementById('statusText');

  function providerBg(provider) {
    return provider === 'NESCO' ? 'var(--nesco-bg)' : provider === 'DESCO' ? 'var(--desco-bg)' : 'var(--pgcl-bg)';
  }
  function providerAccent(provider) {
    return provider === 'NESCO' ? 'var(--nesco-accent)' : provider === 'DESCO' ? 'var(--desco-accent)' : 'var(--pgcl-accent)';
  }

  function cardBodyHtml(state) {
    if (state.isLoading) {
      return `<div class="card-loading"><div class="spinner small"></div><span>Fetching data…</span></div>`;
    }
    if (state.error) {
      return `<div class="card-error">${warningSvg()}<span>${esc(state.error)}</span></div>`;
    }
    if (state.provider === 'PGCL') {
      const data = state.pgclData;
      const dueMessage = (data && data.dueStatusMessage) || '—';
      const isDue = !dueMessage.toLowerCase().includes('no due');
      const lastPayment = data && data.payments && data.payments[0];
      return `
        <div class="balance-amount" style="color:${isDue ? 'var(--balance-low)' : 'var(--balance-ok)'}">${esc(dueMessage)}</div>
        <hr class="card-divider" />
        ${infoRow('Last Payment', formatTaka(lastPayment && lastPayment.total))}
        ${infoRow('Bill Month', dash(lastPayment && lastPayment.billMonth))}
        ${infoRow('Collection Date', dash(lastPayment && lastPayment.collectionDate))}
        ${data && data.customerName ? infoRow('Customer', data.customerName) : ''}
      `;
    }
    if (state.provider === 'NESCO' && state.billingType === 'POSTPAID') {
      const data = state.nescoPostpaidData;
      const { message, isDue } = postpaidDueSummary(data);
      const lastBill = data && data.allBills && data.allBills[0];
      return `
        <div class="balance-amount" style="color:${isDue ? 'var(--balance-low)' : 'var(--balance-ok)'}">${esc(message)}</div>
        <hr class="card-divider" />
        ${infoRow('Last Bill', lastBill ? `${esc(lastBill.month)} ${esc(lastBill.year)}` : '—')}
        ${infoRow('Total Bill', formatTaka(lastBill && lastBill.totalBillTaka))}
        ${infoRow('Status', dash(lastBill && lastBill.paymentStatus))}
        ${data && data.name ? infoRow('Customer', data.name) : ''}
      `;
    }
    const isLow = state.balance !== null && state.balance !== undefined && state.balance < LOW_THRESHOLD;
    const customerName = state.name || (state.descoData && state.descoData.customerName);
    return `
      <div class="balance-amount" style="color:${isLow ? 'var(--balance-low)' : 'var(--balance-ok)'}">${formatTaka(state.balance)}</div>
      ${isLow ? '<div class="low-badge">LOW BALANCE</div>' : ''}
      ${state.balanceAsOf ? `<div class="as-of">as of ${esc(state.balanceAsOf)}</div>` : ''}
      <hr class="card-divider" />
      ${infoRow('Last Recharge', dash(state.lastRechargeDate))}
      ${infoRow('Amount', formatTaka(state.lastRechargeAmount))}
      ${customerName ? infoRow('Customer', customerName) : ''}
      ${infoRow('Estimated to last', formatDaysRemaining(state.predictionDaysRemaining))}
    `;
  }

  function infoRow(label, value) {
    return `<div class="info-row"><span class="label">${esc(label)}</span><span class="value">${value}</span></div>`;
  }
  function warningSvg() {
    return `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>`;
  }
  function trashSvg() {
    return `<svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>`;
  }

  function renderDashboard() {
    if (isInitialLoading) {
      dashboardLoadingEl.classList.remove('hidden');
      dashboardEmptyEl.classList.add('hidden');
      meterListEl.classList.add('hidden');
      return;
    }
    dashboardLoadingEl.classList.add('hidden');

    if (meters.size === 0) {
      dashboardEmptyEl.classList.remove('hidden');
      meterListEl.classList.add('hidden');
      statusTextEl.textContent = '';
      return;
    }
    dashboardEmptyEl.classList.add('hidden');
    meterListEl.classList.remove('hidden');

    meterListEl.innerHTML = '';
    for (const state of meters.values()) {
      const wrap = document.createElement('div');
      wrap.className = 'card-wrap';
      wrap.innerHTML = `
        <div class="card-delete-reveal"><button class="delete-btn" data-meter="${esc(state.meter)}">${trashSvg()}</button></div>
        <div class="meter-card" style="background:${providerBg(state.provider)}" data-meter="${esc(state.meter)}">
          <div class="card-head">
            <div>
              <p class="card-nickname">${esc(state.nickname)}</p>
              <p class="card-meter-no">${esc(state.meter)}</p>
              <span class="provider-badge" style="background:${providerAccent(state.provider)}">${esc(PROVIDERS[state.provider].label)}</span>
            </div>
          </div>
          <div class="card-body">${cardBodyHtml(state)}</div>
          <button class="outlined-btn details-btn" data-meter="${esc(state.meter)}">Meter Details</button>
        </div>
      `;
      meterListEl.appendChild(wrap);
      attachSwipeToDelete(wrap);
    }

    meterListEl.querySelectorAll('.details-btn').forEach((btn) => {
      btn.addEventListener('click', () => showDetails(btn.dataset.meter));
    });
    meterListEl.querySelectorAll('.delete-btn').forEach((btn) => {
      btn.addEventListener('click', () => openDeleteDialog(btn.dataset.meter));
    });

    statusTextEl.textContent = statusText;
  }

  const SWIPE_REVEAL_PX = 72;
  function attachSwipeToDelete(wrapEl) {
    const card = wrapEl.querySelector('.meter-card');
    let startX = 0;
    let baseX = 0;
    let currentX = 0;
    let dragging = false;

    card.addEventListener('pointerdown', (e) => {
      if (e.target.closest('button')) return; // let button clicks through untouched
      dragging = true;
      startX = e.clientX;
      baseX = currentX;
      document.addEventListener('pointermove', onMove);
      document.addEventListener('pointerup', onUp);
      document.addEventListener('pointercancel', onUp);
    });

    function onMove(e) {
      if (!dragging) return;
      const dx = e.clientX - startX;
      currentX = Math.min(0, Math.max(-SWIPE_REVEAL_PX, baseX + dx));
      card.style.transform = `translateX(${currentX}px)`;
    }
    function onUp() {
      if (!dragging) return;
      dragging = false;
      document.removeEventListener('pointermove', onMove);
      document.removeEventListener('pointerup', onUp);
      document.removeEventListener('pointercancel', onUp);
      currentX = currentX < -SWIPE_REVEAL_PX / 2 ? -SWIPE_REVEAL_PX : 0;
      card.style.transform = `translateX(${currentX}px)`;
    }
  }

  // ---- rendering: details ---------------------------------------------------

  const dashboardScreenEl = document.getElementById('dashboard');
  const detailsScreenEl = document.getElementById('detailsScreen');
  const detailsContentEl = document.getElementById('detailsContent');
  const fabEl = document.getElementById('fab');

  function showDashboard() {
    currentDetailsMeter = null;
    dashboardScreenEl.classList.remove('hidden');
    detailsScreenEl.classList.add('hidden');
    fabEl.classList.remove('hidden');
    renderDashboard();
  }

  function showDetails(meterNo) {
    currentDetailsMeter = meterNo;
    dashboardScreenEl.classList.add('hidden');
    detailsScreenEl.classList.remove('hidden');
    fabEl.classList.add('hidden');
    renderDetails(meterNo);
  }

  const expandState = {}; // per-meter expand/collapse flags, keyed by "meter:section"

  function renderDetails(meterNo) {
    const state = meters.get(meterNo);
    if (!state) return showDashboard();

    let body;
    if (state.provider === 'NESCO' && state.billingType === 'POSTPAID') body = nescoPostpaidDetailsHtml(state);
    else if (state.provider === 'NESCO') body = nescoDetailsHtml(state);
    else if (state.provider === 'DESCO') body = descoDetailsHtml(state);
    else body = pgclDetailsHtml(state);

    detailsContentEl.innerHTML = `
      <div class="details-topbar">
        <button class="icon-btn" id="detailsBackBtn">${backSvg()}</button>
        <h2>${esc(state.nickname)}</h2>
      </div>
      <div class="screen">${body}</div>
    `;
    document.getElementById('detailsBackBtn').addEventListener('click', showDashboard);

    detailsContentEl.querySelectorAll('[data-toggle]').forEach((el) => {
      el.addEventListener('click', () => {
        const key = `${meterNo}:${el.dataset.toggle}`;
        expandState[key] = !expandState[key];
        renderDetails(meterNo);
      });
    });
    detailsContentEl.querySelectorAll('[data-toggle-sub]').forEach((el) => {
      el.addEventListener('click', () => {
        const key = `${meterNo}:${el.dataset.toggleSub}`;
        expandState[key] = !expandState[key];
        renderDetails(meterNo);
      });
    });
  }

  function backSvg() {
    return `<svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>`;
  }
  function chevron(expanded) {
    return expanded
      ? `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z"/></svg>`
      : `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z"/></svg>`;
  }

  function nescoDetailsHtml(state) {
    const isLow = state.balance !== null && state.balance !== undefined && state.balance < 150.0;
    const lr = state.lastRecharge;
    const showLastRecharge = !!expandState[`${state.meter}:lastRecharge`];
    const showMonthly = !!expandState[`${state.meter}:monthlyUsage`];

    return `
      <div class="details-card">
        <div class="headline" style="color:${isLow ? 'var(--balance-low)' : 'var(--balance-ok)'}">${formatTaka(state.balance)}</div>
        ${state.balanceAsOf ? `<div class="as-of">as of ${esc(state.balanceAsOf)}</div>` : ''}
        ${infoRow('Estimated to last', formatDaysRemaining(state.predictionDaysRemaining))}
        <hr class="card-divider" />
        ${infoRow('Customer Name', dash(state.name))}
        ${infoRow("Father/Husband's Name", dash(state.fatherOrHusbandName))}
        ${infoRow('Address', dash(state.address))}
        ${infoRow('Mobile', dash(state.mobile))}
        ${infoRow('Office', dash(state.office))}
        ${infoRow('Feeder', dash(state.feederName))}
        ${infoRow('Consumer Number', dash(state.consumerNo))}
        ${infoRow('Meter Number', dash(state.meterSerialNo))}
        ${infoRow('Approved Load (kW)', dash(state.approvedLoadKw))}
        ${infoRow('Approved Tariff', dash(state.tariff))}
        ${infoRow('Meter Type', dash(state.meterType))}
        ${infoRow('Meter Status', dash(state.meterStatus))}
        ${infoRow('Meter Install Date', dash(state.installDate))}
        ${infoRow('Min Recharge Amount', formatTaka(state.minRechargeAmount))}
      </div>

      <div class="details-card">
        <button class="expandable-header" data-toggle="lastRecharge">Last Recharge ${chevron(showLastRecharge)}</button>
        ${showLastRecharge ? `<div class="expandable-body">${
          lr
            ? [
                infoRow('Token Number', dash(lr.tokenNo)),
                infoRow('Meter Rent', formatTaka(lr.meterRent)),
                infoRow('Demand Charge', formatTaka(lr.demandCharge)),
                infoRow('PFC Charge', formatTaka(lr.pfcCharge)),
                infoRow('VAT', formatTaka(lr.vat)),
                infoRow('Paid Arrears/Penalty', formatTaka(lr.paidArrears)),
                infoRow('Rebate', formatTaka(lr.rebate)),
                infoRow('Electricity', formatTaka(lr.electricityCharge)),
                infoRow('Recharge Amount', formatTaka(lr.rechargeAmount)),
                infoRow('Possible Units (kWh)', dash(lr.possibleUnitsKwh)),
                infoRow('Method', dash(lr.method)),
                infoRow('Date', dash(lr.date)),
                infoRow('Status', dash(lr.status)),
              ].join('')
            : '<p>No recharge history available yet.</p>'
        }</div>` : ''}
      </div>

      <div class="details-card">
        <button class="expandable-header" data-toggle="monthlyUsage">মাসিক ব্যবহার (Monthly Usage) ${chevron(showMonthly)}</button>
        ${showMonthly ? `<div class="expandable-body">${
          state.monthlyUsage.length === 0
            ? '<p>Loading monthly usage…</p>'
            : state.monthlyUsage.map((row, i) => nescoMonthlyRowHtml(state.meter, row, i)).join('')
        }</div>` : ''}
      </div>
    `;
  }

  function nescoMonthlyRowHtml(meterNo, row, index) {
    const key = `monthlyRow${index}`;
    const expanded = !!expandState[`${meterNo}:${key}`];
    return `
      <div class="sub-card">
        <div class="sub-card-head" data-toggle-sub="${key}">
          <span class="title">${esc(row.month)} ${esc(row.year)}</span>
          ${chevron(expanded)}
        </div>
        ${infoRow('Total Usage/Deduction', formatTaka(row.totalUsage))}
        ${infoRow('End of Month Balance', formatTaka(row.endOfMonthBalance))}
        ${expanded ? [
          infoRow('Total Recharge', formatTaka(row.totalRecharge)),
          infoRow('Rebate', formatTaka(row.rebate)),
          infoRow('Electricity Used', formatTaka(row.electricityUsedTaka)),
          infoRow('Meter Rent', formatTaka(row.meterRent)),
          infoRow('Demand Charge', formatTaka(row.demandCharge)),
          infoRow('PFC Charge', formatTaka(row.pfcCharge)),
          infoRow('Paid Arrears/Penalty', formatTaka(row.paidArrears)),
          infoRow('VAT', formatTaka(row.vat)),
          infoRow('Units Used (kWh)', dash(row.unitsUsedKwh)),
        ].join('') : ''}
      </div>
    `;
  }

  function descoDetailsHtml(state) {
    const data = state.descoData;
    const isLow = state.balance !== null && state.balance !== undefined && state.balance < 150.0;
    const showLastRecharge = !!expandState[`${state.meter}:lastRecharge`];
    const showMonthly = !!expandState[`${state.meter}:monthlyConsumption`];
    const lr = data && data.lastRecharge;
    const rows = (data && data.monthlyConsumption) || [];

    return `
      <div class="details-card">
        <div class="headline" style="color:${isLow ? 'var(--balance-low)' : 'var(--balance-ok)'}">${formatTaka(state.balance)}</div>
        ${state.balanceAsOf ? `<div class="as-of">Reading time: ${esc(state.balanceAsOf)}</div>` : ''}
        ${infoRow('Estimated to last', formatDaysRemaining(state.predictionDaysRemaining))}
        <hr class="card-divider" />
        ${infoRow('Used This Month (kWh)', dash(data && data.usedThisMonthKwh))}
        ${infoRow('In BDT', formatTaka(data && data.currentMonthConsumptionTaka))}
        ${infoRow('Recharged This Month', formatTaka(data && data.rechargedThisMonth))}
        ${infoRow('Recharged This Year', formatTaka(data && data.rechargedThisYear))}
        ${infoRow('Max Load Last Month', data && data.maxLoadLastMonth !== null && data.maxLoadLastMonth !== undefined ? `${data.maxLoadLastMonth} kW` : '—')}
        ${infoRow('Max Load Last Year', data && data.maxLoadLastYear !== null && data.maxLoadLastYear !== undefined ? `${data.maxLoadLastYear} kW` : '—')}
        <hr class="card-divider" />
        ${infoRow('Name', dash(data && data.customerName))}
        ${infoRow('Account No', dash((data && data.accountNo) || state.meter))}
        ${infoRow('Meter No', dash(data && data.meterNo))}
        ${infoRow('Contact No', dash(data && data.contactNo))}
        ${infoRow('Tariff Solution', dash(data && data.tariffSolution))}
        ${infoRow('S & D', dash(data && data.sdName))}
        ${infoRow('Transformer', dash(data && data.transformer))}
        ${infoRow('Feeder', dash(data && data.feederName))}
        ${infoRow('Installation Date', dash(data && data.installationDate))}
        ${infoRow('Registration Date', dash(data && data.registerDate))}
        ${infoRow('Meter Model', dash(data && data.meterModel))}
        ${infoRow('Phase Type', dash(data && data.phaseType))}
        ${infoRow('Sanctioned Load', dash(data && data.sanctionLoad))}
        ${infoRow('Installation Address', dash(data && data.installationAddress))}
      </div>

      <div class="details-card">
        <button class="expandable-header" data-toggle="lastRecharge">Last Recharge ${chevron(showLastRecharge)}</button>
        ${showLastRecharge ? `<div class="expandable-body">${
          lr
            ? [
                infoRow('Recharge Amount', formatTaka(lr.totalAmount)),
                infoRow('VAT', formatTaka(lr.vat)),
                infoRow('Energy Amount', formatTaka(lr.energyAmount)),
                infoRow('Recharge Time', dash(lr.rechargeDate)),
              ].join('')
            : '<p>No recharge history available yet.</p>'
        }</div>` : ''}
      </div>

      <div class="details-card">
        <button class="expandable-header" data-toggle="monthlyConsumption">Monthly Consumption ${chevron(showMonthly)}</button>
        ${showMonthly ? `<div class="expandable-body">${
          rows.length === 0
            ? '<p>Loading monthly consumption…</p>'
            : rows.map((row) => `
                <div class="sub-card">
                  <div class="title">${esc(row.month)}</div>
                  ${infoRow('Consumed (BDT)', formatTaka(row.consumedTaka))}
                  ${infoRow('Consumed (kWh)', dash(row.consumedUnit))}
                  ${infoRow('Maximum Demand (kW)', dash(row.maximumDemand))}
                </div>
              `).join('')
        }</div>` : ''}
      </div>
    `;
  }

  function pgclDetailsHtml(state) {
    const data = state.pgclData;
    const dueMessage = (data && data.dueStatusMessage) || '—';
    const isDue = !dueMessage.toLowerCase().includes('no due');
    const payments = (data && data.payments) || [];

    return `
      <div class="details-card">
        <div class="headline" style="font-size:18px;color:${isDue ? 'var(--balance-low)' : 'var(--balance-ok)'}">${esc(dueMessage)}</div>
        <hr class="card-divider" />
        ${infoRow('Customer Code', dash((data && data.customerCode) || state.meter))}
        ${infoRow('Customer Name', dash(data && data.customerName))}
        ${infoRow('Address', dash(data && data.address))}
        ${infoRow('Phone', dash(data && data.phone))}
        ${infoRow('Category', dash(data && data.category))}
        ${infoRow('Status', dash(data && data.status))}
        ${infoRow('Connection Date', dash(data && data.connectionDate))}
        ${infoRow('Metered Status', dash(data && data.meteredStatus))}
        ${infoRow('Max Load', dash(data && data.maxLoad))}
        ${infoRow('Min Load', dash(data && data.minLoad))}
        ${infoRow('Burner Count', dash(data && data.burnerCount))}
        ${infoRow('Regional Office', dash(data && data.regionalOffice))}
      </div>
      <div class="details-card">
        <div style="font-weight:600;margin-bottom:8px;">Last 3 Months' Payments</div>
        ${payments.length === 0
          ? '<p>No payment history available.</p>'
          : payments.map((p) => `
              <div class="sub-card">
                <div class="title">${esc(p.billMonth)}</div>
                ${infoRow('Bank', dash(p.bank))}
                ${infoRow('Billed Amount', formatTaka(p.billedAmount))}
                ${infoRow('Surcharge', formatTaka(p.surcharge))}
                ${infoRow('Total', formatTaka(p.total))}
                ${infoRow('Collection Date', dash(p.collectionDate))}
              </div>
            `).join('')}
      </div>
    `;
  }

  function postpaidBillRowHtml(bill) {
    return `
      <div class="sub-card">
        <div class="title">${esc(bill.month)} ${esc(bill.year)}</div>
        ${infoRow('Bill No', dash(bill.billNo))}
        ${infoRow('Total Bill', formatTaka(bill.totalBillTaka))}
        ${infoRow('Late Fee', formatTaka(bill.lateFeeTaka))}
        ${infoRow('Due Date', dash(bill.dueDate))}
        ${infoRow('Paid Amount', formatTaka(bill.paidAmountTaka))}
        ${infoRow('Paid Date', dash(bill.paidDate))}
        ${infoRow('Payment Method', dash(bill.paymentMethod))}
        ${infoRow('Payment Status', dash(bill.paymentStatus))}
      </div>
    `;
  }

  function nescoPostpaidDetailsHtml(state) {
    const data = state.nescoPostpaidData;
    const { message, isDue } = postpaidDueSummary(data);
    const dueBills = (data && data.dueBills) || [];
    const allBills = (data && data.allBills) || [];
    const showDueBills = !!expandState[`${state.meter}:dueBills`];
    const showAllBills = !!expandState[`${state.meter}:allBills`];

    return `
      <div class="details-card">
        <div class="headline" style="font-size:20px;color:${isDue ? 'var(--balance-low)' : 'var(--balance-ok)'}">${esc(message)}</div>
        <hr class="card-divider" />
        ${infoRow('Customer Name', dash(data && data.name))}
        ${infoRow("Father/Husband's Name", dash(data && data.fatherOrHusbandName))}
        ${infoRow('Address', dash(data && data.address))}
        ${infoRow('Mobile', dash(data && data.mobile))}
        ${infoRow('Office', dash(data && data.office))}
        ${infoRow('Feeder', dash(data && data.feederName))}
        ${infoRow('Consumer Number', dash(data && data.consumerNo))}
        ${infoRow("Accounts Office No", dash(data && data.accountsOfficeNo))}
        ${infoRow('Previous Consumer No', dash(data && data.previousConsumerNo))}
        ${infoRow('Meter Number', dash(data && data.meterNo))}
        ${infoRow('Approved Load (kW)', dash(data && data.approvedLoadKw))}
        ${infoRow('Approved Tariff', dash(data && data.tariff))}
        ${infoRow('Meter Status', dash(data && data.meterStatus))}
        ${infoRow('Connection Phase', dash(data && data.connectionPhase))}
        ${infoRow('Bill Group', dash(data && data.billGroup))}
        ${infoRow('Book No', dash(data && data.bookNo))}
        ${infoRow('Work Order', dash(data && data.workOrder))}
      </div>

      <div class="details-card">
        <button class="expandable-header" data-toggle="dueBills">Due Bills ${chevron(showDueBills)}</button>
        ${showDueBills ? `<div class="expandable-body">${
          dueBills.length === 0
            ? '<p>No due bills.</p>'
            : dueBills.map(postpaidBillRowHtml).join('')
        }</div>` : ''}
      </div>

      <div class="details-card">
        <button class="expandable-header" data-toggle="allBills">সকল বিল (All Bills) ${chevron(showAllBills)}</button>
        ${showAllBills ? `<div class="expandable-body">${
          allBills.length === 0
            ? '<p>Loading bill history…</p>'
            : allBills.map(postpaidBillRowHtml).join('')
        }</div>` : ''}
      </div>
    `;
  }

  // ---- add meter dialog -----------------------------------------------------

  const addDialogOverlay = document.getElementById('addDialogOverlay');
  const providerSegmented = document.getElementById('providerSegmented');
  const meterTypeSegmented = document.getElementById('meterTypeSegmented');
  const meterFieldLabel = document.getElementById('meterFieldLabel');
  const meterInput = document.getElementById('meterInput');
  const nicknameInput = document.getElementById('nicknameInput');
  const addError = document.getElementById('addError');
  const addCancelBtn = document.getElementById('addCancelBtn');
  const addConfirmBtn = document.getElementById('addConfirmBtn');

  let selectedProvider = 'NESCO';
  let selectedMeterType = 'PREPAID';
  let isSubmittingAdd = false;

  function openAddDialog() {
    selectedProvider = 'NESCO';
    selectedMeterType = 'PREPAID';
    meterInput.value = '';
    nicknameInput.value = '';
    addError.classList.add('hidden');
    isSubmittingAdd = false;
    updateAddDialogProvider();
    addDialogOverlay.classList.remove('hidden');
    meterInput.focus();
  }
  function closeAddDialog() {
    if (isSubmittingAdd) return;
    addDialogOverlay.classList.add('hidden');
  }
  function updateAddDialogProvider() {
    providerSegmented.querySelectorAll('.segment').forEach((btn) => {
      btn.classList.toggle('selected', btn.dataset.provider === selectedProvider);
    });
    meterFieldLabel.textContent = PROVIDERS[selectedProvider].hint;

    const forced = PROVIDERS[selectedProvider].forcedMeterType;
    if (forced) selectedMeterType = forced;
    meterTypeSegmented.classList.toggle('locked', !!forced);
    meterTypeSegmented.querySelectorAll('.segment').forEach((btn) => {
      btn.disabled = !!forced;
      btn.classList.toggle('selected', btn.dataset.meterType === selectedMeterType);
    });
  }
  providerSegmented.querySelectorAll('.segment').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (isSubmittingAdd) return;
      selectedProvider = btn.dataset.provider;
      addError.classList.add('hidden');
      updateAddDialogProvider();
    });
  });
  meterTypeSegmented.querySelectorAll('.segment').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (isSubmittingAdd || PROVIDERS[selectedProvider].forcedMeterType) return;
      selectedMeterType = btn.dataset.meterType;
      addError.classList.add('hidden');
      updateAddDialogProvider();
    });
  });
  addCancelBtn.addEventListener('click', closeAddDialog);
  addDialogOverlay.addEventListener('click', (e) => {
    if (e.target === addDialogOverlay) closeAddDialog();
  });

  function setAddSubmitting(submitting) {
    isSubmittingAdd = submitting;
    addConfirmBtn.disabled = submitting;
    addConfirmBtn.innerHTML = submitting ? `<div class="spinner small"></div>` : 'Add Meter';
  }

  addConfirmBtn.addEventListener('click', () => {
    const trimmed = meterInput.value.trim();
    if (!trimmed) {
      addError.textContent = `Please enter ${PROVIDERS[selectedProvider].hint.toLowerCase()}.`;
      addError.classList.remove('hidden');
      return;
    }
    setAddSubmitting(true);
    addError.classList.add('hidden');
    addMeter(
      trimmed,
      nicknameInput.value.trim(),
      selectedProvider,
      selectedMeterType,
      () => {
        setAddSubmitting(false);
        addDialogOverlay.classList.add('hidden');
      },
      (message) => {
        setAddSubmitting(false);
        addError.textContent = message;
        addError.classList.remove('hidden');
      }
    );
  });

  // ---- delete dialog ---------------------------------------------------------

  const deleteDialogOverlay = document.getElementById('deleteDialogOverlay');
  const deleteDialogText = document.getElementById('deleteDialogText');
  const deleteCancelBtn = document.getElementById('deleteCancelBtn');
  const deleteConfirmBtn = document.getElementById('deleteConfirmBtn');
  let pendingDeleteMeter = null;

  function openDeleteDialog(meterNo) {
    const state = meters.get(meterNo);
    if (!state) return;
    pendingDeleteMeter = meterNo;
    deleteDialogText.textContent = `Do you really want to delete "${state.nickname}" (${state.meter})? This can't be undone.`;
    deleteDialogOverlay.classList.remove('hidden');
  }
  function closeDeleteDialog(collapseCard) {
    deleteDialogOverlay.classList.add('hidden');
    if (collapseCard) renderDashboard();
    pendingDeleteMeter = null;
  }
  deleteCancelBtn.addEventListener('click', () => closeDeleteDialog(true));
  deleteDialogOverlay.addEventListener('click', (e) => {
    if (e.target === deleteDialogOverlay) closeDeleteDialog(true);
  });
  deleteConfirmBtn.addEventListener('click', () => {
    const meterNo = pendingDeleteMeter;
    deleteDialogOverlay.classList.add('hidden');
    pendingDeleteMeter = null;
    if (meterNo) removeMeter(meterNo);
  });

  // ---- top-level wiring -------------------------------------------------------

  document.getElementById('fab').addEventListener('click', openAddDialog);
  document.getElementById('refreshBtn').addEventListener('click', refreshAll);

  const SPLASH_DURATION_MS = 1800;
  setTimeout(() => {
    const splash = document.getElementById('splashScreen');
    if (!splash) return;
    splash.classList.add('fade-out');
    setTimeout(() => splash.remove(), 400);
  }, SPLASH_DURATION_MS);

  if (window.Notification && Notification.permission === 'default') {
    Notification.requestPermission().catch(() => {});
  }

  async function init() {
    let infos = await fetchSavedMeters();
    if (infos === null) {
      // Server file unreachable — fall back to whatever the browser still has.
      infos = loadMeterInfos();
    } else if (infos.length === 0) {
      // First run against the server file: migrate any meters saved in this browser.
      const local = loadMeterInfos();
      if (local.length > 0) {
        infos = local;
        pushSavedMeters(local);
      }
    }
    try {
      localStorage.setItem(METERS_KEY, JSON.stringify(infos));
    } catch {}
    const cache = loadMonthlyCache();
    for (const info of infos) {
      const state = newMeterState(info.meter, info.nickname, info.provider, info.billingType);
      const cached = cache[info.meter];
      if (cached) state.monthlyUsage = cached.rows;
      meters.set(info.meter, state);
    }
    isInitialLoading = false;
    renderDashboard();
    startAutoRefresh();
    await refreshAll();
  }

  init();
})();
