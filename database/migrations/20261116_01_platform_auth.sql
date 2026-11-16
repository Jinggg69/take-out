-- Platform identities and hashed opaque sessions have no merchant ownership or JWT sessions.
CREATE TABLE IF NOT EXISTS platform_admin (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
 password VARCHAR(255) NOT NULL,
 name VARCHAR(64) NOT NULL,
 status TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_platform_username(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS platform_session (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 admin_id BIGINT NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_platform_session_admin(admin_id),
 KEY idx_platform_session_expiry(expires_at),
 CONSTRAINT fk_platform_session_admin FOREIGN KEY(admin_id) REFERENCES platform_admin(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
