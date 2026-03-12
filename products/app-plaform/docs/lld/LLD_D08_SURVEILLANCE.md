# LOW-LEVEL DESIGN: D-08 SURVEILLANCE

**Module**: D-08 Market Surveillance  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Surveillance & Market Integrity Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Surveillance module provides **market abuse detection, anomaly identification, trade pattern analysis, and case management for regulatory investigations**.

**Core Responsibilities**:
- Wash trade detection — self-dealing across related accounts
- Spoofing/layering detection — order book manipulation patterns
- Front-running detection — staff vs client order timing analysis
- AI/ML anomaly detection via K-09 governed models
- Alert generation and severity classification
- Case management workflow for investigation
- Evidence collection and regulatory report generation
- Real-time and T+1 batch surveillance modes

**Invariants**:
1. All trades MUST be screened by surveillance within T+1
2. High-severity alerts MUST trigger within 60 seconds for real-time mode
3. Evidence packages MUST be immutable once case is opened
4. AI model decisions MUST include explainability scores (K-09)
5. False positive classification REQUIRES reviewer approval

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Surveillance thresholds | K-02 stable |
| K-03 Rules Engine | Pattern matching rules | K-03 stable |
| K-05 Event Bus | TradeExecutedEvent consumption | K-05 stable |
| K-06 Observability | Surveillance metrics | K-06 stable |
| K-07 Audit Framework | Case audit trail | K-07 stable |
| K-09 AI Governance | ML model governance | K-09 stable |
| K-15 Dual-Calendar | Date range analysis | K-15 stable |
| K-18 Resilience | Batch processing resilience | K-18 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/surveillance/alerts?status=OPEN&severity=HIGH&from_bs=2082-11-01&to_bs=2082-11-25

Response 200:
{
  "alerts": [
    {
      "alert_id": "ALT-001",
      "type": "WASH_TRADE",
      "severity": "HIGH",
      "accounts": ["ACC-001", "ACC-007"],
      "instrument_id": "NABIL",
      "detected_at": "2026-03-08T14:30:00Z",
      "confidence": 0.92,
      "status": "OPEN"
    }
  ],
  "total": 15,
  "page": 1
}
```

```yaml
POST /api/v1/surveillance/cases
Authorization: Bearer {compliance_officer_token}

Request:
{
  "alert_ids": ["ALT-001", "ALT-002"],
  "case_type": "MARKET_MANIPULATION",
  "assigned_to": "USR-COMPLIANCE-01",
  "priority": "HIGH"
}

Response 201:
{
  "case_id": "CASE-2082-001",
  "status": "OPEN",
  "alert_count": 2,
  "created_at": "2026-03-08T15:00:00Z"
}
```

```yaml
POST /api/v1/surveillance/cases/{case_id}/evidence
Authorization: Bearer {compliance_officer_token}

Request:
{
  "evidence_type": "TRADE_RECORDS",
  "data": { "trade_ids": ["TRD-001", "TRD-002"] },
  "notes": "Related wash trade pair"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.surveillance.v1;

service SurveillanceService {
  rpc GetAlerts(AlertQuery) returns (AlertList);
  rpc CreateCase(CreateCaseRequest) returns (Case);
  rpc RunBatchSurveillance(BatchRequest) returns (BatchResult);
  rpc GetPatternAnalysis(PatternRequest) returns (PatternResult);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE surveillance_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type VARCHAR(50) NOT NULL, -- WASH_TRADE, SPOOFING, FRONT_RUNNING, ANOMALY
    severity VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    accounts VARCHAR(100)[] NOT NULL,
    instrument_id VARCHAR(50),
    confidence DECIMAL(5,4),
    detection_method VARCHAR(30), -- RULE_BASED, ML_MODEL, HYBRID
    model_id VARCHAR(100),
    explainability JSONB,
    pattern_data JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN', -- OPEN, INVESTIGATING, FALSE_POSITIVE, ESCALATED, CLOSED
    detected_at TIMESTAMPTZ DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(100)
);

CREATE TABLE surveillance_cases (
    id VARCHAR(50) PRIMARY KEY,
    case_type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    assigned_to VARCHAR(100),
    status VARCHAR(30) DEFAULT 'OPEN', -- OPEN, INVESTIGATING, PENDING_REVIEW, REPORTED, CLOSED
    alert_ids UUID[] NOT NULL,
    regulatory_filing_ref VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now(),
    closed_at TIMESTAMPTZ
);

CREATE TABLE case_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id VARCHAR(50) NOT NULL REFERENCES surveillance_cases(id),
    evidence_type VARCHAR(50) NOT NULL,
    data JSONB NOT NULL,
    hash VARCHAR(128) NOT NULL, -- SHA-512 immutability hash
    notes TEXT,
    added_by VARCHAR(100) NOT NULL,
    added_at TIMESTAMPTZ DEFAULT now()
);
```

### 3.2 Event Schemas

```json
{ "event_type": "SurveillanceAlertEvent", "data": { "alert_id": "ALT-001", "type": "WASH_TRADE", "severity": "HIGH" } }
```
```json
{ "event_type": "CaseEscalationEvent", "data": { "case_id": "CASE-2082-001", "escalated_to": "REGULATORY_AUTHORITY" } }
```

---

## 4. CONTROL FLOW

### 4.1 Real-Time Surveillance
```
1. Consume TradeExecutedEvent from K-05
2. Buffer trades in sliding window (configurable: 5min/15min/1hr)
3. Run detection rules:
   a. Wash trade: same beneficial owner, opposite sides, similar price/quantity
   b. Spoofing: large orders placed and cancelled within threshold
   c. Front-running: timing analysis of staff vs client orders
4. If pattern confidence > threshold:
   a. Generate SurveillanceAlertEvent
   b. Store alert with pattern data
5. AI model (via K-09): secondary analysis for HIGH severity
```

### 4.2 T+1 Batch Surveillance
```
1. Scheduled nightly batch after market close
2. Load all trades for the day
3. Run comprehensive pattern analysis across all accounts
4. Cross-reference with D-11 Reference Data for related entities
5. Generate batch alerts
6. Reconcile with real-time alerts (deduplicate)
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Wash Trade Detection
```
For each trade pair (T1, T2) within window:
  if T1.buyer_beneficial_owner == T2.seller_beneficial_owner
     AND abs(T1.price - T2.price) / T1.price < 0.01
     AND T1.instrument == T2.instrument:
    score = f(price_proximity, quantity_ratio, time_gap)
    if score > threshold: ALERT
```

### 5.2 Spoofing Detection
```
For each order sequence on instrument:
  if orders_placed > 5 AND cancellation_rate > 0.8
     AND price_moved_towards_remaining_order:
    confidence = ml_model.predict(features)
    if confidence > threshold: ALERT
```

---

## 6–10. NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Real-time alert <60s; batch surveillance <2 hours; 99.99% uptime
- **Security**: K-01 RBAC (compliance officers only); evidence immutability via SHA-512; case data encrypted at rest
- **Metrics**: `surveillance_alerts_generated`, `surveillance_false_positive_rate`, `surveillance_batch_duration_seconds`
- **Extension Points**: T2 jurisdiction-specific detection rules; T3 exchange surveillance data adapters; custom ML models via K-09
- **Tests**: Wash trade detection accuracy; spoofing pattern benchmarks; evidence immutability verification; alert deduplication correctness
