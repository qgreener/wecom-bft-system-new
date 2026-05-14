# Database

Flyway is the authoritative migration runner for S1.

| Path | Purpose |
|---|---|
| `backend/src/main/resources/db/migration/` | Runtime Flyway migration location |
| `database/migration/` | Migration mirror for review and deployment handoff |
| `database/seed/` | Local/demo seed scripts in later stages |

S1 only creates an infrastructure metadata baseline. Business tables for orders,
payments, refunds, fulfillment, invoices, reconciliation, and auditing are out
of this phase.
