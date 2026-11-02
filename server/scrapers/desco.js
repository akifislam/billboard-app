const axios = require('axios');
const https = require('https');

// DESCO's certificate chain fails strict verification (the community DESCO client
// libraries disable SSL verification for the same reason), so this client — only
// this one — trusts any certificate to get through the handshake.
const insecureAgent = new https.Agent({ rejectUnauthorized: false });

const BASE_URL = 'https://prepaid.desco.org.bd/api/unified/customer';
const meterNumberPattern = /^[A-Za-z0-9-]{4,30}$/;

function pad(n) {
  return String(n).padStart(2, '0');
}
function dayFormat(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function monthFormat(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`;
}

async function get(url, params) {
  const response = await axios.get(url, {
    params,
    httpsAgent: insecureAgent,
    validateStatus: () => true,
    headers: {
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
      Accept: 'application/json, text/plain, */*',
    },
  });
  if (response.status < 200 || response.status >= 300) {
    throw new Error(`DESCO request returned HTTP ${response.status}`);
  }
  return typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
}

function dataObjectOf(json) {
  const d = json && json.data;
  return d && typeof d === 'object' && !Array.isArray(d) ? d : null;
}
function dataArrayOf(json) {
  const d = json && json.data;
  return Array.isArray(d) ? d : null;
}
function getString(obj, key) {
  if (!obj) return null;
  const v = obj[key];
  if (v === null || v === undefined) return null;
  const s = String(v);
  return s.trim() === '' ? null : s;
}
function getDouble(obj, key) {
  if (!obj) return null;
  const v = obj[key];
  if (v === null || v === undefined) return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

function toRechargeRow(obj) {
  return {
    rechargeDate: getString(obj, 'rechargeDate'),
    totalAmount: getDouble(obj, 'totalAmount'),
    vat: getDouble(obj, 'VAT') ?? getDouble(obj, 'vat'),
    energyAmount: getDouble(obj, 'energyAmount'),
  };
}

function toMonthlyRow(obj) {
  return {
    month: getString(obj, 'month') || '',
    consumedTaka: getDouble(obj, 'consumedTaka'),
    consumedUnit: getDouble(obj, 'consumedUnit'),
    maximumDemand: getDouble(obj, 'maximumDemand'),
  };
}

async function scrapeMeter(accountNo) {
  const trimmed = accountNo.trim();
  if (!meterNumberPattern.test(trimmed)) {
    throw new Error('Invalid account number');
  }

  // DESCO's customer portal accepts either the account number or the meter number, but
  // the API only matches when the identifier is sent under the query param name matching
  // what it actually is (accountNo vs meterNo). Probe meterNo first (that's what's printed
  // on the meter and what most users have on hand), then fall back to accountNo, and reuse
  // whichever param name matched for the rest of the calls below.
  let paramName = null;
  let balanceData = null;
  for (const candidate of ['meterNo', 'accountNo']) {
    const json = await get(`${BASE_URL}/getBalance`, { [candidate]: trimmed });
    const data = dataObjectOf(json);
    if (data) {
      paramName = candidate;
      balanceData = data;
      break;
    }
  }
  if (!paramName) throw new Error('DESCO account/meter number not found');

  const infoJson = await get(`${BASE_URL}/getCustomerInfo`, { [paramName]: trimmed });
  const infoData = dataObjectOf(infoJson);

  const now = new Date();
  const yearAgo = new Date(now);
  yearAgo.setDate(yearAgo.getDate() - 365);
  const rechargeJson = await get(`${BASE_URL}/getRechargeHistory`, {
    [paramName]: trimmed,
    dateFrom: dayFormat(yearAgo),
    dateTo: dayFormat(now),
  });
  const rechargeHistory = (dataArrayOf(rechargeJson) || []).map(toRechargeRow);

  const twelveMonthsAgo = new Date(now);
  twelveMonthsAgo.setMonth(twelveMonthsAgo.getMonth() - 11);
  const monthlyJson = await get(`${BASE_URL}/getCustomerMonthlyConsumption`, {
    [paramName]: trimmed,
    monthFrom: monthFormat(twelveMonthsAgo),
    monthTo: monthFormat(now),
  });
  const monthlyConsumption = (dataArrayOf(monthlyJson) || []).map(toMonthlyRow);

  const sortedRecharges = [...rechargeHistory].sort((a, b) => {
    const da = a.rechargeDate || '';
    const db = b.rechargeDate || '';
    return db.localeCompare(da);
  });
  const lastRecharge = sortedRecharges[0] || null;

  const currentMonthKey = monthFormat(now);
  const currentYearPrefix = String(now.getFullYear());

  const rechargedThisMonth = sortedRecharges
    .filter((r) => (r.rechargeDate || '').startsWith(currentMonthKey))
    .reduce((sum, r) => sum + (r.totalAmount || 0), 0);
  const rechargedThisYear = sortedRecharges
    .filter((r) => (r.rechargeDate || '').startsWith(currentYearPrefix))
    .reduce((sum, r) => sum + (r.totalAmount || 0), 0);

  const currentMonthRow = monthlyConsumption.find((r) => r.month === currentMonthKey) || null;
  const maxLoadLastYear = monthlyConsumption
    .filter((r) => r.month.startsWith(currentYearPrefix))
    .map((r) => r.maximumDemand)
    .filter((v) => v !== null && v !== undefined)
    .reduce((max, v) => (max === null || v > max ? v : max), null);

  return {
    accountNo: getString(balanceData, 'accountNo') || trimmed,
    meterNo: getString(balanceData, 'meterNo') || getString(infoData, 'meterNo'),
    balance: getDouble(balanceData, 'balance'),
    currentMonthConsumptionTaka: getDouble(balanceData, 'currentMonthConsumption'),
    readingTime: getString(balanceData, 'readingTime'),
    customerName: getString(infoData, 'customerName'),
    contactNo: getString(infoData, 'contactNo'),
    feederName: getString(infoData, 'feederName'),
    installationAddress: getString(infoData, 'installationAddress'),
    installationDate: getString(infoData, 'installationDate'),
    phaseType: getString(infoData, 'phaseType'),
    registerDate: getString(infoData, 'registerDate'),
    sanctionLoad: getString(infoData, 'sanctionLoad'),
    tariffSolution: getString(infoData, 'tariffSolution'),
    meterModel: getString(infoData, 'meterModel'),
    transformer: getString(infoData, 'transformer'),
    sdName: getString(infoData, 'SDName') || getString(infoData, 'sdName'),
    lastRecharge,
    rechargedThisMonth,
    rechargedThisYear,
    usedThisMonthKwh: currentMonthRow ? currentMonthRow.consumedUnit : null,
    maxLoadLastMonth: currentMonthRow ? currentMonthRow.maximumDemand : null,
    maxLoadLastYear,
    monthlyConsumption: [...monthlyConsumption].sort((a, b) => b.month.localeCompare(a.month)),
    rechargeHistory: sortedRecharges,
  };
}

module.exports = { scrapeMeter };
