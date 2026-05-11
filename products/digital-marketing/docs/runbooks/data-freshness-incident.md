# Data Freshness Incident Runbook

## Trigger

- Dashboard, report, funnel, ROI/ROAS, or attribution data is stale, partial, unavailable, or inconsistent with export values.
- Source freshness breaches SLA or confidence drops below production threshold.

## Immediate Containment

1. Mark affected dashboard/report widgets as stale, partial, or unavailable.
2. Disable exports if values cannot be tied to the same source/formula version.
3. Preserve source, formula version, freshness timestamp, confidence, filters, and authorization scope.
4. Notify stakeholders that metrics are degraded.

## Investigation

1. Identify the failed source domain: connector event ingestion, analytics persistence, formula runtime, or authorization.
2. Compare dashboard, report, and export output for the same filters.
3. Check connector health, ingestion lag, and database query errors.
4. Review recent formula-version or schema changes.

## Recovery

1. Restore ingestion or rerun the affected analytics job.
2. Recompute derived summaries with the correct formula version.
3. Re-enable exports only after parity checks pass.
4. Backfill missing events when source lineage is available.

## Verification

- Freshness status returns to `FRESH` or an explicit partial/stale state.
- Dashboard, report, and export values match for identical filters.
- Audit/telemetry records source, formula version, freshness, and confidence.
- No production UI displays derived values computed client-side.
