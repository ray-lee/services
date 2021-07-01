CREATE OR REPLACE FUNCTION public.get_deaddate(cocid character varying, bedloc character varying)
 RETURNS timestamp without time zone
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
        recCount int;
        deadDate  timestamp;

begin

        select into recCount count(distinct(mc.locationdate))
        from movements_common mc
        join movements_botgarden mb on (mc.id = mb.id)
        join hierarchy hmc on (mc.id = hmc.id)
        join relations_common rc on (hmc.name = rc.subjectcsid and rc.objectdocumenttype = 'CollectionObject')
        join hierarchy hcoc on (rc.objectcsid = hcoc.name)
        join collectionobjects_common coc on (hcoc.id = coc.id)
        where coc.objectnumber = $1
        and getdispl(mc.reasonformove) = 'Dead'
        and getdispl(mb.previouslocation) = $2;

        if recCount = 0 then return null;

        elseif recCount = 1 then
                select into deadDate distinct mc.locationdate
                from movements_common mc
                join movements_botgarden mb on (mc.id = mb.id)
                join hierarchy hmc on (mc.id = hmc.id)
                join relations_common rc on (hmc.name = rc.subjectcsid and rc.objectdocumenttype = 'CollectionObject')
                join hierarchy hcoc on (rc.objectcsid = hcoc.name)
                join collectionobjects_common coc on (hcoc.id = coc.id)
                where coc.objectnumber = $1
                and getdispl(mc.reasonformove) = 'Dead'
                and getdispl(mb.previouslocation) = $2;

                return deadDate;

        elseif recCount > 1 then
                select into deadDate max(distinct(mc.locationdate))
                from movements_common mc
                join movements_botgarden mb on (mc.id = mb.id)
                join hierarchy hmc on (mc.id = hmc.id)
                join relations_common rc on (hmc.name = rc.subjectcsid and rc.objectdocumenttype = 'CollectionObject')
                join hierarchy hcoc on (rc.objectcsid = hcoc.name)
                join collectionobjects_common coc on (hcoc.id = coc.id)
                where coc.objectnumber = $1
                and getdispl(mc.reasonformove) = 'Dead'
                and getdispl(mb.previouslocation) = $2;

                return deadDate;
        end if;

        return null;

end;

$function$
