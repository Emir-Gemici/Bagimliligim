begin;

create table public.bond_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 2 and 40),
  code text not null unique default upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12)),
  share_reels boolean not null default false,
  share_smoking boolean not null default false
);
create table public.bond_friends (
  requester uuid not null references public.bond_profiles(id) on delete cascade,
  recipient uuid not null references public.bond_profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, recipient), check (requester <> recipient)
);
create unique index bond_friend_pair on public.bond_friends (least(requester, recipient), greatest(requester, recipient));
create table public.bond_blocks (
  blocker uuid not null references public.bond_profiles(id) on delete cascade,
  blocked uuid not null references public.bond_profiles(id) on delete cascade,
  primary key (blocker, blocked), check (blocker <> blocked)
);
create table public.bond_requests (
  owner uuid not null references public.bond_profiles(id) on delete cascade,
  at timestamptz not null default now()
);
create index bond_requests_owner_at on public.bond_requests (owner, at);
create table public.bond_days (
  owner uuid not null references public.bond_profiles(id) on delete cascade,
  day date not null,
  swipes integer check (swipes between 0 and 100000),
  distance_meters numeric check (distance_meters between 0 and 100000),
  smoking_tracked boolean not null default false,
  primary key (owner, day)
);
create table public.bond_brand_days (
  owner uuid not null,
  day date not null,
  brand_id text not null check (char_length(brand_id) between 2 and 100),
  brand_name text not null check (char_length(brand_name) between 2 and 80),
  cigarettes integer not null check (cigarettes between 0 and 1000),
  cost_micros bigint not null check (cost_micros between 0 and 1000000000000000),
  primary key (owner, day, brand_id),
  foreign key (owner, day) references public.bond_days(owner, day) on delete cascade
);

create table public.catalog_prices (
  id uuid primary key default gen_random_uuid(),
  brand_id text not null,
  brand_name text not null,
  family text not null,
  pack_size integer not null check (pack_size between 1 and 100),
  price_kurus bigint not null check (price_kurus between 1 and 100000000),
  valid_from date not null,
  checked_at timestamptz not null,
  source text not null,
  source_url text not null check (source_url like 'https://%'),
  unique (brand_id, valid_from)
);
create table public.catalog_comparisons (
  id text primary key,
  title text not null,
  category text not null,
  price_kurus bigint not null check (price_kurus between 1 and 10000000000),
  source text not null,
  url text not null check (url like 'https://%'),
  checked_at timestamptz not null,
  available boolean not null
);

alter table public.bond_profiles enable row level security;
alter table public.bond_friends enable row level security;
alter table public.bond_blocks enable row level security;
alter table public.bond_requests enable row level security;
alter table public.bond_days enable row level security;
alter table public.bond_brand_days enable row level security;
alter table public.catalog_prices enable row level security;
alter table public.catalog_comparisons enable row level security;
revoke all on public.bond_profiles, public.bond_friends, public.bond_blocks, public.bond_requests,
  public.bond_days, public.bond_brand_days, public.catalog_prices, public.catalog_comparisons from anon, authenticated;
grant all on public.catalog_prices, public.catalog_comparisons to service_role;

-- Prices are append-only. Corrections need a new effective date and never rewrite consumption.
create function public.reject_price_rewrite() returns trigger
language plpgsql set search_path = '' as $$
begin raise exception 'Published cigarette prices are immutable'; end;
$$;
create trigger catalog_prices_immutable before update or delete on public.catalog_prices
for each row execute function public.reject_price_rewrite();
revoke all on function public.reject_price_rewrite() from public;

create function public.catalog_snapshot() returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('schema_version', 1,
    'prices', coalesce((select jsonb_agg(to_jsonb(p)) from public.catalog_prices p), '[]'::jsonb),
    'comparisons', coalesce((select jsonb_agg(to_jsonb(c)) from public.catalog_comparisons c
      where c.checked_at >= now() - interval '24 hours' and c.checked_at <= now()), '[]'::jsonb));
$$;
revoke all on function public.catalog_snapshot() from public;
grant execute on function public.catalog_snapshot() to anon, authenticated;

