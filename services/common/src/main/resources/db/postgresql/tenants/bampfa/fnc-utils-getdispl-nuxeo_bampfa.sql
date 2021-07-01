CREATE OR REPLACE FUNCTION utils.getdispl(text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
 SET search_path TO 'public'
AS $function$
SELECT regexp_replace($1, '^.*\)''(.*)''$', '\1')
$function$
