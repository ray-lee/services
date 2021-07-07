CREATE OR REPLACE FUNCTION utils.createculturehierarchytable()
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
BEGIN
  IF NOT EXISTS ( SELECT relname
                  FROM pg_catalog.pg_class c
                       JOIN
                       pg_catalog.pg_namespace n
                       ON (n.oid = c.relnamespace)
               WHERE c.relname = 'culture_hierarchy'
                 AND n.nspname = 'utils' )
  THEN
    CREATE TABLE utils.culture_hierarchy (
       culturecsid text,
       culture     text,
       parentcsid  text,
       nextcsid    text,
       culture_hierarchy text,
       csid_hierarchy text );

    CREATE INDEX uch_ccsid_ndx on utils.culture_hierarchy ( culturecsid );
    CREATE INDEX uch_cname_ndx on utils.culture_hierarchy ( culture );
  END IF;
END;
$function$
