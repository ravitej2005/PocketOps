CREATE TABLE IF NOT EXISTS infrastructures (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    provider_type VARCHAR(64) NULL,
    health_status VARCHAR(32) NOT NULL,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructures_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_infrastructures_user_id ON infrastructures (user_id);

CREATE TABLE IF NOT EXISTS infrastructure_capabilities (
    infrastructure_id CHAR(36) NOT NULL,
    capability VARCHAR(64) NOT NULL,
    PRIMARY KEY (infrastructure_id, capability),
    CONSTRAINT fk_infrastructure_capabilities_infrastructure
        FOREIGN KEY (infrastructure_id) REFERENCES infrastructures (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_resources (
    id CHAR(36) PRIMARY KEY,
    infrastructure_id CHAR(36) NOT NULL,
    external_resource_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    criticality VARCHAR(32) NOT NULL,
    last_seen_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_resources_infrastructure
        FOREIGN KEY (infrastructure_id) REFERENCES infrastructures (id) ON DELETE CASCADE,
    CONSTRAINT uq_infrastructure_resource_external UNIQUE (infrastructure_id, external_resource_id)
);

CREATE INDEX idx_infrastructure_resources_infrastructure_id
    ON infrastructure_resources (infrastructure_id);

CREATE TABLE IF NOT EXISTS provider_credentials (
    id CHAR(36) PRIMARY KEY,
    infrastructure_id CHAR(36) NOT NULL,
    encrypted_payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_provider_credentials_infrastructure
        FOREIGN KEY (infrastructure_id) REFERENCES infrastructures (id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_credentials_infrastructure UNIQUE (infrastructure_id)
);
