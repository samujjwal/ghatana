# DCMAAR Secure Connectors – Implementation Backlog

**Milestone**: Connector-Based Secure Communication for All Products  
**Version**: 1.0  
**Last Updated**: November 2025

This backlog catalogs all planned work for implementing secure connections using the unified connectors library across DCMAAR, Guardian, and Device Health. All connections to external services (backends, APIs, event streams) **MUST** use connectors to ensure:

- Consistent security policies
- Observable connections
- Resilience patterns (retry, circuit breaker)
- Tenant isolation
- Audit trail

---

## Executive Summary

### Problem Statement

Current implementations lack:

- **Unified connection management** - Each product handles security differently
- **Observable connections** - No central metrics/logging for external calls
- **Resilience** - Failures cascade without retry/circuit breaker logic
- **Tenant isolation** - Connection context may leak across tenants
- **Auditability** - No comprehensive audit trail for external connections

### Solution: Connector-Based Communication

All external connections (HTTP, WebSocket, MQTT, gRPC, PostgreSQL, Redis, etc.) route through the `@dcmaar/connectors` library to:

1. **Centralize security** - All mTLS/certificates managed in one place
2. **Enforce resilience** - Retry policies, circuit breakers, pooling automatic
3. **Provide observability** - All connections emit metrics, traces, logs
4. **Ensure isolation** - Tenant context threaded through all operations
5. **Enable audit** - Complete connection history for compliance

### Browser Exception

Browser extensions (Guardian, Device Health) cannot use Node.js connectors (grpc, mqtt, nats, ipc, mtls, mqtts) but **CAN** use:

- **Http**: fetch() API (automatic TLS via browser)
- **WebSocket**: native WebSocket (automatic TLS via browser)
- **FileSystem**: Chrome Storage API (extension sandbox)
- **Native**: chrome.\* APIs (extension functionality)

**Solution**: Browser extensions use **server-side TLS + OAuth2** with backend proxy pattern for mTLS connections.

---

## Security Architecture Overview

### 5 Secure Communication Patterns

| Pattern                    | Browser    | Backend        | Use Case          | Security Level |
| -------------------------- | ---------- | -------------- | ----------------- | -------------- |
| **1. Server-Side TLS**     | ✅ Auto    | ✅ Built-in    | General HTTPS     | ⭐⭐⭐         |
| **2. OAuth2/OIDC**         | ✅ Token   | ✅ Service     | Multi-tenant auth | ⭐⭐⭐⭐       |
| **3. Proxy Pattern**       | ✅ REST    | ✅ mTLS        | mTLS via proxy    | ⭐⭐⭐⭐       |
| **4. Certificate Pinning** | ✅ Custom  | ✅ Native      | CA compromise     | ⭐⭐⭐⭐⭐     |
| **5. E2E Encryption**      | ✅ AES-GCM | ✅ Field-level | Data in transit   | ⭐⭐⭐⭐⭐     |

**Recommended for Guardian**: Server-Side TLS + OAuth2 + Retry Policy + Circuit Breaker

See: `/libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md` for diagrams

---

## Work Items by Phase

### Phase 1: Foundation (Weeks 1-2)

Enable connector usage across all products with standard configuration patterns.

#### 1.1 Connector Initialization Framework

- **Status**: 🔴 Not Started
- **Owner**: Backend Lead
- **Files**: `core/connector-init/ConnectorInitializer.java`
- **Work**:
  - [ ] Create centralized connector factory for Java backend
  - [ ] Implement connector configuration loading from environment/vault
  - [ ] Support all 10 connector types with proper dependency injection
  - [ ] Wire connector metrics collection into observability pipeline
  - [ ] Create init() method to verify connector availability at startup
  - [ ] Document connector setup checklist
- **Success Criteria**:
  - [ ] All 10 connector types initialize without errors
  - [ ] Metrics emit correctly to observability stack
  - [ ] No TypeScript errors in browser connectors
  - [ ] All configurations pass validation
