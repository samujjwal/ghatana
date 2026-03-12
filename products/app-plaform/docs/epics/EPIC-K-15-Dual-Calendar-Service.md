EPIC-ID: EPIC-K-15
EPIC NAME: Dual-Calendar Service (Bikram Sambat & Gregorian)
LAYER: KERNEL
MODULE: K-15 Dual-Calendar Service
VERSION: 1.1.1

---

#### Section 1 — Objective

Establish the K-15 Dual-Calendar Service as a first-class Platform Kernel capability, ensuring that Bikram Sambat (BS) and Gregorian calendars are natively supported at the data layer across the entire platform. This epic satisfies Principle 13 (Dual-Calendar at the Data Layer) and Principle 1 (Zero Hardcoding of Jurisdiction Logic) by abstracting all date conversion, validation, and scheduling logic out of domain modules. It provides a generic Core service that can be extended to support other calendars via T1 Config Packs.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Dual-Calendar Service API and SDK (`CalendarClient`) for date conversion (BS ↔ Gregorian).
  2. Storage abstractions for dual-calendar dates.
  3. Holiday and trading day calculation logic based on injected Calendar Packs (T1).
  4. Generation of `CalendarMismatchEvent` on conversion divergence.
- **Out-of-Scope:**
  1. Jurisdiction-specific holiday dates (provided by T1 Config Packs, not hardcoded here).
  2. Domain-specific business logic (e.g., settlement offset days), which is delegated to Rules/Config engines using this service.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework)
- **Kernel Readiness Gates:** N/A (This is a Kernel Epic)
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Core Conversion:** The service must convert valid Gregorian dates to BS and vice versa using configured mapping tables, without hardcoded offsets.
2. **FR2 Dual Storage Enforcement:** The service must provide types/structs enforcing the storage of both `date_gregorian` and `date_bs` for any date-bearing entity.
3. **FR3 Event-Sourced Behavior:** Changes to underlying calendar configuration packs must emit a `CalendarConfigUpdated` event.
4. **FR4 CQRS Structure:** Write model updates mapping data; read model serves high-throughput date conversion queries.
5. **FR5 Config Integration:** Load active calendar mapping and holiday definitions dynamically via K-02 Config Engine.
6. **FR6 Plugin Hooks:** Support new calendar systems via T1 Calendar Pack extensions.
7. **FR7 Maker-Checker Applicability:** Any manual override to the calendar mapping table requires maker-checker approval.
8. **FR8 Jurisdiction-Aware Behavior:** Jurisdiction-specific holiday and settlement inclusion/exclusion days are loaded from Jurisdiction T1 Config Packs.
9. **FR9 Ledger Impact:** None directly, though ledger relies on this for posting dates.
10. **FR10 Edge Case & Leap Year Handling:** The conversion engine must correctly handle: (a) BS leap years (variable month lengths in Bikram Sambat calendar), (b) Gregorian leap years (Feb 29), (c) month boundary transitions (e.g., BS Chaitra to Baishakh, Gregorian Dec to Jan), (d) year boundary transitions (BS year-end differs from Gregorian), (e) dates falling on different years across calendars, (f) negative timezone offset edge cases. A comprehensive test suite with > 500 edge case assertions must be maintained, covering all known BS-Gregorian conversion edge cases from 2000 BS to 2100 BS. [ARB P2-18]

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

- **New Entities:**
  - `DualDate`: `{ gregorian_date: Date, local_date: String, calendar_type: String }`
  - `CalendarMapping`: `{ calendar_type: String, year: Int, month: Int, days_in_month: Int, start_gregorian_date: Date }`
- **Dual-Calendar Fields:** All platform entities must use `DualDate` instead of standard date fields.
- **Event Schema Changes:** `CalendarMismatchEvent`

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                       |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `CalendarMismatchEvent`                                                                                                           |
| Schema Version    | `v1.0.0`                                                                                                                          |
| Trigger Condition | When a domain module submits a dual date pair that violates the current conversion mapping.                                       |
| Payload           | `{ "submitted_gregorian": "2025-10-22", "submitted_local": "2082-07-06", "expected_local": "2082-07-05", "calendar_type": "BS" }` |
| Consumers         | Audit Framework, Compliance Module, Operator Dashboard                                                                            |
| Idempotency Key   | `hash(submitted_gregorian + submitted_local + timestamp)`                                                                         |
| Replay Behavior   | Side-effects suppressed on replay; logged for observability.                                                                      |
| Retention Policy  | 10 years (jurisdiction config)                                                                                                    |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `UpdateCalendarMappingCommand`                                       |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Mapping data valid, no conflicts, maker-checker approval obtained    |
| Handler          | `CalendarCommandHandler` in K-15 Dual-Calendar Service               |
| Success Event    | `CalendarMappingUpdated`                                             |
| Failure Event    | `CalendarMappingUpdateFailed`                                        |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `AddHolidayCommand`                                                  |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Date valid, jurisdiction specified, requester authorized             |
| Handler          | `HolidayCommandHandler` in K-15 Dual-Calendar Service                |
| Success Event    | `HolidayAdded`                                                       |
| Failure Event    | `HolidayAddFailed`                                                   |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                           |
| ---------------- | ----------------------------------------------------- |
| Command Name     | `ConvertDateCommand`                                  |
| Schema Version   | `v1.0.0`                                              |
| Validation Rules | Source date valid, target calendar specified          |
| Handler          | `DateConversionHandler` in K-15 Dual-Calendar Service |
| Success Event    | `DateConverted`                                       |
| Failure Event    | `DateConversionFailed`                                |
| Idempotency      | Same input returns cached conversion result           |

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
| Logs                | Structured logs with `timestamp_gregorian`, `timestamp_bs`, `operation: convert`, `duration_ms`   |
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

- **SDK Contract:** `CalendarClient` provides `toBS(Date)`, `toGregorian(String)`, `isHoliday(DualDate, jurisdiction)`, `nextBusinessDay(DualDate, offset, jurisdiction)`.
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
