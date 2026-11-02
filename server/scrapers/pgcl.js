const axios = require('axios');
const https = require('https');
const cheerio = require('cheerio');

// PGCL's server presents an expired TLS certificate, so this client (only this one)
// trusts any certificate to get through the handshake. Kept isolated from every other scraper.
const insecureAgent = new https.Agent({ rejectUnauthorized: false });

const URL = 'https://rsm.pgcl.org.bd:8443/PGCL_WEB/getCustomerWebView';
const customerIdPattern = /^[A-Za-z0-9-]{4,30}$/;

async function scrapeMeter(customerId) {
  const trimmed = customerId.trim();
  if (!customerIdPattern.test(trimmed)) {
    throw new Error('Invalid customer code');
  }

  const params = new URLSearchParams();
  params.append('customer_id', trimmed);

  const response = await axios.post(URL, params.toString(), {
    httpsAgent: insecureAgent,
    validateStatus: () => true,
    headers: {
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
      'X-Requested-With': 'XMLHttpRequest',
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
  if (response.status < 200 || response.status >= 300) {
    throw new Error(`PGCL request returned HTTP ${response.status}`);
  }
  const html = response.data || '';
  if (!html || !html.includes('Customer Code')) {
    throw new Error('Customer code not found');
  }
  return parse(html);
}

function parse(html) {
  const $ = cheerio.load(html);

  const info = {};
  $('table.table3 tr').each((_, row) => {
    const cells = $(row).find('td');
    if (cells.length >= 2) {
      const label = $(cells[0]).text().trim();
      const value = $(cells[1]).text().trim();
      info[label] = value;
    }
  });

  const payments = [];
  $('table.table2').each((_, table) => {
    const headerText = $(table).find('thead').text();
    if (!headerText.toLowerCase().includes('payment details')) return;
    $(table)
      .find('tbody tr')
      .each((__, row) => {
        const cells = $(row)
          .find('td')
          .map((___, el) => $(el).text().trim())
          .get();
        if (cells.length < 7) return;
        payments.push({
          sl: cells[0],
          billMonth: cells[1],
          bank: cells[2] || null,
          billedAmount: toNum(cells[3]),
          surcharge: toNum(cells[4]),
          total: toNum(cells[5]),
          collectionDate: cells[6] || null,
        });
      });
  });

  const dueStatusMessage = $('#home .alert').first().text().trim() || null;

  const field = (label) => (info[label] && info[label].trim() !== '' ? info[label] : null);

  return {
    customerCode: field('Customer Code'),
    customerName: field('Customer Name'),
    address: field('Address'),
    phone: field('Phone'),
    category: field('Category'),
    status: field('Status'),
    connectionDate: field('Connection Date'),
    meteredStatus: field('Metered Status'),
    maxLoad: field('Max load'),
    minLoad: field('Min load'),
    burnerCount: field('Burner count'),
    regionalOffice: field('Regional Office'),
    dueStatusMessage,
    payments,
  };
}

function toNum(s) {
  const n = Number(s);
  return Number.isNaN(n) ? null : n;
}

module.exports = { scrapeMeter };
