CREATE OR REPLACE FUNCTION utils.get_first_blobcsid(title character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE blobcsid VARCHAR(50);

BEGIN

select
   mc.blobcsid into blobcsid
from collectionobjects_common co
   JOIN hierarchy hrel on (co.id = hrel.id)
   JOIN relations_common rimg on (hrel.name = rimg.objectcsid and rimg.subjectdocumenttype='Media')
   JOIN hierarchy hmc on (rimg.subjectcsid = hmc.name)
   JOIN media_common mc on (mc.id=hmc.id)
   JOIN misc m on (mc.id=m.id and m.lifecyclestate<>'deleted')
   JOIN media_bampfa mb on (mc.id=mb.id and mb.imagenumber='1' and mb.websitedisplaylevel != 'No public display')
where hrel.name=$1;

RETURN blobcsid;

END;

$function$