- **Documentation**: See `SECURE_CONNECTORS_BOOTSTRAP.md` (to create)
- **Example**:
  ```java
  ConnectorInitializer init = new ConnectorInitializer();
  init.load(environment, vaultService)
      .registerMetrics(metricsCollector)
      .verify()
      .start();
  ```

#### 1.2 Guardian Browser Connector Setup

- **Status**: 🟡 In Progress (4-type restriction done, setup needed)
- **Owner**: Frontend Lead
- **Files**: `apps/guardian/apps/browser-extension/src/connectors/`
- **Work**:
  - [ ] Implement browser connector factory for Http, WebSocket, FileSystem, Native
  - [ ] Create service worker connector bridge (service-worker ↔ UI communication)
  - [ ] Wire browser connectors into extension initialization
  - [ ] Setup Chrome Storage API for FileSystem connector
  - [ ] Create resilience layer (retry, circuit breaker) in browser context
  - [ ] Test all 4 connector types in real Chrome extension
- **Success Criteria**:
  - [ ] All 4 browser connectors functional
  - [ ] Service worker handles connection errors gracefully
  - [ ] Chrome Storage API working for local filesystem operations
  - [ ] Retry policy works for flaky WebSocket connections
  - [ ] Browser bundle size optimized (no Node.js modules)
- **Documentation**: See `apps/guardian/SECURE_COMMUNICATION_SETUP.md`
- **Test Plan**: `apps/guardian/apps/browser-extension/__tests__/connectors.test.ts`

#### 1.3 Device Health Connector Setup

- **Status**: 🔴 Not Started
- **Owner**: Frontend Lead
- **Files**: `apps/device-health/src/connectors/`
- **Work**:
  - [ ] Implement browser connectors for Device Health extension
  - [ ] Create native OS API bridge (chrome.runtime.native messages)
  - [ ] Setup FileSystem connector for device health metrics storage
  - [ ] Wire WebSocket for real-time metric streaming
  - [ ] Create retry logic for unreliable OS API calls
  - [ ] Test across Windows/macOS/Linux
- **Success Criteria**:
  - [ ] Connector calls to native APIs working
  - [ ] FileSystem connector persists metrics correctly
  - [ ] WebSocket streaming at <100ms latency
  - [ ] Works on all three OS platforms
- **Documentation**: `apps/device-health/CONNECTOR_SETUP.md` (to create)

#### 1.4 Installation-Time Connector Configuration

- **Status**: 🔴 Not Started
- **Owner**: DevOps + Backend Lead
- **Files**: `scripts/connector-setup.sh`, `installer/connector-config.json`
- **Work**:
  - [ ] Create installation script that prompts for connector types needed
  - [ ] Generate connector configuration template (Docker, K8s, standalone)
  - [ ] Setup secret management for mTLS certificates
  - [ ] Create quick-start guide for common scenarios
  - [ ] Document environment variable format for each connector
  - [ ] Validate configuration at startup
- **Success Criteria**:
  - [ ] Script handles all 10 connector types
  - [ ] Configuration validates correctly
  - [ ] Documentation covers Docker, K8s, local dev
  - [ ] Team can setup new environment in <30 minutes
- **Documentation**: See `INSTALLATION_GUIDE.md` (to create)
- **Example Config**:
  ```json
  {
    "connectors": {
      "http": { "tlsVerify": true, "timeout": 30000 },
      "mqtt": { "urls": ["mqtt://broker1:1883"], "username": "${MQTT_USER}" },
      "grpc": { "endpoints": ["grpc.service:9090"], "tls": true }
    }
  }
  ```

---

### Phase 2: Backend Integration (Weeks 3-4)

Migrate backend services to use connectors for all external connections.

#### 2.1 Guardian Backend Connector Migration

- **Status**: 🔴 Not Started
- **Owner**: Backend Lead
- **Services**:
  - [ ] `guardian-backend/PolicyService` → Use connectors for API calls
  - [ ] `guardian-backend/DeviceManagerService` → Device telemetry via connectors
  - [ ] `guardian-backend/NotificationService` → Email/Slack via connectors
  - [ ] Database connections via PostgreSQL connector
  - [ ] Redis cache via Redis connector
