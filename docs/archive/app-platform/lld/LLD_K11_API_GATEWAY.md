# LOW-LEVEL DESIGN: K-11 API GATEWAY SERVICE

**Module**: K-11 API Gateway  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Architecture Team

> **Implementation alignment**: Per [../adr/ADR-008_API_GATEWAY_TECHNOLOGY.md](../adr/ADR-008_API_GATEWAY_TECHNOLOGY.md) and [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md), this service standardizes on Envoy/Istio ingress with a custom K-11 control plane.

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The API Gateway Service provides a **single secure entry point for all platform traffic** with authentication validation, rate limiting, dynamic routing, jurisdiction-based routing, and telemetry injection.

**Core Responsibilities**:

- Single ingress point for all external API traffic
- Authentication validation (JWT verification via K-01, mTLS via Istio)
- Rate limiting per tenant, per endpoint, per API key
- Dynamic route registration as microservices are deployed
- Jurisdiction-aware routing (route requests to correct regional instances)
- Telemetry injection (trace_id, correlation_id, tenant_id headers)
- Request size limits and timeout enforcement
- OpenAPI schema validation on incoming requests
- WAF/DDoS protection integration
- API versioning and deprecation management

**Invariants**:

1. All external traffic MUST pass through the API Gateway
2. Every request MUST have a valid authentication token or API key
3. Rate limits MUST be enforced before request forwarding
4. Telemetry headers MUST be injected into every forwarded request
5. All API access MUST be logged for K-07 audit

### 1.2 Explicit Non-Goals

- ❌ Business logic processing (delegated to domain services)
- ❌ Session management (K-01 IAM responsibility)
- ❌ Service mesh internal routing (Istio handles service-to-service)

### 1.3 Dependencies

| Dependency           | Purpose                               | Readiness Gate |
| -------------------- | ------------------------------------- | -------------- |
| K-01 IAM             | JWT validation, API key verification  | K-01 stable    |
| K-02 Config Engine   | Rate limit configs, routing rules     | K-02 stable    |
| K-05 Event Bus       | Gateway events (quota exceeded, etc.) | K-05 stable    |
| K-06 Observability   | Gateway metrics, access logs          | K-06 stable    |
| K-07 Audit Framework | API access audit trail                | K-07 stable    |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 Gateway Management APIs

```yaml
POST /api/v1/gateway/routes
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "route_id": "oms-orders",
  "path_pattern": "/api/v1/orders/**",
  "target_service": "oms-service",
  "target_port": 8080,
  "methods": ["GET", "POST", "PUT", "DELETE"],
  "auth_required": true,
  "rate_limit": {
    "requests_per_second": 1000,
    "burst_size": 2000
  },
  "timeout_ms": 5000,
  "schema_validation": true,
  "openapi_spec_ref": "oms-service.yaml"
}

Response 201:
{
  "route_id": "oms-orders",
  "status": "ACTIVE",
  "created_at_gregorian": "2026-03-08T10:00:00Z",
  "created_at_bs": "2082-11-25"
}
```

```yaml
GET /api/v1/gateway/routes
Authorization: Bearer {admin_token}

Response 200:
{
  "routes": [
    {
      "route_id": "oms-orders",
      "path_pattern": "/api/v1/orders/**",
      "target_service": "oms-service",
      "status": "ACTIVE",
      "request_count_24h": 1250000
    }
  ],
  "total": 45
}
```

```yaml
GET /api/v1/gateway/rate-limits/{tenant_id}
Authorization: Bearer {admin_token}

Response 200:
{
  "tenant_id": "tenant_001",
  "limits": [
    {
      "endpoint": "/api/v1/orders/**",
      "requests_per_second": 1000,
      "current_usage": 450,
      "remaining": 550
    }
  ]
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.gateway.v1;

service GatewayManagementService {
  rpc RegisterRoute(RegisterRouteRequest) returns (RouteResponse);
  rpc DeregisterRoute(DeregisterRouteRequest) returns (RouteResponse);
  rpc ListRoutes(ListRoutesRequest) returns (ListRoutesResponse);
  rpc GetRateLimitStatus(RateLimitRequest) returns (RateLimitResponse);
  rpc UpdateRateLimit(UpdateRateLimitRequest) returns (RateLimitResponse);
}
```

### 2.3 Request Processing Pipeline

```
Client Request
    ↓
[1] TLS Termination
    ↓
[2] WAF/DDoS Check
    ↓
[3] Request Size Validation
    ↓
[4] Authentication (JWT/API Key via K-01)
    ↓
[5] Rate Limit Check (token bucket)
    ↓
[6] Jurisdiction Routing Resolution
    ↓
[7] Schema Validation (OpenAPI)
    ↓
[8] Telemetry Injection (trace_id, correlation_id, tenant_id)
    ↓
[9] Forward to Target Service
    ↓
[10] Response Processing (headers, CORS)
    ↓
[11] Access Log + Metrics
```

---

## 3. DATA MODEL

### 3.1 Storage Tables

