# DMOS Connector Outage Runbook

## Summary
This runbook covers diagnosing and responding to connector outages (e.g., Google Ads API unavailable, rate limits, credential issues).

## Symptoms
- High `dmos_connector_failure_total` rate
- Connector status shows `INACTIVE` or `ERROR`
- Commands failing with connector-related errors
- DLQ entries increasing for connector operations

## Diagnosis

### 1. Check Connector Status
```bash
# Query connector status metrics
curl http://prometheus:9090/api/v1/query?query=dmos_connector_status{connector_type="GOOGLE_ADS"}
```

### 2. Check Connector Logs
```bash
# Check for connector-specific errors
kubectl logs -l app=dmos-application | grep "DMOS-CONNECTOR"
```

### 3. Check External API Status
- Verify Google Ads API status page
- Check for rate limit errors (HTTP 429)
- Check for authentication errors (HTTP 401/403)

### 4. Check Credentials
- Verify OAuth tokens are not expired
- Check token refresh is working
- Verify connector configuration is valid

## Response

### Minor Outage (Single Connector)
1. Identify the failing connector from logs
2. Check if credentials need refresh
3. If rate limited, implement exponential backoff
4. Monitor `dmos_connector_failure_total` for recovery

### Major Outage (All Connectors)
1. Check if external API is down
2. Verify network connectivity from cluster
3. Check for misconfiguration or certificate issues
4. Engage external API support if needed

### Credential Issues
1. Rotate affected credentials
2. Verify OAuth flow is working
3. Test with new credentials
4. Monitor for successful connector calls

## Escalation
- If outage persists > 30 minutes, escalate to on-call engineer
- If external API is down, engage vendor support
- If credential issues, engage security team

## Recovery Verification
- `dmos_connector_failure_total` rate returns to baseline
- Connector status shows `ACTIVE`
- Commands executing successfully
- No new DLQ entries for connector operations

## Related Dashboards
- DMOS Connector: `/d/dmos-connector`
- DMOS Dead-Letter Queue: `/d/dmos-dlq`
