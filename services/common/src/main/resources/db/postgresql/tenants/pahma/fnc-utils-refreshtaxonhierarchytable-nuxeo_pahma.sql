CREATE OR REPLACE FUNCTION utils.refreshtaxonhierarchytable()
 RETURNS void
 LANGUAGE sql
AS $function$
   insert into utils.refresh_log (msg) values ( 'Creating taxon_hierarchy table' );
   select utils.createTaxonHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'Populating taxon_hierarchy table' );
   select utils.populateTaxonHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'Updating taxon_hierarchy table' );
   select utils.updateTaxonHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'All done' );
$function$
