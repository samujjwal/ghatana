# Connector Failures Runbook

## Severity

High

## Symptoms

- Connector failure rate > 10%
- Connector status shows "FAILED"
- Dead letter queue growing
- Alerts firing for connector failures

## Initial Response

1. Check connector status in `/connectors`
2. Identify which connectors are failing
3. Check DLQ for failed messages
4. Review connector error logs

## Investigation Steps

### 1. Check Connector Logs

```bash
kubectl logs -f deployment/dmos-api --tail=1000 | grep connector
```

Look for:
- Authentication errors
- Rate limit errors (429)
- Network timeouts
- API errors from external services

### 2. Check External API Status

- Google Ads API status
- OAuth token validity
- Account access permissions

### 3. Check Dead Letter Queue

Navigate to `/dlq` and check:
- Number of failed messages
- Error messages
- Retry attempts

### 4. Check Credentials

Verify connector credentials:
- OAuth tokens are valid
- Access tokens not expired
- Refresh tokens working

## Common Causes and Solutions

### OAuth Token Expired

**Symptoms:** 401 Unauthorized errors

**Solution:**
```bash
# Re-authenticate connector
# Navigate to /connectors/google-ads
# Click "Re-authenticate"
# Complete OAuth flow
```

### Rate Limit Exceeded

**Symptoms:** 429 Too Many Requests

**Solution:**
- Implement exponential backoff
- Reduce request frequency
- Increase rate limit plan with external provider

### Network Timeout

**Symptoms:** Connection timeout errors

**Solution:**
```bash
# Check network connectivity
# Verify external API is accessible
# Increase timeout if needed
```

### Invalid Credentials

**Symptoms:** 403 Forbidden errors

**Solution:**
- Verify account access permissions
- Re-authenticate connector
- Check manager account ID

## Recovery

### Retry Failed Messages

1. Navigate to `/dlq`
2. Select failed messages
3. Click "Retry"
4. Verify messages are processed

### Re-authenticate Connector

1. Navigate to `/connectors`
2. Click "Re-authenticate" on failing connector
3. Complete OAuth flow
4. Verify connector status returns to "CONNECTED"

### Kill Switch

If connector failures are critical:
1. Navigate to `/connectors`
2. Activate kill switch
3. Investigate issue
4. Fix issue
5. Deactivate kill switch

## Escalation

If unable to resolve within 30 minutes:
1. Notify on-call engineering
2. Notify connector provider support if needed
3. Consider temporary workaround
4. Communicate status to stakeholders

## Prevention

- Monitor connector health regularly
- Set up alerts for OAuth token expiry
- Implement proactive token refresh
- Monitor rate limit usage
