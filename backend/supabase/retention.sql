-- Run once in the Supabase SQL editor after enabling the Cron integration.
create extension if not exists pg_cron with schema pg_catalog;
select cron.unschedule(jobid) from cron.job where jobname = 'bagimliligim-retention';
select cron.schedule('bagimliligim-retention', '0 * * * *', $$
  delete from public.bond_days where day < (now() at time zone 'Europe/Istanbul')::date - 364;
  delete from public.bond_requests where at < now() - interval '1 day';
$$);
