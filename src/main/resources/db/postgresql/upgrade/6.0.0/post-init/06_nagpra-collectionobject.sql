/* -- CSpace_v6.0-2 Pre-deploy for PAHMA
 *
 * CC-1478: migrates data from old to new tables:
 *   -- collectionobjects_anthropology_nagprainventorynamelist
 *          to collectionobjects_nagpra_nagprainventorynames
 *   -- collectionobjects_anthropology_nagpraapplicabilitylist
 *          to collectionobjects_nagpra_nagpracategories
 *   -- collectionobjects_pahma_pahmanagpracodelegacylist
 *          to collectionobjects_nagpra_graveassoccodes
 *   -- collectionobjects_anthropology_repatriationnotelist
 *          to collectionobjects_nagpra_repatriationnotes
 *   -- collectionobjects_anthropology_nagpraculturaldeterminationlist
 *          to collectionobjects_nagpra_nagpraculturaldeterminations
 *
 * Run as app_pahma:
 *     sudo su - app_pahma
 *     psql -h $DB_HOST -p $DB_PORT -d $PGDATABASE -U nuxeo$CSPACE_INSTANCE_ID -a -f cc1478_migrate.sql > cc1478_migrate.log 2>&1
 *
*/

DO $$
DECLARE
    c        varchar[];
    carr     varchar[] := array[['collectionobjects_anthropology_nagprainventorynamelist',
                                    'collectionobjects_nagpra_nagprainventorynames']
                              , ['collectionobjects_anthropology_nagpraapplicabilitylist',
                                    'collectionobjects_nagpra_nagpracategories']
                              , ['collectionobjects_pahma_pahmanagpracodelegacylist',
                                    'collectionobjects_nagpra_graveassoccodes']
                              , ['collectionobjects_anthropology_repatriationnotelist',
                                    'collectionobjects_nagpra_repatriationnotes']
                              , ['collectionobjects_anthropology_nagpraculturaldeterminationlist',
                                    'collectionobjects_nagpra_nagpraculturaldeterminations']];
    rc      int;
    tc0     int;
    tc1     int;
    tc      int;
    errmsg  text;

BEGIN
    -- Check for correct database
    IF (SELECT current_database() = 'pahma_domain_pahma') THEN

        -- Check tables exist
        FOREACH c SLICE 1 IN ARRAY carr LOOP
            IF ((SELECT true FROM pg_tables WHERE tablename = c[1]) AND (SELECT true FROM pg_tables WHERE tablename = c[2])) THEN
                -- RAISE NOTICE 'INFO: TABLES EXIST: %', c[1];

                -- check for existing and conflicting data on id and pos in new tables
                EXECUTE 'select count(*) from ' || c[1] || ' t1 join ' || c[2] || ' t2 on (t1.id = t2.id and t1.pos = t2.pos)'
                INTO rc;

                IF rc > 0 THEN
                    RAISE WARNING 'WARNING: % records in % contain duplicate id, pos.', rc, c[1];
                END IF;

                EXECUTE 'select count(*) from '|| c[2]
                INTO tc0;

                -- copy records from original to destination tables
                EXECUTE 'insert into '|| c[2] || ' (id, item, pos) ' ||
                        ' select id, item, pos from ' || c[1];

                EXECUTE 'select count(*) from '|| c[2]
                INTO tc1;

                tc := tc1 - tc0;

                RAISE NOTICE 'INFO: INSERTED % % records into %', tc, c[1], c[2];

            ELSE
                RAISE WARNING 'ERROR: TABLE(S) MISSING: % or %', c[1], c[2];
            END IF;

        END LOOP;

    ELSE
        RAISE WARNING 'ERROR: Not in pahma_domain_pahma database';
    END IF;

    EXCEPTION
    WHEN OTHERS THEN
        errmsg := SUBSTR(SQLERRM, 1, 100);
        RAISE WARNING 'ERROR: %', errmsg;

END $$;
