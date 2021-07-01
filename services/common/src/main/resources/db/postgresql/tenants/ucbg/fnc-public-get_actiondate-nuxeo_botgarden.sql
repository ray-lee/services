CREATE OR REPLACE FUNCTION public.get_actiondate(cocid character varying, action character varying, bedloc character varying)
 RETURNS timestamp without time zone
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
        recCount int;
        actionDate  timestamp;

begin

        select into recCount count(distinct(mc.locationdate))
        from movements_common mc
        join hierarchy hmc on (mc.id = hmc.id)
        join relations_common rc on (hmc.name = rc.subjectcsid and rc.objectdocumenttype = 'CollectionObject')
        join hierarchy hcoc on (rc.objectcsid = hcoc.name)
        join collectionobjects_common coc on (hcoc.id = coc.id)
        where coc.objectnumber = $1
        and getdispl(mc.reasonformove) = $2
        and getdispl(mc.currentlocation) = $3;

        if recCount = 0 then return null;

        elseif recCount = 1 then
                select into actionDate distinct mc.locationdate
                from movements_common mc
                join hierarchy hmc on (mc.id = hmc.id)
                join relations_common rc on (hmc.name = rc.subjectcsid and rc.objectdocumenttype = 'CollectionObject')
                join hierarchy hcoc on (rc.objectcsid = hcoc.name)
                join collectionobjects_common coc on (hcoc.id = coc.id)
                where coc.objectnumber = $1
                and getdispl(mc.reasonformove) = $2
                and getdispl(mc.currentlocation) = $3;

                return actionDate;

        elseif recCount > 1 then
                select into actionDate max(distinct(mc.locationdate))
                from movements_common mc
                join hierarchy hmc on (mc.id = hmc.id)
                join relations_common rc on (hmc.name = rc.subjectcsid and rc.objectdocumenttype = 'CollectionObject')
                join hierarchy hcoc on (rc.objectcsid = hcoc.name)
                join collectionobjects_common coc on (hcoc.id = coc.id)
                where coc.objectnumber = $1
                and getdispl(mc.reasonformove) = $2
                and getdispl(mc.currentlocation) = $3;

                return actionDate;
        end if;

        return null;

end;
$function$
