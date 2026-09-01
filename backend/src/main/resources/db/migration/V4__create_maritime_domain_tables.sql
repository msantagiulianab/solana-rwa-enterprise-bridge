-- ============================================================================
-- V4__create_maritime_domain_tables.sql
-- Maritime domain: electronic Bill of Lading (eBL), container consignments,
-- and Panama Canal transit settlements for the Delivery-vs-Payment (DvP)
-- settlement lifecycle.
--
-- 1. bills_of_lading is the root aggregate: carrier/vessel, ports, shipper and
--    consignee wallets, declared value, and the clearance decision. The
--    clearance_status column is varchar-backed (@Enumerated(EnumType.STRING))
--    with a PENDING default, mirroring the additive enum pattern in V3.
-- 2. container_consignments is a child of bills_of_lading (ON DELETE CASCADE),
--    holding per-container customs/seal/hazardous metadata.
-- 3. canal_transit_settlements is a child of bills_of_lading and carries a
--    logical reference (outbox_entry_id) to finality_outbox for asynchronous
--    finality confirmation after a cleared settlement.
--
-- This migration is additive only; V1/V2/V3 history is never rewritten.
-- ============================================================================

CREATE TABLE bills_of_lading (
    id                 uuid                     NOT NULL,
    bl_number          varchar(255)             NOT NULL,
    carrier_code       varchar(255)             NOT NULL,
    vessel_imo         varchar(255)             NOT NULL,
    port_of_loading    varchar(255)             NOT NULL,
    port_of_discharge  varchar(255)             NOT NULL,
    shipper_wallet     varchar(44)              NOT NULL,
    consignee_wallet   varchar(44)              NOT NULL,
    declared_value_usd numeric(18, 2)           NOT NULL,
    cargo_description  text                     NOT NULL,
    clearance_status   varchar(32)              NOT NULL DEFAULT 'PENDING',
    token_mint_address varchar(44),
    created_at         timestamp with time zone NOT NULL,
    updated_at         timestamp with time zone NOT NULL,
    CONSTRAINT pk_bills_of_lading PRIMARY KEY (id),
    CONSTRAINT uk_bills_of_lading_bl_number UNIQUE (bl_number)
);

CREATE INDEX idx_bills_of_lading_vessel_imo
    ON bills_of_lading (vessel_imo);

CREATE INDEX idx_bills_of_lading_bl_number
    ON bills_of_lading (bl_number);

CREATE INDEX idx_bills_of_lading_clearance_status
    ON bills_of_lading (clearance_status);

CREATE TABLE container_consignments (
    id                uuid                     NOT NULL,
    bill_of_lading_id uuid                     NOT NULL,
    container_number  varchar(255)             NOT NULL,
    seal_number       varchar(255)             NOT NULL,
    gross_weight_kg   numeric(12, 2)           NOT NULL,
    is_hazardous      boolean                  NOT NULL DEFAULT FALSE,
    customs_status    varchar(32)              NOT NULL DEFAULT 'PENDING',
    CONSTRAINT pk_container_consignments PRIMARY KEY (id),
    CONSTRAINT fk_container_consignments_bol FOREIGN KEY (bill_of_lading_id)
        REFERENCES bills_of_lading (id) ON DELETE CASCADE
);

CREATE INDEX idx_container_consignments_bol
    ON container_consignments (bill_of_lading_id);

CREATE TABLE canal_transit_settlements (
    id                        uuid                     NOT NULL,
    bill_of_lading_id         uuid                     NOT NULL,
    transit_booking_reference varchar(255)             NOT NULL,
    transit_fee_usd           numeric(18, 2)           NOT NULL,
    settlement_token_mint     varchar(44)              NOT NULL,
    escrow_account            varchar(44)              NOT NULL,
    status                    varchar(32)              NOT NULL DEFAULT 'INITIALIZED',
    transaction_signature     varchar(88),
    outbox_entry_id           uuid,
    settled_at                timestamp with time zone,
    created_at                timestamp with time zone NOT NULL,
    updated_at                timestamp with time zone NOT NULL,
    CONSTRAINT pk_canal_transit_settlements PRIMARY KEY (id),
    CONSTRAINT uk_canal_transit_settlements_booking UNIQUE (transit_booking_reference),
    CONSTRAINT fk_canal_transit_settlements_bol FOREIGN KEY (bill_of_lading_id)
        REFERENCES bills_of_lading (id) ON DELETE CASCADE,
    CONSTRAINT fk_canal_transit_settlements_outbox FOREIGN KEY (outbox_entry_id)
        REFERENCES finality_outbox (id) ON DELETE SET NULL
);

CREATE INDEX idx_canal_transit_settlements_bol
    ON canal_transit_settlements (bill_of_lading_id);
