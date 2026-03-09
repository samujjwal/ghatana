# Software Org REST API Reference

Complete REST API reference for software-org service. All endpoints authenticated via X-Tenant-Id header.

## Base URL

```
http://localhost:8080/api/v1
```

## Authentication

All requests require:

```
X-Tenant-Id: {tenantId}
X-Correlation-Id: {correlationId}  # Auto-generated if not provided
Content-Type: application/json
```

## Health Check

### Get Service Health

```http
GET /health
```

**Response:**

```json
{
  "status": "UP",
  "service": "software-org",
  "version": "1.0.0",
  "departments": 10,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Engineering Events

Base path: `/engineering/events`

### Create Feature Request

```http
POST /engineering/events/feature-request
```

**Request:**

```json
{
  "title": "Dark mode support",
  "description": "User-requested feature",
  "priority": "HIGH"
}
```

**Response:** `200 OK`

```json
{
  "featureId": "feat-12345",
  "status": "REQUESTED",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Record Commit

```http
POST /engineering/events/commit
```

**Request:**

```json
{
  "commitHash": "abc123def456",
  "authorEmail": "alice@example.com",
  "message": "Add dark mode toggle"
}
```

**Response:** `200 OK`

### Record Build Result

```http
POST /engineering/events/build
```

**Request:**

```json
{
  "buildId": "build-789",
  "status": "SUCCESS",
  "durationMs": 120000
}
```

**Response:** `200 OK`

### Evaluate Quality Gate

```http
POST /engineering/events/quality-gate
```

**Request:**

```json
{
  "buildId": "build-789",
  "passed": true,
  "coverage": 87.5,
  "testsRun": 250,
  "testsPassed": 248
}
```

**Response:** `200 OK`

### Record Code Review

```http
POST /engineering/events/code-review
```

**Request:**

```json
{
  "reviewId": "review-123",
  "pullRequestId": "pr-456",
  "reviewerEmail": "bob@example.com",
  "approved": true
}
```

**Response:** `200 OK`

---

## QA Events

Base path: `/qa/events`

### Start Test Suite

```http
POST /qa/events/test-suite/start
```

**Request:**

```json
{
  "suiteId": "suite-123",
  "suiteName": "End-to-End Tests",
  "testCount": 150
}
```

**Response:** `200 OK`

### Complete Test Suite

```http
POST /qa/events/test-suite/complete
```

**Request:**

```json
{
  "suiteId": "suite-123",
  "totalTests": 150,
  "passedTests": 148,
  "failedTests": 2,
  "durationMs": 45000
}
```

**Response:** `200 OK`

### Report Code Coverage

```http
POST /qa/events/coverage
```

**Request:**

```json
{
  "buildId": "build-789",
  "overallCoverage": 87.5,
  "lineCoverage": 89.2,
  "branchCoverage": 82.1
}
```

**Response:** `200 OK`

### Report Bug

```http
POST /qa/events/bug
```

**Request:**

```json
{
  "bugId": "bug-456",
  "severity": "HIGH",
  "description": "Login page crashes on Firefox",
  "component": "auth"
}
```

**Response:** `200 OK`

### Record Performance Metrics

```http
POST /qa/events/performance
```

**Request:**

```json
{
  "testName": "API Response Time",
  "responseTimeMs": 250,
  "p95TimeMs": 450,
  "p99TimeMs": 650,
  "throughputRps": 1000
}
```

**Response:** `200 OK`

---

## DevOps Events

Base path: `/devops/events`

### Start Deployment

```http
POST /devops/events/deployment/start
```

**Request:**

```json
{
  "environment": "production",
  "version": "v1.2.3",
  "deploymentId": "dep-789"
}
```

**Response:** `200 OK`

```json
{
  "deploymentId": "dep-789",
  "status": "IN_PROGRESS",
  "startedAt": "2024-01-15T10:30:00Z"
}
```

### Complete Deployment

```http
POST /devops/events/deployment/complete
```

**Request:**

```json
{
  "deploymentId": "dep-789",
  "succeeded": true,
  "durationMs": 300000
}
```

**Response:** `200 OK`

### Report Incident

```http
POST /devops/events/incident
```

**Request:**

```json
{
  "incidentId": "inc-123",
  "severity": "CRITICAL",
  "component": "database",
  "description": "Database connection pool exhausted"
}
```

**Response:** `200 OK`

### Resolve Incident

```http
POST /devops/events/incident/resolve
```

**Request:**

```json
{
  "incidentId": "inc-123",
  "resolutionTime": 1800000
}
```

**Response:** `200 OK`

### Record Configuration Change

```http
POST /devops/events/config-change
```

**Request:**

```json
{
  "changeId": "ch-456",
  "service": "api-gateway",
  "changeType": "SCALING",
  "details": "Increased replica count to 5"
}
```

**Response:** `200 OK`

### Report Health Check

```http
POST /devops/events/health
```

**Request:**

```json
{
  "service": "api-gateway",
  "healthy": true,
  "cpuUsage": 45.2,
  "memoryUsage": 62.1,
  "uptime": 864000
}
```

**Response:** `200 OK`

---

## Support Events

Base path: `/support/events`

### Create Support Ticket

```http
POST /support/events/ticket
```

**Request:**

```json
{
  "subject": "Cannot log in",
  "description": "Getting 500 error on login",
  "priority": "HIGH",
  "customerId": "cust-123"
}
```

**Response:** `200 OK`

```json
{
  "ticketId": "ticket-456",
  "status": "OPEN",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Update Ticket Status

```http
POST /support/events/ticket/status
```

**Request:**

```json
{
  "ticketId": "ticket-456",
  "status": "RESOLVED"
}
```

**Response:** `200 OK`

### Record Feedback

```http
POST /support/events/feedback
```

**Request:**

```json
{
  "ticketId": "ticket-456",
  "rating": 5,
  "feedback": "Issue resolved quickly!"
}
```

**Response:** `200 OK`

### Record SLA Metric

```http
POST /support/events/sla
```

**Request:**

```json
{
  "ticketId": "ticket-456",
  "responseTime": 1800,
  "resolutionTime": 3600,
  "met": true
}
```

**Response:** `200 OK`

---

## Sales Events

Base path: `/sales/events`

### Create Opportunity

```http
POST /sales/events/opportunity
```

**Request:**

```json
{
  "accountName": "ACME Corporation",
  "estimatedValue": 250000,
  "stage": "PROSPECT"
}
```

**Response:** `200 OK`

```json
{
  "opportunityId": "opp-123",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Send Quote

```http
POST /sales/events/quote
```

**Request:**

```json
{
  "quoteId": "quote-456",
  "opportunityId": "opp-123",
  "amount": 150000
}
```

**Response:** `200 OK`

### Sign Contract

```http
POST /sales/events/contract
```

**Request:**

```json
{
  "contractId": "cont-789",
  "opportunityId": "opp-123",
  "amount": 250000
}
```

**Response:** `200 OK`

### Record Revenue

```http
POST /sales/events/revenue
```

**Request:**

```json
{
  "contractId": "cont-789",
  "amount": 250000,
  "currency": "USD",
  "recognitionDate": "2024-01-15"
}
```

**Response:** `200 OK`

---

## Marketing Events

Base path: `/marketing/events`

### Launch Campaign

```http
POST /marketing/events/campaign/launch
```

**Request:**

```json
{
  "campaignId": "camp-123",
  "campaignName": "Q1 Product Launch",
  "channel": "EMAIL",
  "targetAudience": "Enterprise customers"
}
```

**Response:** `200 OK`

### Record Lead

```http
POST /marketing/events/lead
```

**Request:**

```json
{
  "leadId": "lead-456",
  "campaignId": "camp-123",
  "email": "prospect@example.com",
  "source": "email"
}
```

**Response:** `200 OK`

### Record Engagement

```http
POST /marketing/events/engagement
```

**Request:**

```json
{
  "engagementId": "eng-789",
  "leadId": "lead-456",
  "type": "EMAIL_OPEN"
}
```

**Response:** `200 OK`

### Record Campaign Metrics

```http
POST /marketing/events/campaign/metrics
```

**Request:**

```json
{
  "campaignId": "camp-123",
  "impressions": 50000,
  "clicks": 2500,
  "conversions": 500,
  "spendAmount": 10000
}
```

**Response:** `200 OK`

---

## Product Events

Base path: `/product/events`

### Create Feature Request

```http
POST /product/events/feature
```

**Request:**

```json
{
  "title": "Export to PDF",
  "description": "Allow users to export reports",
  "priority": "HIGH"
}
```

**Response:** `200 OK`

```json
{
  "featureId": "feat-123",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Record Feedback

```http
POST /product/events/feedback
```

**Request:**

```json
{
  "feedbackId": "fb-456",
  "sentiment": "POSITIVE",
  "category": "UX",
  "message": "Dashboard is intuitive"
}
```

**Response:** `200 OK`

### Record Experiment Result

```http
POST /product/events/experiment
```

**Request:**

```json
{
  "experimentId": "exp-789",
  "featureFlag": "new_checkout",
  "controlConversion": 2.5,
  "treatmentConversion": 3.2,
  "winner": "TREATMENT"
}
```

**Response:** `200 OK`

---

## Finance Events

Base path: `/finance/events`

### Allocate Budget

```http
POST /finance/events/budget
```

**Request:**

```json
{
  "budgetId": "bud-123",
  "department": "engineering",
  "amount": 500000,
  "period": "QUARTERLY"
}
```

**Response:** `200 OK`

### Record Expense

```http
POST /finance/events/expense
```

**Request:**

```json
{
  "expenseId": "exp-456",
  "amount": 5000,
  "category": "TRAVEL",
  "description": "Team conference"
}
```

**Response:** `200 OK`

### Record Forecast

```http
POST /finance/events/forecast
```

**Request:**

```json
{
  "forecastId": "fc-789",
  "month": "2024-02",
  "revenue": 500000,
  "expenses": 350000
}
```

**Response:** `200 OK`

---

## HR Events

Base path: `/hr/events`

### Record Onboarding

```http
POST /hr/events/onboarding
```

**Request:**

```json
{
  "employeeId": "emp-123",
  "name": "Alice Johnson",
  "role": "Senior Engineer",
  "department": "engineering"
}
```

**Response:** `200 OK`

### Record Performance Review

```http
POST /hr/events/review
```

**Request:**

```json
{
  "reviewId": "rev-456",
  "employeeId": "emp-123",
  "rating": 4,
  "feedback": "Excellent contributor"
}
```

**Response:** `200 OK`

### Record Training Completion

```http
POST /hr/events/training
```

**Request:**

```json
{
  "trainingId": "train-789",
  "employeeId": "emp-123",
  "courseTitle": "Advanced TypeScript",
  "hours": 16
}
```

**Response:** `200 OK`

### Record Compensation

```http
POST /hr/events/compensation
```

**Request:**

```json
{
  "employeeId": "emp-123",
  "baseSalary": 150000,
  "bonus": 30000,
  "equity": 50000
}
```

**Response:** `200 OK`

### Record Exit

```http
POST /hr/events/exit
```

**Request:**

```json
{
  "employeeId": "emp-123",
  "exitDate": "2024-02-15",
  "reason": "RELOCATION"
}
```

**Response:** `200 OK`

---

## Compliance Events

Base path: `/compliance/events`

### Initiate Audit

```http
POST /compliance/events/audit
```

**Request:**

```json
{
  "auditId": "aud-123",
  "type": "INTERNAL",
  "standard": "SOC2"
}
```

**Response:** `200 OK`

### Report Policy Violation

```http
POST /compliance/events/violation
```

**Request:**

```json
{
  "violationId": "vio-456",
  "policyName": "Data Protection",
  "severity": "HIGH",
  "violatingEntity": "user-123"
}
```

**Response:** `200 OK`

### Record Data Protection Action

```http
POST /compliance/events/data-protection
```

**Request:**

```json
{
  "actionId": "act-789",
  "actionType": "DATA_DELETION",
  "userId": "user-123",
  "reason": "GDPR_REQUEST"
}
```

**Response:** `200 OK`

### Record Risk Assessment

```http
POST /compliance/events/risk
```

**Request:**

```json
{
  "riskId": "risk-012",
  "category": "SECURITY",
  "severity": "MEDIUM",
  "mitigation": "Implement MFA"
}
```

**Response:** `200 OK`

### Record Compliance Training

```http
POST /compliance/events/training
```

**Request:**

```json
{
  "trainingId": "ct-345",
  "employeeId": "emp-123",
  "course": "GDPR Compliance",
  "completed": true
}
```

**Response:** `200 OK`

---

## KPI Dashboard

### Get Aggregated KPIs

```http
GET /kpis
```

**Response:** `200 OK`

```json
{
  "delivery": {
    "features_shipped": 42,
    "average_cycle_time_hours": 5.2,
    "average_lead_time_hours": 12.3,
    "deployments_last_quarter": 16
  },
  "quality": {
    "average_coverage_percent": 87.5,
    "bugs_reported": 12,
    "quality_gate_pass_rate": 95,
    "test_suites_run": 248
  },
  "operations": {
    "deployments_per_month": 8,
    "average_mttr_minutes": 23.4,
    "infrastructure_health_score": 98.5,
    "incidents_resolved": 5
  },
  "team": {
    "team_satisfaction_score": 4.2,
    "employees_onboarded": 5,
    "training_hours_completed": 120,
    "attrition_rate_percent": 2.1
  },
  "business": {
    "monthly_recurring_revenue": 245000.5,
    "customer_satisfaction_nps": 72.5,
    "active_customers": 42,
    "churn_rate_percent": 1.2
  },
  "last_updated": "2024-01-15T10:30:00Z"
}
```

---

## Error Responses

### 400 Bad Request

```json
{
  "error": "INVALID_REQUEST",
  "message": "Missing required field: priority",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 401 Unauthorized

```json
{
  "error": "MISSING_TENANT_ID",
  "message": "X-Tenant-Id header is required",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 500 Internal Server Error

```json
{
  "error": "SERVER_ERROR",
  "message": "Failed to publish event",
  "correlationId": "corr-123",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Rate Limiting

- **Requests per minute**: 1000
- **Requests per hour**: 50000
- **Max payload size**: 10 MB

Rate limit headers:

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642247400
```

---

## Webhooks

KPI updates are published as events to EventCloud with type `organization.kpi.updated`. Subscribe to these events for real-time dashboard updates:

```
department: "organization"
event_type: "kpi.updated"
payload: {
  kpis: { /* full KPI dashboard */ },
  updated_categories: ["delivery", "quality"]
}
```
