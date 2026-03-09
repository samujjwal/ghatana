# ADR-010: Flyway for Database Schema Management

**Status:** Accepted  
**Date:** 2026-02-05  
**Decision Makers:** Platform Team  
**Phase:** 4 — Production Hardening  

## Context

The platform requires persistent storage for pipelines, agents, checkpoints, patterns, audit trails, and event metadata. Schema changes must be version-controlled, repeatable, and safely applied across environments (dev, staging, production).

## Decision

Use **Flyway** for all database schema management with version-prefixed SQL migrations:

**AEP migrations** (`products/aep/platform/src/main/resources/db/migration/`):

| File | Purpose |
|------|---------|
| `V001__create_pipeline_checkpoint_tables.sql` | Pipeline execution checkpoints + step checkpoints |
| `V002__create_execution_queue.sql` | Durable execution queue with priority |
| `V003__create_agent_registry.sql` | Agent registration + configuration storage |
| `V004__create_pipeline_registry.sql` | Pipeline definitions + stage configs |
| `V005__create_patterns_table.sql` | Event pattern definitions (NFA/temporal/composite) |
| `V006__create_audit_trail.sql` | Persistent audit events |

**Data-Cloud migrations** (`products/data-cloud/platform/src/main/resources/db/migration/`):

| File | Purpose |
|------|---------|
| `V001__create_events_table.sql` | Event log with JSONB payload, 4-tier storage |
| `V002__create_entities_table.sql` | Entity store with version, soft delete |
| `V003__create_timeseries_table.sql` | TimeSeries data with hypertable-ready structure |
| `V004__create_collections_metadata.sql` | Collection registry with schema + tier policy |

**Design conventions:**
- All tables include `tenant_id` for multi-tenant isolation (with index)
- All tables include `created_at` and `updated_at` timestamps
- JSONB columns for flexible data (`payload`, `config`, `state`, `metadata`)
- Check constraints enforce enums at the database level (e.g., `tier IN ('HOT', 'WARM', 'COOL', 'COLD')`)
- Composite indexes on `(tenant_id, ...)` for efficient tenant-scoped queries
- Version numbering: `V001`, `V002`, etc. (3-digit zero-padded)

## Rationale

- **Flyway** is the industry standard for Java database migration
- **SQL migrations** (not JPA/Hibernate auto-DDL) give full control over schema, indexes, and constraints
- **Version-prefixed** naming ensures deterministic ordering across environments
- **JSONB** columns balance schema flexibility with query performance (PostgreSQL)
- **Tenant-first indexing** ensures all tenant-scoped queries use indexes

## Consequences

- Version catalog declares Flyway 10.12.0 but `platform/java/database` hardcodes 10.4.1 — version alignment needed
- H2 compatibility mode (`MODE=PostgreSQL`) used for tests — some PostgreSQL features (e.g., partitioning) unavailable in tests
- Rollback migrations are not provided — forward-only migration strategy
- Each product manages its own migration directory independently

## Alternatives Considered

1. **Liquibase** — rejected; Flyway is simpler and sufficiently powerful
2. **JPA/Hibernate auto-DDL** — rejected; insufficient control for production schemas
3. **Manual SQL scripts** — rejected; no ordering guarantees, no migration tracking
