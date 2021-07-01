CREATE OR REPLACE FUNCTION cinefiles_denorm.normalizetext(t text)
 RETURNS text
 LANGUAGE sql
AS $function$
select lower((coalesce(ts_lexize('public.unaccent', t), ARRAY[t]))[1])
$function$
