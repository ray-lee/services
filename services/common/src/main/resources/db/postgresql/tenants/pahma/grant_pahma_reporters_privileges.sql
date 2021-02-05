DO $$
BEGIN
   CREATE SCHEMA IF NOT EXISTS utils AUTHORIZATION nuxeo_pahma;
   GRANT USAGE ON SCHEMA utils TO reader_pahma;

   IF EXISTS (
      SELECT *
      FROM   pg_catalog.pg_group
      WHERE  groname = 'reporters_pahma') THEN
   ELSE
      CREATE ROLE reporters_pahma with nologin;
   END IF;
   GRANT SELECT ON ALL TABLES IN SCHEMA public TO GROUP reporters_pahma;
END $$;
