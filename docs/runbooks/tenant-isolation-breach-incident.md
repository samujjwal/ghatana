# Tenant Isolation Breach Incident Runbook

**Incident Type:** Multi-Tenant Data Segregation Failure  
**Severity:** CRITICAL  
**Compliance Impact:** HIPAA, SOX, Nepal Directive 2081, GDPR  
**SLO:** Response within 5 minutes, Resolution within 2 hours  

---

## 1. Detection

### Automated Alerts

- **Alert:** `tenant.isolation.boundary_crossed`
  - Trigger: Query from tenant A returns data belonging to tenant B
  - Frequency: Checked on every data access operation
  - Detection method: Query result set compared against tenant context

- **Alert:** `tenant.isolation.context_mismatch`
  - Trigger: TenantContext.getCurrentTenantId() differs from request-scoped tenant
  - Frequency: Checked at request entry and before each data access
  - Threshold: Any mismatch triggers alert

- **Alert:** `tenant.isolation.sql_injection_detected`
  - Trigger: SQL parameterization bypass detected in tenant filter predicates
  - Frequency: Analyzed in query execution layer
  - Threshold: Any non-parameterized tenant filter triggers alert

### Manual Detection

- Customer support reports seeing another customer's data
- Data analyst notices data cross-contamination in audit trail
- Security team detects anomalous query patterns in database logs

---

## 2. Immediate Actions (0–5 minutes)

### Step 1: Confirm the Isolation Breach

```bash
# Query to detect cross-tenant data access
sqlite3 prod.db <<EOF
-- Find audit entries where accessed entity_id belongs to different tenant than actor
SELECT 
  actor_id, actor_tenant, entity_id, entity_tenant, action, timestamp
FROM plugin_audit_entries
WHERE actor_tenant != entity_tenant
  AND timestamp > (datetime('now', '-10 minutes'))
LIMIT 10;
EOF
```

**If rows returned:** Isolation breach is confirmed.

### Step 2: Identify Affected Tenants

```bash
# List all tenants involved in the breach
./scripts/incident/identify-affected-tenants.sh \
  --time-window "10m" \
  --output affected_tenants.json

# Count of affected tenants
jq '.tenants | length' affected_tenants.json
```

### Step 3: Quarantine All Affected Tenants

Suspend all operations for affected tenants to prevent further cross-contamination:

```bash
jq -r '.tenants[]' affected_tenants.json | while read TENANT_ID; do
  ./scripts/incident/suspend-tenant.sh \
    --tenant-id "$TENANT_ID" \
    --reason "Isolation breach — investigation in progress" \
    --incident-id "INC-TENANT-001"
done

# Verify all tenants are suspended
./scripts/incident/verify-tenant-suspension.sh \
  --tenant-list affected_tenants.json
```

### Step 4: Preserve Forensic Evidence

```bash
# Export complete tenant context state
./scripts/incident/export-tenant-context-snapshot.sh \
  --affected-tenants affected_tenants.json \
  --output tenant_context_snapshot_$(date +%s).sql.gz

# Export database logs for the breach window
./scripts/incident/export-database-logs.sh \
  --time-range "30m" \
  --output database_logs_breach_window.log.gz

# Capture TenantContext state from all application instances
./scripts/incident/export-runtime-tenant-contexts.sh \
  --all-instances true \
  --output runtime_contexts_$(date +%s).json.gz
```

---

## 3. Investigation (5–60 minutes)

### 3.1 Identify the Breach Type

**Question:** How did data from one tenant reach another tenant?

**Possible breach vectors:**

1. **Missing tenant filter** — Query doesn't include WHERE tenant_id = ?
2. **Tenant context null** — Request doesn't set TenantContext, falls back to default
3. **Global/shared data leakage** — Data stored without tenant partition
4. **Cache key collision** — Multi-tenant cache hit with wrong tenant
5. **Event/messaging leak** — Event published without tenant routing
6. **Reporting/export leak** — Audit report includes cross-tenant data

```bash
# For each affected tenant pair, identify the data accessed
./scripts/incident/identify-breach-vectors.sh \
  --affected-tenants affected_tenants.json \
  --output breach_vectors.json

# Group by breach type
jq '.[] | .breach_type' breach_vectors.json | sort | uniq -c
```

### 3.2 Trace the Data Path

