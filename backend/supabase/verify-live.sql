-- Run after installing V2. Synthetic accounts and records are rolled back.
-- No email, password, access token, or persistent user account is created.
begin;
do $verification$
declare
  alice uuid := gen_random_uuid();
  bob uuid := gen_random_uuid();
  stranger uuid := gen_random_uuid();
  alice_code text;
  snapshot jsonb;
  payload jsonb;
  day date := (now() at time zone 'Europe/Istanbul')::date;
  table_name text;
  client_role text;
begin
  foreach table_name in array array['bond_profiles', 'bond_friends', 'bond_blocks', 'bond_requests',
    'bond_days', 'bond_brand_days', 'catalog_prices', 'catalog_comparisons'] loop
    if not exists(select 1 from pg_tables p where p.schemaname = 'public'
      and p.tablename = table_name and p.rowsecurity) then
      raise exception 'Missing RLS: %', table_name;
    end if;
    foreach client_role in array array['anon', 'authenticated'] loop
      if has_table_privilege(client_role, 'public.' || table_name, 'SELECT,INSERT,UPDATE,DELETE') then
        raise exception 'Unexpected direct access: % %', client_role, table_name;
      end if;
    end loop;
  end loop;
  if has_function_privilege('anon', 'public.social_action(text,jsonb)', 'EXECUTE')
    or not has_function_privilege('authenticated', 'public.social_action(text,jsonb)', 'EXECUTE')
    or not has_function_privilege('anon', 'public.catalog_snapshot()', 'EXECUTE') then
    raise exception 'Unexpected RPC privileges';
  end if;

  execute 'set local role anon';
  perform public.catalog_snapshot();
  begin
    perform public.social_action('snapshot');
    raise exception 'Anonymous social access was allowed';
  exception when insufficient_privilege then null;
  end;
  execute 'reset role';

  insert into auth.users(id) values (alice), (bob), (stranger);
  perform set_config('request.jwt.claim.sub', alice::text, true);
  execute 'set local role authenticated';
  snapshot := public.social_action('snapshot');
  alice_code := snapshot->'profile'->>'code';
  perform public.social_action('profile', '{"name":"Verification A","share_reels":true,"share_smoking":true}');
  perform set_config('request.jwt.claim.sub', bob::text, true);
  perform public.social_action('profile', '{"name":"Verification B","share_reels":true,"share_smoking":true}');
  perform public.social_action('request', jsonb_build_object('code', alice_code));
  snapshot := public.social_action('snapshot');
  if jsonb_array_length(snapshot->'leaders') <> 1 then raise exception 'Pending friendship exposed data'; end if;

  perform set_config('request.jwt.claim.sub', stranger::text, true);
  perform public.social_action('accept', jsonb_build_object('id', bob));
  snapshot := public.social_action('snapshot');
  if jsonb_array_length(snapshot->'leaders') <> 1 then raise exception 'Stranger accepted someone else''s request'; end if;

  perform set_config('request.jwt.claim.sub', alice::text, true);
  perform public.social_action('accept', jsonb_build_object('id', bob));
  payload := jsonb_build_object('owner', bob, 'days', jsonb_build_array(jsonb_build_object(
    'date', day, 'swipes', 10, 'distance_meters', 1.2, 'smoking_tracked', true,
    'brands', jsonb_build_array(jsonb_build_object('id', 'verification-brand', 'name', 'Verification brand',
      'count', 3, 'cost_micros', 18000000)))));
  perform public.social_action('sync', payload);
  perform public.social_action('sync', payload);
  perform set_config('request.jwt.claim.sub', bob::text, true);
  snapshot := public.social_action('snapshot', '{"brand":"verification-brand","metric":"spending"}');
  if jsonb_array_length(snapshot->'leaders') <> 2 then raise exception 'Accepted friend missing'; end if;
  if not exists(select 1 from jsonb_array_elements(snapshot->'leaders') r
    where r->>'id' = alice::text and (r->>'cost_micros')::bigint = 18000000
      and (r->>'cigarettes')::integer = 3 and (r->>'swipes')::integer = 10) then
    raise exception 'Friend summary or immutable cost is incorrect';
  end if;
  execute 'reset role';
  if (select count(*) from public.bond_brand_days where owner = alice) <> 1
    or exists(select 1 from public.bond_days where owner = bob) then
    raise exception 'Sync was not idempotent or allowed impersonation';
  end if;

  perform set_config('request.jwt.claim.sub', stranger::text, true);
  execute 'set local role authenticated';
  snapshot := public.social_action('snapshot');
  if jsonb_array_length(snapshot->'leaders') <> 1 then raise exception 'Stranger read a private summary'; end if;
  perform set_config('request.jwt.claim.sub', alice::text, true);
  perform public.social_action('profile', '{"name":"Verification A","share_reels":true,"share_smoking":false}');
  perform set_config('request.jwt.claim.sub', bob::text, true);
  snapshot := public.social_action('snapshot');
  if exists(select 1 from jsonb_array_elements(snapshot->'leaders') r where r->>'id' = alice::text
    and (r->>'cigarettes' is not null or r->>'cost_micros' is not null)) then
    raise exception 'Disabled smoking metric remained visible';
  end if;
  execute 'reset role';
  if exists(select 1 from public.bond_brand_days where owner = alice) then
    raise exception 'Disabled metric remained stored';
  end if;

  perform set_config('request.jwt.claim.sub', alice::text, true);
  execute 'set local role authenticated';
  perform public.social_action('block', jsonb_build_object('id', bob));
  perform set_config('request.jwt.claim.sub', bob::text, true);
  snapshot := public.social_action('snapshot');
  if jsonb_array_length(snapshot->'leaders') <> 1 then raise exception 'Blocked friend retained access'; end if;
  perform set_config('request.jwt.claim.sub', alice::text, true);
  perform public.social_action('delete_account');
  execute 'reset role';
  if exists(select 1 from auth.users where id = alice)
    or exists(select 1 from public.bond_days where owner = alice)
    or exists(select 1 from public.bond_profiles where id = alice) then
    raise exception 'Account deletion did not cascade';
  end if;
end;
$verification$;
select 'PASS: RLS, RPC, friend acceptance, stranger isolation, idempotency, historical cost, consent, blocking, deletion' as verification;
rollback;
