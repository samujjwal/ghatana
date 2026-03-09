# 8. Security Dashboard – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 8. `/security` – Security & Compliance Dashboard](../WEB_PAGE_FEATURE_INVENTORY.md#8-security--security--compliance-dashboard)

**Code file:**

- `src/features/security/pages/SecurityDashboard.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Centralize visibility into access control, audit logs, and compliance status so security and compliance teams can manage risk from one place.

**Primary goals:**

- Show **who can do what** (user access and RBAC matrix).
- Show **what happened** (audit events with filtering).
- Show **where we stand** (compliance status for SOC2/GDPR/HIPAA etc.).

**Non-goals:**

- Editing low-level security policies directly in this view (may be separate management area).
- Performing detailed incident response (that’s shared with HITL/Real-Time Monitor).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Security lead / CISO.**
- **Compliance officer.**
- **Platform/security engineer** managing API keys and access.

**Scenarios:**

1. **Checking if access is appropriate**
   - GIVEN: A compliance review is underway.
   - WHEN: Security lead opens `Access Control` tab.
   - THEN: They can see users/roles and quickly identify privileged accounts.

2. **Investigating suspicious activity**
   - GIVEN: An anomaly was detected.
   - WHEN: Analyst opens `Audit Log` tab and filters by user, action, or time.
   - THEN: They see exactly what actions were taken and by whom.

3. **Verifying compliance posture**
   - GIVEN: External auditor asks about SOC2/GDPR.
   - WHEN: Compliance officer opens `Compliance` tab.
   - THEN: They see high-level statuses and evidence pointers.

---

## 3. Content & Layout Overview

From `SecurityDashboard.tsx`:

- **Header:**
  - Title: `Security & Compliance`.
  - Subtitle: `Central view of access, audit events and compliance status`.
  - Status strip with:
    - Green dot + `All systems secure`.
    - Active Users count.
    - Events (24h) count.

- **Tabs:**
  - `Access Control` (UserAccessPanel).
  - `Audit Log` (AuditLog).
  - `Compliance` (ComplianceStatus).

- **Content area:**
  - Renders corresponding panel for active tab.

- **Footer actions:**
  - `Manage API Keys`.
  - `User Management`.
  - `Export Audit Log`.

---

## 4. UX Requirements – User-Friendly and Valuable

- **At-a-glance status:**
  - Header should convey if there’s a major security concern.
- **Tab clarity:**
  - Users must understand which part of security they’re looking at (access vs audit vs compliance).
- **Footer actions:**
  - High-value actions (API keys, user mgmt, audit export) are always visible and easy to reach.

---

## 5. Completeness and Real-World Coverage

Security Dashboard must support:

- Reviewing who has which level of access.
- Checking recent security-sensitive actions in the audit log.
- Viewing whether major compliance frameworks are in good standing.

Over time it can link to:

- Real-Time Monitor for live incidents.
- HITL Console for security-specific actions.
- Reports for security findings.

---

## 6. Modern UI/UX Nuances and Features

- Dark security-themed palette.
- Clear tab active state with icons (🔐, 📋, ✓).
- Scrollable content area with sticky header and footer actions.
- Export action that can show a confirmation or toast.

---

## 7. Coherence and Consistency Across the App

- Compliance statuses and terminology must match Reporting and Help docs.
- User access model aligns with Settings → Account / Integrations.
- API key management must match the patterns in other admin areas.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#8-security--security--compliance-dashboard`
- Implementation: `src/features/security/pages/SecurityDashboard.tsx`
- Sub-components:
  - `UserAccessPanel`: `src/features/security/components/UserAccessPanel.tsx`
  - `AuditLog`: `src/features/security/components/AuditLog.tsx`
  - `ComplianceStatus`: `src/features/security/components/ComplianceStatus.tsx`

---

## 9. Open Gaps & Enhancement Plan

- Integrate real RBAC and identity provider data.
- Hook audit log to actual security events.
- Provide explanations/tooltips for each compliance indicator.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Security & Compliance
Subtitle: Central view of access, audit events and compliance status
[● All systems secure]  Active Users: 12   Events (24h): 342

[Tabs]
[ 🔐 Access Control ]  [ 📋 Audit Log ]  [ ✓ Compliance ]

[Active Tab Content]

[Footer Actions]
[ Manage API Keys ]  [ User Management ]                 [ Export Audit Log ]
```

### 10.2 Example Header Values

- `All systems secure` (green dot pulsing).
- `Active Users: 12`.
- `Events (24h): 342`.

### 10.3 Conceptual Tab Content (High-Level)

- **Access Control (UserAccessPanel):**
  - Table of users and roles, e.g.:
    - `alice@company.com` – `Admin` – `Last login: 2h ago`.
    - `bob@company.com` – `Read-only` – `Last login: 1d ago`.

- **Audit Log (AuditLog):**
  - Filter bar (user, action, resource, time).
  - Entries like:
    - `2025-11-20 10:12:33  alice  CREATED API key  prod-readonly  SUCCESS`.
    - `2025-11-20 09:53:10  bob    UPDATED role     payments-team  SUCCESS`.

- **Compliance (ComplianceStatus):**
  - Cards for frameworks:
    - `SOC 2` – `Compliant` – `Last audit: 2025-10-15`.
    - `GDPR` – `Compliant with minor findings`.
    - `HIPAA` – `In progress`.

This mockup sets expectations for content and layout while leaving implementation details to the sub-components.
