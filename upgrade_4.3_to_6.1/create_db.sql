DROP DATABASE omca_domain_default;
CREATE DATABASE omca_domain_default WITH TEMPLATE = template0 OWNER = nuxeo_default ENCODING = 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8';
GRANT ALL ON DATABASE omca_domain_default TO nuxeo_default;
GRANT CONNECT ON DATABASE omca_domain_default TO reader_default;

DROP DATABASE cspace_default;
CREATE DATABASE cspace_default WITH TEMPLATE = template0 OWNER = cspace_default ENCODING = 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8';