```bash
# For affected data, identify which code path accessed it
./scripts/incident/trace-data-access-path.sh \
  --affected-data affected_entities.txt \
  --output access_traces.json | \
  jq '.[] | {entity_id, code_path, function_name, line_number, tenant_context}'
```

### 3.3 Root Cause Analysis

**For each breach vector, determine the root cause:**

```bash
# Missing tenant filter: check SQL query
jq '.[] | select(.breach_type == "MISSING_TENANT_FILTER") | .query' \
  breach_vectors.json

# Null tenant context: check request lifecycle
jq '.[] | select(.breach_type == "NULL_TENANT_CONTEXT") | .request_id' \
  breach_vectors.json | while read REQ_ID; do
  grep -r "request_id=$REQ_ID" logs/ | head -5
done

# Cache collision: check cache key
jq '.[] | select(.breach_type == "CACHE_KEY_COLLISION") | .cache_key' \
  breach_vectors.json

# Event leak: check event routing
jq '.[] | select(.breach_type == "EVENT_LEAK") | {event_type, routing_key, intended_recipients, actual_recipients}' \
  breach_vectors.json
```

### 3.4 Determine Impact Scope

```bash
# Count records accessed across tenant boundaries
./scripts/incident/count-exposed-records.sh \
  --breach-vectors breach_vectors.json \
  --output exposure_count.json

jq '.summary' exposure_count.json | column -t

# List affected records by tenant
jq -r '.by_tenant | to_entries[] | "\(.key): \(.value.count) records"' \
  exposure_count.json
```

### 3.5 Identify Attacker

```bash
# If intentional: find which user/service account triggered the breach
./scripts/incident/identify-breach-actor.sh \
  --affected-tenants affected_tenants.json \
  --output breach_actors.json

jq '.actors[] | {user_id, service_account, authentication_method, access_scope}' \
  breach_actors.json
```

---

## 4. Remediation (60–120 minutes)

### 4.1 Fix the Root Cause

**If missing tenant filter:**

```bash
# Identify and fix the vulnerable code
./scripts/incident/find-code-with-missing-tenant-filter.sh \
  --codebase-path src/ \
  --output vulnerable_code_locations.json

# View examples
jq '.[] | {file, line, function, current_query, fixed_query}' \
  vulnerable_code_locations.json

# Deploy hotfix
./gradlew clean build
./scripts/deploy/hotfix-deploy.sh tenant-isolation-filter-fix
```

**If null tenant context:**

```bash
# Check request lifecycle and add tenant context initialization
./scripts/incident/audit-request-lifecycle.sh \
  --output request_lifecycle_audit.json

# Ensure TenantContext.setCurrentTenantId() is called early in request
# Deploy updated code
./gradlew clean build
./scripts/deploy/hotfix-deploy.sh tenant-context-initialization-fix
```

**If cache collision:**

```bash
# Update cache keys to include tenant ID
# Example: "user:123:preferences" → "tenant:456:user:123:preferences"

# Clear affected cache entries
./scripts/incident/clear-cache.sh \
  --cache-type multi-tenant \
  --affected-tenants affected_tenants.json

# Deploy cache key fix
./gradlew clean build
./scripts/deploy/hotfix-deploy.sh tenant-aware-cache-keys
```

**If event leak:**

```bash
# Update event routing to include tenant ID
# Ensure event consumers filter by tenant
./gradlew clean build
./scripts/deploy/hotfix-deploy.sh tenant-aware-event-routing
```

### 4.2 Restore Affected Tenants

Once fix is deployed:

```bash
# Verify isolation is restored
./scripts/incident/verify-tenant-isolation.sh \
  --test-data "sensitive" \
  --test-with-multiple-tenants true

# Resume affected tenants
jq -r '.tenants[]' affected_tenants.json | while read TENANT_ID; do
  ./scripts/incident/resume-tenant.sh \
    --tenant-id "$TENANT_ID" \
    --incident-id "INC-TENANT-001"
done

# Verify normal operations
./scripts/incident/smoke-test-tenant-operations.sh \
  --tenant-list affected_tenants.json
```

### 4.3 Purge Leaked Data

If sensitive data was exposed:

```bash
# For each tenant, purge data that was accessed by wrong tenant
./scripts/incident/purge-exposed-data.sh \
  --affected-tenants affected_tenants.json \
  --exposure-records exposure_count.json \
  --incident-id "INC-TENANT-001"

# Verify purge
./scripts/incident/verify-data-purge.sh \
  --affected-tenants affected_tenants.json
```

---

## 5. Verification (After remediation)

### 5.1 Isolation Restoration

```bash
# Run comprehensive multi-tenant isolation tests
./gradlew :platform-kernel:kernel-core:test --tests "*TenantIsolation*"

# Test results should show: PASSED
```

### 5.2 No Recurrence

```bash
# Monitor for any new isolation breaches in the next 24 hours
./scripts/incident/monitor-tenant-isolation.sh \
  --duration 24h \
  --alert-on "any_cross_tenant_access" \
  --output isolation_monitor.json
```

### 5.3 Data Integrity

```bash
# Verify data consistency within each tenant
./scripts/incident/verify-tenant-data-integrity.sh \
  --affected-tenants affected_tenants.json \
  --output data_integrity_report.json

jq '.summary | {tenant_id, record_count, integrity_status}' \
  data_integrity_report.json
```

---

## 6. Escalation

### 6.1 Escalation Triggers

Escalate to **Compliance + Legal** immediately if:
- **Healthcare data (PHI) exposed** — HIPAA breach notification required
- **Financial data exposed** — SOX compliance issue
- **> 1000 records exposed** — Regulatory notification likely required
- **Patient/customer data intentionally accessed** — Insider threat

Escalate to **Law Enforcement** if:
- **Malicious intent** (intentional cross-tenant access)
- **External intrusion** detected
- **Repeat offender** (same user/service caused isolation breach before)

### 6.2 Escalation Process

```bash
./scripts/incident/escalate.sh \
  --severity CRITICAL \
  --incident-id "INC-TENANT-001" \
  --notify compliance legal privacy-officer ciso \
  --template tenant-isolation-escalation \
  --attach exposure_count.json breach_vectors.json
```

---

## 7. Communication

### 7.1 Internal Notification (0–5 min)

- **Slack**: `#security-incidents`
- **Message**: "Tenant isolation breach confirmed (INC-TENANT-001), [N] tenants affected, mitigation underway"
- **Audience**: CISO, incident commander, platform team, affected product teams

### 7.2 Customer Notification (Within 24 hours)

If sensitive data was exposed:

```bash
./scripts/incident/generate-breach-notification.sh \
  --incident-id "INC-TENANT-001" \
  --incident-type "data_isolation_breach" \
  --affected-tenants affected_tenants.json \
  --exposed-record-count exposed_record_count \
  --output customer_notification_template.md
```

**Template:** `docs/runbooks/templates/isolation-breach-notification.txt`

### 7.3 Regulatory Notification (Within 72 hours if required)

- **HIPAA**: If PHI exposed, notify HHS within 60 days
- **GDPR**: If EU data, notify DPA within 72 hours
- **Nepal**: Follow Directive 2081 notification requirements

---

## 8. Post-Incident (24–72 hours)

### 8.1 Lessons Learned

```bash
./scripts/incident/post-mortem.sh \
  --incident-id "INC-TENANT-001" \
  --facilitator <incident-commander> \
  --participants platform-team,product-teams,security \
  --template docs/runbooks/templates/post-mortem-template.md
```

**Key questions:**
- Why was the isolation breach not caught by automated tests?
- Was isolation tested at code review time?
- Are there other similar vulnerabilities?

### 8.2 Preventive Measures

1. **Mandatory isolation tests** for any query or cache logic
2. **TenantContext validation** at every API boundary
3. **Query analyzer** that warns on missing tenant filters
4. **Quarterly isolation regression tests**
5. **Isolation chaos engineering** (intentionally break isolation to test detection)

### 8.3 Update Runbook

- Document actual detection and response timeline
- Add any new isolation breach vectors discovered
- Update testing procedures with new test cases

---

## Key Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| CISO | [Name] | +977-... | ciso@ghatana.local |
| Compliance Director | [Name] | +977-... | compliance@ghatana.local |
| Privacy Officer | [Name] | +977-... | privacy@ghatana.local |
| Platform Lead | [Name] | +977-... | platform@ghatana.local |
| DBA | [Name] | +977-... | dba@ghatana.local |

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-29  
**Next Review:** 2026-07-29
