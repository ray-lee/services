CREATE OR REPLACE FUNCTION cinefiles_denorm.findfilmyears(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   yearstring text;
   r text;

begin

yearstring := '';

FOR r IN
SELECT
--   h1.name filmCSID,
--   wc.shortidentifier film_id,
   cast(sdg.dateearliestsingleyear as text) filmyear
FROM
   hierarchy h1
   INNER JOIN works_common wc
      ON ( h1.id = wc.id AND h1.primarytype = 'WorkitemTenant50' )
   INNER JOIN misc m
      ON ( wc.id = m.id AND m.lifecyclestate <> 'deleted' )
   LEFT OUTER JOIN hierarchy h4
      ON ( h4.parentid = h1.id AND h4.name = 'works_common:workDateGroupList') 
   LEFT OUTER JOIN structureddategroup sdg
      ON ( h4.id = sdg.id )
WHERE wc.shortidentifier = $1
ORDER BY h4.pos

LOOP

yearstring := yearstring || r || '|';

END LOOP;

if yearstring = '|' then yearstring = null;
end if;

yearstring := trim(trailing '|' from yearstring);

return yearstring;
end;
$function$
