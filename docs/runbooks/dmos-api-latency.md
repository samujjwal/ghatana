# DMOS API Latency Runbook

## Summary
This runbook covers diagnosing and responding to high API latency issues in DMOS.

## Symptoms
- High `dmos_api_duration` p95/p99 values
- API response times > SLA thresholds
- User complaints about slow API responses
- Timeout errors in application logs

## Diagnosis

### 1. Check API Latency Metrics
```bash
# Query p95 latency
curl http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95, rate(dmos_api_duration_seconds_bucket[5m]))

# Query p99 latency
curl http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99, rate(dmos_api_duration_seconds_bucket[5m]))

# Check by endpoint
curl http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95, sum by (endpoint) (rate(dmos_api_duration_seconds_bucket[5m])))
```

### 2. Check Application Logs
```bash
# Check for slow requests
kubectl logs -l app=dmos-application | grep "slow request"

# Check for timeout errors
kubectl logs -l app=dmos-application | grep "timeout"
```

### 3. Check Database Performance
```bash
# Check database query latency
curl http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95, rate(pg_query_duration_seconds_bucket[5m]))

# Check database connection pool
curl http://prometheus:9090/api/v1/query?query=pg_connection_pool_active_count
```

### 4. Check External Dependencies
```bash
# Check connector call latency
curl http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95, rate(dmos_connector_duration_seconds_bucket[5m]))

# Check kernel bridge latency
curl http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95, rate(kernel_bridge_duration_seconds_bucket[5m]))
```

### 5. Check Resource Utilization
```bash
# Check CPU usage
kubectl top pods -l app=dmos-application

# Check memory usage
kubectl top pods -l app=dmos-application

# Check JVM heap
curl http://dmos-application:8081/actuator/metrics/jvm.memory.used
```

## Response

### Database Query Slowdown
1. Identify slow queries from logs
2. Add missing indexes
3. Optimize query logic
4. Scale database resources if needed

### Connection Pool Exhaustion
1. Check connection pool configuration
2. Increase pool size if appropriate
3. Check for connection leaks
4. Add connection pool monitoring

### External Dependency Latency
1. Check external API status
2. Implement caching where appropriate
3. Add timeouts and circuit breakers
4. Consider fallback strategies

### Resource Exhaustion
1. Scale application pods horizontally
2. Increase resource limits
3. Optimize memory usage
4. Check for memory leaks

### Garbage Collection Issues
1. Check GC logs
2. Tune JVM GC settings
3. Check for memory leaks
4. Increase heap size if needed

## Optimization

### Add Caching
```bash
# Enable response caching for read-heavy endpoints
# Configure cache TTL based on data freshness requirements
```

### Add Circuit Breakers
```bash
# Add circuit breakers for external dependencies
# Configure fallback responses
```

### Optimize Database Queries
```bash
# Review query execution plans
# Add appropriate indexes
# Consider materialized views for complex queries
```

## Verification
- `dmos_api_duration` p95 returns to baseline (< 500ms)
- `dmos_api_duration` p99 returns to baseline (< 1s)
- No timeout errors in logs
- User complaints resolved

## Prevention
- Set up latency alerts (p95 > 500ms, p99 > 1s)
- Monitor database query performance
- Implement caching for read-heavy operations
- Add circuit breakers for external dependencies
- Regular performance testing

## Escalation
- If p99 latency > 5s for > 5 minutes, escalate to on-call engineer
- If database is the bottleneck, escalate to DBA
- If external dependency is slow, engage vendor support
- If resource exhaustion, scale infrastructure

## Related Dashboards
- DMOS API Health: `/d/dmos-api-health`
- DMOS Workflow & Command: `/d/dmos-workflow-command`
