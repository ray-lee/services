CREATE OR REPLACE FUNCTION utils.get_first_blobcsid_displevel(title character varying, returnvar character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE
   blobcsid VARCHAR(50);
   displevel VARCHAR(50);

BEGIN

select mc.blobcsid, mb.websitedisplaylevel
into blobcsid, displevel
from collectionobjects_common co
   JOIN hierarchy hrel on (co.id = hrel.id)
   JOIN relations_common rimg on (hrel.name = rimg.objectcsid and rimg.subjectdocumenttype = 'Media')
   JOIN hierarchy hmc on (rimg.subjectcsid = hmc.name)
   JOIN media_common mc on (mc.id = hmc.id)
   JOIN misc m on (mc.id = m.id and m.lifecyclestate <> 'deleted')
   JOIN media_bampfa mb on (mc.id=mb.id and mb.imagenumber = '1')
where hrel.name=$1;

IF $2 = 'blobcsid' THEN
   RETURN blobcsid;
ELSIF $2 = 'displevel' THEN
   RETURN displevel;
ELSIF $2 = 'both' THEN
   RETURN blobcsid || '; ' || displevel;
ELSE
   RAISE NOTICE 'Invalid option: "%". The choices are: "blobcsid", "displevel" or "both".', $2;
END IF;

END;

$function$