- **Work**:
  - [ ] Replace direct HTTP calls with HttpConnector
  - [ ] Replace database `java.sql` with DatabaseConnector
  - [ ] Replace Redis client with RedisConnector
  - [ ] Add connection pooling via connector factories
  - [ ] Wire resilience (retry, circuit breaker) per service
  - [ ] Update integration tests to mock connectors
- **Success Criteria**:
  - [ ] All external calls go through connectors
  - [ ] Metrics show connector usage
  - [ ] Integration tests pass
  - [ ] No direct third-party client usage
- **Metrics to Emit**:
  ```
  guardian.connector.policy_fetch.count
  guardian.connector.policy_fetch.duration_ms
  guardian.connector.device_sync.errors
  guardian.connector.notification.retry_count
  ```

#### 2.2 Device Health Backend Connector Migration

- **Status**: 🔴 Not Started
- **Owner**: Backend Lead
- **Services**:
  - [ ] `device-health-backend/MetricsCollectorService` → Metrics ingestion
  - [ ] `device-health-backend/AlertingService` → Alert distribution
  - [ ] Database and Redis connections
- **Work**:
  - [ ] Migrate to connectors (same pattern as Guardian)
  - [ ] Add resilience for device metric ingestion
  - [ ] Setup alerts via connectors (email, Slack, webhook)
- **Success Criteria**:
  - [ ] All external calls use connectors
  - [ ] Metrics available in observability stack
  - [ ] Alerting works end-to-end

#### 2.3 DCMAAR Framework Connector Support

- **Status**: 🔴 Not Started
- **Owner**: Tech Lead
- **Files**: `core/connectors/`, `core/observability/ConnectorMetrics.java`
- **Work**:
  - [ ] Create `BaseConnectorService` abstract class with common logic
  - [ ] Implement retry/circuit breaker templates
  - [ ] Add connector context propagation (tenant, user, request ID)
  - [ ] Create audit logging for all connector operations
  - [ ] Document connector usage patterns for new services
- **Success Criteria**:
  - [ ] 5+ services using connectors
  - [ ] Consistent retry/circuit breaker policies
  - [ ] Audit logs show all external connections
- **Documentation**: `core/CONNECTOR_DEVELOPER_GUIDE.md` (to create)

---

### Phase 3: Security Patterns Implementation (Weeks 5-6)

Implement each of the 5 secure communication patterns in production services.

#### 3.1 Server-Side TLS (Pattern #1)

- **Status**: 🟢 Complete (Browser automatic, backend HTTPS by default)
- **Work**:
  - [x] Browser: Automatic HTTPS via fetch() API
  - [x] Backend: HTTPS default for HttpConnector
  - [ ] Add configuration option to disable TLS (development only)
  - [ ] Document certificate chain validation
- **Success Criteria**:
  - [x] All production endpoints HTTPS
  - [ ] TLS 1.2+ enforced
  - [ ] Certificate validation enabled

#### 3.2 OAuth2/OIDC Authentication (Pattern #2)

- **Status**: 🔴 Not Started
- **Owner**: Security + Backend Lead
- **Files**: `core/oauth2/OAuth2Connector.java`, `apps/guardian/src/oauth2/`
- **Work**:
  - [ ] Implement OAuth2ConnectorFactory with standard providers (Google, Azure, GitHub)
  - [ ] Guardian browser: Token storage in Chrome extension storage
  - [ ] Guardian service worker: Token refresh logic
  - [ ] Backend: JWT validation with public key rotation
  - [ ] Setup token cache with TTL-based refresh
  - [ ] Create multi-account support in browser extension
  - [ ] Add PKCE flow for browser context
- **Success Criteria**:
  - [ ] Guardian login flow working
  - [ ] Tokens refresh automatically
  - [ ] Multiple user accounts in same extension
  - [ ] Token expiry handled gracefully
