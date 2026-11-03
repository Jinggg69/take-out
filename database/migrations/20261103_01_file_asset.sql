-- Requires 20261029_01_tenant_store.sql. No historical URL is implicitly assigned.
CREATE TABLE IF NOT EXISTS file_asset (
  id varchar(36) NOT NULL PRIMARY KEY,
  tenant_id bigint NOT NULL,
  uploader_employee_id bigint NOT NULL,
  purpose varchar(32) NOT NULL,
  object_key varchar(255) NOT NULL,
  public_url varchar(255) NOT NULL,
  created_at datetime(6) NOT NULL,
  CONSTRAINT uk_file_asset_object UNIQUE (object_key),
  CONSTRAINT uk_file_asset_url UNIQUE (public_url),
  CONSTRAINT fk_file_asset_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT ck_file_asset_purpose CHECK (purpose = 'PUBLIC_CATALOG')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
