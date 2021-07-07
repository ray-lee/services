CREATE OR REPLACE FUNCTION public.concat_comments(cocid character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE commstring VARCHAR(300);

BEGIN

select array_to_string(array_agg(getdispl(cocc.item)), '; ') into commstring
from collectionobjects_common coc
inner join collectionobjects_common_comments cocc on (coc.id = cocc.id)
where coc.id = $1
and cocc.item is not null
and cocc.item != ''
group by coc.id;

RETURN commstring;

END;

$function$
