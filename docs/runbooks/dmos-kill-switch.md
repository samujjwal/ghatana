# DMOS Kill Switch Runbook

## Summary
This runbook covers the DMOS kill switch feature, which provides emergency stop functionality for connector operations and external integrations.

## Purpose
The kill switch is a safety mechanism that immediately stops all connector operations and external API calls. It's designed for emergency scenarios where continued operation could cause:
- Overspending on ad campaigns
- Data integrity issues
- Security incidents
- Regulatory violations

## Symptoms
- Kill switch triggered (manual or automatic)
- All connector operations blocked
- `dmos_kill_switch_enabled` metric = 1
- Commands for connector operations failing with kill switch error

## Diagnosis

### 1. Check Kill Switch Status
```bash
# Query kill switch status
curl http://prometheus:9090/api/v1/query?query=dmos_kill_switch_enabled

# Check feature flag
kubectl get configmap dmos-config -o yaml | grep KILL_SWITCH_ENABLED
```

### 2. Check Kill Switch Reason
```bash
# Check application logs for kill switch trigger reason
kubectl logs -l app=dmos-application | grep "KILL_SWITCH"
```

### 3. Identify Trigger Source
- Manual trigger by operator
- Automatic trigger (overspend detection, security incident, etc.)
- Feature flag change

## Response

### Manual Kill Switch (Intended)
1. Verify the kill switch was intentionally triggered
2. Review the reason for activation
3. Address the underlying issue
4. Re-enable kill switch when safe

### Automatic Kill Switch (Overspend)
1. Check budget pacing dashboard
2. Verify actual spend vs budget
3. Adjust campaign budgets if needed
4. Re-enable kill switch after budget adjustment

### Automatic Kill Switch (Security Incident)
1. Follow security incident response process
2. Investigate potential compromise
3. Rotate credentials if needed
4. Re-enable kill switch after investigation complete

### Automatic Kill Switch (Data Integrity)
1. Identify the data integrity issue
2. Roll back affected changes
3. Fix the root cause
4. Re-enable kill switch after fix verified

## Re-Enable Kill Switch

### Via Feature Flag
```bash
# Update feature flag
kubectl patch configmap dmos-config --type=json \
  -p='[{"op": "replace", "path": "/data/DMOS_KILL_SWITCH_ENABLED", "value":"false"}]'

# Restart application pods
kubectl rollout restart deployment dmos-application
```

### Via Configuration
```bash
# Update environment variable
kubectl set env deployment/dmos-application DMOS_KILL_SWITCH_ENABLED=false

# Restart application pods
kubectl rollout restart deployment dmos-application
```

## Verification
- `dmos_kill_switch_enabled` metric = 0
- Connector operations resume
- Commands executing successfully
- No kill switch errors in logs

## Prevention
- Set up budget pacing alerts
- Implement automated spend controls
- Add pre-flight checks for data integrity
- Monitor for security anomalies

## Escalation
- If kill switch triggered automatically, investigate immediately
- If kill switch cannot be re-enabled, escalate to engineering team
- If kill switch triggered due to security incident, escalate to security team

## Related Dashboards
- DMOS Budget Pacing: `/d/dmos-budget-pacing`
- DMOS Privacy & Security: `/d/dmos-privacy-security`
