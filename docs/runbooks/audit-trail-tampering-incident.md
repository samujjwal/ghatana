# Audit Trail Tampering Incident Runbook

**Incident Type:** Audit Trail Integrity Compromise  
**Severity:** CRITICAL  
**Compliance Impact:** HIPAA, SOX, Nepal Directive 2081  
**SLO:** Response within 10 minutes, Resolution within 4 hours  

---

## 1. Detection

### Automated Alerts

- **Alert:** `audit.trail.integrity_violation_detected`
  - Trigger: Hash chain verification fails (entry.hash ≠ HMAC(entry.data + previous_hash))
  - Frequency: Verified on every audit entry read (< 1ms latency)
  - Threshold: Any single failed verification triggers alert

- **Alert:** `audit.trail.backward_link_corrupted`
  - Trigger: `previous_hash` field doesn't match actual previous entry hash
  - Frequency: Checked every 10 minutes for random samples
  - Sample size: 1% of entries

- **Alert:** `audit.trail.missing_entry_in_sequence`
  - Trigger: Entry IDs are non-contiguous (gap detection)
  - Frequency: Checked every 5 minutes
  - Threshold: Any gap > 1 indicates tampering

### Manual Detection

- Security team runs audit trail integrity audit
- Forensic analysis reveals hash mismatches in query results
- Suspicious database access detected in database logs

---

## 2. Immediate Actions (0–10 minutes)

### Step 1: Verify the Tampering

```bash
# Run integrity verification immediately
./scripts/incident/audit-trail-integrity-check.sh \
  --full-scan true \
  --output tampering_evidence.json

# Check for failed verifications
jq '.failures[] | {entry_id, expected_hash, actual_hash, corruption_type}' tampering_evidence.json
```

**If > 0 failures:** Tampering is confirmed.

### Step 2: Lock the Audit Trail

Prevent further reads to avoid propagating corrupted data:

```bash
./scripts/incident/lock-audit-trail.sh \
  --reason "Tampering detected — forensic investigation in progress" \
  --incident-id "INC-AUDIT-001"

# Verify lock status
curl http://localhost:8081/audit/status
# Expected: "state": "LOCKED_FORENSIC"
```

### Step 3: Preserve Evidence

```bash
# Create forensic snapshot before any repairs
./scripts/incident/export-audit-trail-forensic.sh \
  --include-system-tables true \
  --include-database-logs true \
  --output audit_forensic_snapshot_$(date +%s).tar.gz

# This includes:
# - Complete audit table dump
# - Database transaction logs
# - OS-level file access logs (if available)
# - Hash verification results
```

### Step 4: Engage Incident Commander

```bash
./scripts/incident/declare-incident.sh \
  --type "AUDIT_TAMPERING" \
  --severity "CRITICAL" \
  --incident-id "INC-AUDIT-001"
```

---

## 3. Investigation (10–120 minutes)

### 3.1 Identify Tampering Window

**Question:** What entries were corrupted? When did tampering occur?

```bash
# Find all entries with hash failures
sqlite3 audit.db <<EOF
SELECT 
  id, entry_id, entity_id, action, timestamp, 
  hash, previous_hash, 
  'HASH_VERIFICATION_FAILED' as failure_type
FROM plugin_audit_entries
WHERE id IN (
  SELECT entry_id FROM audit_failures WHERE failure_type = 'HASH_MISMATCH'
)
ORDER BY timestamp DESC;
EOF
```

**Identify the earliest corrupted entry:**
- This entry or its predecessor may be where tampering began

### 3.2 Identify Corruption Type

```bash
# For each corrupted entry, determine the corruption method
jq '.failures[] | 
  select(.corruption_type == "HASH_MISMATCH") |
  {
    entry_id,
    field_modified: (
      .original_hash != .current_hash ? "HASH" : "OTHER"
    ),
    previous_link_valid: (.previous_hash_valid),
    data_integrity: (.data_field_matches_hash)
  }' tampering_evidence.json
```