- **Documentation**: See `SECURE_BROWSER_IMPLEMENTATION.md` (Section: OAuth2)
- **Test Coverage**:
  - [ ] Token refresh without user interaction
  - [ ] Token expiry and recovery
  - [ ] Multi-account scenarios

#### 3.3 Proxy Pattern for mTLS (Pattern #3)

- **Status**: 🔴 Not Started
- **Owner**: Infra + Backend Lead
- **Architecture**:
  ```
  Browser Extension --HTTP--> Backend Proxy --mTLS--> External Service
  ```
- **Work**:
  - [ ] Create proxy service in backend (Nginx/Envoy or Java)
  - [ ] Configure mTLS certificate loading for external services
  - [ ] Browser connector: Send requests to proxy instead of external service
  - [ ] Proxy: Forward with client certificates
  - [ ] Setup metrics for proxy operations
  - [ ] Create proxy deployment templates (Docker, K8s)
  - [ ] Document proxy configuration for new external integrations
- **Success Criteria**:
  - [ ] Proxy forwards requests correctly
  - [ ] mTLS certificates loaded automatically
  - [ ] Metrics show proxy throughput
  - [ ] Zero certificate-related failures
- **Documentation**: See `SECURE_BROWSER_IMPLEMENTATION.md` (Section: Proxy Pattern)
- **Example Config**:
  ```yaml
  proxy:
    routes:
      - path: /api/external/*
        backend: https://external-api.example.com:8443
        clientCert: /secrets/client.crt
        clientKey: /secrets/client.key
        caBundle: /secrets/ca.crt
  ```

#### 3.4 Certificate Pinning (Pattern #4)

- **Status**: 🔴 Not Started
- **Owner**: Security Lead
- **Files**: `core/pinning/CertificatePinningConnector.java`, `apps/guardian/src/pinning/`
- **Work**:
  - [ ] Implement certificate pinning in HttpConnector
  - [ ] Browser: Store pinned certs in Chrome extension storage
  - [ ] Backend: Support both public key and certificate pinning
  - [ ] Create pin update mechanism (fetch from CDN weekly)
  - [ ] Add pinning validation metrics
  - [ ] Document pin generation and rotation process
  - [ ] Create backup pins for emergencies
- **Success Criteria**:
  - [ ] Pinning prevents CA compromise attacks
  - [ ] Pins update without extension restart
  - [ ] Metrics show pin validation rate
  - [ ] Fallback pins work in emergency
- **Documentation**: See `SECURE_BROWSER_IMPLEMENTATION.md` (Section: Certificate Pinning)
- **Example Implementation**:

  ```typescript
  const pinnedCerts = {
    "api.dcmaar.io": ["sha256/AAAA...", "sha256/BBBB..."],
    "backup-pin": "sha256/CCCC...",
  };

  const result = await pinningConnector.validateAndFetch(url, pinnedCerts);
  ```

#### 3.5 End-to-End Encryption (Pattern #5)

- **Status**: 🔴 Not Started
- **Owner**: Security Lead
- **Files**: `core/encryption/E2EEncryptionConnector.java`, `apps/guardian/src/e2e/`
- **Work**:
  - [ ] Implement field-level encryption for sensitive data
  - [ ] Use AES-256-GCM with authenticated encryption
  - [ ] Browser: Encrypt before sending, decrypt after receiving
  - [ ] Backend: Support encryption at rest for encrypted fields
  - [ ] Create key management for field encryption keys
  - [ ] Add performance benchmarks (encryption overhead)
  - [ ] Document which fields should be E2E encrypted
  - [ ] Create key rotation procedures
- **Success Criteria**:
  - [ ] Sensitive data encrypted in transit
  - [ ] Decryption transparent to business logic
  - [ ] Performance overhead <5%
  - [ ] Key rotation works without downtime
- **Documentation**: See `SECURE_BROWSER_IMPLEMENTATION.md` (Section: E2E Encryption)
- **Fields to Encrypt**:
  - Guardian: Device policies, user authentication tokens, device identification
  - Device Health: System metrics containing PII, hardware identifiers
