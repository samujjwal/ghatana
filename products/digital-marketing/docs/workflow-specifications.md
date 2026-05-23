# Digital Marketing Workflow Specifications

## Customer/Account Management Workflow

**Purpose**: Manage customer accounts, onboarding, and lifecycle

**Steps**:
1. Account creation with tenant assignment
2. Identity verification
3. Profile configuration
4. Consent collection
5. Account activation

**Invariants**:
- Customer ID is unique within tenant
- Account status transitions are valid
- Consent is required before activation
- Audit trail for all state changes

**API Endpoints**:
- POST /api/customers - Create customer account
- GET /api/customers/{id} - Fetch customer details
- PUT /api/customers/{id} - Update customer profile
- POST /api/customers/{id}/activate - Activate account
- POST /api/customers/{id}/deactivate - Deactivate account

---

## Campaign Lifecycle Workflow

**Purpose**: Manage campaign creation, approval, execution, and closure

**Steps**:
1. Campaign draft creation
2. Budget allocation
3. Target audience definition
4. Approval workflow
5. Campaign activation
6. Execution monitoring
7. Campaign completion/closure

**Invariants**:
- Campaign budget cannot exceed allocated budget
- Campaign cannot activate without approval
- Campaign cannot be modified while active
- Campaign metrics are immutable after closure

**API Endpoints**:
- POST /api/campaigns - Create campaign draft
- GET /api/campaigns/{id} - Fetch campaign details
- PUT /api/campaigns/{id} - Update campaign (draft only)
- POST /api/campaigns/{id}/submit - Submit for approval
- POST /api/campaigns/{id}/approve - Approve campaign
- POST /api/campaigns/{id}/activate - Activate campaign
- POST /api/campaigns/{id}/pause - Pause campaign
- POST /api/campaigns/{id}/complete - Complete campaign

---

## Campaign Activation with Consent Gates

**Purpose**: Ensure campaign activation respects consent requirements

**Steps**:
1. Consent preflight check
2. Verify audience consent status
3. Validate consent scope
4. Activate campaign with consent metadata
5. Monitor consent revocation

**Invariants**:
- Campaign cannot activate without valid consent
- Consent scope must match campaign targeting
- Consent revocation triggers campaign pause
- Audit trail for consent checks

**API Endpoints**:
- POST /api/campaigns/{id}/consent-preflight - Check consent requirements
- POST /api/campaigns/{id}/activate-with-consent - Activate with consent validation

---

## Lead Capture and Conversion Tracking Workflow

**Purpose**: Capture leads and track conversion through funnel

**Steps**:
1. Lead capture from multiple sources
2. Lead qualification
3. Lead assignment
4. Conversion event tracking
5. Attribution calculation
6. Revenue attribution

**Invariants**:
- Lead source is immutable
- Conversion events are ordered by timestamp
- Attribution model is consistent
- Lead cannot be deleted if conversions exist

**API Endpoints**:
- POST /api/leads - Capture lead
- GET /api/leads/{id} - Fetch lead details
- PUT /api/leads/{id}/qualify - Qualify lead
- POST /api/leads/{id}/conversions - Record conversion
- GET /api/leads/{id}/attribution - Get attribution data

---

## Audience/Segment Management Workflow

**Purpose**: Define and manage audience segments for targeting

**Steps**:
1. Segment definition creation
2. Segment criteria validation
3. Segment population calculation
4. Segment refresh scheduling
5. Segment usage tracking

**Invariants**:
- Segment criteria are validated against available data
- Segment population is calculated deterministically
- Segment cannot be deleted if in use by active campaign
- Segment refresh respects data retention policies

**API Endpoints**:
- POST /api/segments - Create segment
- GET /api/segments/{id} - Fetch segment details
- PUT /api/segments/{id} - Update segment
- POST /api/segments/{id}/refresh - Refresh segment population
- GET /api/segments/{id}/population - Get segment population

---

## Connector Configuration and Google Ads Readiness

**Purpose**: Configure external connectors and validate Google Ads integration

**Steps**:
1. Connector registration
2. Authentication configuration
3. API validation
4. Google Ads account linking
5. Campaign sync configuration
6. Sync health monitoring

**Invariants**:
- Connector credentials are encrypted at rest
- Connector cannot be used if validation fails
- Google Ads sync respects rate limits
- Sync failures trigger alerts

**API Endpoints**:
- POST /api/connectors - Register connector
- GET /api/connectors/{id} - Fetch connector details
- PUT /api/connectors/{id} - Update connector
- POST /api/connectors/{id}/validate - Validate connector
- POST /api/connectors/{id}/google-ads/link - Link Google Ads account
- GET /api/connectors/{id}/google-ads/health - Check sync health

---

## Notification Retry/DLQ Workflow

**Purpose**: Handle notification delivery with retry and dead-letter queue

**Steps**:
1. Notification queueing
2. Delivery attempt with retry
3. Retry backoff calculation
4. DLQ routing after max retries
5. DLQ monitoring and manual replay

**Invariants**:
- Notification delivery is idempotent
- Retry attempts are logged
- DLQ messages are preserved for audit
- Manual replay requires authorization

**API Endpoints**:
- POST /api/notifications - Queue notification
- GET /api/notifications/{id}/status - Check delivery status
- POST /api/notifications/dlq/replay - Replay DLQ message
- GET /api/notifications/dlq - List DLQ messages

---

## Reporting/Dashboard Workflows

**Purpose**: Generate reports and dashboard data for analytics

**Steps**:
1. Report definition
2. Data aggregation
3. Metric calculation
4. Report generation
5. Dashboard refresh
6. Report archival

**Invariants**:
- Report data is immutable after generation
- Dashboard refresh respects rate limits
- Report archival follows retention policy
- Report access is authorized by role

**API Endpoints**:
- POST /api/reports - Generate report
- GET /api/reports/{id} - Fetch report
- GET /api/dashboard/{id} - Get dashboard data
- POST /api/dashboard/{id}/refresh - Refresh dashboard

---

## Operator/Admin Flows

**Purpose**: Administrative operations for platform management

**Steps**:
1. Tenant management
2. User management
3. Role assignment
4. System configuration
5. Audit log review
6. System health monitoring

**Invariants**:
- Admin actions require elevated privileges
- Audit log is immutable
- System configuration changes are versioned
- Tenant isolation is enforced

**API Endpoints**:
- POST /api/admin/tenants - Create tenant
- GET /api/admin/tenants/{id} - Fetch tenant details
- PUT /api/admin/tenants/{id} - Update tenant
- POST /api/admin/users - Create user
- PUT /api/admin/users/{id}/roles - Update user roles
- GET /api/admin/audit-log - Fetch audit log
- GET /api/admin/health - Get system health
