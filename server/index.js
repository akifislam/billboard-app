const path = require('path');
const fs = require('fs');
const express = require('express');
const open = require('open');

const nesco = require('./scrapers/nesco');
const nescoPostpaid = require('./scrapers/nescoPostpaid');
const desco = require('./scrapers/desco');
const pgcl = require('./scrapers/pgcl');

const app = express();
const PORT = process.env.PORT || 4173;

app.use(express.static(path.join(__dirname, '..', 'public')));
app.use(express.json());

// Saved meters live in a JSON file next to the server so the list survives
// cleared browser storage, different browsers, and different ports.
const METERS_FILE = path.join(__dirname, 'meters.json');

function readSavedMeters() {
  try {
    const list = JSON.parse(fs.readFileSync(METERS_FILE, 'utf8'));
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

app.get('/api/meters', (req, res) => {
  res.json(readSavedMeters());
});

app.put('/api/meters', (req, res) => {
  const list = req.body;
  if (!Array.isArray(list)) {
    return res.status(400).json({ error: 'Expected a JSON array of meters' });
  }
  try {
    fs.writeFileSync(METERS_FILE, JSON.stringify(list, null, 2) + '\n');
    res.json({ ok: true, count: list.length });
  } catch (e) {
    res.status(500).json({ error: e.message || 'Failed to save meters' });
  }
});

function idFromQuery(req, res, paramName) {
  const value = (req.query[paramName] || '').toString().trim();
  if (!value) {
    res.status(400).json({ error: `Missing "${paramName}" parameter` });
    return null;
  }
  return value;
}

app.get('/api/nesco', async (req, res) => {
  const meter = idFromQuery(req, res, 'meter');
  if (!meter) return;
  try {
    const data = await nesco.scrapeMeter(meter);
    res.json(data);
  } catch (e) {
    res.status(502).json({ error: e.message || 'Failed to fetch data' });
  }
});

app.get('/api/nesco/monthly', async (req, res) => {
  const meter = idFromQuery(req, res, 'meter');
  if (!meter) return;
  try {
    const rows = await nesco.scrapeMonthlyUsage(meter);
    res.json(rows);
  } catch (e) {
    res.status(502).json({ error: e.message || 'Failed to fetch monthly usage' });
  }
});

app.get('/api/nesco/postpaid', async (req, res) => {
  const meter = idFromQuery(req, res, 'meter');
  if (!meter) return;
  try {
    const data = await nescoPostpaid.scrapeMeter(meter);
    res.json(data);
  } catch (e) {
    res.status(502).json({ error: e.message || 'Failed to fetch data' });
  }
});

app.get('/api/desco', async (req, res) => {
  const account = idFromQuery(req, res, 'account');
  if (!account) return;
  try {
    const data = await desco.scrapeMeter(account);
    res.json(data);
  } catch (e) {
    res.status(502).json({ error: e.message || 'Failed to fetch data' });
  }
});

app.get('/api/pgcl', async (req, res) => {
  const customer = idFromQuery(req, res, 'customer');
  if (!customer) return;
  try {
    const data = await pgcl.scrapeMeter(customer);
    res.json(data);
  } catch (e) {
    res.status(502).json({ error: e.message || 'Failed to fetch data' });
  }
});

app.listen(PORT, () => {
  const url = `http://localhost:${PORT}`;
  console.log(`BillBoard web is running at ${url}`);
  console.log('Press Ctrl+C to stop.');
  open(url).catch(() => {
    /* if auto-open fails, the user can still open the URL above manually */
  });
});
