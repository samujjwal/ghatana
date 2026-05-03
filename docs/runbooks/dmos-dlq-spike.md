# DMOS DLQ Spike Runbook

## Summary
This runbook covers diagnosing and responding to sudden increases in dead-letter queue entries.

## Symptoms
- Sudden spike in `dmos_dlq_count_total` rate
- `dmos_dlq_size` metric increasing rapidly
- DLQ entries by type showing specific pattern
- DLQ entries by reason showing common failure mode

## Diagnosis

### 1. Identify Spike Pattern
```bash
# Query DLQ entry rate by type
curl http://prometheus:9090/api/v1/query?query=sum by (entity_type) (rate(dmos_dlq_count_total[5m]))

# Query DLQ entry rate by reason
curl http://prometheus:9090/api/v1/query?query=sum by (reason) (rate(dmos_dlq_count_total[5m]))
```

### 2. Check Command Failures
```bash
# Check command failure rate
curl http://prometheus:9090/api/v1/query?query=rate(dmos_command_failure_total[5m])

# Check for specific command type failures
curl http://prometheus:9090/api/v1/query?query=rate(dmos_command_failure_total{command_type="GOOGLE_ADS_CAMPAIGN_CREATE"}[5m])
```

### 3. Check Connector Status
```bash
# Check for connector failures
curl http://prometheus:9090/api/v1/query?query=rate(dmos_connector_failure_total[5m])
```

### 4. Check Application Logs
```bash
# Check for DLQ-related errors
kubectl logs -l app=dmos-application | grep "DLQ"
```

## Response

### Connector-Related DLQ Spike
1. Check connector status and credentials
2. Follow [Connector Outage Runbook](./dmos-connector-outage.md)
3. Replay DLQ entries after connector recovery
4. Monitor for new DLQ entries

### Command Validation Failures
1. Check for schema changes in command payloads
2. Verify command handler logic is correct
3. Check for missing dependencies
4. Fix validation issues and replay failed commands

### Rate Limit / Throttling
1. Identify the rate-limited service
2. Implement circuit breaker pattern
3. Add exponential backoff
4. Reduce command throughput temporarily

### Database Issues
1. Check database connectivity
2. Check for connection pool exhaustion
3. Check for optimistic lock conflicts
4. Scale database resources if needed

## Replay DLQ Entries

### Manual Replay
```bash
# Use DLQ replay CLI tool
dmos-dlq-replay --batch-size=100 --max-retries=3
```

### Selective Replay
```bash
# Replay specific entity type
dmos-dlq-replay --entity-type=GOOGLE_ADS_CAMPAIGN --batch-size=50

# Replay specific time range
dmos-dlq-replay --since="2026-03-27T00:00:00Z" --until="2026-03-27T12:00:00Z"
```

## Prevention
- Add circuit breakers for external dependencies
- Implement exponential backoff for retries
- Add preflight validation before command execution
- Monitor DLQ size and set alerts

## Escalation
- If DLQ size > 10,000 entries, escalate to on-call engineer
- If replay fails > 50% of entries, escalate to engineering team
- If DLQ spike continues > 1 hour, declare incident

## Recovery Verification
- `dmos_dlq_count_total` rate returns to baseline
- `dmos_dlq_size` decreasing
- Command success rate returns to normal
- No new DLQ entries for replayed entities

## Related Dashboards
- DMOS Dead-Letter Queue: `/d/dmos-dlq`
- DMOS Workflow & Command: `/d/dmos-workflow-command`
