CREATE TABLE IF NOT EXISTS sys_app_metadata (
    id BIGINT NOT NULL,
    app_name VARCHAR(64) NOT NULL,
    app_version VARCHAR(64) NOT NULL,
    environment_code VARCHAR(32) NOT NULL,
    metadata_status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_app_metadata_name_env (app_name, environment_code)
);

INSERT INTO sys_app_metadata (
    id,
    app_name,
    app_version,
    environment_code,
    metadata_status
) VALUES (
    1000000000000000001,
    'wecom-bft-new-system',
    '0.1.0-SNAPSHOT',
    'baseline',
    'ACTIVE'
);
