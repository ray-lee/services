CREATE OR REPLACE FUNCTION public.createcontentworktable()
 RETURNS void
 LANGUAGE sql
AS $function$
  DROP TABLE IF EXISTS content_work;

  SELECT *, CAST(NULL AS character varying(40)) AS newdata,
         false as rotated,
         0 as count
  INTO content_work
  FROM content
  WHERE data IS NOT NULL
    AND data != ''
    AND name ILIKE '%.jpg';
$function$
