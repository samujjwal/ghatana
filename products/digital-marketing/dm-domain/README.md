# dm-domain

**Package:** `com.ghatana.digitalmarketing.domain`

DMOS Domain module. Contains all domain entities, value objects, enumerations, repository interfaces, and domain events for the Digital Marketing Operating System.

## Domain Model

### Campaign

- **`Campaign`** — Core aggregate root representing a marketing campaign. Enforces lifecycle state machine: `DRAFT → LAUNCHED ↔ PAUSED → COMPLETED → ARCHIVED`.
- **`CampaignStatus`** — Lifecycle status enum.
- **`CampaignType`** — Campaign channel type (EMAIL, SOCIAL, PAID_SEARCH, PUSH, SMS, OMNICHANNEL).

## Design Principles

- All entities are immutable. State-changing methods return new instances.
- All illegal lifecycle transitions throw `IllegalStateException` with a descriptive message.
- Entities are identified by product-scoped string IDs (tenant + workspace scope enforced at service layer).

## Dependencies

- `products:digital-marketing:dm-core-contracts` — typed value objects (`DmWorkspaceId`, etc.)
- `platform-kernel:kernel-core`
- `platform:java:core`
