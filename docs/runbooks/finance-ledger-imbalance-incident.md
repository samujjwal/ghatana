# Finance Ledger Imbalance Incident Runbook

**Incident Type:** Double-Entry Ledger Invariant Violation  
**Severity:** CRITICAL  
**Compliance Impact:** SOX (Sarbanes-Oxley), Financial Integrity  
**SLO:** Response within 5 minutes, Resolution within 2 hours  

---

## 1. Detection

### Automated Alerts

- **Alert:** `finance.ledger.imbalance_detected`
  - Trigger: Debit total ≠ Credit total across any 1-hour window
  - Threshold: Absolute difference > 0 (perfect balance required)
  - Frequency: Checked every 5 minutes

- **Alert:** `finance.ledger.reversal_net_zero_violation`
  - Trigger: Original entry + reversal ≠ 0 (double-entry symmetry broken)
  - Threshold: Sum of any reversed entries ≠ 0
  - Frequency: Checked every 10 minutes

- **Alert:** `finance.reconciliation_failure`
  - Trigger: Reconciliation check fails (sum across time range mismatches expected)
  - Duration: Sustained for > 1 minute
  - Action: Trigger immediate ledger lock

### Manual Detection

- Finance team reports balance sheet doesn't reconcile
- Auditor notices discrepancy in ledger export
- Random integrity check (`./gradlew :products:finance:ledger-integrity-check`) fails

---

## 2. Immediate Actions (0–5 minutes)

### Step 1: Confirm the Imbalance

```bash
# Query ledger for total imbalance (JDBC example)
SELECT 
  SUM(CASE WHEN is_debit = true THEN amount ELSE 0 END) as total_debit,
  SUM(CASE WHEN is_debit = false THEN amount ELSE 0 END) as total_credit,
  SUM(CASE WHEN is_debit = true THEN amount ELSE -amount END) as net_balance
FROM billing_ledger_entries
  WHERE timestamp > (NOW() - INTERVAL 1 HOUR)
    AND status = 'POSTED';
```

**Expected result for healthy ledger:**
```
total_debit | total_credit | net_balance
-----------+--------------+------------
1000000.00 | 1000000.00   | 0.00
```

**If imbalanced:** Proceed to Step 2.

### Step 2: Lock the Ledger

Prevent further writes to prevent the imbalance from growing:

```bash
# Disable all POST operations to the ledger
./scripts/incident/lock-ledger.sh \
  --reason "Ledger imbalance detected — investigation in progress" \
  --incident-id "INC-LDG-001"

# Output: Ledger is now in READ_ONLY mode
# Verify: curl http://localhost:8081/finance/ledger/status
```

### Step 3: Capture Snapshots

```bash
# Export complete ledger state
./scripts/incident/export-ledger-snapshot.sh \
  --output ledger_snapshot_$(date +%s).sql.gz

# Export plugin billing-ledger state
./scripts/incident/export-plugin-state.sh \
  --plugin billing-ledger \
  --output billing_ledger_plugin_state.sql.gz

# Capture recent transaction history
./scripts/incident/export-ledger-transactions.sh \
  --time-range "2h" \
  --output recent_transactions.json.gz
```

---

## 3. Investigation (5–60 minutes)

### 3.1 Identify the Imbalance Time Window

**Question:** When did the imbalance first occur?

```bash
# Check hourly imbalances going back 24 hours
sqlite3 finance.db <<EOF
WITH hourly_totals AS (
  SELECT 
    datetime(timestamp, 'start of hour') as hour,
    SUM(CASE WHEN is_debit THEN amount ELSE 0 END) as debit_total,
    SUM(CASE WHEN NOT is_debit THEN amount ELSE 0 END) as credit_total,
    COUNT(*) as entry_count
  FROM billing_ledger_entries
  WHERE status = 'POSTED'
  GROUP BY hour
  ORDER BY hour DESC
  LIMIT 24
)
SELECT hour, debit_total, credit_total, (debit_total - credit_total) as imbalance, entry_count
FROM hourly_totals
WHERE debit_total != credit_total;
EOF
```

**Identify the first hour with imbalance:**
- If multiple hours show imbalance, the problem likely started in the earliest hour

### 3.2 Identify Affected Transactions