- **Example**:

  ```java
  E2EEncryptionConnector connector = factory.createE2EEncryption()
      .withEncryptedFields(List.of("policy", "deviceId", "authToken"))
      .withKeyProvider(keyService)
      .build();

  ConnectorResponse response = connector.post("/policy", payload);
  // payload automatically encrypted; response automatically decrypted
  ```

---

### Phase 4: Testing & Validation (Weeks 7-8)

Build comprehensive test suites for connector implementations.

#### 4.1 Browser Connector Integration Tests

- **Status**: 🔴 Not Started
- **Owner**: Frontend Lead
- **Test Files**: `apps/guardian/apps/browser-extension/__tests__/connectors.integration.test.ts`
- **Tests**:
  - [ ] Http connector with real HTTPS endpoint
  - [ ] WebSocket connector with real server
  - [ ] FileSystem connector with Chrome Storage API
  - [ ] Native connector with chrome.\* APIs
  - [ ] Connection error recovery (retry logic)
  - [ ] Circuit breaker engagement and recovery
  - [ ] Timeout handling
  - [ ] Concurrent connections
- **Coverage Target**: >90%
- **Documentation**: `apps/guardian/CONNECTOR_TEST_GUIDE.md` (to create)

#### 4.2 Backend Connector Integration Tests

- **Status**: 🔴 Not Started
- **Owner**: Backend Lead
- **Test Coverage**:
  - [ ] All 10 connector types with Testcontainers
  - [ ] mTLS connections (using test certificates)
  - [ ] OAuth2 flow with mock provider
  - [ ] Certificate pinning with real certs
  - [ ] E2E encryption with key rotation
  - [ ] Failure scenarios (timeout, connection refused, etc.)
  - [ ] Resilience patterns (retry backoff, circuit breaker)
  - [ ] Metrics emission
- **Test Matrix**:
  - Local development (H2, in-memory Redis)
  - Testcontainers (PostgreSQL, Redis, Kafka, MQTT)
  - Mock external services (HTTP stubs, gRPC mocks)
- **Documentation**: `core/CONNECTOR_TEST_GUIDE.md` (to create)

#### 4.3 End-to-End Security Tests

- **Status**: 🔴 Not Started
- **Owner**: QA Lead
- **Scenarios**:
  - [ ] Guardian → Backend → External API with OAuth2
  - [ ] Guardian → Backend Proxy → mTLS Service
  - [ ] Device Health → Backend → Database via encrypted connector
  - [ ] Browser extension with certificate pinning
  - [ ] Connector fallback when primary fails
  - [ ] Tenant isolation across connections
  - [ ] Audit logs record all connections
- **Tools**: Cypress (browser), Jest (unit), Testcontainers (integration)
- **Coverage Target**: 100% of happy path + top 5 error scenarios

#### 4.4 Performance & Load Testing

- **Status**: 🔴 Not Started
- **Owner**: DevOps Lead
- **Tests**:
  - [ ] 1000 concurrent Http connections
  - [ ] WebSocket stress test (sustained 5k messages/sec)
  - [ ] Connection pool efficiency
  - [ ] Memory leaks under sustained load
  - [ ] Circuit breaker recovery performance
  - [ ] Encryption overhead measurement
  - [ ] Latency percentiles (p50, p95, p99)
- **Targets**:
  - Http: <50ms p99
  - WebSocket: <20ms p99
  - mTLS: <100ms p99
  - Encryption: <10% overhead
- **Load Test Config**: `products/dcmaar/tests/load/connector-load-test.js` (to create)

---

### Phase 5: Documentation & Runbooks (Weeks 9-10)

Create comprehensive guides for operations and development.

#### 5.1 Developer Quick Start Guides

- **Status**: 🔴 Not Started
- **Files**:
  - [ ] `docs/CONNECTOR_QUICKSTART.md` - 5-minute setup
  - [ ] `docs/CONNECTOR_EXAMPLES.md` - Common scenarios
  - [ ] `docs/SECURITY_PATTERNS_CHOICE_GUIDE.md` - Pattern selection
