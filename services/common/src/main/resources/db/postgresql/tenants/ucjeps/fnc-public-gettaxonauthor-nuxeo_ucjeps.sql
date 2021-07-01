CREATE OR REPLACE FUNCTION public.gettaxonauthor(tcid character varying, tatype character varying DEFAULT 'author'::character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE
        authorname VARCHAR(300);

BEGIN
        SELECT getdispl(tag.taxonauthor) into authorname
        FROM taxonauthorgroup tag, hierarchy h, taxon_common tc
        WHERE tc.id = h.parentid
        AND h.id = tag.id
        AND h.primarytype = 'taxonAuthorGroup'
        AND tc.id = $1
        AND tag.taxonauthortype = $2;

        RETURN authorname;
END;
$function$
