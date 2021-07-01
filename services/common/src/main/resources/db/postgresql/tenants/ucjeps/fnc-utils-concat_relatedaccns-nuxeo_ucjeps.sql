CREATE OR REPLACE FUNCTION utils.concat_relatedaccns(accnid character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE accnstr VARCHAR(1000);

BEGIN

select array_to_string(array_agg(coc.objectnumber), '; ') into accnstr
from
        collectionobjects_common coc,
        collectionobjects_common_fieldcollectors fc,
        (select c.fieldcollectionnumber || f.item as collnum
                from collectionobjects_common c, collectionobjects_common_fieldcollectors f
                where c.id = f.id
                and c.objectnumber = $1) x
where coc.id = fc.id
and coc.fieldcollectionnumber is not null
and length(coc.fieldcollectionnumber) != 0
and fc.item is not null
and length(fc.item) != 0
and coc.fieldcollectionnumber || fc.item = x.collnum
and coc.objectnumber != $1;

RETURN accnstr;

END;

$function$