**Possible corruption scenarios:**
1. **Direct entry modification** — Attacker modified entry data, updated hash
2. **Previous link break** — Attacker corrupted back-link, breaking chain verification
3. **Hash collision attack** — Attacker crafted new data with matching hash (unlikely with HMAC-SHA256)

### 3.3 Identify Attack Vector

```bash
# Check database access logs for suspicious activity
./scripts/incident/analyze-database-access.sh \
  --time-before-tampering "30m" \
  --output db_access_analysis.json | \
  jq '.suspicious_queries[] | {timestamp, user, query, affected_tables}'

# Check for direct SQL modifications (UPDATE/INSERT to audit table)
grep -i "UPDATE plugin_audit_entries\|INSERT INTO plugin_audit_entries" \
  database_logs/*.log | head -20

# Check for authentication anomalies
./scripts/incident/analyze-auth-logs.sh \
  --time-window "audit_tampering_window" \
  --output auth_anomalies.json | \
  jq '.anomalies[] | {timestamp, user, privilege_escalation, unexpected_access}'
```

### 3.4 Determine Impact Scope

```bash
# Count corrupted entries
jq '.failures | length' tampering_evidence.json

# Identify affected entities (patients, transactions, etc.)
jq '.failures[].entity_id' tampering_evidence.json | sort | uniq | \
  wc -l

# List all affected entities
jq -r '.failures[].entity_id' tampering_evidence.json | sort | uniq > affected_entities.txt

# Determine what actions were tampered with
jq -r '.failures[].action' tampering_evidence.json | sort | uniq -c
```

### 3.5 Forensic Timeline

```bash
# Create a timeline of events around tampering
./scripts/incident/build-forensic-timeline.sh \
  --center-time "<tampering_start_time>" \
  --window "±30m" \
  --output timeline.json

# View timeline
jq '.[] | {timestamp, event_type, source, details}' timeline.json | \
  sort -k1,1 | column -t
```

---

## 4. Remediation (120–240 minutes)

### 4.1 Isolate and Quarantine

Move corrupted data to quarantine for forensic analysis:

```bash
./scripts/incident/quarantine-corrupted-entries.sh \
  --failure-list tampering_evidence.json \
  --quarantine-table audit_entries_quarantine_$(date +%Y%m%d_%H%M%S) \
  --incident-id "INC-AUDIT-001"
```

### 4.2 Restore from Backup

```bash
# Identify clean backup point (just before tampering began)
./scripts/incident/find-clean-backup.sh \
  --backup-dir /backups/audit/ \
  --output candidates.json

# Verify backup integrity before restoring
./scripts/incident/verify-backup-integrity.sh \
  --backup-file "<selected_backup>" \
  --expected-hash "<known_good_hash>"

# Restore entries from backup
./scripts/incident/restore-audit-entries-from-backup.sh \
  --backup-file "<selected_backup>" \
  --start-time "<tampering_start_time>" \
  --end-time "<tampering_end_time>" \
  --incident-id "INC-AUDIT-001"
```

### 4.3 Re-verify Chain Integrity

```bash
# Full integrity check after restoration
./scripts/incident/audit-trail-integrity-check.sh \
  --full-scan true \
  --output integrity_post_restoration.json

# Verify no failures
jq '.failures | length' integrity_post_restoration.json
# Should output: 0
```

### 4.4 Identify and Lock Compromised Accounts

```bash
# Find the database user(s) that were used for tampering
./scripts/incident/identify-tampering-account.sh \
  --db-access-logs database_logs/*.log \
  --output tampering_accounts.json

# Lock those accounts
jq -r '.tampered_accounts[]' tampering_accounts.json | while read ACCOUNT; do
  ./scripts/incident/lock-database-account.sh --account "$ACCOUNT"
done

# Force password reset for affected operators
./scripts/incident/force-password-reset.sh \
  --account-list tampering_accounts.json \
  --reason "Account may be compromised — audit trail tampering detected"
```

---

## 5. Verification (After restoration)

### 5.1 Integrity Verification

