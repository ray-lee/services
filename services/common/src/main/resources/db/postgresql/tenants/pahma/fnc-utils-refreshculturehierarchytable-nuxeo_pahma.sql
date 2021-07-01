CREATE OR REPLACE FUNCTION utils.refreshculturehierarchytable()
 RETURNS void
 LANGUAGE sql
AS $function$
   insert into utils.refresh_log (msg) values ( 'Creating culture_hierarchy table' );
   select utils.createCultureHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'Populating culture_hierarchy table' );
   select utils.populateCultureHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'Updating culture_hierarchy table' );
   select utils.updateCultureHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'All done' );
$function$
