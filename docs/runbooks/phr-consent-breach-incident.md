# PHR Consent Breach Incident Runbook

**Incident Type:** Consent-Before-Access Policy Violation  
**Severity:** CRITICAL  
**Compliance Impact:** HIPAA, Nepal Directive 2081  
**SLO:** Response within 15 minutes, Resolution within 4 hours  

---

## 1. Detection

### Automated Alerts

- **Alert:** `phr.consent.access_denied_exception`
  - Counter: `phr.consent.denied_access_total` spike (> 5σ above baseline)
  - Duration: Sustained for > 5 minutes
  - Threshold: > 10 denied accesses within 5-minute window

- **Alert:** `phr.consent.missing_grant_error`
  - Log pattern: "ConsentAccessDeniedException: Access to patient record denied"
  - Count: > 3 within 1 minute across all providers

### Manual Detection

- Audit trail inspection reveals `decision=DENIED` entries followed by successful data access to the same patient record
- Patient or provider reports unauthorized data access
- Log review finds missing consent grant entries in the audit trail

---

## 2. Immediate Actions (0–5 minutes)

### Step 1: Confirm the Breach

```bash
# Query audit trail for consent violations (SQL example)
SELECT * FROM plugin_audit_entries
  WHERE entity_id LIKE 'phr.patient:%'
    AND action = 'CONSENT_CHECK'
    AND JSON_EXTRACT(details, '$.decision') = 'DENIED'
    AND timestamp > (NOW() - INTERVAL 30 MINUTE)
ORDER BY timestamp DESC;

# Check if data was accessed after denial
SELECT * FROM plugin_audit_entries
  WHERE entity_id LIKE 'phr.patient:%'
    AND action = 'PATIENT_READ'
    AND timestamp > (SELECT MAX(timestamp) 
                     FROM plugin_audit_entries 
                     WHERE action = 'CONSENT_CHECK' 
                     AND JSON_EXTRACT(details, '$.decision') = 'DENIED');
```

### Step 2: Pause Affected Operations

If the breach is **confirmed** (data accessed after consent denial):

```bash
# Suspend the provider account that triggered the breach
./scripts/incident/suspend-provider.sh <provider_id> "Incident INC-CONSENT-001"

# Disable further data writes to the affected patient's records
./scripts/incident/lock-patient-record.sh <patient_id> "Data access breach — investigation in progress"
```

### Step 3: Capture Initial Evidence

```bash
# Export audit trail for the affected time window
./scripts/incident/export-audit-trail.sh \
  --entity-id "phr.patient:<patient_id>" \
  --start-time "$(date -d '30 minutes ago' -Iseconds)" \
  --end-time "$(date -Iseconds)" \
  --output breach_evidence.json.gz

# Capture consent plugin state snapshot
./scripts/incident/export-plugin-state.sh \
  --plugin consent \
  --output consent_state_snapshot.sql.gz
```

---

## 3. Investigation (5–60 minutes)

### 3.1 Trace the Access Path

**Question:** How did the provider access data after consent denial?

```bash
# Identify the access route
cat breach_evidence.json.gz | jq '
  .[] | select(.action == "PATIENT_READ") |
  {timestamp, actor_id, actor_type, access_route, resource_id, status}
' | head -20
```

**Possible root causes:**
1. Consent service cache was stale — allowed access despite recent denial
2. Consent enforcement was bypassed (legacy code path without consent gate)
3. Provider used a different authentication context that has separate consent
4. Bug in consent check predicate logic

### 3.2 Root Cause Analysis

**For each access event after denial:**

```bash
# Verify the consent state at the time of access
./scripts/incident/verify-consent-state.sh \
  --patient-id <patient_id> \
  --provider-id <provider_id> \
  --timestamp "<access_event_timestamp>"

# Check if consent.py or ConsentService.checkAccess was called
grep -r "ConsentService.checkAccess\|ConsentEnforcementService.checkAccess" \
  logs/phr/*.log | grep "<provider_id>" | grep "<patient_id>"

# Review provider audit log for all accesses during the incident window
./scripts/incident/provider-access-audit.sh \
  --provider-id <provider_id> \
  --time-range "30min" \
  --output provider_accesses_during_incident.json
```

### 3.3 Determine Impact Scope

```bash
# Count affected patients
./scripts/incident/count-affected-patients.sh \
  --provider-id <provider_id> \
  --start-time "$(date -d '30 minutes ago' -Iseconds)"

# List affected patient IDs for notification
./scripts/incident/list-affected-patients.sh \
  --provider-id <provider_id> \
  --start-time "$(date -d '30 minutes ago' -Iseconds)" \
  --output affected_patients.txt
```

---

## 4. Remediation (60–240 minutes)

### 4.1 Fix the Root Cause

**If cache staleness:** Force refresh consent cache
```bash
./scripts/incident/invalidate-consent-cache.sh --tenant-id <tenant_id>
```

