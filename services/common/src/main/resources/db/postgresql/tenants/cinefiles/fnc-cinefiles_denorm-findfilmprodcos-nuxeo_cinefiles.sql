CREATE OR REPLACE FUNCTION cinefiles_denorm.findfilmprodcos(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   prodcostring text;
   r text;

begin

prodcostring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(pg.publisher) filmProdco
FROM hierarchy h1
INNER JOIN works_common wc
   ON ( h1.id = wc.id AND h1.primarytype = 'WorkitemTenant50' )
LEFT OUTER JOIN hierarchy h5
   ON ( h5.parentid = h1.id AND h5.primarytype = 'publisherGroup')
LEFT OUTER JOIN publisherGroup pg
   ON ( h5.id = pg.id )
WHERE wc.shortidentifier = $1
ORDER BY h5.pos
LOOP

prodcostring := prodcostring || r || '|';

END LOOP;

if prodcostring = '|' then prodcostring = null;
end if;

prodcostring := trim(trailing '|' from prodcostring);

return prodcostring;
end;
$function$