- **Content**:
  - [ ] Setup connector for new service (step-by-step)
  - [ ] Add OAuth2 to existing service
  - [ ] Setup certificate pinning
  - [ ] Test connector locally
  - [ ] Debug connection issues
  - [ ] Common mistakes and fixes

#### 5.2 Operations Runbooks

- **Status**: 🔴 Not Started
- **Files**:
  - [ ] `docs/CONNECTOR_TROUBLESHOOTING.md` - Issue diagnosis
  - [ ] `docs/CERTIFICATE_ROTATION.md` - Cert management
  - [ ] `docs/KEY_ROTATION.md` - Encryption key management
  - [ ] `docs/CONNECTOR_MONITORING.md` - Metrics and alerts
  - [ ] `docs/CONNECTOR_DEPLOYMENT.md` - K8s and Docker
- **Runbooks**:
  - [ ] Debugging "connection refused" errors
  - [ ] Certificate expiry response
  - [ ] mTLS certificate rotation
  - [ ] Service account key rotation
  - [ ] Emergency circuit breaker reset
  - [ ] Connector health check interpretation

#### 5.3 Architecture Decision Records (ADRs)

- **Status**: 🔴 Not Started
- **ADRs**:
  - [ ] `docs/adr/CONNECTOR_ARCHITECTURE.md` - Why connectors abstraction
  - [ ] `docs/adr/SECURITY_PATTERNS.md` - Why 5 patterns chosen
  - [ ] `docs/adr/BROWSER_RESTRICTIONS.md` - Why 4-type browser limitation
  - [ ] `docs/adr/OAUTH2_CHOICE.md` - Why OAuth2 for Guardian
  - [ ] `docs/adr/PROXY_PATTERN.md` - Why proxy for mTLS

#### 5.4 API Documentation

- **Status**: 🔴 Not Started
- **Files**:
  - [ ] `@dcmaar/connectors` JSDoc → API docs
  - [ ] `ConnectorFactory` usage guide
  - [ ] Each connector type interface documentation
  - [ ] Resilience policy configuration
  - [ ] Metrics payload documentation
- **Tools**: Typedoc for TypeScript, Dokka for Java

---

### Phase 6: Production Hardening (Weeks 11-12)

Final security audit, performance optimization, and deployment readiness.

#### 6.1 Security Audit

- **Status**: 🔴 Not Started
- **Owner**: Security Lead
- **Audit Scope**:
  - [ ] Static analysis for credential leaks
  - [ ] Certificate chain validation
  - [ ] TLS version enforcement (1.2+)
  - [ ] Cipher suite audit
  - [ ] Key material protection (no logs)
  - [ ] OAuth2 token security (no localStorage, secure storage only)
  - [ ] CORS policy validation
  - [ ] CSRF protection review
  - [ ] SQL injection prevention in database connector
  - [ ] Dependency vulnerability scan
- **Tools**: Trivy, OWASP ZAP, SonarQube
- **Success Criteria**:
  - [ ] Zero critical vulnerabilities
  - [ ] Zero high-severity findings
  - [ ] All mitigations documented

#### 6.2 Performance Optimization

- **Status**: 🔴 Not Started
- **Owner**: Backend Lead
- **Optimizations**:
  - [ ] Connection pooling tuning
  - [ ] Circuit breaker thresholds optimization
  - [ ] Retry backoff curves
  - [ ] Encryption algorithm selection (AES-NI support)
  - [ ] Compression for large payloads
  - [ ] DNS caching
  - [ ] Keep-alive settings
  - [ ] Buffer sizing
- **Benchmarks**:
  - [ ] Compare before/after latencies
  - [ ] Memory footprint analysis
  - [ ] CPU usage profiling
  - [ ] GC impact measurement

#### 6.3 Disaster Recovery & Failover

