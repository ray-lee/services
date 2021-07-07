CREATE OR REPLACE FUNCTION public.concat_docids(title character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE docidstring VARCHAR(1000);

BEGIN

select array_to_string(array_agg(x.doc_id), '; ')
into docidstring
from
(select distinct replace(wc.shortidentifier, 'pfafilm', '')::numeric as doc_id
from works_common wc
inner join hierarchy hwtg on (wc.id = hwtg.parentid and primarytype = 'workTermGroup')
inner join worktermgroup wtg on (hwtg.id = wtg.id)
where wtg.termdisplayname = $1
order by doc_id
) x;

RETURN docidstring;

END;

$function$
