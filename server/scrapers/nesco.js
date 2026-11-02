const axios = require('axios');
const cheerio = require('cheerio');

const NESCO_URL = 'https://customer.nesco.gov.bd/pre/panel';
const AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';
const SUBMIT_RECHARGE_HISTORY = 'রিচার্জ হিস্ট্রি';
const SUBMIT_MONTHLY_USAGE = 'মাসিক ব্যবহার';

const meterNumberPattern = /^[A-Za-z0-9-]{4,30}$/;
const csrfMetaPattern = /name="csrf-token"\s+content="([^"]+)"/;
const csrfInputPattern = /name="_token"\s+value="([^"]+)"/;
const balanceAsOfPattern =
  /(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{4}[^<)]*?[AP]M)/;

const browserHeaders = {
  'User-Agent': AGENT,
  Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.9,bn;q=0.8',
  'Cache-Control': 'no-cache',
  Pragma: 'no-cache',
};

function upstreamMessage(phase, code) {
  if (code === 403) return `NESCO blocked the ${phase} request (HTTP 403)`;
  return `NESCO ${phase} request returned HTTP ${code}`;
}

function stripTags(s) {
  return cheerio
    .load(`<div>${s}</div>`)('div')
    .text()
    .trim();
}

function toNum(s) {
  if (s === null || s === undefined || s === '') return null;
  const cleaned = String(s).replace(/[^\d.-]/g, '');
  if (cleaned === '' || cleaned === '-') return null;
  const n = Number(cleaned);
  return Number.isNaN(n) ? null : n;
}

/**
 * Read a value from NESCO's label/input detail fields.
 *
 * Do not scan a fixed-size slice of raw HTML here. The amount of markup between a
 * label and its input changes, and the balance field in particular now starts more
 * than 600 characters after its label. Walking the relevant DOM nodes also handles
 * either quote style and prevents a missing input from borrowing the next field.
 */
function panelField($, labelPrefix) {
  const nodes = $('label, input').toArray();
  const labelIndex = nodes.findIndex(
    (node) => node.tagName === 'label' && $(node).text().trim().startsWith(labelPrefix),
  );
  if (labelIndex < 0) return null;

  for (let i = labelIndex + 1; i < nodes.length; i += 1) {
    const node = nodes[i];
    if (node.tagName === 'label') return null;
    if (node.tagName === 'input') {
      const value = $(node).attr('value');
      if (value === undefined) return null;
      const cleaned = stripTags(value).trim();
      return cleaned === '' ? null : cleaned;
    }
  }
  return null;
}

function firstRechargeRow($) {
  let result = [];
  $('table').each((_, table) => {
    const rows = $(table).find('tr');
    if (rows.length === 0) return;
    const headerCells = $(rows[0]).find('th, td');
    const headerText = headerCells
      .map((__, el) => $(el).text())
      .get()
      .join(' ');
    if (headerText.includes('টোকেন') || headerText.includes('রিচার্জের তারিখ')) {
      if (rows.length > 1) {
        result = $(rows[1])
          .find('th, td')
          .map((__, el) => $(el).text().trim())
          .get();
        return false;
      }
    }
  });
  return result;
}

function toRechargeRecord(rr) {
  if (rr.length < 15) return null;
  return {
    tokenNo: rr[2] || null,
    meterRent: toNum(rr[3]),
    demandCharge: toNum(rr[4]),
    pfcCharge: toNum(rr[5]),
    vat: toNum(rr[6]),
    paidArrears: toNum(rr[7]),
    rebate: toNum(rr[8]),
    electricityCharge: toNum(rr[9]),
    rechargeAmount: toNum(rr[10]),
    possibleUnitsKwh: toNum(rr[11]),
    method: rr[12] || null,
    date: rr[13] || null,
    status: rr[14] || null,
  };
}