- **Status**: 🔴 Not Started
- **Owner**: DevOps Lead
- **Procedures**:
  - [ ] Primary backend unavailable → fallback connector
  - [ ] Certificate expiry → emergency backup pins
  - [ ] Service account key compromised → revocation and rotation
  - [ ] Circuit breaker stuck open → manual intervention
  - [ ] Database connector connection pool exhausted → recovery
  - [ ] WebSocket connection lost → automatic reconnection
- **RTO/RPO Targets**:
  - [ ] Service failure → 30 second recovery (circuit breaker)
  - [ ] Certificate expiry → 5 minute response (backup pins)
  - [ ] Key compromise → 1 hour full rotation

#### 6.4 Deployment & Rollout

- **Status**: 🔴 Not Started
- **Owner**: DevOps Lead
- **Checklist**:
  - [ ] Canary deployment (10% of traffic)
  - [ ] Metrics validation (error rates, latencies)
  - [ ] Gradual rollout (10% → 50% → 100%)
  - [ ] Rollback procedure (if issues detected)
  - [ ] Blue-green deployment option
  - [ ] Feature flags for connector toggle
  - [ ] Kill switch documentation
  - [ ] On-call runbook for issues
- **Validation Gates**:
  - [ ] Error rate <0.1%
  - [ ] Latency p99 <100ms
  - [ ] Retry success rate >99%
  - [ ] Circuit breaker activations <5/day

---

## Cross-Cutting Concerns

### Observability & Metrics

All connectors emit standardized metrics:

```
{prefix}.connector.{type}.{operation}.{metric}

Examples:
- guardian.connector.http.fetch.count
- guardian.connector.http.fetch.duration_ms
- guardian.connector.http.fetch.error.count
- guardian.connector.http.fetch.retry.count
- device_health.connector.websocket.message.count
- dcmaar_framework.connector.database.query.duration_ms
```

**Metrics to Collect**:

- Operation count (total calls)
- Duration (p50, p95, p99)
- Error count (by type)
- Retry count
- Circuit breaker state changes
- Connection pool utilization
- TLS handshake duration
- Authentication token refresh rate
- Encryption/decryption time

**Dashboard**: Create centralized connector observability dashboard in Prometheus/Grafana

### Audit Logging

All connector operations logged with:

```json
{
  "timestamp": "2025-11-15T10:30:00Z",
  "connector_type": "http",
  "tenant_id": "tenant-123",
  "user_id": "user-456",
  "operation": "fetch",
  "endpoint": "/api/policy",
  "method": "GET",
  "status_code": 200,
  "duration_ms": 42,
  "retry_count": 0,
  "circuit_breaker_state": "closed",
  "tls_version": "1.3",
  "result": "success"
}
```

**Audit Storage**: Write to centralized audit log (PostgreSQL audit table, Elasticsearch, or CloudWatch)

### Tenant Isolation

Every connector operation includes tenant context:

```typescript
const response = await connector
  .withTenant("tenant-123")
  .post("/policy", payload);

// Internally:
// 1. Validate tenant has access to this endpoint
// 2. Add tenant header to request
// 3. Audit log includes tenant
// 4. Metrics tagged with tenant
// 5. Certificates loaded per tenant (if applicable)
```

---

## Success Criteria

### Phase 1 Completion (Weeks 1-2)

- [ ] Connector initialization works across all products
- [ ] Browser connectors functional (4 types)
- [ ] Installation script works
- [ ] Documentation complete
- [ ] Zero TypeScript errors
- [ ] Browser bundle optimized

### Phase 2 Completion (Weeks 3-4)

- [ ] 5+ backend services migrated to connectors
- [ ] All external calls go through connectors
- [ ] Metrics emit correctly
- [ ] Integration tests pass

### Phase 3 Completion (Weeks 5-6)

- [ ] OAuth2 working in Guardian
- [ ] mTLS via proxy pattern functional
- [ ] Certificate pinning implemented
- [ ] E2E encryption working
- [ ] All 5 patterns documented and tested

### Phase 4 Completion (Weeks 7-8)

- [ ] 90%+ test coverage
- [ ] All error scenarios handled
- [ ] Performance benchmarks published
- [ ] Load tests pass

