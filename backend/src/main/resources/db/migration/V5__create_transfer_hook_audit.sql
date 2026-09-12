-- ============================================================================
-- V5__create_transfer_hook_audit.sql
-- Immutable audit ledger for transfer-hook compliance evaluations.
--
-- Every CLEARED and BLOCKED transfer-hook decision is appended here before the
-- Token-2022 transfer hook execution path returns its TransferComplianceResult,
-- satisfying the enterprise requirement that every compliance outcome is
-- durably recorded off-chain.
--
-- transaction_signature is NULLABLE: a BLOCKED evaluation never broadcasts, so
-- there is no on-chain signature to record for the fail-closed path.
-- ============================================================================

CREATE TABLE transfer_hook_audit_logs (
    id                    uuid                     NOT NULL,
    transaction_signature varchar(88),
    mint_address          varchar(44)              NOT NULL,
    source_wallet         varchar(44)              NOT NULL,
    destination_wallet    varchar(44)              NOT NULL,
    amount                bigint                   NOT NULL,
    compliance_status     varchar(16)              NOT NULL,
    reason_code           varchar(255),
    created_at            timestamp with time zone NOT NULL,
    CONSTRAINT pk_transfer_hook_audit_logs PRIMARY KEY (id)
);

CREATE INDEX idx_transfer_hook_audit_logs_mint_address
    ON transfer_hook_audit_logs (mint_address);

CREATE INDEX idx_transfer_hook_audit_logs_source_wallet
    ON transfer_hook_audit_logs (source_wallet);

CREATE INDEX idx_transfer_hook_audit_logs_destination_wallet
    ON transfer_hook_audit_logs (destination_wallet);

CREATE INDEX idx_transfer_hook_audit_logs_created_at
    ON transfer_hook_audit_logs (created_at);