async function postToPanel(meterNo, submitValue) {
  const trimmed = meterNo.trim();
  if (!meterNumberPattern.test(trimmed)) {
    throw new Error('Invalid meter number');
  }

  const getResponse = await axios.get(NESCO_URL, {
    headers: browserHeaders,
    validateStatus: () => true,
  });
  if (getResponse.status < 200 || getResponse.status >= 300) {
    throw new Error(upstreamMessage('GET', getResponse.status));
  }
  const getHtml = getResponse.data || '';
  const setCookie = getResponse.headers['set-cookie'] || [];
  const cookies = setCookie.map((c) => c.split(';')[0]).join('; ');

  const tokenMatch = csrfMetaPattern.exec(getHtml) || csrfInputPattern.exec(getHtml);
  if (!tokenMatch) throw new Error('CSRF token not found on GET');
  const token = tokenMatch[1];

  const params = new URLSearchParams();
  params.append('_token', token);
  params.append('cust_no', trimmed);
  params.append('submit', submitValue);

  const postHeaders = {
    ...browserHeaders,
    Cookie: cookies,
    Origin: 'https://customer.nesco.gov.bd',
    Referer: NESCO_URL,
    'Content-Type': 'application/x-www-form-urlencoded',
  };

  const postResponse = await axios.post(NESCO_URL, params.toString(), {
    headers: postHeaders,
    validateStatus: () => true,
  });
  if (postResponse.status < 200 || postResponse.status >= 300) {
    throw new Error(upstreamMessage('POST', postResponse.status));
  }
  return postResponse.data || '';
}

function parsePanel(html) {
  const $ = cheerio.load(html);
  const lastRecharge = toRechargeRecord(firstRechargeRow($));
  const balanceAsOfMatch = balanceAsOfPattern.exec(html);
  return {
    name: panelField($, 'গ্রাহকের নাম'),
    fatherOrHusbandName: panelField($, 'পিতা/স্বামীর নাম'),
    address: panelField($, 'ঠিকানা'),
    mobile: panelField($, 'মোবাইল'),
    office: panelField($, 'সংশ্লিষ্ট বিদ্যুৎ অফিস'),
    feederName: panelField($, 'ফিডারের নাম'),
    consumerNo: panelField($, 'কনজ্যুমার নম্বর'),
    meterNo: panelField($, 'মিটার নম্বর'),
    approvedLoadKw: panelField($, 'অনুমোদিত লোড'),
    tariff: panelField($, 'অনুমোদিত ট্যারিফ'),
    meterType: panelField($, 'মিটারের ধরণ'),
    meterStatus: panelField($, 'মিটার স্ট্যাটাস'),
    installDate: panelField($, 'মিটার স্থাপনের তারিখ'),
    minRechargeAmount: toNum(panelField($, 'মিনিমাম রিচার্জের পরিমাণ')),
    balance: toNum(panelField($, 'অবশিষ্ট ব্যালেন্স')),
    balanceAsOf: balanceAsOfMatch ? balanceAsOfMatch[1] : null,
    lastRecharge,
  };
}

function parseMonthlyTable(html) {
  const $ = cheerio.load(html);
  let rows = [];
  $('table').each((_, table) => {
    const headerText = $(table)
      .find('thead th')
      .map((__, el) => $(el).text())
      .get()
      .join(' ');
    if (!headerText.includes('বছর') || !headerText.includes('সর্বমোট রিচার্জ')) return;
    rows = $(table)
      .find('tbody tr')
      .map((__, row) => {
        const cells = $(row)
          .find('td')
          .map((___, el) => $(el).text().trim())
          .get();
        if (cells.length < 13) return null;
        return {
          year: cells[0],
          month: cells[1],
          totalRecharge: toNum(cells[2]),
          rebate: toNum(cells[3]),
          electricityUsedTaka: toNum(cells[4]),
          meterRent: toNum(cells[5]),
          demandCharge: toNum(cells[6]),
          pfcCharge: toNum(cells[7]),
          paidArrears: toNum(cells[8]),
          vat: toNum(cells[9]),
          totalUsage: toNum(cells[10]),
          endOfMonthBalance: toNum(cells[11]),
          unitsUsedKwh: toNum(cells[12]),
        };
      })
      .get()
      .filter((r) => r !== null);
    return false;
  });
  return rows;
}

async function scrapeMeter(meterNo) {
  const html = await postToPanel(meterNo, SUBMIT_RECHARGE_HISTORY);
  const data = parsePanel(html);
  // Unknown numbers return HTTP 200 with an empty page shell. Do not let the UI
  // save and display that response as if it were a valid, permanently blank meter.
  if (
    !data.name &&
    !data.consumerNo &&
    !data.meterNo &&
    !data.address &&
    data.balance === null &&
    !data.lastRecharge
  ) {
    throw new Error('No meter found with this number');
  }
  return data;
}

async function scrapeMonthlyUsage(meterNo) {
  const html = await postToPanel(meterNo, SUBMIT_MONTHLY_USAGE);
  return parseMonthlyTable(html);
}

module.exports = { scrapeMeter, scrapeMonthlyUsage, parsePanel, parseMonthlyTable };