create function public.social_action(action text, payload jsonb default '{}'::jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  target uuid;
  my_profile public.bond_profiles;
  d jsonb;
  b jsonb;
  dt date;
  today date := (now() at time zone 'Europe/Istanbul')::date;
  friends jsonb;
  leaders jsonb;
  brand text := nullif(payload->>'brand', '');
  metric text := coalesce(payload->>'metric', 'reduction');
begin
  if me is null or not exists(select 1 from auth.users where id = me) then
    raise insufficient_privilege using message = 'Authentication required';
  end if;
  insert into public.bond_profiles(id, name) values (me, 'Bir arkadaş') on conflict (id) do nothing;
  select * into my_profile from public.bond_profiles where id = me for update;

  if action = 'delete_account' then
    delete from auth.users where id = me;
    return jsonb_build_object('ok', true);
  elsif action = 'profile' then
    if char_length(trim(payload->>'name')) not between 2 and 40 then raise exception 'Invalid name'; end if;
    update public.bond_profiles set name = trim(payload->>'name'),
      share_reels = coalesce((payload->>'share_reels')::boolean, false),
      share_smoking = coalesce((payload->>'share_smoking')::boolean, false)
      where id = me returning * into my_profile;
    if not my_profile.share_reels then update public.bond_days set swipes = null, distance_meters = null where owner = me; end if;
    if not my_profile.share_smoking then
      delete from public.bond_brand_days where owner = me;
      update public.bond_days set smoking_tracked = false where owner = me;
    end if;
    return jsonb_build_object('ok', true);
  elsif action = 'request' then
    delete from public.bond_requests where owner = me and at < now() - interval '1 day';
    if (select count(*) from public.bond_requests where owner = me and at > now() - interval '1 hour') >= 10 then
      raise exception 'Request limit reached';
    end if;
    insert into public.bond_requests(owner) values (me);
    select id into target from public.bond_profiles where code = upper(trim(payload->>'code')) and id <> me;
    -- Uniform response prevents probing whether a code is blocked or nonexistent.
    if target is not null and not exists(select 1 from public.bond_blocks where
      (blocker = me and blocked = target) or (blocker = target and blocked = me))
      and (select count(*) from public.bond_friends where requester = me or recipient = me) < 100
      and (select count(*) from public.bond_friends where requester = target or recipient = target) < 100 then
      insert into public.bond_friends(requester, recipient) values (me, target) on conflict do nothing;
    end if;
    return jsonb_build_object('ok', true);
  elsif action in ('accept', 'remove', 'block') then
    target := (payload->>'id')::uuid;
    if target = me then raise exception 'Invalid friend'; end if;
    if action = 'accept' then
      update public.bond_friends set status = 'accepted' where requester = target and recipient = me
        and not exists(select 1 from public.bond_blocks where (blocker = me and blocked = target) or (blocker = target and blocked = me));
    else
      delete from public.bond_friends where (requester = target and recipient = me) or (requester = me and recipient = target);
      if action = 'block' and exists(select 1 from public.bond_profiles where id = target) then
        insert into public.bond_blocks(blocker, blocked) values (me, target) on conflict do nothing;
      end if;
    end if;
    return jsonb_build_object('ok', true);
  elsif action = 'sync' then
    if jsonb_typeof(payload->'days') <> 'array' or jsonb_array_length(payload->'days') > 14 then raise exception 'Invalid days'; end if;
    for d in select value from jsonb_array_elements(payload->'days') loop
      dt := (d->>'date')::date;
      if dt < today - 13 or dt > today then raise exception 'Invalid date'; end if;
      if jsonb_typeof(d->'brands') <> 'array' or jsonb_array_length(d->'brands') > 100 then raise exception 'Invalid brands'; end if;
      delete from public.bond_days where owner = me and day = dt;
      insert into public.bond_days(owner, day, swipes, distance_meters, smoking_tracked) values
        (me, dt, case when my_profile.share_reels then (d->>'swipes')::integer end,
        case when my_profile.share_reels then (d->>'distance_meters')::numeric end,
        my_profile.share_smoking and coalesce((d->>'smoking_tracked')::boolean, false));
      if my_profile.share_smoking then
        for b in select value from jsonb_array_elements(d->'brands') loop
          insert into public.bond_brand_days(owner, day, brand_id, brand_name, cigarettes, cost_micros)
            values (me, dt, b->>'id', b->>'name', (b->>'count')::integer, (b->>'cost_micros')::bigint);
        end loop;
      end if;
    end loop;
    delete from public.bond_days where owner = me and day < today - 27;
    return jsonb_build_object('ok', true);
  elsif action <> 'snapshot' then
    raise exception 'Unknown action';
  end if;

  select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'status', f.status,
    'incoming', f.recipient = me) order by p.name), '[]'::jsonb) into friends
  from public.bond_friends f join public.bond_profiles p on p.id = case when f.requester = me then f.recipient else f.requester end
  where f.requester = me or f.recipient = me;

  with circle as (
    select me id union select case when requester = me then recipient else requester end
    from public.bond_friends where (requester = me or recipient = me) and status = 'accepted'
  ), days as (
    select owner, count(*) filter(where smoking_tracked and day >= today - 6)::integer tracked,
      count(*) filter(where smoking_tracked and day < today - 6)::integer previous_tracked,
      sum(swipes) filter(where day >= today - 6) swipes,
      sum(distance_meters) filter(where day >= today - 6) distance
    from public.bond_days where day between today - 13 and today and owner in (select id from circle) group by owner
  ), consumption as (
    select owner, coalesce(sum(cigarettes) filter(where day >= today - 6), 0)::integer cigarettes,
      coalesce(sum(cigarettes) filter(where day < today - 6), 0)::integer previous,
      coalesce(sum(cost_micros) filter(where day >= today - 6), 0)::bigint cost
    from public.bond_brand_days where day between today - 13 and today and owner in (select id from circle)
      and (brand is null or brand_id = brand) group by owner
  ), rows as (
    select p.id, p.name, case when p.share_smoking then coalesce(c.cigarettes, 0) end cigarettes,
      case when p.share_smoking then coalesce(c.cost, 0) end cost_micros,
      case when p.share_reels then coalesce(d.swipes, 0) end swipes,
      case when p.share_reels then coalesce(d.distance, 0) end distance_meters,
      case when p.share_smoking and d.tracked = 7 and d.previous_tracked = 7 and c.previous > 0
        then trunc((c.previous - c.cigarettes) * 100.0 / c.previous)::integer end reduction,
      coalesce(d.tracked, 0) tracked_days
    from public.bond_profiles p join circle on circle.id = p.id
    left join days d on d.owner = p.id left join consumption c on c.owner = p.id
  ) select coalesce(jsonb_agg(to_jsonb(rows) order by
    case when metric = 'reduction' then reduction end desc nulls last,
    case when metric = 'spending' and tracked_days = 7 then cost_micros end asc nulls last,
    case when metric = 'swipes' then swipes end asc nulls last, name, id), '[]'::jsonb) into leaders from rows;
  return jsonb_build_object('profile', to_jsonb(my_profile) - 'id', 'friends', friends, 'leaders', leaders);
end;
$$;
revoke all on function public.social_action(text, jsonb) from public;
grant execute on function public.social_action(text, jsonb) to authenticated;
commit;
