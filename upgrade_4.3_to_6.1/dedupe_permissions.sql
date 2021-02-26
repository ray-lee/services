-- CSpace 6.1 creates permissions on initialization that were previously created during role
-- creation. This can result in duplicate permissions when a database from an older version of
-- CSpace is upgraded. This script removes the duplicates.

DO $$
DECLARE
    trow record;
    newcsid varchar;
BEGIN
    FOR trow IN
      SELECT * from permissions p
      INNER JOIN (
        SELECT tenant_id, resource_name, action_group, effect
        FROM permissions
        GROUP BY tenant_id, resource_name, action_group, effect
        HAVING COUNT(*) > 1
      ) AS dupe
      ON
        p.tenant_id = dupe.tenant_id AND
        p.resource_name = dupe.resource_name AND
        p.action_group = dupe.action_group AND
        p.effect = dupe.effect AND
        p.metadata_protection IS NULL
    LOOP
      SELECT csid INTO newcsid
      FROM permissions p
      WHERE
        p.tenant_id = trow.tenant_id AND
        p.resource_name = trow.resource_name AND
        p.action_group = trow.action_group AND
        p.effect = trow.effect AND
        p.metadata_protection IS NOT NULL;

      RAISE NOTICE 'Permission with csid % duplicates %', trow.csid, newcsid;

      UPDATE permissions_actions
        SET action__permission_csid = newcsid
        WHERE action__permission_csid = trow.csid;

      UPDATE permissions_roles
        SET permission_id = newcsid
        WHERE permission_id = trow.csid;

      DELETE FROM permissions WHERE csid = trow.csid;
    END LOOP;
END
$$;