```bash
# Find which transactions caused the imbalance
# In a healthy ledger, every debit entry must have a corresponding credit entry (or be a standing balance)

./scripts/incident/identify-unmatched-entries.sh \
  --output unmatched_entries.json

# Check for orphaned entries (debit without credit, or vice versa)
sqlite3 finance.db <<EOF
SELECT 
  le.id, le.entry_id, le.is_debit, le.amount, le.description, 
  le.timestamp, le.correlation_id,
  (SELECT COUNT(*) FROM billing_ledger_entries le2 
   WHERE le2.entry_id = le.entry_id AND le2.is_debit != le.is_debit) as matching_count
FROM billing_ledger_entries le
WHERE le.status = 'POSTED'
  AND le.correlation_id NOT IN (
    SELECT correlation_id FROM billing_ledger_entries 
    WHERE status = 'POSTED'
    GROUP BY correlation_id
    HAVING SUM(CASE WHEN is_debit THEN 1 ELSE -1 END) = 0
  );
EOF
```

### 3.3 Root Cause Analysis

**Possible causes:**

1. **Incomplete transaction post** — Debit posted but credit failed
2. **Double-posting** — Same transaction posted twice with same direction
3. **Reversal without original** — Reversal entry posted but original missing
4. **Corrupt entry** — Entry in ledger has wrong sign or amount
5. **Bug in billing-ledger plugin** — Logic error in post/reversal methods

**Diagnostic steps:**

```bash
# Check transaction correlations (should have pairs)
./scripts/incident/analyze-transaction-correlations.sh \
  --time-range "2h" \
  --output correlation_analysis.json

# Check for duplicate postings
sqlite3 finance.db <<EOF
SELECT correlation_id, is_debit, amount, COUNT(*) as count
FROM billing_ledger_entries
WHERE status = 'POSTED'
GROUP BY correlation_id, is_debit, amount
HAVING COUNT(*) > 1;
EOF

# Check plugin billing-ledger logs for errors
grep -i "error\|exception\|fail" logs/plugin-billing-ledger.log | tail -50 | \
  jq -r '{timestamp, level, message, exception}' > billing_ledger_errors.json
```

### 3.4 Determine Impact Scope

```bash
# Count affected trade IDs
./scripts/incident/count-affected-trades.sh \
  --time-range "2h" \
  --output affected_trades.txt

# Estimate financial impact
IMBALANCE=$(sqlite3 finance.db "SELECT ABS(SUM(CASE WHEN is_debit THEN amount ELSE -amount END)) FROM billing_ledger_entries WHERE status = 'POSTED' AND timestamp > datetime('now', '-2 hours')")
echo "Ledger imbalance: $IMBALANCE currency units"
```

---

## 4. Remediation (60–120 minutes)

### 4.1 Fix the Root Cause

**If incomplete post — manually post the missing entry:**

```bash
./scripts/incident/manual-ledger-post.sh \
  --entry-id "<missing_credit_entry>" \
  --correlation-id "<original_correlation_id>" \
  --is-debit false \
  --amount "<amount>" \
  --description "INCIDENT CORRECTION: Missing credit entry — INC-LDG-001" \
  --incident-id "INC-LDG-001"
```

**If double-posting — reverse the duplicate:**

```bash
./scripts/incident/reverse-ledger-entry.sh \
  --ledger-entry-id "<duplicate_entry_id>" \
  --incident-id "INC-LDG-001"
```

**If reversal without original — fix the reversal:**

```bash
# Remove the orphan reversal
./scripts/incident/delete-ledger-entry.sh \
  --ledger-entry-id "<reversal_without_original>" \
  --incident-id "INC-LDG-001"
```

**If plugin bug — deploy hotfix:**

```bash
# Fix the bug in plugin-billing-ledger
# e.g., fix post() or reversal() method
./gradlew :platform-plugins:plugin-billing-ledger:build
./scripts/deploy/plugin-deploy.sh billing-ledger
./scripts/incident/verify-plugin-restart.sh billing-ledger
```

### 4.2 Re-verify Balance

