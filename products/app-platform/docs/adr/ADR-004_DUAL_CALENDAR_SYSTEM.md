# ADR-004: Multi-Calendar System Implementation

## AppPlatform — Architectural Decision Record

**Status**: Accepted (revised 2026-03-12: generalised from Dual-Calendar to Multi-Calendar)  
**Date**: 2026-03-08 | **Revised**: 2026-03-12  
**Decision**: Implement a domain-agnostic Multi-Calendar Service (K-15) as a first-class kernel service  
**Impact**: High

> **PLATFORM-LEVEL DOCUMENT.** The Nepal/BS instantiation is handled by the Capital Markets (Siddhanta) domain pack via T1 config packs.

---

# CONTEXT

## Problem Statement

Platforms deployed across jurisdictions must handle multiple calendar systems. Any domain pack may require:

- Recording timestamps in the primary calendar of the jurisdiction plus UTC
- Calculating fiscal year boundaries per jurisdiction and calendar system
- Supporting settlement date calculations (T+n) with cross-calendar holiday awareness
- Business day convention support across calendar systems
- Regulatory compliance with jurisdiction-specific date requirements

**Driving example** — Capital Markets (Siddhanta) domain pack in Nepal: SEBON mandates Bikram Sambat (BS) dates; international markets use Gregorian. This is a _domain-pack concern_ handled via K-15 configuration, not a kernel invariant.

## Constraints

1. **Regulatory Requirement**: Any domain pack may declare required calendar systems via T1 config.
2. **International Interop**: ISO 8601 / UTC is always the canonical instant.
3. **Performance**: Calendar conversions must add <1ms per event.
4. **Accuracy**: Conversions must be exact — no approximation.
5. **Extensibility**: Additional calendar systems (e.g., Islamic Hijri, Thai Solar, Ethiopic) MUST be addable via T1 config packs without kernel code changes.

---

# DECISION

## Architecture Choice

**Implement a dedicated Multi-Calendar Service (K-15) as a kernel module, with a pluggable `CalendarDate` composite type used wherever timestamps require multi-calendar representation.**

### **Core Design Principles**

#### **1. CalendarDate as First-Class Type**

Every timestamp field that requires multi-calendar representation uses the `CalendarDate` composite type. The set of calendars is _not_ hard-coded at the kernel level — it is determined by the installed T1 calendar config packs.

```typescript
/** Open string identifier for a calendar system, e.g. "gregorian", "bs", "hijri", "thai" */
type CalendarId = string;

/** Per-calendar representation of a moment in time */
interface CalendarDateTime {
  readonly date: string; // locale format for the calendar, e.g. "2081-11-17" for BS
  readonly formatted?: string; // human-readable, e.g. "17 Falgun 2081"
}

/**
 * Domain-agnostic multi-calendar timestamp.
 * `primary` is always the canonical UTC instant (ISO 8601).
 * `calendars` is populated by K-15 based on the domain pack's declared T1 calendar config.
 */
interface CalendarDate {
  readonly primary: string; // ISO 8601 UTC, e.g. "2025-03-02T10:30:00Z"
  readonly timezone: string; // IANA timezone, e.g. "Asia/Kathmandu"
  readonly calendars: Record<CalendarId, CalendarDateTime>; // populated by K-15
  readonly fiscalYear?: Record<CalendarId, string>; // e.g. { "bs": "2081/82" }
}
```

**Capital Markets (Siddhanta) example** — after loading the Nepal T1 calendar pack:

```json
{
  "primary": "2025-03-02T10:30:00Z",
  "timezone": "Asia/Kathmandu",
  "calendars": {
    "gregorian": { "date": "2025-03-02", "formatted": "2 March 2025" },
    "bs": { "date": "2081-11-17", "formatted": "17 Falgun 2081" }
  },
  "fiscalYear": { "bs": "2081/82", "gregorian": "2024/25" }
}
```

#### **2. Conversion Algorithm**

