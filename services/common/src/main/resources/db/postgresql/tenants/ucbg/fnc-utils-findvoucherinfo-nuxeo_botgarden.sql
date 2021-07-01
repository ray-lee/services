CREATE OR REPLACE FUNCTION utils.findvoucherinfo(text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
AS $function$

select
  array_to_string(array_agg(concat_ws(', ', regexp_replace(lc.borrower, '^.*\)''(.*)''$', '\1'), to_char(lc.loanoutdate, 'YYYY-MM-DD'))), '|') as voucherinfo
from relations_common rv 
join hierarchy hv on (rv.objectcsid=hv.name and rv.subjectcsid = $1 and rv.objectdocumenttype='Loanout')
join loansout_common lc on (lc.id=hv.id)
join misc mv on (lc.id=mv.id and mv.lifecyclestate <> 'deleted');

$function$
