import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { publish } from './catalog-lib.mjs';

const file = process.argv.find((argument, index) => index > 1 && !argument.startsWith('--'));
const feed = process.env.BAGIMLILIGIM_VERIFIED_PRICE_FEED;
let data;
if (file) data = JSON.parse(await readFile(file, 'utf8'));
else {
  assert.ok(feed, 'Provide an approved JSON price list file or BAGIMLILIGIM_VERIFIED_PRICE_FEED.');
  const url = new URL(feed);
  assert.equal(url.protocol, 'https:');
  assert.ok(!url.username && !url.password);
  const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(15000) });
  assert.ok(response.ok && response.headers.get('content-type')?.includes('application/json'));
  const text = await response.text();
  assert.ok(text.length <= 2000000);
  data = JSON.parse(text);
}
assert.ok(Array.isArray(data) && data.length > 0 && data.length <= 1000);
const seen = new Set();
const rows = data.map(p => {
  assert.match(p.brand_id, /^[a-z0-9-]{2,100}$/);
  assert.ok(typeof p.brand_name === 'string' && p.brand_name.length >= 2 && p.brand_name.length <= 80);
  assert.ok(typeof p.family === 'string' && p.family.length >= 2 && p.family.length <= 60);
  assert.ok(Number.isInteger(p.pack_size) && p.pack_size >= 1 && p.pack_size <= 100);
  assert.ok(Number.isSafeInteger(p.price_kurus) && p.price_kurus > 0 && p.price_kurus <= 100000000);
  assert.match(p.valid_from, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(new Date(p.valid_from).toISOString().slice(0, 10), p.valid_from);
  const checked = Date.parse(p.checked_at);
  assert.ok(Number.isFinite(checked) && checked <= Date.now() + 300000 && checked >= Date.now() - 48 * 3600000, 'Only recently verified source data may be imported.');
  assert.ok(typeof p.source === 'string' && p.source.length >= 2 && p.source.length <= 100);
  const source = new URL(p.source_url);
  assert.equal(source.protocol, 'https:');
  assert.ok(!source.username && !source.password);
  const key = `${p.brand_id}:${p.valid_from}`;
  assert.ok(!seen.has(key), 'Duplicate brand/effective-date row.');
  seen.add(key);
  return { brand_id: p.brand_id, brand_name: p.brand_name, family: p.family, pack_size: p.pack_size,
    price_kurus: p.price_kurus, valid_from: p.valid_from, checked_at: new Date(checked).toISOString(),
    source: p.source, source_url: p.source_url };
});
if (process.argv.includes('--publish')) await publish('catalog_prices', rows);
console.log(JSON.stringify({ validRows: rows.length, published: process.argv.includes('--publish') }));
