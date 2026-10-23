-- Keep the initial deployed migration immutable; extend its retention to 365 dates.
begin;
do $migration$
declare
  definition text := pg_get_functiondef('public.social_action(text,jsonb)'::regprocedure);
begin
  if position('day < today - 27' in definition) > 0 then
    execute replace(definition, 'day < today - 27', 'day < today - 364');
  elsif position('day < today - 364' in definition) = 0 then
    raise exception 'Unrecognized retention expression; inspect social_action before migrating';
  end if;
end;
$migration$;
commit;