- **Platform default**: Julian Day Number (JDN) for any calendar with a defined JDN mapping.
- **Domain pack can register**: custom `CalendarConverter` implementations via T1 config.
- **Accuracy**: Domain packs are responsible for providing and validating lookup data.

#### **3. Kernel Service (K-15 Multi-Calendar Service)**

- Centralized conversion API with sub-millisecond latency.
- Business day calculation with holiday-calendar awareness (holiday data via T1).
- Settlement date (T+n) computation respecting multi-calendar weekends/holidays.
- Fiscal year boundary resolution per configured calendar.

#### **4. Extension via T1 Config Packs**

- Calendar definitions (month lengths, epoch, leap year rules) — T1.
- Holiday calendars per jurisdiction — T1.
- Fiscal year boundary rules per calendar — T1.
- Weekend definitions per market — T1.

---

# CONSEQUENCES

## Positive Consequences

### **Regulatory Compliance**

- **Any jurisdiction**: Domain packs configure the calendars they need; the kernel stores and serves them.
- **Multi-Jurisdiction**: Each jurisdiction sees dates in its native calendar without kernel changes.
- **Audit Trail**: Immutable `CalendarDate` entries satisfy all regulators.

### **Data Integrity**

- **Single Source of Truth**: K-15 is the authoritative conversion service.
- **Consistency**: All services using K-15 produce identical `CalendarDate` values.
- **Immutability**: `CalendarDate` is computed at creation time, never retroactively changed.

### **Extensibility**

- **New calendars**: Additional calendar systems via T1 config packs — zero kernel code changes.
- **New markets**: Holiday calendars and fiscal years added without code changes.
- **Multi-pack**: Multiple domain packs can add different calendars; K-15 merges them.

### **Performance**

- **Cached Conversions**: Hot-path conversions cached in Redis (<0.1ms).
- **Batch Support**: Bulk conversion API for EOD processing.
- **Minimal Overhead**: <1ms per conversion, zero overhead when calendars map is empty.

## Negative Consequences

### **Complexity**

- **Schema Overhead**: `CalendarDate` is larger than a plain timestamp.
- **Storage Cost**: Proportional to number of configured calendars.
- **Testing Burden**: Date-sensitive tests must validate all configured calendars.

### **Mitigation**

- Platform SDK (K-12) auto-generates `CalendarDate` from a single UTC input.
- `calendars` map is empty (but valid) when no calendar packs are installed.
- Comprehensive test fixtures via domain pack TDD specs.

---

# ALTERNATIVES CONSIDERED

## Option 1: Hard-code Gregorian + BS in Kernel

- **Rejected**: Binds the kernel to Nepal/Siddhanta domain pack concerns.
- **Risk**: Every new jurisdiction requires kernel changes.

## Option 2: Store Only UTC, Convert at Display

- **Rejected**: Regulatory requirements frequently mandate storage-level calendar fields.
- **Risk**: Conversion errors at display time; audit trail gaps.

## Option 3: Application-Level Conversion Libraries per Domain Pack

- **Rejected**: Inconsistent conversions across services; duplicated holiday management.
- **Risk**: Drift between packs; synchronization failures.

---

# IMPLEMENTATION NOTES

## Technology

- **Service**: K-15 Multi-Calendar Service (TypeScript/Node.js)
- **Cache**: Redis for hot conversions
- **Algorithm**: JDN-based with T1-registered calendar definitions
- **BS Implementation**: `nepali-date-converter` (npm) as reference for Capital Markets pack

## Dependencies

- K-02 (T1 config: holiday calendars, fiscal year rules, calendar definitions)
- K-05 (EventBus: `CalendarDate` in event envelope when multi-calendar is required)

## Validation

- Each T1 calendar pack must include a validation dataset.
- Capital Markets (Siddhanta) pack: verified against Nepal Government Patro (BS 2000–2100).

---

**Decision Makers**: AppPlatform Architecture Team  
**Reviewers**: All domain pack leads, Regulatory Compliance Team  
**Approval Date**: 2026-03-08 | **Revised**: 2026-03-12
