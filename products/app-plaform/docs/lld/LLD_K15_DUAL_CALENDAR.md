# LOW-LEVEL DESIGN: K-15 DUAL-CALENDAR SERVICE

**Module**: K-15 Dual-Calendar Service  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-15 provides **calendar conversion, dual-date generation, fiscal year boundaries, and business day calculation** for all Siddhanta modules needing calendar-aware operations.

**Core Responsibilities**:
- Bikram Sambat (BS) ↔ Gregorian (AD) bidirectional conversion
- `DualDate` generation: every platform timestamp carries both BS and Gregorian
- Fiscal year boundary calculation per jurisdiction (T1 config)
- Business day calculation with jurisdiction-specific holiday calendars (T1 config)
- Settlement date calculation (T+n with business day adjustment)
- Sub-millisecond conversion latency for hot-path usage
- Extensible to additional calendar systems (Islamic/Hijri, Thai Solar, etc.)
- Blockchain Unix epoch ↔ BS conversion for digital asset timestamps

**Invariants**:
1. Every timestamp persisted or published MUST have a BS companion field
2. Conversion accuracy MUST be exact (lookup-table based, not algorithmic approximation)
3. Lookup table MUST cover at least 100 years (2000 BS – 2100 BS / 1943 AD – 2043 AD)
4. Conversion latency MUST be ≤ 0.1ms P99 (in-memory lookup)
5. Business day calculation MUST use the holiday calendar for the effective jurisdiction
6. Fiscal year start MUST be configurable per jurisdiction via K-02

### 1.2 Explicit Non-Goals

- ❌ Date/time zone management (use standard `ZonedDateTime` / `Instant`)
- ❌ Calendar UI rendering (handled by frontend libraries)
- ❌ Historical calendar reform handling (pre-2000 BS not required)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Holiday calendars, fiscal year config, calendar extensions | K-02 stable |
| K-05 Event Bus | Calendar config change events | K-05 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/calendar/convert?from=AD&date=2025-03-02&to=BS
Authorization: Bearer {service_token}

Response 200:
{
  "input": { "calendar": "AD", "date": "2025-03-02" },
  "output": { "calendar": "BS", "date": "2081-11-18" },
  "day_of_week": "Sunday",
  "fiscal_year_bs": "2081/82",
  "is_holiday": false,
  "is_business_day": true
}
```

```yaml
POST /api/v1/calendar/dual-date
Authorization: Bearer {service_token}
Content-Type: application/json

Request:
{
  "timestamp": "2025-03-02T10:30:00Z"
}

Response 200:
{
  "gregorian": "2025-03-02T10:30:00Z",
  "bs": "2081-11-18",
  "bs_full": "2081-11-18 10:30:00",
  "fiscal_year_bs": "2081/82",
  "fiscal_year_ad": "2024/25"
}
```

```yaml
GET /api/v1/calendar/business-days?from=2025-03-02&count=3&jurisdiction=NP
Authorization: Bearer {service_token}

Response 200:
{
  "from_date": "2025-03-02",
  "from_date_bs": "2081-11-18",
  "count": 3,
  "settlement_date": "2025-03-05",
  "settlement_date_bs": "2081-11-21",
  "skipped_dates": [
    { "date": "2025-03-03", "reason": "SATURDAY (NP weekend)" }
  ],
  "jurisdiction": "NP"
}
```

```yaml
GET /api/v1/calendar/holidays?jurisdiction=NP&year_bs=2081
Authorization: Bearer {service_token}

Response 200:
{
  "jurisdiction": "NP",
  "year_bs": 2081,
  "holidays": [
    { "date_bs": "2081-01-01", "date_ad": "2024-04-13", "name": "Nepali New Year", "type": "NATIONAL" },
    { "date_bs": "2081-07-15", "date_ad": "2024-10-31", "name": "Tihar (Laxmi Puja)", "type": "NATIONAL" }
  ],
  "total_holidays": 52,
  "total_business_days": 253
}
```

```yaml
GET /api/v1/calendar/fiscal-year?jurisdiction=NP&date=2025-03-02
Authorization: Bearer {service_token}

