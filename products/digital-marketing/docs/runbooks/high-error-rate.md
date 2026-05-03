# High Error Rate Runbook

## Severity

Critical

## Symptoms

- Error rate > 5% for 5 minutes
- Increase in 5xx errors
- Alerts firing for high error rate

## Initial Response

1. Check Grafana dashboard for error rate spike
2. Identify which endpoints are failing
3. Check recent deployments
4. Check connector health status

## Investigation Steps

### 1. Check Application Logs

```bash
kubectl logs -f deployment/dmos-api --tail=1000
```

Look for:
- Exception stack traces
- Error messages
- Failed connector executions

### 2. Check Database Health

```bash
kubectl exec -it postgres-0 -- psql -U dmos -d dmos -c "SELECT 1"
```

Check:
- Connection pool status
- Slow queries
- Lock contention

### 3. Check Connector Status

Navigate to `/connectors` and check:
- Which connectors are failing
- Error messages from connectors
- Rate limit status

### 4. Check Kernel Health

```bash
curl http://kernel:8080/health
```

Check:
- Kernel availability
- Response time

## Common Causes and Solutions

### Database Connection Issues

**Symptoms:** Connection timeout, pool exhaustion

**Solution:**
```bash
# Check database pod
kubectl get pods -l app=postgres
kubectl logs postgres-0

# Restart application if needed
kubectl rollout restart deployment/dmos-api
```

### Connector Rate Limits

**Symptoms:** 429 errors from external APIs

**Solution:**
- Reduce connector execution frequency
- Implement queue for connector requests
- Add backoff and retry logic

### Memory Issues

**Symptoms:** OOMKilled, high memory usage

**Solution:**
```bash
# Check memory usage
kubectl top pods

# Increase memory limit if needed
kubectl set resources deployment/dmos-api --limits=memory=2Gi
```

### Recent Deployment

**Symptoms:** Errors started after deployment

**Solution:**
```bash
# Rollback to previous version
kubectl rollout undo deployment/dmos-api

# Investigate deployment
kubectl describe deployment/dmos-api
```

## Escalation

If unable to resolve within 15 minutes:
1. Notify on-call engineering
2. Notify product owner
3. Consider enabling kill switch for connectors
4. Communicate status to stakeholders

## Recovery

1. Implement fix
2. Deploy fix
3. Verify error rate returns to normal
4. Monitor for 30 minutes
5. Document incident
6. Update runbook if needed
