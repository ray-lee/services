CREATE OR REPLACE FUNCTION cinefiles_denorm.findfilmgenres(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   genrestring text;
   r text;

begin

genrestring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(wcg.item) filmGenre
FROM works_common wc
LEFT OUTER JOIN works_cinefiles_genres wcg ON ( wc.id = wcg.id)
WHERE wc.shortidentifier = $1
ORDER BY wcg.pos

LOOP

genrestring := genrestring || r || '|';

END LOOP;

if genrestring = '|' then genrestring = null;
end if;

genrestring := trim(trailing '|' from genrestring);

return genrestring;
end;
$function$
