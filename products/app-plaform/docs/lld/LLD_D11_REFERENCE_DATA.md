# LOW-LEVEL DESIGN: D-11 REFERENCE DATA

**Module**: D-11 Reference Data Service  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Data Management Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Reference Data Service is the **golden source** for instruments, legal entities, benchmarks, and other master data across the platform.

**Core Responsibilities**:
- Instrument master data management (ISIN, symbol, exchange codes, lot size)
- Legal entity management (issuers, counterparties, brokers, custodians)
- Benchmark/index definitions and constituent management
- Versioned master data with temporal validity (effective dates)
- T3 data provider adapters for external data feeds
- Data quality validation and golden record reconciliation
- Dual-calendar effective date management
- Cross-reference management (instrument aliases across venues)

**Invariants**:
1. Every instrument MUST have a unique ISIN or internal identifier
2. Reference data changes MUST be versioned with effective dates
3. Entity data MUST pass validation rules before activation
4. T3 data updates MUST NOT override manual golden record overrides without approval
5. Historical reference data MUST be queryable at any point-in-time

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Data validation rules, provider configs | K-02 stable |
| K-05 Event Bus | InstrumentUpdatedEvent, EntityUpdatedEvent | K-05 stable |
| K-07 Audit Framework | Data change audit | K-07 stable |
| K-15 Dual-Calendar | Effective date management | K-15 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/reference-data/instruments/{instrument_id}

Response 200:
{
  "instrument_id": "NABIL",
  "isin": "NPE001A00001",
  "name": "Nabil Bank Limited",
  "instrument_type": "EQUITY",
  "currency": "NPR",
  "exchange": "NEPSE",
  "sector": "Commercial Banking",
  "lot_size": 10,
  "face_value": 100,
  "listed_date_bs": "2050-04-15",
  "status": "ACTIVE",
  "version": 12,
  "effective_from_bs": "2082-07-01"
}
```

```yaml
GET /api/v1/reference-data/instruments?type=EQUITY&sector=Commercial%20Banking&status=ACTIVE

Response 200:
{
  "instruments": [...],
  "total": 28,
  "page": 1
}
```

```yaml
GET /api/v1/reference-data/entities/{entity_id}

Response 200:
{
  "entity_id": "ENT-001",
  "name": "Nepal Stock Exchange",
  "entity_type": "EXCHANGE",
  "lei": "...",
  "jurisdiction": "NP",
  "status": "ACTIVE"
}
```

```yaml
POST /api/v1/reference-data/instruments
Authorization: Bearer {data_admin_token}

Request:
{
  "instrument_id": "NEWCO",
  "isin": "NPE099A00001",
  "name": "New Company Limited",
  "instrument_type": "EQUITY",
  "currency": "NPR",
  "exchange": "NEPSE",
  "sector": "Hydropower",
  "lot_size": 10,
  "face_value": 100,
  "effective_from_bs": "2082-12-01"
}
```

```yaml
GET /api/v1/reference-data/instruments/{instrument_id}/history?as_of_bs=2080-06-15

Response 200:
{
  "instrument_id": "NABIL",
  "as_of_bs": "2080-06-15",
  "version": 9,
  "data": { ... }
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.refdata.v1;

service ReferenceDataService {
  rpc GetInstrument(InstrumentRequest) returns (Instrument);
  rpc SearchInstruments(InstrumentQuery) returns (InstrumentList);
  rpc GetEntity(EntityRequest) returns (Entity);
  rpc GetInstrumentAsOf(AsOfRequest) returns (Instrument);
  rpc GetBenchmark(BenchmarkRequest) returns (Benchmark);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE instruments (
    id VARCHAR(50) PRIMARY KEY,
    isin VARCHAR(20) UNIQUE,
    name VARCHAR(255) NOT NULL,
    instrument_type VARCHAR(50) NOT NULL, -- EQUITY, BOND, MUTUAL_FUND, DERIVATIVE, DEBENTURE
    currency VARCHAR(10) NOT NULL,
    exchange VARCHAR(50),
    sector VARCHAR(100),
    sub_sector VARCHAR(100),
    lot_size INTEGER DEFAULT 1,
    face_value DECIMAL(18,4),
    listed_date DATE,
    listed_date_bs VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    metadata JSONB,
    current_version INTEGER DEFAULT 1,
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE instrument_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id VARCHAR(50) NOT NULL REFERENCES instruments(id),
    version INTEGER NOT NULL,
    data JSONB NOT NULL,
    effective_from DATE NOT NULL,
    effective_from_bs VARCHAR(20),
    effective_to DATE,
    effective_to_bs VARCHAR(20),
    changed_by VARCHAR(100),
    change_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (instrument_id, version)
);

CREATE TABLE legal_entities (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL, -- EXCHANGE, BROKER, CUSTODIAN, ISSUER, CSD, REGULATOR
    lei VARCHAR(20),
    jurisdiction VARCHAR(10),
    registration_number VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    metadata JSONB,
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE benchmarks (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    benchmark_type VARCHAR(50), -- INDEX, RATE, COMPOSITE
    base_date DATE,
    base_value DECIMAL(18,4),
    calculation_method VARCHAR(50),
    constituents JSONB,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

CREATE TABLE cross_references (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id VARCHAR(50) NOT NULL REFERENCES instruments(id),
    source VARCHAR(50) NOT NULL,
    external_code VARCHAR(100) NOT NULL,
    UNIQUE (instrument_id, source)
);
```

### 3.2 Event Schemas

```json
{ "event_type": "InstrumentCreatedEvent", "data": { "instrument_id": "NEWCO", "type": "EQUITY" } }
```
```json
{ "event_type": "InstrumentUpdatedEvent", "data": { "instrument_id": "NABIL", "version": 12, "fields_changed": ["sector"] } }
```
```json
{ "event_type": "EntityUpdatedEvent", "data": { "entity_id": "ENT-001", "entity_type": "EXCHANGE" } }
```

---

## 4. CONTROL FLOW

### 4.1 Instrument Onboarding
```
1. Data admin or T3 adapter submits new instrument
2. Validate against schema rules (K-02 config)
3. Check ISIN uniqueness
4. Create instrument record with version=1
5. Create initial version in instrument_versions
6. Publish InstrumentCreatedEvent
7. Downstream services (D-04, D-05, etc.) update caches
```

### 4.2 Point-in-Time Query
```
function getInstrumentAsOf(instrumentId, date_bs):
  version = SELECT * FROM instrument_versions
    WHERE instrument_id = instrumentId
      AND effective_from_bs <= date_bs
      AND (effective_to_bs IS NULL OR effective_to_bs > date_bs)
    ORDER BY version DESC LIMIT 1
  return version.data
```

### 4.3 T3 Data Provider Sync
```
1. Scheduled sync from T3 data provider adapter
2. Fetch external data (instruments, entities, benchmarks)
3. Compare with golden record
4. If delta found:
   a. Auto-apply if no manual override exists
   b. Queue for approval if manual override exists
5. Create new version, publish event
```

---

## 5–10. ALGORITHMS, NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Instrument lookup <5ms (cached); search <50ms; 99.99% uptime; support 100K+ instruments
- **Security**: K-01 RBAC; data admin role for writes; audit trail for all changes
- **Metrics**: `refdata_instruments_total`, `refdata_lookups_per_second`, `refdata_versions_created`, `refdata_sync_failures`
- **Extension Points**: T3 data provider adapters for new vendors; T2 jurisdiction-specific instrument types; custom validation rules
- **Tests**: Version history integrity; point-in-time query accuracy; cross-reference resolution; T3 sync conflict handling
