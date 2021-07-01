CREATE OR REPLACE FUNCTION cinefiles_denorm.finddoclanguages(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   doclangstring text;
   r text;

begin

doclangstring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(cdl.item) doclanguage
FROM collectionobjects_common co
LEFT OUTER JOIN collectionobjects_cinefiles_doclanguages cdl ON (co.id = cdl.id)
WHERE co.objectnumber = $1
ORDER BY cdl.pos

LOOP

doclangstring := doclangstring || r || '|';

END LOOP;

if doclangstring = '|' then doclangstring = null;
end if;

doclangstring := trim(trailing '|' from doclangstring);

return doclangstring;
end;
$function$
