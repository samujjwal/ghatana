# ADR-004: Dual-Calendar System Implementation
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: Implement native dual-calendar (Bikram Sambat + Gregorian) as a first-class kernel service  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta operates across jurisdictions that use different calendar systems. Nepal's financial ecosystem relies on **Bikram Sambat (BS)** for fiscal years, regulatory reporting, and settlement cycles, while international markets use the **Gregorian (AD)** calendar. The platform must:

- Record every timestamp in both calendar systems simultaneously
- Calculate fiscal year boundaries per jurisdiction
- Support settlement date calculations (T+n) with awareness of holidays in both calendars
- Handle business day conventions across calendar systems
- Ensure regulatory compliance with jurisdiction-specific date requirements

## Constraints

1. **Regulatory Requirement**: SEBON mandates BS dates on all financial records
2. **International Interop**: SEBI, MiFID II, and global systems use Gregorian dates
3. **Performance**: Calendar conversions must not add perceptible latency (<1ms)
4. **Accuracy**: Conversion must be exact — no approximation allowed
5. **Future-Proof**: Must support additional calendar systems (Islamic, Thai) via plugins

---

# DECISION

## Architecture Choice

**Implement a dedicated Dual-Calendar Service (K-15) as a kernel module, with mandatory `DualDate` types across all data models and event schemas.**

### **Core Design Principles**

#### **1. DualDate as First-Class Type**
Every timestamp in the platform uses the `DualDate` composite type:
```typescript
interface DualDate {
  gregorian: ISO8601DateTime;  // "2025-03-02T10:30:00Z"
  bs: BSDate;                  // "2081-11-17"
  timezone: IANATimezone;      // "Asia/Kathmandu"
  fiscal_year_bs?: string;     // "2081/82"
  fiscal_year_ad?: string;     // "2024/25"
}
```

#### **2. Conversion Algorithm**
- Primary: Julian Day Number (JDN) algorithm for mathematical conversion
- Fallback: 100-year lookup table (BS 2000–2100) for edge-case validation
- Accuracy: Verified against Nepal Government Patro for all dates in range

#### **3. Kernel Service (K-15)**
- Centralized conversion API with sub-millisecond latency
- Business day calculation with holiday calendar awareness
- Settlement date (T+n) computation with weekend/holiday skipping
- Fiscal year boundary resolution per jurisdiction

#### **4. Extension via T1 Config Packs**
- Holiday calendars per jurisdiction (T1)
- Fiscal year boundary rules (T1)
- Additional calendar systems (T1 — Islamic, Thai, etc.)
- Weekend definitions per market (T1)

---

# CONSEQUENCES

## Positive Consequences

### **Regulatory Compliance**
- **SEBON Compliance**: BS dates natively on all financial records
- **Multi-Jurisdiction**: Each jurisdiction sees dates in its native calendar
- **Audit Trail**: Immutable dual-date audit entries satisfy all regulators
- **Fiscal Year**: Accurate fiscal year resolution per jurisdiction

### **Data Integrity**
- **Single Source of Truth**: K-15 is the authoritative conversion service
- **Consistency**: All services produce identical DualDate values
- **Immutability**: DualDate is computed at creation, never retroactively changed
- **Validation**: Conversion accuracy verified against official sources

### **Extensibility**
- **New Calendars**: Additional calendar systems via T1 config packs
- **New Markets**: Holiday calendars added without code changes
- **Custom Fiscal Years**: Jurisdiction-specific fiscal year boundaries via T1

### **Performance**
- **Cached Conversions**: Hot-path conversions cached in Redis (<0.1ms)
- **Batch Support**: Bulk conversion API for EOD processing
- **Minimal Overhead**: <1ms per conversion, negligible at scale

## Negative Consequences

### **Complexity**
- **Schema Overhead**: Every timestamp field requires dual representation
- **Storage Cost**: ~40 bytes additional per timestamp
- **Testing Burden**: All date-sensitive tests must validate both calendars
- **Developer Learning Curve**: Developers must understand DualDate semantics

### **Mitigation**
- Platform SDK (K-12) auto-generates DualDate from single input
- Comprehensive test fixtures covering BS/AD edge cases
- Developer documentation with conversion examples

---

# ALTERNATIVES CONSIDERED

## Option 1: Gregorian-Only with Display Conversion
- **Rejected**: Regulatory non-compliance — SEBON requires BS dates at storage level
- **Risk**: Conversion errors at display time; audit trail gaps

## Option 2: Store BS Only, Convert on Export
- **Rejected**: International interop requires Gregorian for cross-border operations
- **Risk**: Conversion delays; inconsistency with global standards

## Option 3: Application-Level Conversion Libraries
- **Rejected**: Inconsistent conversions across services; no centralized holiday management
- **Risk**: Drift between services; holiday calendar synchronization failures

---

# IMPLEMENTATION NOTES

## Technology
- **Service**: K-15 Dual-Calendar Service (TypeScript/Node.js)
- **Cache**: Redis for hot conversions
- **Algorithm**: JDN + lookup table hybrid
- **Library**: `nepali-date-converter` (npm) as reference, custom implementation for production

## Dependencies
- K-02 (holiday calendar config, fiscal year rules)
- K-05 (DualDate in all event schemas)

## Validation
- Verified against Nepal Government Patro (BS 2000–2100)
- Cross-validated with `nepali-date-converter` library
- Edge cases: BS month boundaries, leap year handling, Chaitra/Baisakh transition

---

**Decision Makers**: Platform Architecture Team  
**Reviewers**: Regulatory Compliance Team, Nepal Market Operations  
**Approval Date**: 2026-03-08
