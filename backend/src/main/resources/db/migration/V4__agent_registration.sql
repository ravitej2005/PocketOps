CREATE TABLE IF NOT EXISTS agent_registration_tokens (
    id CHAR(36) PRIMARY KEY,
    infrastructure_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_registration_tokens_infrastructure
        FOREIGN KEY (infrastructure_id) REFERENCES infrastructures (id) ON DELETE CASCADE,
    CONSTRAINT uq_agent_registration_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_agent_registration_tokens_infrastructure_id
    ON agent_registration_tokens (infrastructure_id);

CREATE TABLE IF NOT EXISTS agents (
    id CHAR(36) PRIMARY KEY,
    infrastructure_id CHAR(36) NOT NULL,
    identity_token_hash VARCHAR(255) NOT NULL,
    version VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    registered_at TIMESTAMP NULL,
    last_seen_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_agents_infrastructure
        FOREIGN KEY (infrastructure_id) REFERENCES infrastructures (id) ON DELETE CASCADE,
    CONSTRAINT uq_agents_infrastructure UNIQUE (infrastructure_id),
    CONSTRAINT uq_agents_identity_token_hash UNIQUE (identity_token_hash)
);

CREATE INDEX idx_agents_status_last_seen ON agents (status, last_seen_at);
