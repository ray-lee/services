CREATE OR REPLACE FUNCTION utils.findconserveorg(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   conserveorg text;
   r text;

begin

conserveorg := '';

FOR r IN
select 
   regexp_replace(pag.conservationorganization, '^.*\)''(.*)''$', '\1') as conserveorg
from taxon_common tc
left outer join hierarchy h
     on (tc.id = h.parentid and h.name = 'taxon_naturalhistory:plantAttributesGroupList')
left outer join plantattributesgroup pag on (pag.id=h.id)
where pag.conservationorganization is not null and pag.conservationorganization not like '%not applicable%'
     and tc.refname = $1
order by h.pos

LOOP

conserveorg := conserveorg || r || '|';

END LOOP;

if conserveorg = '|' then conserveorg = null;
end if;

conserveorg := trim(trailing '|' from conserveorg);

return conserveorg;
end;
$function$
