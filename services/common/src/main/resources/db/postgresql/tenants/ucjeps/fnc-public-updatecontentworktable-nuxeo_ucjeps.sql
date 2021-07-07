CREATE OR REPLACE FUNCTION public.updatecontentworktable()
 RETURNS bigint
 LANGUAGE sql
AS $function$
  UPDATE content_work cw
    SET count = wd.count,
        rotated = wd.rotated,
        newdata = wd.newdata
  FROM content_work_done wd
  WHERE cw.id = wd.id;

  SELECT COUNT(*) FROM content_work;
$function$
