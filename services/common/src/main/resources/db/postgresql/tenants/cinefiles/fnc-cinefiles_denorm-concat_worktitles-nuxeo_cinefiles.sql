CREATE OR REPLACE FUNCTION cinefiles_denorm.concat_worktitles(shortid character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
DECLARE
    titlestring VARCHAR(1000);
    preftitle VARCHAR(500);
    engltitle VARCHAR(500);
    prefcount INTEGER;
    englcount INTEGER;
    errormsg VARCHAR(500);
BEGIN
select into prefcount count(*)
from works_common wc
inner join hierarchy hwc on (
    wc.id = hwc.parentid
    and hwc.primarytype = 'workTermGroup'
    and hwc.pos = 0)
inner join worktermgroup wtg on (hwc.id = wtg.id)
where wc.shortidentifier = $1;

select into englcount count(*)
from works_common wc
inner join hierarchy hwc on (
    wc.id = hwc.parentid
    and hwc.primarytype = 'workTermGroup'
    and hwc.pos > 0)
inner join worktermgroup wtg on (
    hwc.id = wtg.id
    and wtg.termlanguage like '%''English''%')
where wc.shortidentifier = $1;

IF prefcount = 0 THEN
    return NULL;

ELSEIF prefcount > 1 THEN
    errormsg := 'There can be only one! But there are ' ||
        prefcount::text || ' preferred titles!';
    RAISE EXCEPTION '%', errormsg;

ELSEIF prefcount = 1 THEN
    select into preftitle trim(wtg.termdisplayname)
    from works_common wc
    inner join hierarchy hwc on (
        wc.id = hwc.parentid
        and hwc.primarytype = 'workTermGroup'
        and hwc.pos = 0)
    inner join worktermgroup wtg on (hwc.id = wtg.id)
    where wc.shortidentifier = $1;

    IF englcount = 0 THEN
        titlestring := preftitle;
        RETURN titlestring;

    ELSEIF englcount >= 1 THEN

        IF englcount > 1 THEN
            errormsg := 'There should be only one! But there are ' ||
                englcount::text || ' non-preferred English titles!';
        END IF;
        
        select into engltitle trim(wtg.termdisplayname)
        from works_common wc
        inner join hierarchy hwc on (
            wc.id = hwc.parentid
            and hwc.primarytype = 'workTermGroup'
            and hwc.pos > 0)
        inner join worktermgroup wtg on (
            hwc.id = wtg.id
            and wtg.termlanguage like '%''English''%')
        where wc.shortidentifier = $1 limit 1;

        titlestring := preftitle || ' (' || engltitle || ')';
        RETURN titlestring;

    ELSE
        errormsg := 'Unable to get a count of non-preferred English titles!';
        RAISE EXCEPTION '%', errormsg;
    END IF;

ELSE
    errormsg := 'Unable to get a count of preferred titles!';
    RAISE EXCEPTION '%', errormsg;
END IF;

RETURN NULL;

END;

$function$