```bash
# Full integrity check
./scripts/incident/audit-trail-integrity-check.sh \
  --full-scan true \
  --sample-size 100% \
  --output final_verification.json

# Verify results
STATUS=$(jq -r '.status' final_verification.json)
if [ "$STATUS" = "PASS" ]; then
  echo "✓ Audit trail integrity verified"
else
  echo "✗ Integrity verification failed — escalate to forensics team"
fi
```

### 5.2 Chain Continuity Check

```bash
# Verify no gaps in entry sequence
./scripts/incident/verify-entry-sequence-continuity.sh \
  --output sequence_verification.json

jq '.gaps' sequence_verification.json
# Should output: [] (empty array)
```

### 5.3 Audit Consistency

```bash
# Cross-check audit entries against other audit sources
# (if multiple audit trails exist for cross-verification)
./scripts/incident/cross-verify-audit-sources.sh \
  --sources "audit_trail,syslog,application_logs" \
  --output cross_verification.json
```

---

## 6. Escalation

### 6.1 Escalation Triggers

Escalate to **Security + Compliance + Legal** immediately if:
- **Tampering confirmed** (any hash failures)
- **Patient/healthcare data affected** (HIPAA breach notification may be required)
- **Financial data affected** (SOX impact)
- **Multiple entries** affected (indicates systematic attack)

Escalate to **Law Enforcement** if:
- **Evidence of external intrusion** (network-based attack)
- **Insider threat indicators** (unauthorized user account used)
- **Intentional tampering** (not accidental corruption)

### 6.2 Escalation Process

```bash
./scripts/incident/escalate.sh \
  --severity CRITICAL \
  --incident-id "INC-AUDIT-001" \
  --notify security-director compliance legal ciso \
  --template audit-tampering-escalation \
  --attach forensic_snapshot_*.tar.gz
```

---

## 7. Communication

### 7.1 Internal Notification (0–10 min)

- **Slack**: `#security-incidents` channel
- **Message**: "Audit trail tampering detected (INC-AUDIT-001), forensic investigation underway, audit trail locked"
- **Audience**: CISO, incident commander, security team, compliance

### 7.2 Customer Notification (Within 24 hours)

If patient/sensitive data was affected:

```bash
./scripts/incident/generate-breach-notification.sh \
  --incident-id "INC-AUDIT-001" \
  --incident-type "audit_trail_compromise" \
  --affected-records-file affected_entities.txt \
  --output customer_notification.md
```

### 7.3 Regulatory Notification (Within 72 hours)

If HIPAA or SOX obligations:
- **HIPAA**: 60-day notification to HHS
- **SOX**: Internal auditors + audit committee
- **Nepal**: Potential notification under Directive 2081

---

## 8. Post-Incident (24–72 hours)

### 8.1 Lessons Learned

```bash
./scripts/incident/post-mortem.sh \
  --incident-id "INC-AUDIT-001" \
  --facilitator <incident-commander> \
  --template docs/runbooks/templates/post-mortem-template.md
```

**Questions:**
- How did attacker gain access to audit table?
- Why did tampering go undetected for [X] minutes?
- What compensating controls failed?

### 8.2 Preventive Measures

Common improvements:
1. **Enable database table replication** — Read-only replicas make tampering obvious
2. **Store audit trail in append-only log** — Prevent UPDATE/DELETE operations
3. **Increase integrity check frequency** — Every entry write, not just reads
4. **Implement cryptographic audit trail** — Hardware security modules for key storage
5. **Monitor database access** — Alert on any access to audit tables

### 8.3 Update Runbook

- Document actual detection and response timeline
- Add new detection methods discovered
- Update contact list if personnel changed

---

## Key Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| CISO | [Name] | +977-... | ciso@ghatana.local |
| Security Director | [Name] | +977-... | security@ghatana.local |
| DBA | [Name] | +977-... | dba@ghatana.local |
| Compliance | [Name] | +977-... | compliance@ghatana.local |
| Legal | [Name] | +977-... | legal@ghatana.local |

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-29  
**Next Review:** 2026-07-29
