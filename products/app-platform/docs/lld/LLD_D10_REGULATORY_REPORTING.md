# LOW-LEVEL DESIGN: D-10 REGULATORY REPORTING

**Module**: D-10 Regulatory Reporting  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Regulatory Reporting Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Regulatory Reporting module provides **automated report generation, multi-format rendering (PDF/CSV/XBRL), submission to regulatory portals via T3 adapters, and acknowledgement tracking**.

**Core Responsibilities**:
- Report definition management (templates, schedules, destinations)
- Data aggregation from multiple domain services
- Multi-format rendering: PDF, CSV, XLSX, XBRL, JSON
- T3 regulatory portal adapter execution for automated submission
- Submission tracking and ACK/NACK handling
- Report versioning and amendment workflows
- Dual-calendar date alignment for reporting periods
- Regulatory deadline tracking and alerting

**Invariants**:
1. Reports MUST match regulatory template schema exactly
2. Submitted reports MUST be immutable (versioned amendments only)
3. All submissions MUST be audited via K-07
4. Regulatory deadlines MUST be tracked against K-15 BS calendar
5. Failed submissions MUST retry with exponential backoff

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Report templates, schedules | K-02 stable |
| K-04 Plugin Runtime | T3 regulatory portal adapters | K-04 stable |
| K-05 Event Bus | ReportGeneratedEvent, SubmissionEvent | K-05 stable |
| K-07 Audit Framework | Submission audit | K-07 stable |
| K-15 Dual-Calendar | Reporting period dates | K-15 stable |
| K-16 Ledger | Financial data for reports | K-16 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/reports/generate
Authorization: Bearer {service_token}

Request:
{
  "report_type": "SEBON_MONTHLY_TRADE_SUMMARY",
  "period_start_bs": "2082-11-01",
  "period_end_bs": "2082-11-30",
  "format": "XLSX",
  "auto_submit": true
}

Response 202:
{
  "report_id": "RPT-2082-11-001",
  "status": "GENERATING",
  "estimated_completion": "2026-03-08T18:00:00Z"
}
```

```yaml
GET /api/v1/reports/{report_id}

Response 200:
{
  "report_id": "RPT-2082-11-001",
  "report_type": "SEBON_MONTHLY_TRADE_SUMMARY",
  "period_bs": "2082-11-01 to 2082-11-30",
  "format": "XLSX",
  "status": "SUBMITTED",
  "generated_at": "2026-03-08T18:00:01Z",
  "submitted_at": "2026-03-08T18:00:05Z",
  "ack_status": "ACKNOWLEDGED",
  "ack_ref": "SEBON-ACK-12345",
  "download_url": "/api/v1/reports/RPT-2082-11-001/download"
}
```

```yaml
GET /api/v1/reports/deadlines?upcoming=30d

Response 200:
{
  "deadlines": [
    {
      "report_type": "SEBON_QUARTERLY_FINANCIAL",
      "due_date_bs": "2082-12-15",
      "due_date_gregorian": "2026-03-28",
      "status": "PENDING",
      "days_remaining": 20
    }
  ]
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.reporting.v1;

service ReportingService {
  rpc GenerateReport(ReportRequest) returns (ReportStatus);
  rpc GetReportStatus(ReportQuery) returns (ReportStatus);
  rpc SubmitReport(SubmissionRequest) returns (SubmissionResult);
  rpc ListDeadlines(DeadlineQuery) returns (DeadlineList);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE report_definitions (
    id VARCHAR(100) PRIMARY KEY,
    report_name VARCHAR(255) NOT NULL,
    regulatory_body VARCHAR(100) NOT NULL, -- SEBON, NRB, CDSC
    frequency VARCHAR(30), -- DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUAL, AD_HOC
    template_schema JSONB NOT NULL,
    output_formats VARCHAR(20)[] NOT NULL,
    submission_adapter_id VARCHAR(100), -- T3 adapter ref
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE report_instances (
    id VARCHAR(100) PRIMARY KEY,
    definition_id VARCHAR(100) NOT NULL REFERENCES report_definitions(id),
    period_start DATE NOT NULL,
    period_start_bs VARCHAR(20),
    period_end DATE NOT NULL,
    period_end_bs VARCHAR(20),
    format VARCHAR(20) NOT NULL,
    status VARCHAR(30) DEFAULT 'GENERATING', -- GENERATING, GENERATED, SUBMITTING, SUBMITTED, ACKNOWLEDGED, REJECTED, AMENDED
    file_path VARCHAR(500),
    file_hash VARCHAR(128), -- SHA-512
    version INTEGER DEFAULT 1,
    amendment_of VARCHAR(100),
    generated_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    ack_status VARCHAR(30),
    ack_ref VARCHAR(100),
    ack_received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE submission_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(100) NOT NULL REFERENCES report_instances(id),
    adapter_id VARCHAR(100) NOT NULL,
    attempt_number INTEGER DEFAULT 1,
    status VARCHAR(20) NOT NULL, -- SUCCESS, FAILED, TIMEOUT
    response_code VARCHAR(20),
    response_body TEXT,
    submitted_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE reporting_deadlines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id VARCHAR(100) NOT NULL,
    period_bs VARCHAR(40) NOT NULL,
    due_date DATE NOT NULL,
    due_date_bs VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, MET, MISSED
    report_id VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now()
);
```

### 3.2 Event Schemas

```json
{ "event_type": "ReportGeneratedEvent", "data": { "report_id": "RPT-2082-11-001", "type": "SEBON_MONTHLY_TRADE_SUMMARY" } }
```
```json
{ "event_type": "ReportSubmittedEvent", "data": { "report_id": "RPT-2082-11-001", "adapter": "SEBON_PORTAL", "ack_ref": "SEBON-ACK-12345" } }
```
```json
{ "event_type": "ReportDeadlineApproachingEvent", "data": { "definition_id": "SEBON_QUARTERLY_FINANCIAL", "due_date_bs": "2082-12-15", "days_remaining": 5 } }
```

---

## 4. CONTROL FLOW

### 4.1 Report Generation & Submission
```
1. Trigger: Scheduled (cron via K-02) or manual API call
2. Load report definition and template schema
3. Aggregate data from relevant domain services (D-01..D-13, K-16)
4. Apply jurisdiction-specific formatting rules
5. Render to target format (PDF/CSV/XLSX/XBRL)
6. Compute file hash (SHA-512) for integrity
7. Store report instance with status=GENERATED
8. If auto_submit: invoke T3 regulatory portal adapter
9. Track submission status, ACK/NACK
10. Publish ReportGeneratedEvent / ReportSubmittedEvent
```

### 4.2 Deadline Monitoring
```
1. Nightly job scans reporting_deadlines
2. For each deadline with days_remaining <= threshold:
   a. If report not yet generated: emit ReportDeadlineApproachingEvent
   b. If days_remaining <= 0 and not submitted: mark MISSED, alert
```

---

## 5–10. ALGORITHMS, NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Report generation <5 min; submission <30s; 99.9% uptime
- **Security**: K-01 RBAC; report files encrypted at rest; submission via mTLS; immutable report versioning
- **Metrics**: `reporting_reports_generated`, `reporting_submission_success_rate`, `reporting_deadlines_missed`
- **Extension Points**: T3 regulatory portal adapters for new jurisdictions; custom report templates; T2 jurisdiction-specific data rules
- **Tests**: XBRL schema validation; deadline tracking accuracy; submission retry behavior; report amendment chain integrity
