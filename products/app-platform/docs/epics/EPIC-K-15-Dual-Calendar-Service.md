EPIC-ID: EPIC-K-15
EPIC NAME: Multi-Calendar Service
LAYER: KERNEL
MODULE: K-15 Multi-Calendar Service
VERSION: 1.2.0

> **Revision 1.2.0 (2026-03-12)**: Renamed from "Dual-Calendar Service (Bikram Sambat & Gregorian)" to "Multi-Calendar Service". Replaced `DualDate` with `CalendarDate` (open, pluggable design). Nepal/BS specifics moved to Capital Markets (Siddhanta) T1 config pack.

---

#### Section 1 — Objective

Establish the K-15 Multi-Calendar Service as a first-class Platform Kernel capability, ensuring that any number of calendar systems can be natively supported at the data layer through T1 config packs. This epic satisfies Principle 13 (Multi-Calendar at the Data Layer) and Principle 1 (Zero Hardcoding of Jurisdiction Logic) by abstracting all date conversion, validation, and scheduling logic out of domain modules. It provides a generic Core service extensible to any calendar system.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Multi-Calendar Service API and SDK (`CalendarClient`) for date conversion between any two registered calendars.
  2. Storage abstractions for multi-calendar dates (`CalendarDate` type).
  3. Holiday and trading day calculation logic based on injected Calendar Packs (T1).
  4. Generation of `CalendarMismatchEvent` on conversion divergence.
- **Out-of-Scope:**
  1. Jurisdiction-specific holiday dates (provided by T1 Config Packs, not hardcoded here).
  2. Domain-specific business logic (e.g., settlement offset days), which is delegated to Rules/Config engines using this service.
  3. Any hard-coded calendar pair (e.g., BS ⇔ Gregorian) — all pairs are registered via T1.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework)
- **Kernel Readiness Gates:** N/A (This is a Kernel Epic)
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Core Conversion:** The service MUST convert dates between any two registered calendars using T1-supplied mapping tables, without hardcoded calendar offsets.
2. **FR2 CalendarDate Storage Abstractions:** The service MUST provide a `CalendarDate` type enforcing the storage of the canonical UTC instant plus all additional calendars registered by active T1 Calendar Packs.
3. **FR3 Event-Sourced Behavior:** Changes to calendar configuration packs MUST emit a `CalendarConfigUpdated` event.
4. **FR4 CQRS Structure:** Write model updates mapping data; read model serves high-throughput date conversion queries.
5. **FR5 Config Integration:** Load active calendar mapping and holiday definitions dynamically via K-02 Config Engine.
6. **FR6 Plugin Hooks:** Support any calendar system via T1 Calendar Pack extensions.
7. **FR7 Maker-Checker Applicability:** Any manual override to a calendar mapping table requires maker-checker approval.
8. **FR8 Jurisdiction-Aware Behavior:** Jurisdiction-specific holiday and settlement inclusion/exclusion days are loaded from Jurisdiction T1 Config Packs.
9. **FR9 Ledger Impact:** None directly; domain packs (e.g., K-16 Ledger in Capital Markets) rely on this for posting dates.
10. **FR10 Edge Case & Leap Year Handling:** The conversion engine MUST correctly handle: (a) leap years per calendar system (each defined in its T1 pack), (b) month/year boundary transitions across different calendar systems, (c) dates falling on different calendar years simultaneously. A comprehensive test suite MUST be maintained per T1 calendar pack.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The conversion engine, API contracts, and dual-date data structures are completely jurisdiction-agnostic.
2. **Jurisdiction Plugin:** The actual BS-Gregorian mapping tables, Nepal public holidays (Dashain, Tihar, etc.), and NEPSE trading calendar are T1 Config Packs.
3. **Resolution Flow:** Config Engine resolves the active Calendar Pack based on jurisdiction context ("NP").
4. **Hot Reload:** Calendar updates (e.g., ad-hoc government holidays) are hot-reloaded via K-02.
5. **Backward Compatibility:** Past date mappings remain immutable to ensure historical audit trails are preserved.
6. **Future Jurisdiction:** A new calendar (e.g., Hijri, Saka) can be added simply by defining a new T1 Config Pack.

