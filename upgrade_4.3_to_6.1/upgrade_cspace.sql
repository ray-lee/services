ALTER TABLE permissions ADD COLUMN actions_protection VARCHAR(255);
ALTER TABLE permissions ADD COLUMN metadata_protection VARCHAR(255);

ALTER TABLE tenants ADD COLUMN config_md5hash VARCHAR(255);
ALTER TABLE tenants ADD COLUMN authorities_initialized BOOLEAN;
UPDATE tenants SET authorities_initialized = FALSE;
ALTER TABLE tenants ALTER COLUMN authorities_initialized SET NOT NULL;

ALTER TABLE users ADD COLUMN lastlogin TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE users ADD COLUMN salt VARCHAR(128);

CREATE TABLE tokens (
  id VARCHAR(128) NOT NULL,
  account_csid VARCHAR(128) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  expire_seconds INTEGER NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP, PRIMARY KEY (id)
);

ALTER TABLE tokens OWNER TO cspace_default;

ANALYZE;
