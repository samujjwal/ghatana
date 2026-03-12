# LOW-LEVEL DESIGN: K-12 PLATFORM SDK

**Module**: K-12 Platform SDK  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Architecture Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Platform SDK provides **unified client libraries for all kernel and domain services**, supporting Go, Java, Python, TypeScript, and C# with protocol abstraction, error translation, and offline support.

**Core Responsibilities**:
- Unified interface per service (one client class per module)
- Protocol abstraction (gRPC/REST/WebSocket transparent to consumer)
- Standardized error translation across all services
- SemVer versioning with backward compatibility guarantees
- Multi-language support (Go, Java, Python, TypeScript, C#)
- Auto-generation from proto/OpenAPI definitions
- Package manager distribution (npm, pip, Maven, NuGet, Go modules)
- Offline mode support for air-gapped environments
- Request/response middleware hooks
- Automatic retry, timeout, and circuit breaker integration (K-18)

**Invariants**:
1. SDK MUST be backward-compatible within a major version
2. All SDK methods MUST propagate trace context (OpenTelemetry)
3. SDK MUST handle authentication transparently (K-01 token refresh)
4. All errors MUST be translated to standardized error types
5. SDK overhead MUST be <1ms per call

### 1.2 Explicit Non-Goals

- ❌ Implementing business logic (SDK is a transport layer)
- ❌ Managing service lifecycles (K-10 Deployment Abstraction)
- ❌ UI framework bindings (frontend uses SDK via API layer)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| All Kernel Services | SDK wraps all K-xx APIs | Target service stable |
| All Domain Services | SDK wraps all D-xx APIs | Target service stable |
| K-01 IAM | Token management, authentication | K-01 stable |
| K-18 Resilience | Circuit breaker, retry policies | K-18 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 SDK Client Architecture

```typescript
// Unified SDK entry point
import { SiddhantaSDK } from '@siddhanta/sdk';

const sdk = SiddhantaSDK.create({
  endpoint: 'https://api.siddhanta.io',
  auth: {
    type: 'JWT',
    token: '<access_token>',
    refreshToken: '<refresh_token>'
  },
  resilience: {
    retryPolicy: 'EXPONENTIAL_BACKOFF',
    maxRetries: 3,
    circuitBreakerThreshold: 5,
    timeout: 5000
  },
  telemetry: {
    enabled: true,
    serviceName: 'my-app'
  }
});

// Service-specific clients
const iam = sdk.iam();          // K-01 IAM Client
const config = sdk.config();    // K-02 Config Client
const rules = sdk.rules();      // K-03 Rules Client
const events = sdk.events();    // K-05 Event Bus Client
const oms = sdk.oms();          // D-01 OMS Client
const risk = sdk.risk();        // D-06 Risk Client
```

### 2.2 Service Client Interfaces

```typescript
// K-01 IAM Client
interface IAMClient {
  authenticate(credentials: Credentials): Promise<AuthToken>;
  refreshToken(refreshToken: string): Promise<AuthToken>;
  getUser(userId: string): Promise<User>;
  createUser(user: CreateUserRequest): Promise<User>;
  assignRole(userId: string, roleId: string): Promise<void>;
  checkPermission(userId: string, resource: string, action: string): Promise<boolean>;
}

// K-02 Config Client
interface ConfigClient {
  get<T>(key: string, context?: ConfigContext): Promise<T>;
  set(key: string, value: any, context?: ConfigContext): Promise<void>;
  watch(key: string, callback: (value: any) => void): Unsubscribe;
}

// K-05 Event Bus Client
interface EventBusClient {
  publish(event: DomainEvent): Promise<void>;
  subscribe(eventType: string, handler: EventHandler): Subscription;
  replay(aggregateId: string, fromSequence?: number): AsyncIterable<DomainEvent>;
}

// D-01 OMS Client
interface OMSClient {
  placeOrder(order: OrderRequest): Promise<OrderResponse>;
  getOrder(orderId: string): Promise<Order>;
  cancelOrder(orderId: string, reason: string): Promise<void>;
  listOrders(filters: OrderFilters): Promise<PaginatedResult<Order>>;
  getPosition(accountId: string, instrumentId: string): Promise<Position>;
}
```

### 2.3 Error Handling

```typescript
// Standardized error hierarchy
class SiddhantaError extends Error {
  code: string;           // e.g., "OMS_E001"
  httpStatus: number;     // e.g., 400
  service: string;        // e.g., "oms-service"
  traceId: string;        // Correlation trace ID
  retryable: boolean;     // Whether the error is retryable
}

class ValidationError extends SiddhantaError { }
class AuthenticationError extends SiddhantaError { }
class AuthorizationError extends SiddhantaError { }
class NotFoundError extends SiddhantaError { }
class RateLimitError extends SiddhantaError { }
class ServiceUnavailableError extends SiddhantaError { }
class TimeoutError extends SiddhantaError { }
```

### 2.4 Middleware Hooks

```typescript
interface RequestMiddleware {
  onRequest(request: SDKRequest): SDKRequest | Promise<SDKRequest>;
}

interface ResponseMiddleware {
  onResponse(response: SDKResponse): SDKResponse | Promise<SDKResponse>;
}

// Usage
sdk.use(new LoggingMiddleware());
sdk.use(new MetricsMiddleware());
sdk.use(new TenantContextMiddleware());
```

---

## 3. DATA MODEL

### 3.1 SDK Configuration Schema

```json
{
  "$schema": "https://siddhanta.io/sdk-config.schema.json",
  "type": "object",
  "properties": {
    "endpoint": { "type": "string", "format": "uri" },
    "auth": {
      "type": "object",
      "properties": {
        "type": { "enum": ["JWT", "API_KEY", "MTLS"] },
        "token": { "type": "string" },
        "refreshToken": { "type": "string" },
        "apiKey": { "type": "string" }
      }
    },
    "resilience": {
      "type": "object",
      "properties": {
        "retryPolicy": { "enum": ["NONE", "FIXED", "EXPONENTIAL_BACKOFF"] },
        "maxRetries": { "type": "integer", "default": 3 },
        "circuitBreakerThreshold": { "type": "integer", "default": 5 },
        "timeout": { "type": "integer", "default": 5000 }
      }
    }
  }
}
```

### 3.2 SDK Versioning

```json
{
  "event_type": "SDKVersionDeprecatedEvent",
  "data": {
    "sdk_language": "typescript",
    "deprecated_version": "1.2.0",
    "minimum_supported": "1.3.0",
    "sunset_date_gregorian": "2026-09-08",
    "sunset_date_bs": "2083-05-24",
    "migration_guide_url": "https://docs.siddhanta.io/sdk/migration/1.3"
  }
}
```

---

## 4. CONTROL FLOW

### 4.1 SDK Request Flow
```
1. Application calls sdk.oms().placeOrder(request)
2. SDK resolves protocol (gRPC preferred, REST fallback)
3. Request middleware pipeline:
   a. Authentication injection (JWT token, auto-refresh if expired)
   b. Tenant context injection
   c. Trace context propagation (OpenTelemetry)
   d. Request serialization
4. Send request to K-11 API Gateway
5. Response middleware pipeline:
   a. Error translation (gRPC status → SiddhantaError)
   b. Response deserialization
   c. Metrics recording
6. Return typed response to application
```

### 4.2 Auto-Generation Pipeline
```
1. Service publishes proto/OpenAPI spec
2. SDK codegen tool generates client code (per language)
3. Run automated compatibility checks (SemVer)
4. If breaking change: bump major version, generate migration guide
5. Publish to package managers (npm, pip, Maven, NuGet, Go modules)
6. Publish SDKVersionDeprecatedEvent for old versions
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Protocol Selection
```
function selectProtocol(service, operation):
  if service.supportsGRPC and environment.supportsHTTP2:
    return GRPC
  elif service.supportsREST:
    return REST
  elif operation.isStreaming:
    return WEBSOCKET
  else:
    return REST  // fallback
```

### 5.2 Token Auto-Refresh
```
function ensureAuthentication(request):
  if token.isExpired():
    if refreshToken.isValid():
      token = iam.refreshToken(refreshToken)
    else:
      throw AuthenticationError("Session expired — re-authenticate")
  request.headers['Authorization'] = 'Bearer ' + token
```

---

## 6. NFR BUDGETS

| Operation | P99 Overhead | Notes |
|-----------|-------------|-------|
| SDK per-call overhead | <1ms | Excluding network latency |
| Authentication injection | <0.5ms | Token cached in memory |
| Trace context propagation | <0.1ms | Header injection only |
| Error translation | <0.1ms | Code mapping only |
| Protocol serialization | <0.5ms | Protobuf or JSON |

**Concurrent Clients**: 10,000+  
**Supported Languages**: TypeScript, Python, Java, Go, C#

---

## 7. SECURITY DESIGN

- **Token Management**: Automatic JWT refresh, secure token storage
- **mTLS**: Supported for service-to-service authentication
- **No Credential Logging**: SDK never logs tokens or API keys
- **Transport Security**: TLS 1.3 enforced for all connections

---

## 8. OBSERVABILITY & AUDIT

### Metrics (auto-collected by SDK)
- `sdk_request_duration_seconds` — Request latency by service/operation
- `sdk_request_total` — Request count by service/operation/status
- `sdk_retry_total` — Retry attempts by service
- `sdk_circuit_breaker_state` — Circuit breaker state by service

---

## 9. EXTENSIBILITY & EVOLUTION

### Extension Points
- `ClientPlugin` interface for custom service clients
- `RequestMiddleware` / `ResponseMiddleware` hooks
- Custom protocol adapters for specialized transports
- `ErrorMapper` extensions for custom error types

### Backward Compatibility
- Minor versions: additive only (new methods, new fields)
- Major versions: breaking changes with migration guide
- Deprecation: 6-month sunset period with warnings

---

## 10. TEST PLAN

### Unit Tests
- Protocol selection logic
- Token auto-refresh (valid, expired, invalid refresh token)
- Error translation for all error types
- Middleware pipeline execution order
- SemVer compatibility checking

### Integration Tests
- End-to-end request to each service via SDK
- gRPC and REST protocol paths
- Circuit breaker behavior under service failure
- Retry behavior with exponential backoff

### Compatibility Tests
- SDK backward compatibility across minor versions
- Cross-language consistency (same request → same response)
- Offline mode behavior (cached responses)

### Performance Tests
- SDK overhead <1ms at 10K concurrent clients
- Token refresh under concurrent load
- Memory usage under sustained load
