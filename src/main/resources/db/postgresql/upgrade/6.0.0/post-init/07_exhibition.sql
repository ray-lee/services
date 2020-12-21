/* -- CSpace_v6.0-2 Pre-deploy for PAHMA
 *
 * CC-1509: Related to CC-1267
 *
 * Exhibition data is not being displayed in UI due to changes in CC-1267 to remove Exhibition customizations.
 *
 * Update hierarchy.primarytype values for exhibition records to remove customization suffix.
 *
 * Update hierarchy.name values for exhibition repeating group records 
 * from exhibitions_pahma to exhibitions_common for:
 * -- exhibitionStatusGroup
 * -- exhibitionSectionGroup
 * -- exhibitionObjectGroup
 * -- galleryRotationGroup
 *
*/

DO $$
DECLARE
    uc      integer;
    errmsg  text;

BEGIN
    -- Check for correct database
    IF (SELECT current_database() = 'pahma_domain_pahma') THEN

        -- update Exhibition records with correct hiearchy.primarytype
        update hierarchy
        set primarytype = 'Exhibition'
        where primarytype = 'ExhibitionTenant15';

        get diagnostics uc = row_count;

        RAISE NOTICE '% hierarchy.primarytype records updated for Exhibitions', uc;

        -- update Exhibition repeating group records with correct hiearchy.name
        update hierarchy
        set name = regexp_replace(name, '^exhibitions_pahma:', 'exhibitions_common:')
        where primarytype in (
          'exhibitionObjectGroup',
          'exhibitionSectionGroup',
          'exhibitionStatusGroup',
          'galleryRotationGroup');

        get diagnostics uc = row_count;

        RAISE NOTICE '% hierarchy.name records updated for Exhibitions repeating groups', uc;

    ELSE
        RAISE WARNING 'ERROR: Not in pahma_domain_pahma database';
    END IF;

    EXCEPTION
    WHEN OTHERS THEN
        errmsg := SUBSTR(SQLERRM, 1, 100);
        RAISE WARNING 'ERROR: %', errmsg;

END $$;
