import assert from 'node:assert/strict';

export function kurus(value) {
  const text = String(value);
  assert.match(text, /^\d{1,9}(?:\.\d{1,2})?$/);
  const [whole, decimals = ''] = text.split('.');
  const amount = Number(BigInt(whole) * 100n + BigInt(decimals.padEnd(2, '0')));
  assert.ok(Number.isSafeInteger(amount) && amount > 0 && amount <= 10000000000);
  return amount;
}

function objects(value) {
  if (Array.isArray(value)) return value.flatMap(objects);
  if (value && typeof value === 'object') return [value, ...objects(value['@graph'])];
  return [];
}
const hasType = (value, type) => [value['@type']].flat().includes(type);

export function productOffer(html, product, now = new Date()) {
  const source = merchantName(product.url);
  for (const match of html.matchAll(/<script\b[^>]*type\s*=\s*["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi)) {
    let value;
    try { value = JSON.parse(match[1]); } catch { continue; }
    for (const item of objects(value).filter(p => hasType(p, 'Product'))) {
      for (const offer of objects(item.offers).filter(o => hasType(o, 'Offer'))) {
        if (offer.priceCurrency !== 'TRY' || !/^https?:\/\/schema\.org\/InStock$/.test(offer.availability ?? '')) continue;
        if (offer.priceValidUntil && (!/^\d{4}-\d{2}-\d{2}$/.test(offer.priceValidUntil) || offer.priceValidUntil < now.toISOString().slice(0, 10))) continue;
        if (typeof item.name !== 'string' || item.name.length < 2 || item.name.length > 160) continue;
        try {
          return { id: product.id, title: item.name, category: product.category,
            price_kurus: kurus(offer.price), source, url: product.url,
            checked_at: now.toISOString(), available: true };
        } catch { continue; }
      }
    }
  }
  return null;
}

export function merchantName(url) {
  const parsed = new URL(url);
  assert.ok(parsed.protocol === 'https:' && !parsed.username && !parsed.password);
  if (parsed.origin === 'https://www.ikea.com.tr' && parsed.pathname.startsWith('/urun/')) return 'IKEA Türkiye';
  if (parsed.origin === 'https://www.kitapyurdu.com' && /^\/kitap\/[a-z0-9-]+\/[0-9]+\.html$/.test(parsed.pathname)) return 'Kitapyurdu';
  throw new Error('Unsupported public merchant URL.');
}

export async function downloadProduct(url) {
  merchantName(url);
  const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'Bagimliligim/2.0 price-check', Accept: 'text/html' } });
  assert.ok(response.ok && response.headers.get('content-type')?.includes('text/html'));
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      size += chunk.value.length;
      assert.ok(size <= 3000000);
      chunks.push(chunk.value);
    }
  } finally { await reader.cancel(); }
  return Buffer.concat(chunks).toString('utf8');
}

export async function publish(table, rows) {
  const url = process.env.BAGIMLILIGIM_BACKEND_URL;
  const key = process.env.BAGIMLILIGIM_SERVICE_ROLE_KEY;
  assert.match(url ?? '', /^https:\/\/[a-z0-9-]+\.supabase\.co$/);
  assert.ok(key && key.length > 30, 'A server-side service role key is required; never embed it in the Android app.');
  assert.ok(['catalog_prices', 'catalog_comparisons'].includes(table));
  const conflict = table === 'catalog_prices' ? 'brand_id,valid_from' : 'id';
  const response = await fetch(`${url}/rest/v1/${table}?on_conflict=${conflict}`, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20000),
    headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json',
      Prefer: `resolution=${table === 'catalog_prices' ? 'ignore' : 'merge'}-duplicates,return=minimal` },
    body: JSON.stringify(rows),
  });
  assert.ok(response.ok, `Catalog publication failed (${response.status}).`);
}
