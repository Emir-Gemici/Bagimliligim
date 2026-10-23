import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

let db;
const alice = '11111111-1111-4111-8111-111111111111';
const bob = '22222222-2222-4222-8222-222222222222';
const stranger = '33333333-3333-4333-8333-333333333333';
before(async () => {
  db = new PGlite();
  await db.exec(`create role anon; create role authenticated; create role service_role;
    create schema auth; create table auth.users (id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      'select nullif(current_setting(''request.jwt.claim.sub'', true), '''')::uuid';
    grant usage on schema auth to authenticated, anon;
    grant execute on function auth.uid() to authenticated, anon;
    alter default privileges in schema public grant execute on functions to anon, authenticated, service_role;`);
  await db.exec(await readFile(new URL('../supabase/migrations/202610170001_v2.sql', import.meta.url), 'utf8'));
  await db.exec(await readFile(new URL('../supabase/migrations/202610170002_retention_365.sql', import.meta.url), 'utf8'));
  await db.exec(await readFile(new URL('../supabase/migrations/202610170003_explicit_rpc_privileges.sql', import.meta.url), 'utf8'));
  for (const id of [alice, bob, stranger]) await db.query('insert into auth.users(id) values ($1)', [id]);
});
after(async () => { await db?.close(); });

async function rpc(user, action, payload = {}) {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [user]);
  await db.exec('set role authenticated');
  try {
    return (await db.query('select public.social_action($1, $2::jsonb) as result', [action, JSON.stringify(payload)])).rows[0].result;
  } finally { await db.exec('reset role'); }
}

test('unauthenticated users cannot call social RPC or read personal tables', async () => {
  await db.exec('set role anon');
  try {
    await assert.rejects(db.query("select public.social_action('snapshot', '{}')"), /permission denied/);
    await assert.rejects(db.query('select * from public.bond_profiles'), /permission denied/);
    const rows = await db.query('select public.catalog_snapshot() as catalog');
    assert.equal(rows.rows[0].catalog.schema_version, 1);
  } finally { await db.exec('reset role'); }
});

test('friend requests require acceptance; unrelated users see no private summaries', async () => {
  const a = await rpc(alice, 'snapshot');
  await rpc(bob, 'snapshot');
  await rpc(stranger, 'snapshot');
  await rpc(alice, 'profile', { name: 'Alice', share_reels: true, share_smoking: true });
  await rpc(bob, 'profile', { name: 'Bob', share_reels: true, share_smoking: true });
  await rpc(bob, 'request', { code: a.profile.code });
  assert.equal((await rpc(alice, 'snapshot')).leaders.length, 1);
  await rpc(stranger, 'accept', { id: bob });
  assert.equal((await rpc(stranger, 'snapshot')).leaders.length, 1);
  await rpc(alice, 'accept', { id: bob });
  assert.equal((await rpc(alice, 'snapshot')).leaders.length, 2);
  assert.equal((await rpc(stranger, 'snapshot')).leaders.length, 1);
});

test('sync is idempotent, cannot impersonate someone else, and preserves historical money', async () => {
  const today = (await db.query("select (now() at time zone 'Europe/Istanbul')::date::text as day")).rows[0].day;
  const payload = { owner: bob, days: [{ date: today, swipes: 10, distance_meters: 1.2, smoking_tracked: true,
    brands: [{ id: 'winston-dark-blue', name: 'Winston Dark Blue', count: 3, cost_micros: 18000000 }] }] };
  await rpc(alice, 'sync', payload);
  await rpc(alice, 'sync', payload);
  const rows = (await rpc(bob, 'snapshot', { brand: 'winston-dark-blue', metric: 'spending' })).leaders;
  const a = rows.find(row => row.id === alice);
  assert.equal(a.cigarettes, 3);
  assert.equal(a.cost_micros, 18000000);
  assert.equal(a.reduction, null);
  assert.equal(rows.find(row => row.id === bob).cigarettes, 0);
  const raw = await db.query('select count(*)::integer n from public.bond_brand_days where owner=$1', [alice]);
  assert.equal(raw.rows[0].n, 1);
});