---

#### Section 5 — Data Model Impact

- **CalendarDate Type** (replaces the old `DualDate`):
  ```typescript
  interface CalendarDate {
    primary: string; // ISO 8601 UTC canonical instant
    timezone: string; // IANA timezone string
    calendars: Record<CalendarId, CalendarDateTime>; // populated by K-15 from active T1 packs
    fiscalYear?: Record<CalendarId, string>; // optional, per-calendar fiscal year
  }
  ```
- **New Entities:**
  - `CalendarMapping`: `{ calendar_id: String, year: Int, month: Int, days_in_month: Int, start_primary_date: Date }`
- **Multi-Calendar Fields:** All platform entities requiring multi-calendar storage MUST use `CalendarDate` instead of plain date fields.
- **Event Schema Changes:** `CalendarMismatchEvent`

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                        |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `CalendarMismatchEvent`                                                                                                            |
| Schema Version    | `v2.0.0`                                                                                                                           |
| Trigger Condition | When a domain module submits a `CalendarDate` with local calendar fields that violate the current conversion mapping.              |
| Payload           | `{ "primary_date": "2025-10-22T00:00:00Z", "calendar_id": "bs", "submitted_local": "2082-07-06", "expected_local": "2082-07-05" }` |
| Consumers         | Audit Framework, Compliance Module, Operator Dashboard                                                                             |
| Idempotency Key   | `hash(primary_date + calendar_id + submitted_local + timestamp)`                                                                   |
| Replay Behavior   | Side-effects suppressed on replay; logged for observability.                                                                       |
| Retention Policy  | 10 years (jurisdiction config)                                                                                                     |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `UpdateCalendarMappingCommand`                                       |
| Schema Version   | `v2.0.0`                                                             |
| Validation Rules | Mapping data valid, no conflicts, maker-checker approval obtained    |
| Handler          | `CalendarCommandHandler` in K-15 Multi-Calendar Service              |
| Success Event    | `CalendarMappingUpdated`                                             |
| Failure Event    | `CalendarMappingUpdateFailed`                                        |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `AddHolidayCommand`                                                  |
| Schema Version   | `v2.0.0`                                                             |
| Validation Rules | Date valid, jurisdiction specified, requester authorized             |
| Handler          | `HolidayCommandHandler` in K-15 Multi-Calendar Service               |
| Success Event    | `HolidayAdded`                                                       |
| Failure Event    | `HolidayAddFailed`                                                   |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                  |
| ---------------- | ------------------------------------------------------------ |
| Command Name     | `ConvertDateCommand`                                         |
| Schema Version   | `v2.0.0`                                                     |
| Validation Rules | Source date valid, source and target calendar IDs registered |
| Handler          | `DateConversionHandler` in K-15 Multi-Calendar Service       |
| Success Event    | `DateConverted`                                              |
| Failure Event    | `DateConversionFailed`                                       |
| Idempotency      | Same input returns cached conversion result                  |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Calendar Mapping updates.
- **Model Registry Usage:** `calendar-anomaly-detector-v1`
- **Explainability Requirement:** AI flags anomalous holiday declarations or mapping shifts; explains variance from historical patterns. Stored in K-07 Audit.
- **Human Override Path:** Operator can approve the mapping despite AI warning.
- **Drift Monitoring:** Deviation in mapping predictions > 0.01% triggers alert.
- **Fallback Behavior:** Standard static mapping tables used if AI is offline.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                             |
| ------------------------- | ---------------------------------------------------------------------------- |
| Latency / Throughput      | P99 < 2ms for date conversion; 10,000 TPS                                    |
| Scalability               | Stateless service; scales horizontally based on CPU > 70%                    |
| Availability              | 99.999% uptime                                                               |
| Consistency Model         | Strong consistency for mapping updates                                       |
| Security                  | mTLS for service-to-service communication                                    |
| Data Residency            | Cacheable globally; mappings are public reference data                       |
| Data Retention            | Retain mapping history indefinitely                                          |
| Auditability              | All mapping table changes logged via K-07                                    |
| Observability             | Metrics: `conversion_latency`, `mismatch_count`, dimensions: `calendar_type` |
| Extensibility             | New calendar added via T1 Config Pack < 1 day                                |
| Upgrade / Compatibility   | Full backward compatibility for historical dates                             |
| On-Prem Constraints       | Mapping data included in offline deployment bundles                          |
| Ledger Integrity          | N/A                                                                          |
| Dual-Calendar Correctness | 0 mismatches per 1M conversions                                              |

