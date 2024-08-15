-- Upgrade collectionobject. Move number of objects into the new repeating object count group (DRYD-1372).

DO $$
DECLARE
    trow record;
    maxpos int;
    uuid varchar(36);
    objectcounttype varchar;
BEGIN

    -- For new install, if collectionobjects_common.numberofobjects does not exist, there is nothing to migrate.

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='collectionobjects_common' AND column_name='numberofobjects') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='collectionobjects_pahma' AND column_name='inventorycount') THEN
            -- If this isn't PAHMA, create a temp (empty) collectionobjects_pahma table so that the rest of this script will work.
            CREATE TEMP TABLE collectionobjects_pahma (inventorycount VARCHAR);
        END IF;

        IF starts_with(current_database(), 'pahma') THEN
            -- If this is PAHMA, set object count type to piece count.
            objectcounttype = 'urn:cspace:pahma.cspace.berkeley.edu:vocabularies:name(objectcounttypes):item:name(piece_count)''piece count''';
        END IF;

        FOR trow IN
            -- Get record in collectionobjects_common that does not have an existing/matching record in objectcountgroup:

            SELECT cc.id AS parentid, cc.numberofobjects, cp.inventorycount
            FROM public.collectionobjects_common cc
            LEFT OUTER JOIN collectionobjects_pahma cp ON cc.id = cp.id
            WHERE cc.id NOT IN (
                SELECT cc.id
                FROM public.collectionobjects_common cc
                JOIN collectionobjects_pahma cp ON cc.id = cp.id
                JOIN public.hierarchy h ON (cc.id = h.parentid)
                JOIN public.objectcountgroup ocg ON (
                    h.id = ocg.id
                    AND cc.numberofobjects IS NOT DISTINCT FROM ocg.objectcount
                    AND cp.inventorycount IS NOT DISTINCT FROM ocg.objectcountnote
                )
            )
            AND (cc.numberofobjects IS NOT NULL OR cp.inventorycount IS NOT NULL)

            LOOP
                -- Get max pos value for the collectionobject record's object count group, and generate a new uuid:

                SELECT
                    coalesce(max(pos), -1),
                    uuid_generate_v4()::varchar
                INTO
                    maxpos,
                    uuid
                FROM public.hierarchy
                WHERE parentid = trow.parentid
                    AND name = 'collectionobjects_common:objectCountGroupList'
                    AND primarytype = 'objectCountGroup';

                -- Insert new record into hierarchy table first, due to foreign key on objectcountgroup table:

                INSERT INTO public.hierarchy (
                    id,
                    parentid,
                    pos,
                    name,
                    isproperty,
                    primarytype)
                VALUES (
                    uuid,
                    trow.parentid,
                    maxpos + 1,
                    'collectionobjects_common:objectCountGroupList',
                    TRUE,
                    'objectCountGroup');

                -- Migrate collectionobject number of objects data into objectcountgroup table:

                INSERT INTO public.objectcountgroup (
                    id,
                    objectcount,
                    objectcountnote,
                    objectcounttype)
                VALUES (
                    uuid,
                    trow.numberofobjects,
                    trow.inventorycount,
                    objectcounttype);

            END LOOP;

    ELSE
        RAISE NOTICE 'No v7.2 collectionobject number of objects data to migrate: collectionobjects_common.numberofobjects does not exist';

    END IF;
END
$$;