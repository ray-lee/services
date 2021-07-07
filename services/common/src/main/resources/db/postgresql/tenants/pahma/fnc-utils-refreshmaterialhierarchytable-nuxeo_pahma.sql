CREATE OR REPLACE FUNCTION utils.refreshmaterialhierarchytable()
 RETURNS void
 LANGUAGE sql
AS $function$
   insert into utils.refresh_log (msg) values ( 'Creating material_hierarchy table' );
   select utils.createMaterialHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'Populating material_hierarchy table' );
   select utils.populateMaterialHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'Updating material_hierarchy table' );
   select utils.updateMaterialHierarchyTable();

   insert into utils.refresh_log (msg) values ( 'All done' );
$function$
