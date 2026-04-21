# Incident Service Ownership

## Owner

**Team:** Security Team (Platform Sub-team)  
**Contact:** security-team@ghatana.com  
**On-Call:** security-oncall@ghatana.com

## Service Purpose

The Incident Service provides emergency incident response capabilities for the Ghatana platform:

- **Kill Switch:** Immediate halt of all agent activity per tenant or globally
- **Graceful Degradation:** Progressive capability reduction during security incidents
- **Incident Management:** HTTP API for activating/deactivating incident controls

## Operational Responsibilities

### Day-to-Day Operations

- Monitor kill switch activations and deactivations
- Monitor degradation mode changes
- Review incident response logs for patterns
- Maintain incident response documentation

### Incident Response

- **P0 Incidents:** Activate global kill switch immediately, notify stakeholders
- **P1 Incidents:** Activate tenant-specific kill switch or degradation mode
- **P2 Incidents:** Monitor and assess need for degradation controls
- **Post-Incident:** Conduct retrospective, update runbooks

### Service Dependencies

- **PostgreSQL:** Kill switch persistence (PostgresKillSwitchService)
- **Redis:** Degradation mode caching (RedisGracefulDegradationManager)
- **platform:java:security:** JWT validation for API access
- **platform:java:http:** HTTP server infrastructure

### Service Consumers

- auth-gateway: Should integrate kill switch to block auth during incidents
- All products: Can query incident status to adjust behavior

## Contribution Guidelines

All changes to the Incident Service require approval from the Security Team (Platform Sub-team).

### Required Approvals

- **Security Team:** All changes
- **Platform Team:** Infrastructure changes (database, Redis)

### Change Process

1. Create issue describing change and incident response impact
2. Submit PR with security team as reviewer
3. Include tests for all incident response scenarios
4. Security team approves after review
5. Update runbooks if behavior changes

## Service Details

### Technology Stack

- **Framework:** ActiveJ HTTP
- **Language:** Java 21
- **Database:** PostgreSQL (kill switch state)
- **Cache:** Redis (degradation mode)
- **Authentication:** JWT (platform:java:security)

### Configuration

- **JWT_SECRET:** Required for API authentication (32+ chars in production)
- **DB_*:** PostgreSQL connection parameters
- **REDIS_*:** Redis connection parameters

### Endpoints

- `POST /api/v1/incident/kill-switch/activate` - Activate kill switch
- `POST /api/v1/incident/kill-switch/deactivate` - Deactivate kill switch
- `GET /api/v1/incident/kill-switch/status/{tenantId}` - Check status
- `POST /api/v1/incident/kill-switch/global/activate` - Global kill switch
- `POST /api/v1/incident/degradation/set` - Set degradation mode
- `GET /api/v1/incident/degradation/status/{tenantId}` - Get degradation mode

### Deployment

- **Replicas:** 2 (can be scaled during incidents)
- **Resource Requests:** 512Mi memory, 500m CPU
- **Resource Limits:** 2Gi memory, 2000m CPU
- **Health Checks:** `/health` endpoint
- **Metrics:** `/metrics` endpoint

## Runbooks

### Global Kill Switch Activation

1. Verify incident severity (P0 only)
2. Notify stakeholders (CTO, product leads, customers)
3. Activate global kill switch via API
4. Monitor incident service logs
5. Document activation reason and incident ID

### Tenant-Specific Kill Switch

1. Verify incident severity (P1+)
2. Notify tenant stakeholders
3. Activate kill switch for tenant
4. Monitor incident service logs
5. Document activation reason

### Degradation Mode Activation

1. Assess incident severity (P2+)
2. Select appropriate degradation mode
3. Activate via API
4. Monitor impact
5. Document degradation reason

## Monitoring

### Critical Alerts

- Global kill switch activation (P0)
- Kill switch activation for top 10 tenants by revenue
- Degradation mode activation
- Service health check failures
- Database connection failures
- Redis connection failures

### Dashboards

- Incident Service Health
- Kill Switch Activations
- Degradation Mode Status
- API Response Times
- Error Rates

## Security Considerations

- JWT authentication required for all mutating endpoints
- Audit logging for all kill switch and degradation operations
- Rate limiting on API endpoints
- IP allowlist for management endpoints (production)
