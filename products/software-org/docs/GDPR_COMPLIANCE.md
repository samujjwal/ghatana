# Software-Org GDPR Compliance Documentation

**Version:** 1.0.0  
**Date:** 2025-11-16  
**Status:** ✅ Compliant  

---

## Table of Contents

1. Data Processing Agreement
2. Privacy Policy
3. Data Subject Rights
4. Consent Management
5. Data Protection Impact Assessment
6. Breach Notification
7. Compliance Checklist

---

## 1. Data Processing Agreement (DPA)

### Overview

This Data Processing Addendum (DPA) governs how Software-Org processes personal data on behalf of customers (data controllers).

### Parties

- **Data Controller:** Customer/Organization using Software-Org
- **Data Processor:** Ghatana (Software-Org)
- **Sub-processors:** Listed in Section 3 below

### Scope of Processing

Software-Org processes the following personal data:

| Category | Purpose | Retention | Legal Basis |
|---|---|---|---|
| User Credentials | Authentication | 90 days after deletion | Performance of contract |
| Email Addresses | Notifications | Until user deletion request | Performance of contract |
| Department Members | Team assignments | 90 days after deletion | Performance of contract |
| Activity Logs | Audit trail | 1 year | Legal obligation |
| Event Data | Process optimization | Customer-defined | Performance of contract |
| KPI Data | Metrics calculation | Customer-defined | Performance of contract |

### Data Protection Commitments

#### 1.1 Processing Instructions

Ghatana will only process personal data:
- As instructed by the data controller
- To provide the Software-Org service
- As required by law (law enforcement requests)

#### 1.2 Data Security

Ghatana implements:
- Encryption at rest (AES-256 GCM)
- Encryption in transit (TLS 1.3)
- Access controls (RBAC + ABAC)
- Audit logging
- Regular security assessments
- Incident response procedures

#### 1.3 Sub-processor Management

Sub-processors used:
- PostgreSQL (database) - Data storage
- Kafka/EventCloud (event streaming) - Event processing
- AWS S3 (backups) - Data backup
- Prometheus (metrics) - Performance monitoring

**Change Process:**
- 30-day notice before adding sub-processor
- Right to object to new sub-processor
- Equivalent data protection with each sub-processor

#### 1.4 Data Subject Rights

Ghatana enables the controller to fulfill data subject requests:

| Right | Implementation | Timeline |
|---|---|---|
| Access | REST API `/api/v1/data-export` | 15 days |
| Rectification | PATCH endpoints for mutable fields | 15 days |
| Erasure (Right to be Forgotten) | DELETE endpoints + cascade delete | 15 days |
| Restriction of Processing | Archive flag (stop processing, keep data) | 15 days |
| Portability | JSON/CSV export via `/api/v1/data-export` | 15 days |
| Objection | Opt-out mechanisms + preference APIs | 15 days |

#### 1.5 Audit Rights

Ghatana provides:
- Access to audit logs via dashboard
- Annual audit report
- Security certifications (SOC 2, ISO 27001 planned)
- Right to conduct audits (30-day notice)

#### 1.6 Data Transfer

**EU-US Transfers:** Using Standard Contractual Clauses (SCCs)
**Non-EU Locations:** Transfers only with adequate safeguards

#### 1.7 Return or Deletion of Data

Upon termination:
- Data returned or deleted within 30 days (or as required)
- Backups retained for 90 days
- Verification of deletion provided

#### 1.8 Liability

- Ghatana liable for processor violations
- Capped at 12 months of service fees
- Annual review of data processing terms

---

## 2. Privacy Policy

### 2.1 Information We Collect

**Directly from Users:**
- Name, email, organization
- Account creation date and preferences
- Login history and activity logs

**Automatically:**
- IP address and user agent
- Event data (timestamps, actions taken)
- Performance metrics (page load times)
- Cookies for session management

**From Third Parties:**
- Single Sign-On (SSO) providers
- Email providers

### 2.2 How We Use Your Data

| Purpose | Legal Basis | Retention |
|---|---|---|
| Provide the service | Contract | Duration of service + 90 days |
| Improve product | Legitimate interest | Anonymized after 6 months |
| Security & fraud prevention | Legal obligation | 2 years |
| Comply with law | Legal obligation | As required by law |
| Send notifications | Consent | Until revoked |
| Marketing (opt-in) | Consent | Until revoked |

### 2.3 Data Sharing

We share data with:
- **Sub-processors:** Listed in DPA section
- **Law enforcement:** With valid legal process
- **Successors:** In case of acquisition

We do NOT sell data to third parties.

### 2.4 Data Subject Rights

All subjects have the right to:
1. **Access:** Know what data we hold
2. **Rectification:** Correct inaccurate data
3. **Erasure:** Delete personal data (with exceptions)
4. **Restriction:** Pause processing
5. **Portability:** Export data
6. **Objection:** Opt-out of processing
7. **Automated Decision-Making:** Not be subjected to automated decisions with legal effect

**To exercise rights:** Submit request to privacy@ghatana.com

### 2.5 Retention Periods

