CREATE OR REPLACE FUNCTION utils.findconserveinfo(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   conserveinfo text;
   r text;

begin

conserveinfo := '';

FOR r IN
select 
  case when (pag.conservationorganization is not null and pag.conservationorganization not like '%not applicable%')
        then regexp_replace(pag.conservationorganization, '^.*\)''(.*)''$', '\1')||': '||regexp_replace(pag.conservationcategory, '^.*\)''(.*)''$', '\1')
       else regexp_replace(pag.conservationcategory, '^.*\)''(.*)''$', '\1')
  end as conserveinfo
from taxon_common tc
left outer join hierarchy h
     on (tc.id = h.parentid and h.name = 'taxon_naturalhistory:plantAttributesGroupList')
left outer join plantattributesgroup pag on (pag.id=h.id)
where pag.conservationorganization is not null and pag.conservationorganization not like '%not applicable%'
     and tc.refname = $1
order by h.pos

LOOP

conserveinfo := conserveinfo || r || '|';

END LOOP;

if conserveinfo = '|' then conserveinfo = null;
end if;

conserveinfo := trim(trailing '|' from conserveinfo);

return conserveinfo;
end;
$function$
