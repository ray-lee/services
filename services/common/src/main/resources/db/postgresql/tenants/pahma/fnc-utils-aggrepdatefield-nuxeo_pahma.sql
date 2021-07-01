CREATE OR REPLACE FUNCTION utils.aggrepdatefield(coid character varying, tname character varying, cname character varying, sepval character varying)
 RETURNS character varying
 LANGUAGE plpgsql
AS $function$
DECLARE
    ctype VARCHAR;
    cstr VARCHAR := cname::text;
    aggstr VARCHAR;
BEGIN

    EXECUTE 'SELECT STRING_AGG(CASE WHEN s.' || cname || ' IS NULL THEN ''%NULLVALUE%'''
        || ' ELSE s.' || cstr || ' END, ''' || sepval || ''' ORDER BY h.pos)'
        || ' FROM hierarchy h' 
        || ' JOIN ' || tname || ' n ON (h.id = n.id)'
        || ' JOIN hierarchy hn on (n.id = hn.parentid AND hn.primarytype = ''structuredDateGroup'')' 
        || ' JOIN structureddategroup s ON (hn.id = s.id)'
        || ' WHERE h.parentid = $2'
        || ' GROUP BY h.parentid'
        INTO aggstr
        USING tname, coid;

    RETURN aggstr;

END;
$function$
