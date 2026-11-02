const axios = require('axios');
const cheerio = require('cheerio');

const POSTPAID_URL = 'https://customer.nesco.gov.bd/post/bill';
const AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';
const SUBMIT_DUE_BILL = 'বকেয়া বিল';
const SUBMIT_ALL_BILLS = 'সকল বিল';

const meterNumberPattern = /^[A-Za-z0-9-]{4,30}$/;
const csrfMetaPattern = /name="csrf-token"\s+content="([^"]+)"/;
const csrfInputPattern = /name="_token"\s+value="([^"]+)"/;
// Unlike the prepaid panel, this page mixes single- and double-quoted value="" attributes.
const valuePattern = /value=["']([^"']*)["']/;

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

function grabAfter(html, label) {
  const i = html.indexOf(label);
  if (i < 0) return null;
  const end = Math.min(i + 600, html.length);
  const segment = html.substring(i, end);
  const m = valuePattern.exec(segment);
  if (!m) return null;
  const value = stripTags(m[1]);
  return value === '' ? null : value;
}

async function postToPanel(meterNo, submitValue) {
  const trimmed = meterNo.trim();
  if (!meterNumberPattern.test(trimmed)) {
    throw new Error('Invalid meter number');
  }

  const getResponse = await axios.get(POSTPAID_URL, {
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
    Referer: POSTPAID_URL,
    'Content-Type': 'application/x-www-form-urlencoded',
  };

  const postResponse = await axios.post(POSTPAID_URL, params.toString(), {
    headers: postHeaders,
    validateStatus: () => true,
  });
  if (postResponse.status < 200 || postResponse.status >= 300) {
    throw new Error(upstreamMessage('POST', postResponse.status));
  }
  return postResponse.data || '';
}

function parseCustomerInfo(html) {
  return {
    name: grabAfter(html, 'গ্রাহকের নাম'),
    fatherOrHusbandName: grabAfter(html, 'পিতা/স্বামীর নাম'),
    address: grabAfter(html, 'ঠিকানা'),
    mobile: grabAfter(html, 'মোবাইল'),
    office: grabAfter(html, 'সংশ্লিষ্ট বিদ্যুৎ অফিস'),
    feederName: grabAfter(html, 'ফিডারের নাম'),
    consumerNo: grabAfter(html, 'কনজ্যুমার নম্বর'),
    accountsOfficeNo: grabAfter(html, 'দপ্তরের হিসাব নং'),
    previousConsumerNo: grabAfter(html, 'পূর্বের কনজ্যুমার নম্বর'),
    meterNo: grabAfter(html, 'মিটার নম্বর'),
    approvedLoadKw: grabAfter(html, 'অনুমোদিত লোড'),
    tariff: grabAfter(html, 'অনুমোদিত ট্যারিফ'),
    meterStatus: grabAfter(html, 'মিটার স্ট্যাটাস'),
    connectionPhase: grabAfter(html, 'কানেকশন ফেজ'),
    billGroup: grabAfter(html, 'বিল গ্রুপ'),
    bookNo: grabAfter(html, 'বই নং'),
    workOrder: grabAfter(html, 'ওয়াক অর্ডার'),
  };
}

function parseBillTable(html) {
  const $ = cheerio.load(html);
  let rows = [];
  $('table').each((_, table) => {
    const headerText = $(table)
      .find('thead th')
      .map((__, el) => $(el).text())
      .get()
      .join(' ');
    if (!headerText.includes('বিল নম্বর') || !headerText.includes('পেমেন্ট স্ট্যাটাস')) return;
    rows = $(table)
      .find('tbody tr')
      .map((__, row) => {
        const cells = $(row).find('td');
        if (cells.length < 10) return null;
        return {
          billNo: $(cells[0]).text().trim(),
          year: $(cells[1]).text().trim(),
          month: $(cells[2]).text().trim(),
          totalBillTaka: toNum($(cells[3]).text()),
          lateFeeTaka: toNum($(cells[4]).text()),
          dueDate: $(cells[5]).text().trim() || null,
          paidAmountTaka: toNum($(cells[6]).text()),
          paidDate: $(cells[7]).text().trim() || null,
          paymentMethod: $(cells[8]).text().trim() || null,
          paymentStatus: $(cells[9]).text().trim() || null,
        };
      })
      .get()
      .filter((r) => r !== null);
    return false;
  });
  return rows;
}

async function scrapeMeter(meterNo) {
  const allBillsHtml = await postToPanel(meterNo, SUBMIT_ALL_BILLS);
  if (!allBillsHtml.includes('গ্রাহকের নাম')) {
    throw new Error('Postpaid meter/account number not found');
  }
  const info = parseCustomerInfo(allBillsHtml);
  const allBills = parseBillTable(allBillsHtml);

  // The "বকেয়া বিল" (due bill) action reliably errors server-side on NESCO's end when
  // there's nothing due, instead of returning an empty table. Treat that failure mode
  // (and any other fetch failure for this secondary call) the same as "no due bills"
  // rather than surfacing it as an error — the primary "all bills" call above already
  // succeeded, so we have a meter to show.
  let dueBills = [];
  try {
    const dueHtml = await postToPanel(meterNo, SUBMIT_DUE_BILL);
    if (!dueHtml.includes('Could not process')) {
      dueBills = parseBillTable(dueHtml);
    }
  } catch {
    // leave dueBills empty
  }

  return { ...info, dueBills, allBills };
}

module.exports = { scrapeMeter };
