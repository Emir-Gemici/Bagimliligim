import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { downloadProduct, productOffer, publish } from './catalog-lib.mjs';

const library = JSON.parse(await readFile(new URL('../catalog/products.json', import.meta.url), 'utf8'));
const result = [];
let cursor = 0;
let failed = 0;
async function worker() {
  while (cursor < library.length) {
    const product = library[cursor++];
    try {
      const offer = productOffer(await downloadProduct(product.url), product);
      if (offer) result.push(offer); else failed++;
    } catch { failed++; }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
}
await Promise.all([worker(), worker()]);
await mkdir(new URL('../local-output/', import.meta.url), { recursive: true });
await writeFile(new URL('../local-output/comparisons.json', import.meta.url), JSON.stringify(result, null, 2));
if (!result.length) throw new Error('No fresh in-stock TRY offers were verified; previous timestamps remain unchanged.');
if (process.argv.includes('--publish')) await publish('catalog_comparisons', result);
console.log(JSON.stringify({ checked: library.length, verified: result.length, unavailableOrFailed: failed, published: process.argv.includes('--publish') }));
