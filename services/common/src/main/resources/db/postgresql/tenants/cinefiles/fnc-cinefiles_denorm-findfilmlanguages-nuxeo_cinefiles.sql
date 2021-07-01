CREATE OR REPLACE FUNCTION cinefiles_denorm.findfilmlanguages(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   langstring text;
   r text;

begin

langstring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(wcl.item) filmLanguage
FROM works_common wc
LEFT OUTER JOIN works_cinefiles_languages wcl
   ON ( wc.id = wcl.id)
WHERE wc.shortidentifier = $1
ORDER BY wcl.pos
LOOP

langstring := langstring || r || '|';

END LOOP;

if langstring = '|' then langstring = null;
end if;

langstring := trim(trailing '|' from langstring);

return langstring;
end;
$function$
