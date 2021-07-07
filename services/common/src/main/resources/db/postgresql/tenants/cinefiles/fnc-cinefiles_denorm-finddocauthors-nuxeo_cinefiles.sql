CREATE OR REPLACE FUNCTION cinefiles_denorm.finddocauthors(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   docauthorstring text;
   r text;

begin

docauthorstring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(oppg.objectproductionperson) docauthor
FROM collectionobjects_common co
LEFT OUTER JOIN hierarchy h2
   ON (h2.parentid = co.id AND h2.primarytype = 'objectProductionPersonGroup')
LEFT OUTER JOIN objectProductionPersonGroup oppg
   ON (h2.id = oppg.id)
WHERE co.objectnumber = $1
ORDER BY h2.pos

LOOP

docauthorstring := docauthorstring || r || '|';

END LOOP;

if docauthorstring = '|' then docauthorstring = null;
end if;

docauthorstring := trim(trailing '|' from docauthorstring);

return docauthorstring;
end;
$function$
