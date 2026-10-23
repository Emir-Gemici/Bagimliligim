import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';

const args = process.argv.slice(2);
const requireGoogle = args.includes('--require-google');
const configArgs = args.filter(arg => arg !== '--require-google');
let config = {};
if (configArgs.length) {
  assert.equal(configArgs.length, 2, 'Usage: node scripts/check-live.mjs [--config path] [--require-google]');
  assert.equal(configArgs[0], '--config');
  config = JSON.parse((await readFile(configArgs[1], 'utf8')).replace(/^\uFEFF/, ''));
}
const url = (process.env.BAGIMLILIGIM_BACKEND_URL || config.url || '').replace(/\/$/, '');
const key = process.env.BAGIMLILIGIM_PUBLIC_KEY || config.publishableKey || '';
assert.match(url, /^https:\/\/[a-z0-9-]+\.supabase\.co$/);
assert.match(key, /^sb_publishable_[A-Za-z0-9_-]{20,}$/);
const checks = [];

async function request(path, body) {
  const response = await fetch(url + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { apikey: key, Accept: 'application/json', 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    redirect: 'error', signal: AbortSignal.timeout(20000),
  });
  return { status: response.status, body: await response.json() };
}

const catalog = await request('/rest/v1/rpc/catalog_snapshot', {});
assert.equal(catalog.status, 200, 'Public catalog must be reachable');
assert.equal(catalog.body.schema_version, 1);
assert.ok(Array.isArray(catalog.body.prices) && Array.isArray(catalog.body.comparisons));
checks.push({ name: 'public_catalog', status: catalog.status, comparisons: catalog.body.comparisons.length });

const social = await request('/rest/v1/rpc/social_action', { action: 'snapshot', payload: {} });
assert.ok([401, 403].includes(social.status), 'Anonymous social access must be denied');
checks.push({ name: 'anonymous_social_denied', status: social.status });
for (const table of ['bond_profiles', 'bond_friends', 'bond_blocks', 'bond_requests',
  'bond_days', 'bond_brand_days', 'catalog_prices', 'catalog_comparisons']) {
  const response = await request(`/rest/v1/${table}?select=*&limit=1`);
  assert.ok([401, 403].includes(response.status), `Direct access must be denied: ${table}`);
  checks.push({ name: `${table}_direct_access_denied`, status: response.status });
}
const auth = await request('/auth/v1/settings');
assert.equal(auth.status, 200);
assert.equal(auth.body.mailer_autoconfirm, false, 'Email confirmation must remain enabled');
checks.push({ name: 'email_confirmation_required', status: auth.status });
assert.notEqual(auth.body.external.anonymous_users, true, 'Anonymous account creation must remain disabled');
if (requireGoogle) {
  assert.equal(auth.body.external.google, true, 'Google sign-in must be enabled before release');
  checks.push({ name: 'google_provider_enabled', status: auth.status });
}

const report = { checkedAt: new Date().toISOString(), project: new URL(url).hostname,
  googleEnabled: auth.body.external.google === true, passed: checks.length, checks };
await mkdir(new URL('../local-output/', import.meta.url), { recursive: true });
await writeFile(new URL('../local-output/live-check.json', import.meta.url), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
