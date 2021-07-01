CREATE OR REPLACE FUNCTION public.concat_collectors(cocid character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE collstring VARCHAR(300);

BEGIN

select array_to_string(array_agg(getdispl(cocfc.item)), '; ') into collstring
from collectionobjects_common coc
inner join collectionobjects_common_fieldcollectors cocfc on (coc.id = cocfc.id)
where coc.id = $1
and cocfc.item is not null
and cocfc.item != ''
group by coc.id;

RETURN collstring;

END;

$function$
