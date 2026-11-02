const assert = require('node:assert/strict');
const test = require('node:test');

const { parsePanel, parseMonthlyTable } = require('../server/scrapers/nesco');

test('parses a balance whose input is beyond the old 600-character window', () => {
  const padding = '<span aria-hidden="true">x</span>'.repeat(35);
  const html = `
    <section>
      <label>কনজ্যুমার নম্বর</label>${padding}<input value="TEST-CONSUMER">
      <label>মিটার নম্বর</label>${padding}<input value="TEST-METER">
      <label>অবশিষ্ট ব্যালেন্স</label>${padding}<input value="1248.50">
    </section>`;
  const data = parsePanel(html);

  assert.equal(data.consumerNo, 'TEST-CONSUMER');
  assert.equal(data.meterNo, 'TEST-METER');
  assert.equal(data.balance, 1248.50);
});

test('parses monthly usage independently from panel fields', () => {
  const rows = parseMonthlyTable(`
    <table><thead><tr><th>বছর</th><th>সর্বমোট রিচার্জ</th></tr></thead>
      <tbody><tr>${[
        '2026', 'August', '5000', '-23.49', '4548.73', '40', '168', '0', '0',
        '238.10', '4994.83', '919.43', '481.261',
      ].map((value) => `<td>${value}</td>`).join('')}</tr></tbody>
    </table>`);

  assert.equal(rows.length, 1);
  assert.equal(rows[0].totalUsage, 4994.83);
  assert.equal(rows[0].unitsUsedKwh, 481.261);
});
