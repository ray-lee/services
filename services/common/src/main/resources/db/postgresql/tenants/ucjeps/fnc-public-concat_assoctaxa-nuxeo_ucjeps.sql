CREATE OR REPLACE FUNCTION public.concat_assoctaxa(cocid character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE atstring VARCHAR(2000);

BEGIN

select array_to_string(array_agg(getdispl(atg.associatedtaxon)), '; ') into atstring
from collectionobjects_common coc
inner join hierarchy hatg on (
    coc.id = hatg.parentid and hatg.primarytype = 'associatedTaxaGroup')
inner join associatedTaxaGroup atg on (hatg.id = atg.id)
where coc.id = $1
and atg.associatedtaxon is not null
and atg.associatedtaxon != ''
group by coc.id;

RETURN atstring;

END;

$function$
