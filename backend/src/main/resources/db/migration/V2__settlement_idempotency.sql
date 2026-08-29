-- ============================================================================
-- V2__settlement_idempotency.sql
-- Settlement idempotency keys + settlement metadata.
--
-- 1. asset_tokens.idempotency_key is unique so a tokenization request is never
--    broadcast twice and every on-chain mint has a persisted (pre-dispatch)
--    off-chain registry row (no orphan mints).
-- 2. audit_logs gains the immutable settlement-proof columns consumed by the
--    compliance export engine (asset id, KYC/OFAC flags, execution status,
--    compute budget, transaction signature, slot, and blockhash). The unique
--    idempotency_key guards against duplicate RPC broadcasts on retries.
-- ============================================================================

ALTER TABLE asset_tokens ADD COLUMN idempotency_key varchar(255);
ALTER TABLE asset_tokens ADD COLUMN settlement_status varchar(32);

CREATE UNIQUE INDEX uk_asset_tokens_idempotency_key ON asset_tokens (idempotency_key);

ALTER TABLE audit_logs ADD COLUMN idempotency_key varchar(255);
ALTER TABLE audit_logs ADD COLUMN asset_id varchar(255);
ALTER TABLE audit_logs ADD COLUMN kyc_verified boolean;
ALTER TABLE audit_logs ADD COLUMN ofac_passed boolean;
ALTER TABLE audit_logs ADD COLUMN settlement_status varchar(32);
ALTER TABLE audit_logs ADD COLUMN compute_unit_price_micro_lamports bigint;
ALTER TABLE audit_logs ADD COLUMN compute_unit_limit integer;
ALTER TABLE audit_logs ADD COLUMN solana_transaction_signature varchar(88);
ALTER TABLE audit_logs ADD COLUMN slot bigint;
ALTER TABLE audit_logs ADD COLUMN blockhash varchar(88);

CREATE UNIQUE INDEX uk_audit_logs_idempotency_key ON audit_logs (idempotency_key);
