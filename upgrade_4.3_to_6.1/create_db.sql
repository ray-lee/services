DROP DATABASE omca_domain_omca;
CREATE DATABASE omca_domain_omca WITH TEMPLATE = template0 OWNER = nuxeo_omca ENCODING = 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8';
GRANT ALL ON DATABASE omca_domain_omca TO nuxeo_omca;
GRANT CONNECT ON DATABASE omca_domain_omca TO reader_omca;

DROP DATABASE cspace_omca;
CREATE DATABASE cspace_omca WITH TEMPLATE = template0 OWNER = cspace_omca ENCODING = 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8';
