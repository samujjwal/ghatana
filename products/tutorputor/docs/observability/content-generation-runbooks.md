# Content Generation Runbooks

This document provides runbooks for common content generation incidents and operational procedures.

## Table of Contents
- [Validation Failure Runbook](#validation-failure-runbook)
- [AI Model Degradation Runbook](#ai-model-degradation-runbook)
- [Queue Backlog Runbook](#queue-backlog-runbook)
- [Review Backlog Runbook](#review-backlog-runbook)
- [Cost Management Runbook](#cost-management-runbook)
- [Cache Performance Runbook](#cache-performance-runbook)

---

## Validation Failure Runbook

### Severity
Critical

### Trigger
- Validation failure rate > 10% for 5 minutes
- Sudden spike in validation failures

### Impact
- Content generation pipeline blocked
- Users unable to publish content
- Review backlog increases

### Diagnosis Steps

1. **Check Validation Service Status**
   ```bash
   # Check validation service health
   curl -s http://localhost:3200/api/v1/health/validation
   ```

2. **Review Validation Logs**
   ```bash
   # Check validation service logs
   kubectl logs -f deployment/validation-worker --tail=100
   ```

3. **Identify Failure Pattern**
   - Query metrics: `content_generation_validation_failures_total` by `reason` label
   - Common reasons: profanity, pii, safety, format, accuracy
   - Check if specific content type or domain is affected

4. **Check Validator Configuration**
   - Verify validator rules haven't changed
   - Check for recent deployment of new validation logic
   - Review feature flags for validation

### Resolution Steps

1. **Immediate Mitigation**
   - If specific rule causing failures, disable temporarily via feature flag
   - Increase validation timeout if service is slow
   - Scale up validation worker pods

2. **Fix Root Cause**
   - If false positives: adjust validation thresholds
   - If true issues: fix content generation prompts/templates
   - If service degradation: restart validation workers

3. **Verify Resolution**
   ```bash
   # Monitor validation failure rate
   watch -n 10 'curl -s http://localhost:3200/api/v1/metrics/validation | grep failure_rate'
   ```

4. **Clear Backlog**
   - Re-process failed validations after fix
   - Monitor queue depth: `content_generation_queue_depth`

### Prevention
- Implement canary deployments for validation changes
- Add validation test suite to CI/CD
- Set up alerts for gradual degradation (not just spikes)

### Escalation
- If unresolved after 30 minutes: escalate to platform team
- If affecting all tenants: declare incident, notify stakeholders

---

## AI Model Degradation Runbook

### Severity
Critical

### Trigger
- AI model success rate < 95% for 10 minutes
- AI model latency p95 > 30s
- AI model error rate spikes

### Impact
- Content generation slow or failed
- Increased costs due to retries
- Poor user experience

### Diagnosis Steps

1. **Check AI Model Status**
   ```bash
   # Check AI model health
   curl -s http://localhost:3200/api/v1/health/ai
   ```

2. **Identify Affected Model**
   - Query metrics: `content_generation_ai_model_status` by `model` label
   - Check if specific model (OpenAI, Ollama, Claude) is affected

3. **Check External Provider Status**
   - OpenAI status page: https://status.openai.com
   - Check rate limits and quotas
   - Verify API keys are valid

4. **Review AI Service Logs**
   ```bash
   # Check AI service logs
   kubectl logs -f deployment/ai-service --tail=100
   ```

5. **Check Network Connectivity**
   - Test connectivity to AI providers
   - Check DNS resolution
   - Verify firewall rules

### Resolution Steps

1. **Immediate Mitigation**
   - Switch to backup AI model if available
   - Enable failover to alternative provider
   - Reduce request rate to avoid rate limiting

2. **Fix Root Cause**
   - If provider outage: wait for provider resolution
   - If rate limited: implement exponential backoff
   - If network issue: fix network configuration
   - If API key issue: rotate keys

3. **Verify Resolution**
   ```bash
   # Monitor AI model success rate
   watch -n 10 'curl -s http://localhost:3200/api/v1/metrics/ai | grep success_rate'
   ```

4. **Clear Backlog**
   - Re-process failed generations after fix
   - Monitor queue depth

### Prevention
- Implement circuit breaker pattern for AI calls
- Set up provider health checks
- Maintain multiple AI providers for redundancy
- Implement rate limiting at application level

### Escalation
- If provider outage: contact provider support
- If internal issue: escalate to platform team
- If affecting critical features: declare incident

---

## Queue Backlog Runbook

### Severity
Critical

### Trigger
- Generation queue depth > 1000 for 15 minutes
- Queue wait time > 30 minutes

### Impact
- Content generation delayed
- Poor user experience
- Potential resource exhaustion

### Diagnosis Steps

1. **Check Queue Status**
   ```bash
   # Check queue depth
   curl -s http://localhost:3200/api/v1/metrics/queue | grep depth
   ```

2. **Check Worker Status**
   ```bash
   # Check worker pods
   kubectl get pods -l app=generation-worker
   kubectl top pods -l app=generation-worker
   ```

3. **Identify Bottleneck**
   - Check if workers are processing (CPU/memory usage)
   - Check if AI model is slow (see AI Model Degradation runbook)
   - Check if database is slow (query performance)

4. **Check for Stuck Jobs**
   ```bash
   # Check for jobs in processing state too long
   kubectl exec -it <postgres-pod> -- psql -c "
     SELECT * FROM content_generation_queue 
     WHERE status = 'processing' 
     AND updated_at < NOW() - INTERVAL '30 minutes'
   "
   ```

### Resolution Steps

1. **Immediate Mitigation**
   - Scale up worker pods: `kubectl scale deployment generation-worker --replicas=10`
   - Increase worker concurrency if safe
   - Prioritize high-priority jobs

2. **Fix Root Cause**
   - If workers stuck: restart worker pods
   - If AI slow: see AI Model Degradation runbook
   - If database slow: optimize queries, add indexes
   - If resource constrained: increase resource limits

3. **Clear Stuck Jobs**
   ```bash
   # Reset stuck jobs to pending
   kubectl exec -it <postgres-pod> -- psql -c "
     UPDATE content_generation_queue 
     SET status = 'pending', attempt_count = 0 
     WHERE status = 'processing' 
     AND updated_at < NOW() - INTERVAL '30 minutes'
   "
   ```

4. **Verify Resolution**
   - Monitor queue depth decreasing
   - Monitor wait time improving

### Prevention
- Implement job timeout and auto-retry
- Set up queue depth alerts with lower threshold
- Implement horizontal pod autoscaling
- Monitor worker resource usage

### Escalation
- If queue not clearing after scaling: escalate to platform team
- If database issue: escalate to DBA team

---

## Review Backlog Runbook

### Severity
Warning

### Trigger
- Review queue depth > 500 for 30 minutes

### Impact
- Content not published to production
- Delayed content availability for users
- Potential SLA violations

### Diagnosis Steps

1. **Check Review Queue Status**
   ```bash
   # Check review queue depth
   curl -s http://localhost:3200/api/v1/metrics/review | grep queue_depth
   ```

2. **Check Reviewer Availability**
   - Check if reviewers are active
   - Check reviewer capacity
   - Check for reviewer on-call status

3. **Identify Bottleneck**
   - Check if reviewers are overwhelmed
   - Check if content quality is poor (requiring more review)
   - Check if review process is inefficient

### Resolution Steps

1. **Immediate Mitigation**
   - Increase auto-publish threshold if safe
   - Assign additional reviewers to queue
   - Escalate to content team for additional reviewers

2. **Fix Root Cause**
   - If reviewers overwhelmed: add more reviewers or improve tools
   - If quality poor: improve generation quality
   - If process inefficient: streamline review process

3. **Verify Resolution**
   - Monitor review queue depth decreasing
   - Monitor review SLA metrics

### Prevention
- Implement auto-publish for high-quality content
- Set up reviewer capacity planning
- Improve content generation quality
- Streamline review UI/process

### Escalation
- If backlog not clearing: escalate to content team lead
- If SLA at risk: notify stakeholders

---

## Cost Management Runbook

### Severity
Warning

### Trigger
- Hourly cost exceeds budget threshold
- Cost per artifact increases significantly

### Impact
- Budget overruns
- Need for cost optimization
- Potential service limitations

### Diagnosis Steps

1. **Check Cost Metrics**
   ```bash
   # Check current cost metrics
   curl -s http://localhost:3200/api/v1/metrics/cost
   ```

2. **Identify Cost Driver**
   - Check cost by model: `content_generation_cost_by_model`
   - Check cost by content type
   - Check token usage patterns

3. **Check for Anomalies**
   - Look for unexpected spikes in generation requests
   - Check for expensive models being used
   - Check for inefficient prompts (high token usage)

### Resolution Steps

1. **Immediate Mitigation**
   - Switch to cheaper AI model if possible
   - Implement rate limiting for expensive operations
   - Disable non-critical generation features

2. **Fix Root Cause**
   - If usage spike: identify source, implement controls
   - If expensive model: evaluate alternatives
   - If inefficient prompts: optimize prompts

3. **Long-term Optimization**
   - Implement caching for repeated generations
   - Use smaller models for simple tasks
   - Implement cost-aware routing

4. **Verify Resolution**
   - Monitor cost metrics returning to normal
   - Monitor cost per artifact

### Prevention
- Set up budget alerts with thresholds
- Implement cost tracking at tenant level
- Regular cost reviews and optimization
- Educate teams on cost implications

### Escalation
- If budget critical: escalate to finance/management
- If cost optimization needed: involve platform team

---

## Cache Performance Runbook

### Severity
Warning

### Trigger
- Cache hit rate < 80% for 30 minutes
- Cache latency increasing

### Impact
- Increased load on database
- Slower response times
- Increased costs (more AI calls)

### Diagnosis Steps

1. **Check Cache Metrics**
   ```bash
   # Check cache hit rate
   curl -s http://localhost:3200/api/v1/metrics/cache | grep hit_rate
   ```

2. **Check Cache Status**
   ```bash
   # Check Redis status
   redis-cli ping
   redis-cli info stats
   ```

3. **Identify Issue**
   - Check if cache is full (eviction happening)
   - Check if cache keys are expiring too quickly
   - Check if cache is being cleared/invalidated frequently
   - Check for cache stampede

### Resolution Steps

1. **Immediate Mitigation**
   - Increase cache size if possible
   - Increase TTL for cache keys
   - Implement cache warming for hot keys

2. **Fix Root Cause**
   - If cache full: increase memory, optimize key size
   - If TTL too short: increase TTL
   - If frequent invalidation: review invalidation logic
   - If cache stampede: implement request coalescing

3. **Verify Resolution**
   - Monitor cache hit rate improving
   - Monitor cache latency

### Prevention
- Implement cache monitoring and alerting
- Right-size cache for workload
- Implement cache warming strategies
- Review cache TTL policies regularly

### Escalation
- If cache infrastructure issue: escalate to platform team
- If persistent performance issue: involve performance team

---

## General Operational Procedures

### Health Checks

```bash
# Check all service health
curl -s http://localhost:3200/api/v1/health

# Check specific service health
curl -s http://localhost:3200/api/v1/health/validation
curl -s http://localhost:3200/api/v1/health/ai
curl -s http://localhost:3200/api/v1/health/generation
```

### Metrics Query Examples

```bash
# Query validation failure rate
curl -s http://localhost:3200/api/v1/metrics/validation | grep failure_rate

# Query queue depth
curl -s http://localhost:3200/api/v1/metrics/queue | grep depth

# Query AI model status
curl -s http://localhost:3200/api/v1/metrics/ai | grep model_status
```

### Log Queries

```bash
# View recent logs
kubectl logs -f deployment/generation-worker --tail=100

# View logs for specific pod
kubectl logs -f <pod-name> --tail=100

# View logs across all workers
kubectl logs -l app=generation-worker --tail=100
```

### Common Commands

```bash
# Scale workers
kubectl scale deployment generation-worker --replicas=10

# Restart deployment
kubectl rollout restart deployment/generation-worker

# Check pod status
kubectl get pods -l app=generation-worker

# Check resource usage
kubectl top pods -l app=generation-worker
```

### On-Call Procedures

1. **Acknowledge Alert**
   - Respond to PagerDuty alert
   - Update incident status
   - Communicate in Slack #alerts-critical

2. **Triage**
   - Assess severity and impact
   - Identify affected services
   - Estimate resolution time

3. **Work Runbook**
   - Follow appropriate runbook
   - Document actions taken
   - Update incident status

4. **Communicate**
   - Regular status updates in Slack
   - Notify stakeholders if critical
   - Escalate if needed

5. **Post-Incident**
   - Write post-mortem
   - Implement prevention measures
   - Update runbooks if needed
