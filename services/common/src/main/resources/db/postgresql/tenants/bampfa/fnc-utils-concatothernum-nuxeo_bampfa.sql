CREATE OR REPLACE FUNCTION utils.concatothernum(id character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
 
 DECLARE othernumstr VARCHAR(300);
 
 BEGIN
 
 select array_to_string(
     array_agg(
         o.numbervalue ||
         case when o.numbertype is null or o.numbertype = ''
             then ''
             else ' (' || o.numbertype || ')'
         end
     order by ho.pos),
     '; ')
 into othernumstr
 from collectionobjects_common coc
 join hierarchy ho on (
     coc.id = ho.parentid
     and ho.name = 'collectionobjects_common:otherNumberList')
 join othernumber o on (ho.id = o.id)
 where coc.id = $1
 and o.numbervalue is not null
 and o.numbervalue != ''
 group by coc.id;
 
 RETURN othernumstr;
 
 END;
 
$function$
