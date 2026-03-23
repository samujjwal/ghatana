# AppPlatform Legacy Archive

> Status: archived reference only
> Live replacements: `products/finance`, `platform/java`, `products/yappc`
> Build status: not included in `settings.gradle.kts`
> Workspace status: no active pnpm workspace packages

This directory no longer represents a live product or canonical implementation path.

Use this documentation only for historical reference, migration archaeology, or traceability when older plans mention AppPlatform. Current implementation authority has moved to active product and platform modules.

Current authority order:

1. `products/finance/**` for finance-domain product code migrated out of AppPlatform
2. `platform/java/**` for canonical platform libraries and kernel contracts
3. `products/yappc/**` for the active product-platform surface that superseded historical AppPlatform product concerns
4. `products/app-platform/docs/archive/**` for historical reviews, plans, and snapshots

What remains here:

- ADRs and architecture notes useful for historical context
- Implementation plans and migration-era documents that explain earlier decisions
- Reference material that may still be cited by older audits

What no longer remains here:

- active JVM modules
- active TypeScript workspace packages
- active CI workflows dedicated to AppPlatform modules
- active local orchestration for AppPlatform runtime code

Treat all material in this folder as non-authoritative unless a newer live document explicitly points back to it for historical context.
4. **K-05 Standard Event Envelope** — Every event carries `event_type`, `aggregate_id`, `causation_id`, `trace_id`, `timestamp` (ISO 8601 UTC), a `data` payload wrapper, an optional `calendar_date` field (type `CalendarDate`), and an optional `ai_annotation` field (type `AiAnnotation`) populated by K-09 when a registered model enriches the event
5. **10-Year Regulatory Retention** — Platform default; configurable per jurisdiction via T1 Config Packs; all audit/trade/event data retained for at minimum the jurisdictional requirement
6. **99.999% Availability** — Active-active multi-AZ with automatic failover
7. **ADR-011 Stack Standardization** — One canonical stack baseline aligned to finance ADRs and reusable Ghatana platform products
8. **Metadata-Driven Process Model** — Step order, human-task forms, routing policies, and operator choice lists are governed as versioned metadata, not embedded in service or portal code

## Epic Layers

| Layer               | Count | Scope                                                                                                                                                                            |
| ------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kernel (K)          | 19    | IAM, Config, Rules, Plugin, Event Bus, Observability, Audit, Data Governance, AI Governance, Deployment, API Gateway, Platform SDK, Multi-Calendar, Ledger, DTC, Resilience, DLQ |
| Domain (D)          | 14    | OMS, EMS, PMS, Market Data, Pricing, Risk, Compliance, Surveillance, Post-Trade, Regulatory Reporting, Reference Data, Corporate Actions, Client Money, Sanctions                |
| Workflow (W)        | 2     | Orchestration, Client Onboarding                                                                                                                                                 |
| Packs (P)           | 1     | Content Pack Certification Pipeline                                                                                                                                              |
| Testing (T)         | 2     | Integration Testing, Chaos Engineering                                                                                                                                           |
| Operations (O)      | 1     | Operator Console                                                                                                                                                                 |
| Regulatory (R)      | 2     | Regulator Portal, Incident Response & Escalation                                                                                                                                 |
| Platform Unity (PU) | 1     | Platform Manifest                                                                                                                                                                |

## Version

**v2.2** — March 10, 2026 | Post-ARB Review | Hardened for consistency, extensibility, metadata-driven runtime configuration, and future-proofing.
