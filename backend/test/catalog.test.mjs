import { test } from 'node:test';
import assert from 'node:assert/strict';
import { kurus, productOffer, merchantName } from '../scripts/catalog-lib.mjs';

const source = { id: '90279724', category: 'Kahve molası', source: 'IKEA Türkiye', url: 'https://www.ikea.com.tr/urun/ikea-365--cam-36-cl-kupa-90279724' };
const html = '<script type="application/ld+json">{"@type":"Product","name":"Cam kupa","offers":{"@type":"Offer","price":"149.50","priceCurrency":"TRY","availability":"https://schema.org/InStock"}}</script>';

test('retailer offers use integer money and explicit currency and stock', () => {
  const result = productOffer(html, source, new Date('2026-10-17T12:00:00Z'));
  assert.equal(result.price_kurus, 14950);
  assert.equal(result.checked_at, '2026-10-17T12:00:00.000Z');
  assert.equal(productOffer(html.replace('InStock', 'OutOfStock'), source), null);
  assert.equal(productOffer(html.replace('TRY', 'USD'), source), null);
  assert.equal(productOffer('<html>Blocked</html>', source), null);
});
test('book merchant provenance is derived from the allowlisted source URL', () => {
  const book = { ...source, source: 'Ignored', url: 'https://www.kitapyurdu.com/kitap/test/1.html' };
  assert.equal(productOffer(html, book).source, 'Kitapyurdu');
  assert.throws(() => merchantName('https://www.kitapyurdu.com.evil.test/kitap/test/1.html'));
  assert.throws(() => merchantName('http://www.kitapyurdu.com/kitap/test/1.html'));
  assert.throws(() => merchantName('https://www.kitapyurdu.com/account/'));
});
test('invalid or ambiguous money cannot reach the catalog', () => {
  for (const price of ['NaN', '-1', '1,000.00', '0', '1.234', 'Infinity']) assert.throws(() => kurus(price));
  assert.equal(kurus('0.01'), 1);
  assert.equal(kurus('100.1'), 10010);
});
