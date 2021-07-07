CREATE OR REPLACE FUNCTION public.concat_othernumber(cocid character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE othernumstring VARCHAR(300);

BEGIN

select array_to_string(
    array_agg(coalesce(o.numbertype, 'NULL Number Type') || ':' || o.numbervalue),
    '; ') into othernumstring
from collectionobjects_common coc
inner join hierarchy h on (coc.id = h.parentid and h.primarytype = 'otherNumber')
inner join othernumber o on (h.id = o.id)
where coc.id = $1
and o.numbervalue is not null
and o.numbervalue != ''
group by coc.id;

RETURN othernumstring;

END;

$function$