```bash
# Check that debit = credit after remediation
sqlite3 finance.db <<EOF
SELECT 
  SUM(CASE WHEN is_debit = true THEN amount ELSE 0 END) as total_debit,
  SUM(CASE WHEN is_debit = false THEN amount ELSE 0 END) as total_credit,
  SUM(CASE WHEN is_debit = true THEN amount ELSE -amount END) as net_balance
FROM billing_ledger_entries
WHERE status = 'POSTED';
EOF
```

**Expected output:**
```
total_debit | total_credit | net_balance
-----------+--------------+------------
[equal]    | [equal]      | 0.00
```

### 4.3 Unlock the Ledger

Once balance is confirmed:

```bash
./scripts/incident/unlock-ledger.sh \
  --incident-id "INC-LDG-001" \
  --verified-by "<operator_name>"
```

---

## 5. Verification (After remediation)

### 5.1 Run Automated Invariant Tests

```bash
# Test 1: Debit = Credit symmetry
./scripts/incident/test-ledger-symmetry.sh \
  --expected-balance 0.00

# Test 2: Reversal net-zero property
./scripts/incident/test-ledger-reversals.sh \
  --check-net-zero true

# Test 3: Reconciliation across time ranges
./scripts/incident/test-ledger-reconciliation.sh \
  --time-ranges "1h,6h,24h"
```

### 5.2 Manual Reconciliation

```bash
# Finance team manually verifies balance sheet
./scripts/incident/export-balance-sheet.sh \
  --format pdf \
  --output balance_sheet_post_remediation.pdf

# For review by finance.manager@ghatana.local
```

### 5.3 Monitor for Recurrence

```bash
# Watch for any new imbalances in the next 24 hours
./scripts/incident/monitor-ledger-balance.sh \
  --alert-threshold 0.00 \
  --monitor-duration 24h
```

---

## 6. Escalation

### 6.1 Escalation Triggers

Escalate to **Finance Director + Compliance** if:
- **Imbalance > 10,000 currency units**
- **Cannot determine root cause** after 30 minutes
- **Multiple imbalances** within a single day
- **Impact affects external reporting** (month-end, audit period)

Escalate to **External Auditors** if:
- **Imbalance > 100,000 currency units**
- **SOX compliance impact** (material control failure)
- **Imbalance crosses audit period boundary** (month-end, quarter-end)

### 6.2 Escalation Process

```bash
./scripts/incident/escalate.sh \
  --severity CRITICAL \
  --incident-id "INC-LDG-001" \
  --notify finance-director compliance external-audit \
  --template finance-ledger-escalation
```

---

## 7. Communication

### 7.1 Internal Notification (0–5 min)

- **Slack**: `#finance-incidents` channel
- **Message**: Imbalance amount, incident ID, lock status
- **Audience**: Finance team, incident commander, CTO

### 7.2 Finance Team Update (Every 30 min during investigation)

- Status report with remediation progress
- Estimated time to resolution
- Finance director sign-off when ledger is unlocked

### 7.3 External Auditor Notification (If material)

- Formal notification within 24 hours if imbalance > threshold
- Documentation of root cause and remediation

---

## 8. Post-Incident (24–72 hours)

### 8.1 Lessons Learned

```bash
./scripts/incident/post-mortem.sh \
  --incident-id "INC-LDG-001" \
  --facilitator <incident-commander> \
  --template docs/runbooks/templates/post-mortem-template.md
```

**Review with:**
- Finance team (business logic)
- Engineering team (technical root cause)
- Compliance (SOX impact)

### 8.2 Preventive Measures

Common improvements:
1. **Increase invariant check frequency** (every 1 min instead of 5 min)
2. **Add integration tests** for incomplete transactions
3. **Implement automatic reversal** for incomplete double-entry pairs
4. **Add billing-ledger plugin health checks** to service startup

### 8.3 Update Runbook

- Document actual timeline and response effectiveness
- Update detection thresholds if needed
- Add new diagnostic commands if discovered

---

## Key Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| Incident Commander | On-call | On-call rotation | on-call@ghatana.local |
| Finance Director | [Name] | +977-... | finance-director@ghatana.local |
| Database Admin | [Name] | +977-... | dba@ghatana.local |
| Compliance | [Name] | +977-... | compliance@ghatana.local |

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-29  
**Next Review:** 2026-07-29
