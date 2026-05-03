# DMOS Authorization Failures Runbook

## Summary
This runbook covers diagnosing and responding to authorization failures in DMOS.

## Symptoms
- High `dmos_authz_failure_total` rate
- Users unable to access resources
- API returning 403 Forbidden errors
- Permission-related errors in logs

## Diagnosis

### 1. Check Authorization Failure Rate
```bash
# Query authz failure rate
curl http://prometheus:9090/api/v1/query?query=rate(dmos_authz_failure_total[5m])

# Check by tenant
curl http://prometheus:9090/api/v1/query?query=sum by (tenant_id) (rate(dmos_authz_failure_total[5m]))

# Check by resource type
curl http://prometheus:9090/api/v1/query?query=sum by (resource_type) (rate(dmos_authz_failure_total[5m]))
```

### 2. Check Application Logs
```bash
# Check for authorization errors
kubectl logs -l app=dmos-application | grep "AUTHZ"

# Check for permission denied errors
kubectl logs -l app=dmos-application | grep "Permission denied"
```

### 3. Check Kernel Bridge Status
```bash
# Check if kernel bridge is responding
curl http://kernel-bridge:8080/health

# Check authorization with kernel bridge
curl -H "Authorization: Bearer <token>" http://kernel-bridge:8080/authorize
```

### 4. Check Tenant/Workspace Configuration
```bash
# Check tenant configuration
kubectl get configmap dmos-tenant-config -o yaml

# Check workspace configuration
kubectl get configmap dmos-workspace-config -o yaml
```

## Response

### Tenant-Wide AuthZ Failures
1. Check tenant configuration in kernel bridge
2. Verify tenant is active and not suspended
3. Check tenant RBAC rules
4. Contact platform team if tenant misconfiguration

### Resource-Specific AuthZ Failures
1. Check resource-specific permissions
2. Verify user has required role
3. Check if resource is protected by additional policies
4. Grant required permissions if appropriate

### Kernel Bridge Issues
1. Check kernel bridge health
2. Verify kernel bridge connectivity
3. Check kernel bridge logs
4. Restart kernel bridge if needed

### Permission Model Changes
1. Check if permission model was recently updated
2. Verify migration completed successfully
3. Roll back permission model if needed
4. Re-apply permissions after fix

## Common Issues

### Missing Role Assignment
**Symptom:** User cannot access resources despite having correct permissions
**Solution:** Assign appropriate role to user in tenant configuration

### Policy Conflict
**Symptom:** User has role but still denied access
**Solution:** Check for conflicting policies, resolve conflict

### Stale Cache
**Symptom:** Permissions updated but still denied
**Solution:** Clear authorization cache, restart affected services

### Tenant Suspension
**Symptom:** All tenant users denied access
**Solution:** Check tenant status, reactivate if appropriate

## Verification
- `dmos_authz_failure_total` rate returns to baseline
- Users can access resources successfully
- No 403 errors in API logs
- Authorization checks passing in logs

## Prevention
- Set up alerts for authz failure rate spikes
- Monitor permission changes
- Test authorization flows regularly
- Document permission model changes

## Escalation
- If authz failures > 100/min, escalate to on-call engineer
- If tenant-wide issue, escalate to platform team
- If permission model issue, escalate to engineering team
- If kernel bridge issue, escalate to platform team

## Related Dashboards
- DMOS Privacy & Security: `/d/dmos-privacy-security`
- DMOS API Health: `/d/dmos-api-health`
