# Week 3 Plan: Token-2022 Transfer Hooks & Permanent Delegate

## Objectives
1. **Token-2022 Integration:** Migrate asset issuance from the legacy token program to the Solana Token-2022 Program ID (`TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb`).
2. **Transfer Hook CPI Validation:** Implement program-derived transfer hook logic to enforce real-time compliance screening on secondary token transfers.
3. **Permanent Delegate Control:** Configure a mint-level permanent delegate account for enterprise-grade regulatory oversight, asset recovery, and compliance freezes.

## Execution Steps
- **Step 1:** Update `SolanaMintService.java` and associated instruction builders to target the Token-2022 program and initialize mint-level extensions.
- **Step 2:** Construct transfer hook PDA resolution and ensure the transaction builder appends required extra account metas.
- **Step 3:** Wire pre-broadcast compliance SPI evaluation output directly into transfer hook requirements.
- **Step 4:** Expand the test suite (`Token2022MintExtensionTest.java`, `TransferHookIT.java`) and verify via `./mvnw clean verify`.