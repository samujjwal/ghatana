# Domain Pack Upgrade Runbook

**Document Type**: Operational Runbook  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Owner**: AppPlatform Platform Engineering  
**Canonical Path**: `products/app-platform/docs/DOMAIN_PACK_UPGRADE_RUNBOOK.md`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Pre-Upgrade Checklist](#2-pre-upgrade-checklist)
3. [Upgrade Strategy (Blue-Green)](#3-upgrade-strategy-blue-green)
4. [Step-by-Step Procedure](#4-step-by-step-procedure)
5. [Data Migration Validation](#5-data-migration-validation)
6. [Post-Upgrade Smoke Tests](#6-post-upgrade-smoke-tests)
7. [Rollback Procedure](#7-rollback-procedure)
8. [Lifecycle Hook Reference](#8-lifecycle-hook-reference)
9. [Kernel Compatibility Verification](#9-kernel-compatibility-verification)
10. [Escalation](#10-escalation)

---

## 1. Overview

This runbook governs the process of upgrading a certified domain pack from version `N` to version `N+x` in a live AppPlatform environment. It covers zero-downtime blue-green upgrades, data migration validation, and rollback.

**Applies to**: All domain packs implementing the `DomainPack` interface from `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`.

### 1.1 Upgrade Classification

| Class               | Description                           | Downtime                         | Rollback                |
| ------------------- | ------------------------------------- | -------------------------------- | ----------------------- |
| **Patch** (`0.0.x`) | Bug fixes, no schema change           | Zero                             | Instant                 |
| **Minor** (`0.x.0`) | New capabilities, backward-compatible | Zero                             | Instant                 |
| **Major** (`x.0.0`) | Breaking API/schema change            | Maintenance window or blue-green | Requires data migration |

---

## 2. Pre-Upgrade Checklist

Complete **all** items before initiating upgrade. Items marked ⛔ are blocking gates.

### 2.1 Compatibility

- [ ] ⛔ `KernelModuleConstraint` semver ranges in new manifest satisfied by installed kernel version
- [ ] ⛔ No deprecation warnings from `requiredKernels` validation endpoint
- [ ] Cross-pack `crossPackDependencies` verified against installed pack registry

### 2.2 Build Artifacts

- [ ] ⛔ Pack JAR/image signed by AppPlatform Certification Authority (cert not older than 90 days)
- [ ] ⛔ All automated certification gates passed (coverage ≥90%, zero critical CVEs, performance benchmarks met)
- [ ] SHA-256 digest of artifact matches `PackCertification.artifactDigest`

### 2.3 Environment

- [ ] ⛔ Successful upgrade rehearsal completed in staging/UAT environment
- [ ] Backup of tenant-scoped pack database schema and data taken
- [ ] EventBus consumer lag ≤ 100 messages before cutover
- [ ] Canary rollout (≥5% traffic) held for ≥30 minutes with no elevated error rate

### 2.4 Stakeholders

- [ ] Change Advisory Board (CAB) approval for Major upgrades
- [ ] Tenant administrator notified (at least 48h for Minor; 7 days for Major)
- [ ] On-call engineer paged and available for duration of upgrade window

---

## 3. Upgrade Strategy (Blue-Green)

The AppPlatform Kernel supports **blue-green domain pack activation** per tenant. This enables zero-downtime upgrades for Minor and patch versions.

```
Tenant Traffic (100%)
        │
        ▼
┌───────────────┐
│  K-01 Router  │
└───┬───────────┘
    │ Active: BLUE (v1.2.3)
    │
    ├──▶ [BLUE] Pack v1.2.3  ←── current live
    │
    └──▶ [GREEN] Pack v1.3.0 ←── being prepared
```

### 3.1 Traffic Shift Schedule (Minor/Patch)

| Phase   | Traffic to GREEN | Dwell Time | Abort Condition                       |
| ------- | ---------------- | ---------- | ------------------------------------- |
| Canary  | 5%               | 30 min     | Error rate > 0.1% OR p99 latency +50% |
| Partial | 25%              | 15 min     | Same                                  |
| Half    | 50%              | 10 min     | Same                                  |
| Full    | 100%             | —          | Cutover complete                      |

### 3.2 Major Version Strategy

Major upgrades require a **maintenance window** with explicit data migration. Blue-green is used for infrastructure, but the data migration step is blocking:

```
1. Maintenance window starts
2. Traffic paused (or read-only mode enabled)
3. Data migration runs (see §5)
4. GREEN pack activated at 100%
5. Maintenance window ends
```

---

## 4. Step-by-Step Procedure

### Step 1 — Stage the GREEN Instance

```bash
# Platform CLI
appplatform pack stage \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --version ${NEW_VERSION} \
  --artifact ${ARTIFACT_PATH}

# Verify staging health
appplatform pack status --tenant-id ${TENANT_ID} --pack-id ${PACK_ID} --slot green
```

Expected output:

```
GREEN slot: STAGED | Health: HEALTHY | Migrations: PENDING
```

### Step 2 — Run Pre-Upgrade Hook

The kernel calls `LifecycleHooks.onUpgrade` on the GREEN pack before traffic shift. This hook MUST be idempotent and MUST NOT mutate live data.

```java
// Pack implements:
@Override
public Promise<UpgradeResult> onUpgrade(UpgradeContext ctx) {
    return Promise.ofBlocking(executor, () -> {
        validateSchemaCompatibility(ctx.getPreviousVersion());
        runMigrationDryRun(ctx);
        return UpgradeResult.PROCEED;
    });
}
```

If `onUpgrade` returns `UpgradeResult.ABORT`, the upgrade halts and BLUE remains active.

### Step 3 — Run Data Migrations (Major only)

```bash
appplatform pack migrate \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --from-version ${OLD_VERSION} \
  --to-version ${NEW_VERSION} \
  --dry-run   # validate first

# If dry-run passes:
appplatform pack migrate \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --from-version ${OLD_VERSION} \
  --to-version ${NEW_VERSION}
```

See §5 for validation requirements.

### Step 4 — Canary Traffic Shift

```bash
appplatform pack promote \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --to-slot green \
  --traffic-percent 5
```

Monitor for 30 minutes. Use the K-17 dashboard: `appplatform/dashboards/pack-upgrade`.

### Step 5 — Progressive Promotion

Repeat `promote` calls for 25%, 50%, 100% (see §3.1). At each step, run the smoke-test suite (§6).

### Step 6 — Retire BLUE Slot

```bash
appplatform pack retire \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --slot blue
```

BLUE slot is held for 24h in `RETIRED` state to accelerate rollback if needed.

---

## 5. Data Migration Validation

### 5.1 Row Count Invariant

After migration, validate that no data was lost:

```sql
-- Run in BLUE database BEFORE migration
SELECT COUNT(*) AS before_count FROM {pack_schema}.{migrated_table};

-- Run in GREEN database AFTER migration
SELECT COUNT(*) AS after_count FROM {pack_schema}.{migrated_table};

-- Assert: after_count >= before_count (deletions only allowed if explicitly documented)
```

### 5.2 Schema Diff Validation

```bash
appplatform pack schema-diff \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --from-version ${OLD_VERSION} \
  --to-version ${NEW_VERSION}
```

Expected output must enumerate only the expected column additions/removals declared in the pack's `CHANGELOG.md`.

### 5.3 Event Log Consistency

The AppPlatform EventLog for the pack MUST remain append-only. After migration:

```bash
appplatform pack verify-event-log \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --since-version ${OLD_VERSION}
```

Any re-written events are a critical error requiring immediate escalation (see §10).

### 5.4 Business Rule Validation

Run the pack's declarative business rule golden-dataset test suite against the migrated data:

```bash
appplatform pack test \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --suite golden-dataset
```

All assertions must pass. Failed assertions block promotion.

---

## 6. Post-Upgrade Smoke Tests

Run after each traffic shift increment and again at 100%.

### 6.1 Automated Suite

```bash
appplatform pack smoke-test \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --version ${NEW_VERSION}
```

The smoke-test suite exercises:

| Test              | Assertion                                                                 |
| ----------------- | ------------------------------------------------------------------------- |
| Manifest load     | `DomainManifest` parseable, all required fields present                   |
| Kernel constraint | All `KernelModuleConstraint` ranges satisfied                             |
| Health endpoint   | `GET /api/v1/packs/{pack_id}/health` returns `200 HEALTHY`                |
| Core capability   | At least one `CoreDomainCapability` operable (end-to-end)                 |
| Event publish     | Pack publishes a synthetic test event to K-05; delivery confirmed         |
| Event subscribe   | Pack receives a K-05 synthetic routed event; handler invoked              |
| K-15 integration  | If `CALENDAR_AWARE` capability declared: `CalendarDate` round-trip passes |
| Feature flags     | `FeatureFlagDefinition` values match expected defaults                    |

### 6.2 Manual Validation (Major Only)

For Major version upgrades, a designated QA lead performs:

- [ ] End-to-end business workflow (pack-specific — referenced in pack's own runbook)
- [ ] Cross-pack event flow involving any `crossPackDependencies`
- [ ] Rollback rehearsal (execute §7 on a non-production tenant, then re-promote)

---

## 7. Rollback Procedure

### 7.1 Instant Rollback (Patch / Minor)

If smoke tests fail or error rate exceeds threshold at any traffic increment:

```bash
appplatform pack rollback \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --to-slot blue
```

Traffic shifts to BLUE instantly. GREEN slot enters `FAILED` state. BLUE slot is NOT retired until root cause is identified.

**RTO**: < 60 seconds.

### 7.2 Major Rollback (Post-Migration)

Major rollback requires reversing the data migration:

```bash
# 1. Shift traffic back to BLUE slot
appplatform pack rollback \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --to-slot blue

# 2. Run reverse migration (if defined)
appplatform pack migrate \
  --tenant-id ${TENANT_ID} \
  --pack-id ${PACK_ID} \
  --from-version ${NEW_VERSION} \
  --to-version ${OLD_VERSION}   # reverse direction

# 3. Validate (repeat §5 checks against old schema)
```

> ⚠️ **Warning**: If the new version published events to K-05 before rollback, those events are immutable and already persisted. Downstream consumers may have processed them. Document any resulting inconsistencies in the incident report.

**RTO**: < 30 minutes (if reverse migration defined); contact on-call Platform Engineering if not.

### 7.3 Rollback Decision Tree

```
Error rate spike?
    YES → Instant rollback (§7.1)
    NO → Continue monitoring

Smoke tests failing?
    YES → Instant rollback (§7.1)
    NO → Continue

Data migration failed?
    YES → Major rollback (§7.2)
    NO → Continue

On-call escalated beyond 15 min?
    YES → Major rollback (§7.2)
    NO → Continue
```

---

## 8. Lifecycle Hook Reference

The Platform Kernel calls the following hooks at defined points. Pack authors implement these via `LifecycleHooks`:

| Hook           | When Called                                    | Blocking?            | Timeout |
| -------------- | ---------------------------------------------- | -------------------- | ------- |
| `onInstall`    | First-time installation                        | Yes                  | 5 min   |
| `onUpgrade`    | Before traffic shift to new version            | Yes                  | 5 min   |
| `onActivate`   | After 100% traffic shift — new version is live | No (fire-and-forget) | —       |
| `onDeactivate` | Before BLUE slot retirement                    | Yes                  | 2 min   |
| `onUninstall`  | Pack removed from tenant                       | Yes                  | 10 min  |

If a blocking hook exceeds its timeout, the upgrade step is aborted automatically.

---

## 9. Kernel Compatibility Verification

Before staging, verify kernel compatibility programmatically:

```bash
appplatform kernel check-compatibility \
  --pack-manifest ${MANIFEST_PATH} \
  --kernel-version $(appplatform kernel version)
```

Internally this validates each `KernelModuleConstraint`:

```typescript
// From DomainManifest
requiredKernels: [
  { module: "K-05", version: ">=3.0.0 <4.0.0", optional: false },
  { module: "K-15", version: ">=2.0.0", optional: true },
];
```

If any non-optional constraint is unsatisfied, upgrade is **blocked** until the Kernel is upgraded first. Kernel upgrades follow a separate runbook (`KERNEL_UPGRADE_RUNBOOK.md` — forthcoming).

---

## 10. Escalation

| Situation                         | Action                                                     |
| --------------------------------- | ---------------------------------------------------------- |
| Hook timeout                      | Page on-call Platform Engineering immediately              |
| Event log re-write detected       | Halt upgrade; declare P0 incident                          |
| Data count mismatch > 0.01%       | Halt upgrade; page DBA on-call                             |
| Rollback fails                    | Declare P0 incident; engage Vendor Support if kernel issue |
| CVE discovered post-certification | Emergency patch process; contact `security@appplatform.io` |

**Incident Command**: All upgrade P0 incidents use the `#platform-upgrade-war-room` Slack channel and follow the standard Incident Response Runbook.

---

_Maintained by AppPlatform Platform Engineering. Review cycle: every 6 months or after any P0 upgrade incident._
