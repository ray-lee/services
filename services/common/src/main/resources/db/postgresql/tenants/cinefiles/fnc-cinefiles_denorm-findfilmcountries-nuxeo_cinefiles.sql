CREATE OR REPLACE FUNCTION cinefiles_denorm.findfilmcountries(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   countrystring text;
   r text;

begin

countrystring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(wcc.item) filmCountry
FROM works_common wc
LEFT OUTER JOIN works_cinefiles_countries wcc
   ON (wc.id = wcc.id)
WHERE wc.shortidentifier = $1
ORDER BY wcc.pos

LOOP

countrystring := countrystring || r || '|';

END LOOP;

if countrystring = '|' then countrystring = null;
end if;

countrystring := trim(trailing '|' from countrystring);

return countrystring;
end;
$function$
