CREATE OR REPLACE FUNCTION utils.get_object_image_count(objcsid character varying)
 RETURNS integer
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE
   imagecount INTEGER;

BEGIN

select count(*)
into imagecount
from collectionobjects_common co
   JOIN hierarchy hrel on (co.id = hrel.id)
   JOIN relations_common rimg on (
       hrel.name = rimg.objectcsid and rimg.subjectdocumenttype = 'Media')
   JOIN hierarchy hmc on (rimg.subjectcsid = hmc.name)
   JOIN media_common mc on (mc.id = hmc.id)
   JOIN misc m on (mc.id = m.id and m.lifecyclestate <> 'deleted')
where hrel.name = $1;

RETURN imagecount;

END;

$function$