test('revoking a metric deletes its server data and blocking removes access', async () => {
  await rpc(alice, 'profile', { name: 'Alice', share_reels: true, share_smoking: false });
  const a = (await rpc(bob, 'snapshot')).leaders.find(row => row.id === alice);
  assert.equal(a.cigarettes, null);
  assert.equal(a.cost_micros, null);
  assert.equal((await db.query('select count(*)::integer n from public.bond_brand_days where owner=$1', [alice])).rows[0].n, 0);
  await rpc(alice, 'block', { id: bob });
  assert.equal((await rpc(bob, 'snapshot')).leaders.length, 1);
  const code = (await rpc(alice, 'snapshot')).profile.code;
  await rpc(bob, 'request', { code });
  assert.equal((await rpc(alice, 'snapshot')).friends.length, 0);
});

test('published price rows cannot be overwritten by a later price update', async () => {
  await db.query(`insert into public.catalog_prices(brand_id,brand_name,family,pack_size,price_kurus,valid_from,checked_at,source,source_url)
    values ('winston-dark-blue','Winston Dark Blue','Winston',20,12000,'2026-01-01',now(),'Verified test fixture','https://example.org/test')`);
  await assert.rejects(db.query("update public.catalog_prices set price_kurus=15000"), /immutable/);
  await db.query(`insert into public.catalog_prices(brand_id,brand_name,family,pack_size,price_kurus,valid_from,checked_at,source,source_url)
    values ('winston-dark-blue','Winston Dark Blue','Winston',20,15000,'2027-01-01',now(),'Verified test fixture','https://example.org/test')`);
  const rows = await db.query("select price_kurus from public.catalog_prices where valid_from<='2026-12-31' order by valid_from desc limit 1");
  assert.equal(Number(rows.rows[0].price_kurus), 12000);
});

test('retention keeps 365 dates and removes older days with their brand records', async () => {
  await db.query(`insert into public.bond_days(owner,day,swipes) select $1, (now() at time zone 'Europe/Istanbul')::date - n, n from unnest(array[28,364,365]) n`, [bob]);
  await db.query(`insert into public.bond_brand_days(owner,day,brand_id,brand_name,cigarettes,cost_micros)
    select owner,day,'retention-fixture','Retention fixture',1,1000000 from public.bond_days where owner=$1`, [bob]);
  await rpc(bob, 'sync', { days: [] });
  const kept = await db.query(`select ((now() at time zone 'Europe/Istanbul')::date - day)::integer age from public.bond_days where owner=$1 order by age`, [bob]);
  assert.deepEqual(kept.rows.map(row => row.age), [28, 364]);
  const brands = await db.query('select count(*)::integer n from public.bond_brand_days where owner=$1', [bob]);
  assert.equal(brands.rows[0].n, 2);
  const cronSql = await readFile(new URL('../supabase/retention.sql', import.meta.url), 'utf8');
  const command = cronSql.match(/\$\$([\s\S]*?)\$\$/)[1];
  await db.query(`insert into public.bond_days(owner,day) values ($1,(now() at time zone 'Europe/Istanbul')::date - 365)`, [bob]);
  await db.exec(command);
  const scheduled = await db.query(`select ((now() at time zone 'Europe/Istanbul')::date - day)::integer age from public.bond_days where owner=$1 order by age`, [bob]);
  assert.deepEqual(scheduled.rows, kept.rows);
});

test('account deletion cascades through personal data and friends', async () => {
  await rpc(alice, 'delete_account');
  assert.equal((await db.query('select count(*)::integer n from public.bond_profiles where id=$1', [alice])).rows[0].n, 0);
  assert.equal((await db.query('select count(*)::integer n from public.bond_days where owner=$1', [alice])).rows[0].n, 0);
  await assert.rejects(rpc(alice, 'snapshot'), /Authentication required/);
});

test('live verification script exercises real roles and rolls back every fixture', async () => {
  const before = await db.query('select count(*)::integer n from auth.users');
  await db.exec(await readFile(new URL('../supabase/verify-live.sql', import.meta.url), 'utf8'));
  const after = await db.query('select count(*)::integer n from auth.users');
  assert.deepEqual(after.rows, before.rows);
  const synthetic = await db.query("select count(*)::integer n from public.bond_profiles where name like 'Verification %'");
  assert.equal(synthetic.rows[0].n, 0);
});