```sql
CREATE TABLE gateway_routes (
    route_id VARCHAR(255) PRIMARY KEY,
    path_pattern VARCHAR(500) NOT NULL,
    target_service VARCHAR(255) NOT NULL,
    target_port INTEGER NOT NULL DEFAULT 8080,
    methods TEXT[] NOT NULL DEFAULT '{GET}',
    auth_required BOOLEAN DEFAULT true,
    rate_limit_rps INTEGER DEFAULT 1000,
    rate_limit_burst INTEGER DEFAULT 2000,
    timeout_ms INTEGER DEFAULT 5000,
    schema_validation BOOLEAN DEFAULT false,
    openapi_spec_ref VARCHAR(500),
    jurisdiction_routing JSONB DEFAULT '{}',
    priority INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE rate_limit_state (
    tenant_id VARCHAR(255) NOT NULL,
    endpoint_pattern VARCHAR(500) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER DEFAULT 0,
    PRIMARY KEY (tenant_id, endpoint_pattern, window_start)
);

CREATE TABLE api_access_log (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(255),
    tenant_id VARCHAR(255),
    user_id VARCHAR(255),
    method VARCHAR(10) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    status_code INTEGER NOT NULL,
    response_time_ms INTEGER NOT NULL,
    client_ip VARCHAR(45),
    user_agent TEXT,
    jurisdiction VARCHAR(10),
    timestamp_gregorian TIMESTAMPTZ DEFAULT NOW(),
    timestamp_bs VARCHAR(20)
);
CREATE INDEX idx_access_log_time ON api_access_log(timestamp_gregorian);
CREATE INDEX idx_access_log_tenant ON api_access_log(tenant_id, timestamp_gregorian);
```

### 3.2 Event Schemas

```json
{
  "event_type": "ApiQuotaExceededEvent",
  "data": {
    "tenant_id": "tenant_001",
    "endpoint": "/api/v1/orders",
    "limit_rps": 1000,
    "actual_rps": 1250,
    "action": "THROTTLED"
  }
}
```

```json
{
  "event_type": "RouteRegisteredEvent",
  "data": {
    "route_id": "oms-orders",
    "path_pattern": "/api/v1/orders/**",
    "target_service": "oms-service"
  }
}
```

---

## 4. CONTROL FLOW

### 4.1 Rate Limiting Algorithm (Token Bucket)

```
function checkRateLimit(tenantId, endpoint):
  bucket = getRateLimitBucket(tenantId, endpoint)
  if bucket.tokens > 0:
    bucket.tokens -= 1
    return ALLOW
  else:
    publish ApiQuotaExceededEvent
    return REJECT(429 Too Many Requests)

  // Token refill: rate_limit_rps tokens added per second
```

### 4.2 Jurisdiction Routing

```
function resolveTarget(request, route):
  jurisdiction = extractJurisdiction(request)  // from header, token, or path
  if route.jurisdiction_routing[jurisdiction]:
    return route.jurisdiction_routing[jurisdiction]  // regional instance
  return route.target_service  // default instance
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Authentication Pipeline

```
function authenticate(request):
  if hasHeader('Authorization: Bearer'):
    token = extractBearerToken(request)
    claims = K01.validateJWT(token)
    request.tenant_id = claims.tenant_id
    request.user_id = claims.user_id
  elif hasHeader('X-API-Key'):
    apiKey = extractApiKey(request)
    identity = K01.validateApiKey(apiKey)
    request.tenant_id = identity.tenant_id
  else:
    return REJECT(401 Unauthorized)
```

---

## 6. NFR BUDGETS

| Operation                        | P99 Latency | Throughput | Timeout |
| -------------------------------- | ----------- | ---------- | ------- |
| Request routing (added overhead) | 2ms         | 50,000/s   | N/A     |
| JWT validation                   | 1ms         | 100,000/s  | 50ms    |
| Rate limit check                 | 0.5ms       | 200,000/s  | 10ms    |
| Schema validation                | 2ms         | 50,000/s   | 100ms   |
| Route registration               | 10ms        | 100/s      | 1000ms  |

**Availability**: 99.999%

---

## 7. SECURITY DESIGN

- **TLS Termination**: TLS 1.3 at gateway edge
- **WAF**: OWASP Top 10 ruleset, custom rules
- **DDoS**: Rate limiting + connection limiting per IP
- **mTLS**: Istio mTLS for gateway-to-service traffic
- **CORS**: Configurable CORS policies per route
- **CSP**: Content Security Policy headers

---

## 8. OBSERVABILITY & AUDIT

### Metrics

- `gateway_requests_total` — Total requests by method, path, status
- `gateway_request_duration_seconds` — Request latency histogram
- `gateway_rate_limit_rejections_total` — Rate limit rejections by tenant
- `gateway_active_connections` — Current active connections

### Dashboards

- Real-time traffic dashboard (requests/sec, latency, error rate)
- Per-tenant usage dashboard
- Rate limiting dashboard

---

## 9. EXTENSIBILITY & EVOLUTION

### Extension Points

- Custom authentication providers (T3)
- Custom rate limiting algorithms (T3)
- Gateway middleware plugins (T3)
- Route configuration via T1 Config Packs

---

## 10. TEST PLAN

### Unit Tests

- Token bucket rate limiting algorithm
- Jurisdiction routing resolution
- Authentication pipeline (JWT, API key, missing auth)
- Schema validation against OpenAPI spec

### Integration Tests

- End-to-end request routing to target services
- Rate limiting under concurrent load
- K-01 JWT validation integration
- K-06 access log and metrics verification

### Security Tests

- Invalid JWT → 401
- Expired token → 401
- Rate limit exceeded → 429
- Malformed request → 400
- SQL injection in path → WAF block

### Performance Tests

- 50K TPS sustained with <2ms added latency
- Rate limit evaluation at 200K/s
- Connection handling under DDoS simulation