| Data Type | Retention Period |
|---|---|
| Account data | Until deletion request or 2 years inactivity |
| Activity logs | 1 year (or longer if required) |
| Support tickets | 2 years |
| Marketing communications | Until unsubscribe |
| Cookies | Session (or 12 months for persistent) |
| Backups | 90 days after deletion |

### 2.6 Security

We implement:
- Encryption (AES-256, TLS 1.3)
- Access controls (RBAC, ABAC)
- Authentication (OAuth 2.1, MFA)
- Audit logging
- Regular security audits
- Incident response plans

### 2.7 International Transfers

**EU/EEA residents:** Data transferred under Standard Contractual Clauses  
**UK residents:** Data transferred under Adequacy Decisions  
**Other locations:** Consent or other appropriate mechanism

### 2.8 Contact Information

- **Privacy Officer:** privacy@ghatana.com
- **Data Protection Authority:** See European Data Protection Board website
- **Response time:** 15 days

---

## 3. Data Subject Rights Implementation

### 3.1 Right of Access (Article 15)

**Endpoint:** `GET /api/v1/data-subject/export`

```bash
curl -X GET https://api.software-org/api/v1/data-subject/export \
  -H "Authorization: Bearer token" \
  -H "X-Tenant-Id: tenant-123"
```

**Response:** JSON with all personal data stored

```json
{
  "user": {
    "id": "user-123",
    "name": "John Doe",
    "email": "john@example.com",
    "created_at": "2025-01-15"
  },
  "activity": [
    {
      "action": "CREATE_FEATURE",
      "timestamp": "2025-11-16T10:30:00Z",
      "details": {...}
    }
  ],
  "preferences": {...}
}
```

### 3.2 Right of Rectification (Article 16)

**Endpoints:**
```bash
PATCH /api/v1/users/{id}
PATCH /api/v1/organizations/{id}
PATCH /api/v1/user-preferences/{id}
```

**Example:**
```bash
curl -X PATCH https://api.software-org/api/v1/users/user-123 \
  -H "Authorization: Bearer token" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newemail@example.com",
    "name": "Jane Doe"
  }'
```

**Result:** Data updated immediately, audit log created

### 3.3 Right of Erasure (Article 17)

**Endpoint:** `DELETE /api/v1/data-subject/me`

```bash
curl -X DELETE https://api.software-org/api/v1/data-subject/me \
  -H "Authorization: Bearer token" \
  -H "X-Tenant-Id: tenant-123"
```

**Process:**
1. Soft delete user (retention_flag = true, deleted_at = now)
2. Mark all related records for retention policy
3. Cascade delete non-essential data (preferences, activity logs)
4. Retain legally required data (audit logs, financial records)
5. Audit log entry created
6. Email confirmation sent to last known address

**Exceptions** (data retained):
- 1 year of activity logs (legal obligation)
- Financial transaction records
- Law enforcement holds

### 3.4 Right to Restriction (Article 18)

**Endpoint:** `PUT /api/v1/data-subject/restriction`

```bash
curl -X PUT https://api.software-org/api/v1/data-subject/restriction \
  -H "Authorization: Bearer token" \
  -d '{"restricted": true}'
```

**Effect:**
- Data retained but not processed
- Account frozen (no new operations)
- Existing features continue read-only
- Email notification sent

### 3.5 Right to Data Portability (Article 20)

**Endpoint:** `GET /api/v1/data-subject/export?format=csv`

**Supported Formats:** JSON, CSV, XML

```bash
curl -X GET 'https://api.software-org/api/v1/data-subject/export?format=csv' \
  -H "Authorization: Bearer token" \
  > my_data.csv
```

### 3.6 Right to Object (Article 21)

**Endpoint:** `PUT /api/v1/data-subject/preferences/marketing`

```bash
curl -X PUT https://api.software-org/api/v1/data-subject/preferences/marketing \
  -H "Authorization: Bearer token" \
  -d '{"allow_marketing": false}'
```

### 3.7 Right to Human Review (Article 22)

Software-Org does not use automated decision-making with legal effect.

All decisions affecting users (rate limits, feature access) are:
- Policy-based (no ML)
- Human-reviewable
- Subject to appeal

---

## 4. Consent Management

### 4.1 Consent Banner

**Implementation:** Modal on first login + preferences page

```javascript
// Consent categories
const consentCategories = {
  essential: { required: true, label: "Essential (session management)" },
  marketing: { required: false, label: "Marketing (emails, promotions)" },
  analytics: { required: false, label: "Analytics (usage metrics)" },
  cookies: { required: false, label: "Cookies (preferences)" }
};
```

### 4.2 Consent Preferences

**Endpoint:** `GET/PUT /api/v1/user-preferences/consent`

```bash
curl -X GET https://api.software-org/api/v1/user-preferences/consent \
  -H "Authorization: Bearer token"
```

```json
{
  "consent_version": "1.0",
  "given_at": "2025-11-16T10:00:00Z",
  "preferences": {
    "marketing_emails": false,
    "analytics": true,
    "cookies": true
  },
  "ip_address": "192.168.1.100",
  "user_agent": "Mozilla/5.0..."
}
```

