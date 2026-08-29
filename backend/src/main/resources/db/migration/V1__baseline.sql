-- ============================================================================
-- V1__baseline.sql
-- Baseline schema for the Solana RWA Enterprise Bridge.
--
-- Mirrors the JPA entities Investor, AssetToken, and AuditLog. This is the
-- canonical, versioned source of truth for the PostgreSQL schema; production
-- runs with hibernate.ddl-auto: validate so any entity/schema drift is rejected
-- at startup rather than silently auto-migrated.
-- ============================================================================

CREATE TABLE investors (
    id              uuid                     NOT NULL,
    full_name       varchar(255)             NOT NULL,
    email           varchar(255)             NOT NULL,
    wallet_address  varchar(44)              NOT NULL,
    kyc_status      varchar(32)              NOT NULL,
    country         varchar(2),
    created_at      timestamp with time zone NOT NULL,
    updated_at      timestamp with time zone NOT NULL,
    CONSTRAINT pk_investors PRIMARY KEY (id),
    CONSTRAINT uk_investors_wallet_address UNIQUE (wallet_address)
);

CREATE INDEX idx_investors_wallet_address ON investors (wallet_address);

CREATE TABLE asset_tokens (
    id                uuid                     NOT NULL,
    asset_name        varchar(255)             NOT NULL,
    valuation_usd     numeric(20, 2)           NOT NULL,
    mint_address      varchar(44),
    compliance_status varchar(32)              NOT NULL,
    created_at        timestamp with time zone NOT NULL,
    updated_at        timestamp with time zone NOT NULL,
    CONSTRAINT pk_asset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_asset_tokens_mint_address UNIQUE (mint_address)
);

CREATE INDEX idx_asset_tokens_mint_address ON asset_tokens (mint_address);

CREATE TABLE audit_logs (
    id             uuid                     NOT NULL,
    wallet_address varchar(44)              NOT NULL,
    action         varchar(64)              NOT NULL,
    status         varchar(16)              NOT NULL,
    reason         text,
    timestamp      timestamp with time zone NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX idx_audit_logs_wallet_address ON audit_logs (wallet_address);