Response 200:
{
  "jurisdiction": "NP",
  "fiscal_year_label": "2081/82",
  "start_date_bs": "2081-04-01",
  "start_date_ad": "2024-07-16",
  "end_date_bs": "2082-03-31",
  "end_date_ad": "2025-07-15",
  "quarter": 3,
  "quarter_start_bs": "2081-10-01",
  "quarter_end_bs": "2081-12-30"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.calendar.v1;

service DualCalendarService {
  rpc ConvertDate(ConvertDateRequest) returns (ConvertDateResponse);
  rpc GenerateDualDate(DualDateRequest) returns (DualDateResponse);
  rpc CalculateBusinessDays(BusinessDaysRequest) returns (BusinessDaysResponse);
  rpc GetHolidays(HolidaysRequest) returns (HolidaysResponse);
  rpc GetFiscalYear(FiscalYearRequest) returns (FiscalYearResponse);
  rpc BatchConvert(BatchConvertRequest) returns (BatchConvertResponse);
}

message ConvertDateRequest {
  string from_calendar = 1;  // "AD" or "BS"
  string date = 2;           // "2025-03-02" or "2081-11-18"
  string to_calendar = 3;
}

message DualDateRequest {
  google.protobuf.Timestamp timestamp = 1;
}

message DualDateResponse {
  string gregorian = 1;
  string bs = 2;
  string bs_full = 3;
  string fiscal_year_bs = 4;
  string fiscal_year_ad = 5;
}

message BatchConvertRequest {
  repeated ConvertDateRequest conversions = 1;
}

message BatchConvertResponse {
  repeated ConvertDateResponse results = 1;
}
```

### 2.3 SDK Method Signatures

```typescript
interface DualCalendarClient {
  /** Convert date between calendars */
  convert(date: string, from: CalendarType, to: CalendarType): Promise<string>;

  /** Generate DualDate from timestamp */
  toDualDate(timestamp: Date): Promise<DualDate>;

  /** Get BS string for current moment */
  nowBs(): string;

  /** Calculate settlement date (T+n business days) */
  settlementDate(fromDate: Date, businessDays: number, jurisdiction: string): Promise<SettlementResult>;

  /** Check if date is a business day */
  isBusinessDay(date: Date, jurisdiction: string): Promise<boolean>;

  /** Get fiscal year info */
  getFiscalYear(date: Date, jurisdiction: string): Promise<FiscalYearInfo>;

  /** Batch convert (efficient for bulk operations) */
  batchConvert(dates: ConvertRequest[]): Promise<ConvertResult[]>;
}

interface DualDate {
  gregorian: string;   // ISO-8601
  bs: string;          // YYYY-MM-DD
  bsFull: string;      // YYYY-MM-DD HH:mm:ss
}

type CalendarType = 'AD' | 'BS' | 'HIJRI' | 'THAI_SOLAR';
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| CAL_E001 | 400 | No | Date out of supported range |
| CAL_E002 | 400 | No | Invalid date format |
| CAL_E003 | 400 | No | Unknown calendar type |
| CAL_E004 | 404 | No | Holiday calendar not found for jurisdiction |
| CAL_E005 | 500 | Yes | Conversion table not loaded |
| CAL_E006 | 400 | No | Invalid jurisdiction code |

---

## 3. DATA MODEL

### 3.1 BS ↔ AD Lookup Table (In-Memory)

```java
/**
 * Pre-computed BS month lengths for years 2000-2100 BS.
 * Each BS year has 12 months with varying days (29-32).
 * Source: Nepal Patro authoritative data.
 */
public class BsCalendarData {
    // Map<bsYear, int[12]> — days per month for each BS year
    private static final Map<Integer, int[]> BS_MONTH_DAYS = Map.of(
        2081, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30},
        2082, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31},
        // ... 100 years of data loaded from bundled resource file
    );
    
    // Epoch anchor: 2000-01-01 BS = 1943-04-14 AD (Julian Day Number)
    public static final long BS_EPOCH_JDN = 2430851L;
}
```

### 3.2 Holiday Calendar Table

```sql
CREATE TABLE holiday_calendars (
  holiday_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  jurisdiction VARCHAR(10) NOT NULL,
  date_ad DATE NOT NULL,
  date_bs VARCHAR(10) NOT NULL,
  holiday_name VARCHAR(255) NOT NULL,
  holiday_type VARCHAR(50) NOT NULL CHECK (holiday_type IN ('NATIONAL', 'EXCHANGE', 'BANK', 'REGIONAL')),
  is_half_day BOOLEAN NOT NULL DEFAULT false,
  year_bs INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  UNIQUE (jurisdiction, date_ad, holiday_type)
);

CREATE INDEX idx_holidays_jurisdiction_year ON holiday_calendars(jurisdiction, year_bs);
CREATE INDEX idx_holidays_date ON holiday_calendars(date_ad);
```

### 3.3 Fiscal Year Config Table

```sql
CREATE TABLE fiscal_year_config (
  config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  jurisdiction VARCHAR(10) NOT NULL UNIQUE,
  fy_start_month_bs INT NOT NULL DEFAULT 4,    -- Shrawan (month 4 in BS)
  fy_start_day_bs INT NOT NULL DEFAULT 1,
  quarter_months INT NOT NULL DEFAULT 3,
  label_format VARCHAR(50) NOT NULL DEFAULT '{start_year}/{end_year_short}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL
);

-- Nepal: FY starts Shrawan 1 (mid-July)
INSERT INTO fiscal_year_config (jurisdiction, fy_start_month_bs, fy_start_day_bs)
VALUES ('NP', 4, 1);

-- India: FY starts April 1 (use AD-based calculation)
INSERT INTO fiscal_year_config (jurisdiction, fy_start_month_bs, fy_start_day_bs)
VALUES ('IN', 1, 1);  -- Mapped via AD: April 1
```

### 3.4 Calendar Extension Registry

```sql
CREATE TABLE calendar_extensions (
  calendar_type VARCHAR(20) PRIMARY KEY,
  converter_plugin VARCHAR(255) NOT NULL,  -- T3 plugin name
  epoch_reference VARCHAR(50),
  supported_range_start VARCHAR(20),
  supported_range_end VARCHAR(20),
  enabled BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL
);
```

---

## 4. CONTROL FLOW

### 4.1 AD → BS Conversion (Hot Path)

```
Input: Gregorian date (2025-03-02)
  → Calculate Julian Day Number (JDN) for input date
  → Subtract BS_EPOCH_JDN to get days since BS epoch
  → Walk BS_MONTH_DAYS table year by year:
    FOR each BS year from 2000:
      FOR each month [1..12]:
        IF remaining_days < month_days[month]:
          RETURN BsDate(year, month, remaining_days + 1)
        remaining_days -= month_days[month]
  → Return BS date string "YYYY-MM-DD"
```

### 4.2 BS → AD Conversion

```
Input: BS date (2081-11-18)
  → Count total days from BS epoch to input date:
    FOR each year from 2000 to input.year:
      FOR each month [1..12] (or partial for target year):
        total_days += BS_MONTH_DAYS[year][month]
  → Add total_days to BS_EPOCH_JDN
  → Convert JDN to Gregorian date
  → Return AD date string "YYYY-MM-DD"
```

### 4.3 Settlement Date Calculation

```
Input: trade_date, T+n, jurisdiction
  → current_date = trade_date
  → business_days_counted = 0
  → WHILE business_days_counted < n:
      current_date = current_date + 1 day
      IF is_business_day(current_date, jurisdiction):
        business_days_counted += 1
  → settlement_date_ad = current_date
  → settlement_date_bs = convert(current_date, AD, BS)
  → RETURN { settlement_date_ad, settlement_date_bs }

  is_business_day(date, jurisdiction):
    → IF date.dayOfWeek in weekend_days(jurisdiction):  // NP: Saturday; IN: Saturday+Sunday
        RETURN false
    → IF holiday_cache.contains(date, jurisdiction):
        RETURN false
    → RETURN true
```

### 4.4 Startup / Cache Warm

```
On Service Start:
  → Load BS_MONTH_DAYS from bundled resource (100 years)
  → Load holiday_calendars from DB into in-memory cache
  → Load fiscal_year_config from DB
  → Pre-compute JDN-to-BS index for O(1) lookups (optional optimization)
  → Verify lookup table integrity (checksum)
  → Subscribe to K-05 events: "HolidayCalendarUpdated", "FiscalYearConfigUpdated"
    → On event: refresh in-memory cache
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Julian Day Number Conversion

```java
public static long gregorianToJDN(int year, int month, int day) {
    // Meeus algorithm for Gregorian → Julian Day Number
    int a = (14 - month) / 12;
    int y = year + 4800 - a;
    int m = month + 12 * a - 3;
    return day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045;
}

public static int[] jdnToGregorian(long jdn) {
    long a = jdn + 32044;
    long b = (4 * a + 3) / 146097;
    long c = a - (146097 * b) / 4;
    long d = (4 * c + 3) / 1461;
    long e = c - (1461 * d) / 4;
    long m = (5 * e + 2) / 153;
    int day = (int)(e - (153 * m + 2) / 5 + 1);
    int month = (int)(m + 3 - 12 * (m / 10));
    int year = (int)(100 * b + d - 4800 + m / 10);
    return new int[]{year, month, day};
}
```

### 5.2 Optimized Lookup (Pre-computed Index)

```java
/**
 * Pre-compute cumulative day counts for O(1) BS→AD conversion.
 * Index: cumulativeDays[bsYear - 2000] = total days from epoch to year start
 */
private int[] cumulativeDays;

public void buildIndex() {
    cumulativeDays = new int[101]; // 2000-2100 BS
    int total = 0;
    for (int year = 2000; year <= 2100; year++) {
        cumulativeDays[year - 2000] = total;
        for (int month = 0; month < 12; month++) {
            total += BS_MONTH_DAYS.get(year)[month];
        }
    }
}

public String bsToAd(int bsYear, int bsMonth, int bsDay) {
    int daysSinceEpoch = cumulativeDays[bsYear - 2000];
    for (int m = 0; m < bsMonth - 1; m++) {
        daysSinceEpoch += BS_MONTH_DAYS.get(bsYear)[m];
    }
    daysSinceEpoch += bsDay - 1;
    long jdn = BS_EPOCH_JDN + daysSinceEpoch;
    int[] ad = jdnToGregorian(jdn);
    return String.format("%04d-%02d-%02d", ad[0], ad[1], ad[2]);
}
```

### 5.3 Weekend Configuration per Jurisdiction

```json
{
  "NP": { "weekend_days": ["SATURDAY"], "comment": "Nepal: Saturday is weekend, Friday is half-day" },
  "IN": { "weekend_days": ["SATURDAY", "SUNDAY"], "comment": "India: Standard western weekend" },
  "BD": { "weekend_days": ["FRIDAY", "SATURDAY"], "comment": "Bangladesh: Friday-Saturday weekend" },
  "AE": { "weekend_days": ["FRIDAY", "SATURDAY"], "comment": "UAE: Friday-Saturday weekend" }
}
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| AD → BS conversion (in-memory) | 0.01ms | 0.05ms | 0.1ms | 0.5ms |
| BS → AD conversion (in-memory) | 0.01ms | 0.05ms | 0.1ms | 0.5ms |
| DualDate generation | 0.02ms | 0.08ms | 0.15ms | 0.5ms |
| Settlement date (T+3) | 0.05ms | 0.2ms | 0.5ms | 2ms |
| Batch convert (100 dates) | 1ms | 3ms | 5ms | 10ms |
| Holiday calendar query | 2ms | 5ms | 10ms | 50ms |

### 6.2 Throughput Targets

| Metric | Target |
|--------|--------|
| Conversions (in-process) | 10M/sec |
| REST API conversions | 50K/sec |
| gRPC conversions | 100K/sec |
| Batch conversions | 1K batches/sec (100 dates each) |

### 6.3 Resource Limits

| Resource | Limit |
|----------|-------|
| Lookup table memory | ~50KB (100 years × 12 months × 4 bytes) |
| Holiday cache memory | ~5MB (10 jurisdictions × 100 years × 60 holidays) |
| Pre-computed index memory | ~400 bytes |

---

## 7. SECURITY DESIGN

### 7.1 Data Integrity

- Lookup table loaded from signed, bundled resource file (Ed25519 signature verified on startup)
- Holiday calendar updates require maker-checker approval
- Conversion results are deterministic — same input always yields same output (no external dependencies)

### 7.2 Access Control

- Conversion APIs: any authenticated service or user
- Holiday management APIs: `calendar:admin` permission
- Fiscal year config: `calendar:admin` permission
- All changes audited via K-07

### 7.3 Air-Gap Support

- Lookup table bundled in application artifact — no network required
- Holiday calendar pre-loaded from signed bundle files
- Fiscal year config included in T1 config bundle

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_calendar_conversion_total{from, to}                 counter
siddhanta_calendar_conversion_duration_ns{from, to}           histogram
siddhanta_calendar_settlement_calculation_total{jurisdiction}  counter
siddhanta_calendar_holiday_cache_size{jurisdiction}            gauge
siddhanta_calendar_lookup_table_loaded{status}                 gauge
siddhanta_calendar_business_day_check_total{jurisdiction}      counter
```

### 8.2 Structured Logs

```json
{
  "level": "INFO",
  "module": "K-15-Calendar",
  "action": "HOLIDAY_CALENDAR_UPDATED",
  "jurisdiction": "NP",
  "year_bs": 2082,
  "holidays_count": 52,
  "updated_by": "usr_admin1",
  "trace_id": "tr_abc",
  "timestamp_bs": "2081-11-18",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

### 8.3 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Lookup table not loaded | `lookup_table_loaded == 0` on startup | P1 |
| Conversion error rate | >0.1% over 5min | P2 |
| Holiday cache stale | No refresh >24h | P3 |
| Upcoming year data missing | BS year N+1 not in lookup table (within 90 days) | P2 |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Additional Calendar Systems

```typescript
interface CalendarConverterPlugin {
  readonly metadata: {
    calendarType: string;   // e.g., "HIJRI", "THAI_SOLAR"
    version: string;
    supportedRange: { start: string; end: string };
  };

  /** Convert Gregorian to this calendar */
  fromGregorian(date: string): Promise<string>;

  /** Convert this calendar to Gregorian */
  toGregorian(date: string): Promise<string>;

  /** Health check */
  healthCheck(): Promise<boolean>;
}
```

**Registration**: New calendar systems registered via `calendar_extensions` table. K-15 routes conversion requests to the appropriate T3 plugin.

### 9.2 Holiday Calendar Packs (T1)

```json
{
  "content_pack_type": "T1",
  "name": "nepal-holidays-2082",
  "jurisdiction": "NP",
  "year_bs": 2082,
  "holidays": [
    { "date_bs": "2082-01-01", "name": "Nepali New Year", "type": "NATIONAL" },
    { "date_bs": "2082-01-11", "name": "Rato Machhindranath", "type": "REGIONAL" }
  ]
}
```

### 9.3 CBDC / T+0 Settlement Support

For instant settlement (T+0), the settlement date calculation returns the same business day:
```
settlementDate(tradeDate, T=0, jurisdiction) → tradeDate (if business day) OR next business day
```

The sub-millisecond conversion latency ensures T+0 settlement pipelines have zero calendar overhead.

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-CAL-001 | AD→BS known dates | 2025-03-02 → 2081-11-18 |
| UT-CAL-002 | BS→AD known dates | 2081-11-18 → 2025-03-02 |
| UT-CAL-003 | Round-trip conversion | AD→BS→AD yields original date |
| UT-CAL-004 | Leap year handling | BS year boundary dates correct |
| UT-CAL-005 | Fiscal year NP | 2081-11-18 → FY 2081/82 |
| UT-CAL-006 | Business day NP | Saturday → not business day |
| UT-CAL-007 | Business day IN | Sunday → not business day |
| UT-CAL-008 | Settlement T+2 NP | Skips Saturday + holidays |
| UT-CAL-009 | Settlement T+0 | Returns trade date (business day) |
| UT-CAL-010 | Edge: first day of range | 2000-01-01 BS converts correctly |
| UT-CAL-011 | Edge: last day of range | 2100-12-30 BS converts correctly |
| UT-CAL-012 | Out of range | Date before 2000 BS → CAL_E001 |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-CAL-001 | REST API round-trip | Convert and back via endpoints |
| IT-CAL-002 | gRPC batch convert | 1000 dates converted correctly |
| IT-CAL-003 | Holiday cache refresh | DB update reflected in API within 5s |
| IT-CAL-004 | K-02 config change | FY boundary change takes effect |
| IT-CAL-005 | K-05 event trigger | HolidayCalendarUpdated event published |
| IT-CAL-006 | Multi-jurisdiction | NP and IN fiscal years differ correctly |

### 10.3 Performance Tests

| Test | Description | Target |
|------|-------------|--------|
| PT-CAL-001 | Single conversion latency | P99 < 0.1ms |
| PT-CAL-002 | Batch 10K conversions | < 10ms |
| PT-CAL-003 | Concurrent REST | 50K reqs/sec at P99 < 5ms |
| PT-CAL-004 | Memory footprint | < 10MB for full lookup table + cache |

### 10.4 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-CAL-001 | DB unavailable at startup | Lookup table from bundled resource, holidays from cache |
| CT-CAL-002 | Corrupted lookup table | Checksum fails, startup aborted (fail-fast) |
| CT-CAL-003 | Holiday cache eviction | Re-loaded from DB on next access |

---

**END OF K-15 DUAL-CALENDAR LLD**
