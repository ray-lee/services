CREATE OR REPLACE FUNCTION utils.aggrepfield(coid character varying, tname character varying, sepval character varying)
 RETURNS character varying
 LANGUAGE plpgsql
AS $function$
DECLARE
    aggstr VARCHAr;
BEGIN
    EXECUTE 'SELECT STRING_AGG(CASE WHEN item IS NULL THEN ''%NULLVALUE%'''
        || ' ELSE regexp_replace(item, E''[\\n\\r]+'', ''\n'', ''g'') END, ''' || sepval || ''' ORDER BY pos)'
        || ' FROM ' || tname 
        || ' WHERE id = $1'
        || ' GROUP BY id'
        INTO aggstr
        USING coid;

    RETURN aggstr;
END;
$function$