---

#### Section 10 — Acceptance Criteria

1. **Given** a valid Gregorian date, **When** passed to `toBS()`, **Then** the correct Bikram Sambat date is returned under 2ms.
2. **Given** an invalid BS/Gregorian pair, **When** validated by the service, **Then** a `CalendarMismatchEvent` is emitted.
3. **Given** a new T1 Calendar Config Pack is deployed, **When** a holiday is checked for the newly configured date, **Then** it reflects as a holiday without restarting the service.
4. **Given** a request to modify historical calendar mapping, **When** submitted by a single operator, **Then** it is rejected pending maker-checker approval.
5. **Given** tenant A and tenant B with different jurisdiction configs, **When** both query the next trading day, **Then** they receive correct tenant-isolated results.
6. **Given** BS date 2081-12-30 (last day of Chaitra), **When** `nextBusinessDay()` is called, **Then** it correctly transitions to 2082-01-01 (Baishakh 1) accounting for any holidays.
7. **Given** Gregorian date 2024-02-29 (leap year), **When** converted to BS, **Then** the correct BS date is returned without error.
8. **Given** BS date 2080-01-01, **When** verified against the mapping table, **Then** the correct variable month lengths for BS year 2080 are used (not Gregorian month lengths).

---

#### Section 11 — Failure Modes & Resilience

- **Config Engine Unreachable:** Service falls back to locally cached mapping tables; raises alert.
- **Calendar Mismatch:** `CalendarMismatchEvent` emitted; transaction flagged for manual review; operator alerted.
- **Retry/Backoff:** Idempotent read queries retry up to 3 times with exponential backoff.
- **Partition Tolerance:** Operates cleanly during network partitions using cached data.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------- |
| Metrics             | `calendar.conversion.latency`, `calendar.mismatch.count`, dimensions: `tenant_id`, `jurisdiction` |
| Logs                | Structured logs with `timestamp` (UTC ISO-8601), `operation: convert`, `duration_ms`              |
| Traces              | Span `CalendarClient.convert`                                                                     |
| Audit Events        | Action: `UpdateMapping`, `before_state`, `after_state` [LCA-AUDIT-001]                            |
| Regulatory Evidence | Audit trail of calendar mapping changes for market surveillance [ASR-RPT-001]                     |

---

#### Section 13 — Compliance & Regulatory Traceability

- Reporting accuracy — dual-calendar timestamp on all regulatory submissions `[ASR-RPT-001]`
- Record retention — minimum retention periods met `[LCA-RET-001]`
- Audit logging of configuration changes `[LCA-AUDIT-001]`

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `CalendarClient` provides `convert(date: Date, fromCalendar: CalendarId, toCalendar: CalendarId)`, `isHoliday(calendarDate: CalendarDate, jurisdiction: String)`, `nextBusinessDay(calendarDate: CalendarDate, offset: Int, jurisdiction: String)`.
- **Jurisdiction Plugin Extension Points:** T1 Config Pack schema for calendar mappings and holiday definitions.
- **Backward Compatibility:** Client API guaranteed stable for 2 major versions.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                                             |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, by loading a Saka or Gregorian T1 Config Pack.                                                                                         |
| Can a new calendar system be added without kernel changes?            | Yes, the schema is generic `calendar_type`.                                                                                                 |
| Can tax rules change without redeploy?                                | N/A                                                                                                                                         |
| Can this run in an air-gapped deployment?                             | Yes, mapping data is bundled.                                                                                                               |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Blockchain timestamps (Unix epoch) are converted to both Gregorian and Bikram Sambat for dual-calendar compliance on tokenized assets. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Sub-millisecond timestamp resolution and instant calendar conversion support T+0 settlement date computation.                          |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
