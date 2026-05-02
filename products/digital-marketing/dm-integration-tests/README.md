# dm-integration-tests

**Package:** `com.ghatana.digitalmarketing.integration`

DMOS Integration Tests. Validates cross-module behavior across `dm-domain`, `dm-application`, `dm-kernel-bridge`, and `dm-api`.

## Test Scenarios

### `CampaignLifecycleIT`

| Scenario | Coverage |
|----------|----------|
| Full lifecycle: create → launch → pause → resume → get | Happy path, state machine |
| Authorization denial on create | Security enforcement |
| Authorization denial on launch | Security enforcement |
| Compliance preflight failure on launch | Compliance enforcement |
| Audit events recorded for create and launch | Observability |
| Campaign workspace isolation | Tenant/workspace scoping |

## Design

Integration tests use:
- **In-memory `CampaignRepository`** — validates real persistence scoping without a database
- **Mock `DigitalMarketingKernelAdapter`** — validates kernel interactions at real service call sites
- **Mock `CompliancePlugin`** — validates compliance evaluation is correctly invoked
- **`EventloopTestBase`** — validates ActiveJ async flows complete correctly

## Dependencies

All `dm-*` modules and platform testing utilities.
