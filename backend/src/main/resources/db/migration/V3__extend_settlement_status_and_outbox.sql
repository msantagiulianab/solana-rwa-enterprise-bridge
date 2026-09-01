-- ============================================================================
-- V3__extend_settlement_status_and_outbox.sql
-- Finality Confirmation Outbox — durable, resumable polling table for advancing
-- on-chain settlement records from CONFIRMED to FINALIZED (or FAILED / EXPIRED).
--
-- 1. SettlementStatus is extended (additive, EnumType.STRING) with FINALIZED and
--    EXPIRED in the Java enum. Both asset_tokens.settlement_status and
--    finality_outbox.status are varchar-backed @Enumerated(EnumType.STRING)
--    columns with no CHECK constraint, so adding enum values requires no ALTER
--    of existing columns and no rewrite of applied history.
-- 2. The durable outbox lives in its own table so polling bookkeeping never
--    mutates the immutable audit_logs ledger or the asset_tokens source of
--    truth. Each row references the source asset and the broadcast transaction
--    signature whose finality must be confirmed on-chain.
-- ============================================================================

CREATE TABLE finality_outbox (
    id                           uuid                     NOT NULL,
    asset_token_id               uuid,
    idempotency_key              varchar(255),
    solana_transaction_signature varchar(88),
    status                       varchar(32)              NOT NULL,
    commitment_level             varchar(32),
    poll_attempts                integer                  NOT NULL DEFAULT 0,
    max_poll_attempts            integer                  NOT NULL DEFAULT 30,
    last_polled_at               timestamp with time zone,
    next_poll_at                 timestamp with time zone,
    error_message                text,
    settled_at                   timestamp with time zone,
    created_at                   timestamp with time zone NOT NULL,
    updated_at                   timestamp with time zone NOT NULL,
    CONSTRAINT pk_finality_outbox PRIMARY KEY (id),
    CONSTRAINT uk_finality_outbox_idempotency_key UNIQUE (idempotency_key)
);

-- Performance index for the poller's hot path:
--   SELECT ... WHERE status = 'CONFIRMED' AND commitment_level <> 'FINALIZED'
--                 AND next_poll_at <= NOW() ORDER BY next_poll_at;
CREATE INDEX idx_settlements_outbox_polling
    ON finality_outbox (status, commitment_level, next_poll_at);

CREATE INDEX idx_finality_outbox_next_poll_at
    ON finality_outbox (next_poll_at);