**If enforcement bypass:** Enable consent gate on the legacy code path
```bash
# Edit the code path, add ConsentEnforcementService.checkAccess() call
# Deploy hotfix
./gradlew :products:phr:build -PversionSuffix=".hotfix-consent-bypass"
./scripts/deploy/hotfix-deploy.sh phr-hotfix
```

**If consent check bug:** Deploy fix and restart consent plugin
```bash
./gradlew :platform-plugins:plugin-consent:build
# Deploy and verify
./scripts/deploy/plugin-deploy.sh consent
./scripts/incident/verify-plugin-restart.sh consent
```

### 4.2 Restore Affected Patient Records

```bash
# Unlock patient records after forensics are complete
./scripts/incident/unlock-patient-record.sh <patient_id>

# Re-enable provider access after investigation is complete and fix is deployed
./scripts/incident/restore-provider.sh <provider_id>
```

### 4.3 Notify Affected Parties

Generate notification list and send to patients/providers/compliance team:

```bash
./scripts/incident/generate-breach-notification.sh \
  --affected-patients affected_patients.txt \
  --incident-id "INC-CONSENT-001" \
  --root-cause "<root_cause_summary>" \
  --remediation-date "$(date -Iseconds)" \
  --output notifications.json

# Email template: docs/runbooks/templates/phr-breach-notification-template.txt
```

---

## 5. Verification (After deployment)

### 5.1 Confirm Consent Gate is Active

```bash
# Test consent enforcement with a denied access
./scripts/incident/test-consent-denied.sh \
  --patient-id test-patient-consent-001 \
  --provider-id test-provider-no-consent \
  --expected-result DENIED

# Should return ConsentAccessDeniedException immediately
```

### 5.2 Verify No Recurrence

```bash
# Monitor for the same pattern in the next 24 hours
./scripts/incident/monitor-consent-pattern.sh \
  --pattern "denied_access_then_read" \
  --duration 24h \
  --alert-threshold 1
```

### 5.3 Audit Trail Verification

```bash
# Confirm breach record is in audit trail
sqlite3 audit.db <<EOF
SELECT COUNT(*) FROM plugin_audit_entries
  WHERE action = 'BREACH_INVESTIGATION'
    AND JSON_EXTRACT(details, '$.incident_id') = 'INC-CONSENT-001';
EOF
```

---

## 6. Escalation

### 6.1 Escalation Triggers

Escalate to **Patient Privacy Officer + Compliance** if:
- **> 10 patients** affected
- **PHI exposure** confirmed (data in logs, external systems)
- **Repeated breach** (same root cause within 30 days)

Escalate to **Regulatory Compliance** if:
- **> 100 patients** affected
- Breach may trigger **Nepal Directive 2081** breach notification requirement
- **Legal review** is needed

### 6.2 Escalation Process

```bash
./scripts/incident/escalate.sh \
  --severity CRITICAL \
  --incident-id "INC-CONSENT-001" \
  --notify privacy-officer compliance legal \
  --template phr-breach-escalation
```

---

## 7. Communication

### 7.1 Internal Notification (0–15 min)

- **Slack**: `#security-incidents` channel post
- **Message**: Incident summary, impact scope, remediation status
- **Audience**: On-call team, incident commander, stakeholders

### 7.2 Patient Notification (Within 24 hours)

**Letter template:** `docs/runbooks/templates/phr-patient-breach-notification.txt`

**Required elements:**
- What happened (incident summary)
- What data was affected
- What they should do
- Contact for support

### 7.3 Regulatory Notification (Within 72 hours if required)

- **Nepal**: Contact Ministry of Health if > 500 patient records
- **HIPAA** (if applicable): 60-day notification to HHS
- **Internal audit**: File in compliance system

---

## 8. Post-Incident (24–72 hours)

### 8.1 Lessons Learned

```bash
./scripts/incident/post-mortem.sh \
  --incident-id "INC-CONSENT-001" \
  --facilitator <incident-commander> \
  --template docs/runbooks/templates/post-mortem-template.md
```

**Document:**
- Root cause (technical + organizational)
- Contributing factors
- Detection time
- Remediation time
- Preventive measures

### 8.2 Preventive Measures

Common fixes:
1. **Add consent enforcement at every data access point**
2. **Reduce consent cache TTL** (e.g., 5 minutes instead of 1 hour)
3. **Add consent check to test suites** (property-based tests)
4. **Improve monitoring** for consent denial patterns

### 8.3 Update Runbook

If this runbook failed to detect or respond to the breach:
- Update detection thresholds
- Update response procedures
- Add new verification steps

---

## Key Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| Incident Commander | On-call | On-call rotation | on-call@ghatana.local |
| Privacy Officer | [Person Name] | +977-... | privacy@ghatana.local |
| Compliance Manager | [Person Name] | +977-... | compliance@ghatana.local |
| Database Admin | [Person Name] | +977-... | dba@ghatana.local |

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-29  
**Next Review:** 2026-07-29
