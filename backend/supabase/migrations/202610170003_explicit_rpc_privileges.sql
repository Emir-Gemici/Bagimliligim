-- Supabase can grant EXECUTE directly to API roles through default privileges.
-- Revoking PUBLIC alone does not remove those explicit grants.
begin;
revoke all on function public.reject_price_rewrite() from public, anon, authenticated;
revoke all on function public.catalog_snapshot() from public, anon, authenticated;
revoke all on function public.social_action(text, jsonb) from public, anon, authenticated;
grant execute on function public.catalog_snapshot() to anon, authenticated;
grant execute on function public.social_action(text, jsonb) to authenticated;
commit;