### Phase 5 Completion (Weeks 9-10)

- [ ] All documentation complete
- [ ] Runbooks written and tested
- [ ] ADRs published
- [ ] API docs generated

### Phase 6 Completion (Weeks 11-12)

- [ ] Security audit passed
- [ ] Performance targets met
- [ ] Disaster recovery tested
- [ ] Production deployment ready

---

## Risks & Mitigation

| Risk                                 | Impact                   | Mitigation                                   |
| ------------------------------------ | ------------------------ | -------------------------------------------- |
| Browser security restrictions evolve | Connector APIs may break | Regular security scanning, fallback patterns |
| Certificate management complexity    | Operations overhead      | Automated rotation, monitoring, runbooks     |
| Performance regression               | User experience degraded | Continuous benchmarking, performance gates   |
| Key leakage                          | Complete compromise      | Key rotation procedures, audit logging       |
| Connector adoption resistance        | Inconsistent usage       | Training, examples, automated enforcement    |
| OAuth2 token expiry handling bugs    | Authentication failures  | Comprehensive testing, graceful fallbacks    |
| mTLS certificate expiry              | Service unavailable      | Automated rotation, backup certificates      |

---

## Dependencies & Prerequisites

### External Services

- [ ] OAuth2 provider setup (Google/Azure/GitHub)
- [ ] mTLS certificate infrastructure (PKI, rotation)
- [ ] Observability stack (Prometheus, Grafana, ELK)
- [ ] Vault or secret management service

### Internal Dependencies

- [ ] `@dcmaar/connectors` library (browser version complete ✓, backend WIP)
- [ ] `core/observability` for metrics collection
- [ ] `core/resilience` for retry/circuit breaker
- [ ] Database migration framework for audit tables
- [ ] Feature flag system for gradual rollout

### Team Resources

- Backend engineers: 2 FTE
- Frontend engineers: 1.5 FTE
- Security engineer: 0.5 FTE
- QA engineer: 1 FTE
- DevOps engineer: 0.5 FTE
- **Total**: 5.5 FTE over 12 weeks

---

## Related Documentation

### Security Documentation

- `/libs/typescript/connectors/SECURE_COMMUNICATION_BROWSER.md` - 5 patterns detailed
- `/libs/typescript/connectors/SECURE_BROWSER_IMPLEMENTATION.md` - Implementation guide
- `/apps/guardian/SECURE_COMMUNICATION_SETUP.md` - Guardian service worker setup
- `/libs/typescript/connectors/SECURE_COMMUNICATION_SUMMARY.md` - Comprehensive overview
- `/libs/typescript/connectors/MTLS_MQTTS_QUICK_REFERENCE.md` - Quick reference
- `/libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md` - Visual diagrams

### Connector Documentation

- `/libs/typescript/connectors/BROWSER_CONNECTORS.md` - Configuration guide
- `/libs/typescript/connectors/BROWSER_IMPLEMENTATION_SUMMARY.md` - Technical summary
- `/libs/typescript/connectors/QUICKSTART.md` - Quick reference
- `/libs/typescript/connectors/IMPLEMENTATION_CHECKLIST.md` - Verification

### Architecture

- `/products/dcmaar/docs/DCMAAR_BACKLOG.md` - Main backlog
- `/products/dcmaar/docs/GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md`
- `/products/dcmaar/docs/DEVICE_HEALTH_CONTRACTS.md`

---

## Approval & Sign-Off

| Role          | Name             | Date | Status     |
| ------------- | ---------------- | ---- | ---------- |
| Tech Lead     | [To be assigned] |      | 🔴 Pending |
| Security Lead | [To be assigned] |      | 🔴 Pending |
| PM            | [To be assigned] |      | 🔴 Pending |

---

**Version History**

| Version | Date       | Changes                       |
| ------- | ---------- | ----------------------------- |
| 1.0     | 2025-11-15 | Initial comprehensive backlog |

**Last Updated**: November 15, 2025  
**Next Review**: After Phase 1 completion (Week 2)