### 4.3 Withdrawal of Consent

**Endpoint:** `DELETE /api/v1/user-preferences/consent/{category}`

```bash
curl -X DELETE https://api.software-org/api/v1/user-preferences/consent/marketing \
  -H "Authorization: Bearer token"
```

---

## 5. Data Protection Impact Assessment (DPIA)

### 5.1 Processing Activities Assessed

| Activity | Risk Level | Mitigation |
|---|---|---|
| User authentication | LOW | Encryption + MFA |
| Event processing | MEDIUM | Tenant isolation + encryption |
| Analytics | LOW | Anonymization after 6 months |
| Audit logging | LOW | Access controls + retention policies |
| Data export | MEDIUM | Consent + rate limiting |

### 5.2 Risk Assessment

**Identified Risks:**
1. **Unauthorized access to tenant data** (Medium)
   - Mitigation: RBAC + encryption + audit logging
   
2. **Data breach during transit** (Low)
   - Mitigation: TLS 1.3 + certificate pinning
   
3. **Excessive data retention** (Low)
   - Mitigation: Automated cleanup + retention policies

**Residual Risk:** LOW

### 5.3 DPIA Conclusion

Processing activities are compliant with GDPR with adequate safeguards in place.

---

## 6. Breach Notification

### 6.1 Detection & Response

**If breach suspected:**

1. **Immediate (< 1 hour):**
   - Isolate affected systems
   - Halt data processing
   - Activate incident response team

2. **Assessment (< 2 hours):**
   - Determine scope and severity
   - Identify affected data subjects
   - Document evidence

3. **Notification (< 72 hours to authorities):**
   - Notify supervisory authority (if risk to rights/freedoms)
   - Notify affected data subjects (if high risk)
   - Provide breach details

### 6.2 Notification Template

**To Data Subjects:**

```
Subject: Important Security Notice - Data Breach

Dear [Name],

We are writing to inform you that we discovered a security incident 
affecting your personal data.

INCIDENT DETAILS:
- Date discovered: [DATE]
- Data affected: [TYPES]
- Number of subjects: [COUNT]
- Risk level: [MEDIUM|HIGH]

ACTIONS TAKEN:
- Incident contained and investigated
- Breach reported to authorities
- Security measures enhanced

RECOMMENDED ACTIONS:
- Change your password
- Enable two-factor authentication
- Monitor for suspicious activity

For more information, contact: privacy@ghatana.com

Regards,
Data Protection Officer
```

### 6.3 Documentation

All breaches documented in:
- Incident log (non-public)
- Audit trail (access controlled)
- Data Protection Register (public summary)

---

## 7. Compliance Checklist

### Legal Framework

- [x] GDPR Articles 1-99 reviewed
- [x] GDPR recitals 1-173 considered
- [x] ePrivacy Directive compliance
- [x] National law variations (GDPR only)

### Data Processing

- [x] Lawful basis documented for each processing
- [x] Purposes clearly defined
- [x] Data minimization practiced
- [x] Retention periods defined
- [x] Processing agreements in place

### Technical & Organizational Measures

- [x] Encryption at rest (AES-256)
- [x] Encryption in transit (TLS 1.3)
- [x] Access controls (RBAC + ABAC)
- [x] Audit logging
- [x] Incident response plan
- [x] Breach notification procedures
- [x] Regular security assessments

### Data Subject Rights

- [x] Right of access implemented (API endpoint)
- [x] Right of rectification implemented (PATCH endpoints)
- [x] Right of erasure implemented (DELETE endpoint)
- [x] Right to restriction implemented
- [x] Right to portability implemented (export endpoint)
- [x] Right to object implemented (preferences API)
- [x] Right to human review ensured (no automated decisions)

### Documentation

- [x] Privacy policy published
- [x] Data Processing Agreement in place
- [x] DPIA conducted
- [x] Processing register maintained
- [x] Breach notification procedures documented
- [x] Staff trained on GDPR

### Accountability

- [x] Data Protection Officer appointed (reporting to privacy@ghatana.com)
- [x] Privacy by design implemented
- [x] Vendor compliance verified
- [x] Regular compliance audits scheduled

---

## 8. Contact Information

**Data Protection Officer:**
- Email: privacy@ghatana.com
- Phone: +1-555-DATA-PRV (internal escalation)
- Response SLA: 15 days

**Supervisory Authority (EU):**
- European Data Protection Board: https://edpb.ec.europa.eu/
- National Data Protection Authorities: https://edpb.ec.europa.eu/about-edpb/board/members_en

**Data Subject Rights Portal:**
- Access, export, delete: https://software-org.ghatana.com/api/v1/data-subject
- Preferences & consent: https://software-org.ghatana.com/settings/privacy
- Complaint form: https://ghatana.com/privacy/complaint

---

**Last Reviewed:** 2025-11-16  
**Next Review:** 2026-05-16 (6-month cycle)  
**Status:** ✅ **COMPLIANT**
