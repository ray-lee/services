CREATE OR REPLACE FUNCTION cinefiles_denorm.finddocnamesubjects(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   docnamesubjectstring text;
   r text;

begin

docnamesubjectstring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(ccn.item) docnamesubject
FROM collectionobjects_common co
LEFT OUTER JOIN collectionobjects_cinefiles_namesubjects ccn
  ON (co.id = ccn.id)
WHERE co.objectnumber = $1
ORDER BY ccn.pos

LOOP

docnamesubjectstring := docnamesubjectstring || r || '|';

END LOOP;

if docnamesubjectstring = '|' then docnamesubjectstring = null;
end if;

docnamesubjectstring := trim(trailing '|' from docnamesubjectstring);

return docnamesubjectstring;
end;
$function$
