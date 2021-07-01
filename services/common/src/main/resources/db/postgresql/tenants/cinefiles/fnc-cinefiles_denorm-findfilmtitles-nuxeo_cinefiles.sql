CREATE OR REPLACE FUNCTION cinefiles_denorm.findfilmtitles(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   titlestring text;
   r text;

begin

titlestring := '';

FOR r IN
SELECT wtg.termdisplayname filmTitle
FROM hierarchy h1
INNER JOIN works_common wc
   ON ( h1.id = wc.id AND h1.primarytype = 'WorkitemTenant50' )
LEFT OUTER JOIN hierarchy h3
   ON ( h3.parentid = h1.id AND h3.primarytype = 'workTermGroup')
LEFT OUTER JOIN worktermgroup wtg
   ON ( h3.id = wtg.id)
WHERE wc.shortidentifier = $1
ORDER BY h3.pos

LOOP

titlestring := titlestring || r || '|';

END LOOP;

if titlestring = '|' then titlestring = null;
end if;

titlestring := trim(trailing '|' from titlestring);

return titlestring;
end;
$function$
