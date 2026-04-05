# Detailed File-by-File Test Plan
## Platform, Shared-Services, Audio-Video, and Data-Cloud

**Plan Date:** April 5, 2026  
**Scope:** platform/, shared-services/, products/audio-video/, products/data-cloud/  
**Standard:** File-level granularity with specific test requirements

---

## Table of Contents

1. [Platform Java](#1-platform-java)
2. [Platform TypeScript](#2-platform-typescript)
3. [Shared-Services](#3-shared-services)
4. [Audio-Video](#4-audio-video)
5. [Data-Cloud](#5-data-cloud)

---

## 1. Platform Java

### 1.1 Core Package (platform/java/core)

**Current Coverage:** 25%  
**Target Coverage:** 80%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/validation/ValidationUtilsTest.java`
- Test all validation utility methods
- Test null/empty string validation
- Test email validation with edge cases
- Test phone number validation
- Test URL validation
- Test UUID validation
- Test numeric range validation
- **Assertions:** Validate correct validation results, error messages

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/strings/StringUtilsTest.java`
- Test string manipulation methods
- Test null-safe operations
- Test trimming, padding, case conversion
- Test substring operations with boundary values
- Test regex operations
- **Assertions:** Validate output correctness, null safety

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/json/JsonUtilsTest.java`
- Test JSON parsing and serialization
- Test null handling
- Test invalid JSON error handling
- Test nested object serialization
- Test array handling
- **Assertions:** Validate correct JSON structure, error propagation

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/dates/DateUtilsTest.java`
- Test date parsing and formatting
- Test timezone handling
- Test date arithmetic (add, subtract)
- Test boundary dates (min/max)
- Test leap year handling
- **Assertions:** Validate correct date calculations, timezone conversions

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/collections/CollectionUtilsTest.java`
- Test collection operations
- Test null-safe collection methods
- Test filtering, mapping, reducing
- Test empty collection handling
- Test immutable collections
- **Assertions:** Validate collection transformations, null safety

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/paging/PaginationUtilsTest.java`
- Test pagination calculations
- Test offset/limit validation
- Test page number calculation
- Test total pages calculation
- Test boundary conditions (first page, last page)
- **Assertions:** Validate correct pagination math, edge cases

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/exception/ErrorCodeTest.java`
- Test all error code values
- Test error code categorization
- Test error code to string conversion
- Test error code severity levels
- **Assertions:** Validate error code correctness, categorization

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/exception/PlatformExceptionTest.java` ✅
- Test exception creation with error codes
- Test exception message formatting
- Test exception chaining
- Test exception serialization
- **Assertions:** Validate exception behavior, message formatting

**File:** `platform/java/core/src/test/java/com/ghatana/platform/core/client/AsyncClientContractTest.java` ✅
- Test start/stop lifecycle
- Test rejected Promise handling
- Test timeout behavior
- Test concurrent start/stop
- **Assertions:** Validate lifecycle correctness, error handling

---

### 1.2 Database Package (platform/java/database)

**Current Coverage:** 45%  
**Target Coverage:** 85%  
**Priority:** P0

#### Test Files to Create

**File:** `platform/java/database/src/test/java/com/ghatana/platform/database/adapter/PostgreSQLAdapterIntegrationTest.java` ✅
- Use testcontainers for real PostgreSQL
- Test connection pool behavior
- Test query execution with real data
- Test transaction commit/rollback
- Test connection timeout
- Test connection leak detection
- **Assertions:** Validate real DB operations, connection management

**File:** `platform/java/database/src/test/java/com/ghatana/platform/database/adapter/MySQLAdapterIntegrationTest.java` ✅
- Use testcontainers for real MySQL
- Test connection pool behavior
- Test query execution with real data
- Test transaction commit/rollback
- Test connection timeout
- **Assertions:** Validate real DB operations, MySQL-specific features

**File:** `platform/java/database/src/test/java/com/ghatana/platform/database/adapter/TransactionRollbackTest.java` ✅
- Test transaction rollback on exception
- Test nested transaction behavior
- Test savepoint behavior
- Test transaction isolation levels
- Test deadlock detection
- **Assertions:** Validate transaction correctness, rollback behavior

**File:** `platform/java/database/src/test/java/com/ghatana/platform/database/query/QueryCorrectnessTest.java` ✅
- Test SELECT with filters
- Test JOIN operations (inner, left, right, full)
- Test ORDER BY with multiple columns
- Test LIMIT/OFFSET pagination
- Test GROUP BY with aggregations
- Test HAVING clauses
- Test subqueries
- Test UNION operations
- **Assertions:** Validate query results match expected data

**File:** `platform/java/database/src/test/java/com/ghatana/platform/database/query/QueryPerformanceTest.java` ✅
- Test query execution time baselines
- Test index usage
- Test N+1 query detection
- Test large dataset handling (10K, 100K rows)
- **Assertions:** Validate query performance, index effectiveness

**File:** `platform/java/database/src/test/java/com/ghatana/platform/database/connection/ConnectionPoolBehaviorTest.java` ✅
- Test pool sizing
- Test connection reuse
- Test pool exhaustion
- Test idle connection timeout
- Test validation queries
- **Assertions:** Validate pool behavior under load

---

### 1.3 Security Package (platform/java/security)

**Current Coverage:** 55%  
**Target Coverage:** 85%  
**Priority:** P0

#### Test Files to Create

**File:** `platform/java/security/src/test/java/com/ghatana/platform/security/jwt/JwtValidationIntegrationTest.java` ✅
- Test JWT validation with real providers (Auth0, Firebase, custom)
- Test token expiration handling
- Test token refresh flow
- Test token revocation
- Test invalid token handling
- Test token signature verification
- **Assertions:** Validate JWT security, provider integration

**File:** `platform/java/security/src/test/java/com/ghatana/platform/security/rbac/RbacMatrixTest.java` ✅
- Test all role-permission combinations
- Test role hierarchy
- Test permission inheritance
- Test wildcard permissions
- Test negative permissions (deny)
- Test permission caching
- **Assertions:** Validate RBAC matrix correctness

**File:** `platform/java/security/src/test/java/com/ghatana/platform/security/rbac/RbacIntegrationTest.java` ✅
- Test RBAC enforcement in real scenarios
- Test role assignment/revocation
- Test permission checks across modules
- Test RBAC with multi-tenancy
- **Assertions:** Validate RBAC enforcement, isolation

**File:** `platform/java/security/src/test/java/com/ghatana/platform/security/oauth/OAuthFlowIntegrationTest.java` ✅
- Test OAuth 2.0 authorization code flow
- Test OAuth 2.0 implicit flow
- Test OAuth 2.0 client credentials flow
- Test OAuth 2.0 PKCE flow
- Test token exchange
- Test token introspection
- **Assertions:** Validate OAuth flow correctness, security

---

### 1.4 Observability Package (platform/java/observability)

**Current Coverage:** 50%  
**Target Coverage:** 80%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/java/observability/src/test/java/com/ghatana/platform/observability/clickhouse/ClickHouseTraceStorageIntegrationTest.java` ✅
- Use testcontainers for real ClickHouse
- Test trace storage
- Test trace retrieval by trace ID
- Test trace retrieval by span ID
- Test trace filtering (time range, tags)
- Test trace deletion
- **Assertions:** Validate ClickHouse integration, query correctness

**File:** `platform/java/observability/src/test/java/com/ghatana/platform/observability/ebpf/EbpfEventloopStallTracerTest.java`
- Test eBPF stall detection
- Test stall threshold configuration
- Test stall reporting
- Test eBPF probe installation
- **Assertions:** Validate eBPF integration, stall detection

**File:** `platform/java/observability/src/test/java/com/ghatana/platform/observability/metrics/HistogramAccuracyTest.java` ✅
- Test histogram bucket distribution
- Test histogram percentile calculation
- Test histogram aggregation
- Test histogram with boundary values
- **Assertions:** Validate histogram correctness, precision

---

### 1.5 AI Integration Package (platform/java/ai-integration)

**Current Coverage:** 15%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/java/ai-integration/src/test/java/com/ghatana/platform/ai/integration/RealModelCallIntegrationTest.java` ✅
- Test real AI model calls (OpenAI, Anthropic, local models)
- Test model response parsing
- Test model error handling
- Test model timeout
- Test model retry logic
- Test model rate limiting
- **Assertions:** Validate AI model integration, error handling

**File:** `platform/java/ai-integration/src/test/java/com/ghatana/platform/ai/integration/ModelRoutingTest.java` ✅
- Test model selection logic
- Test model fallback
- Test model load balancing
- Test model cost optimization
- **Assertions:** Validate routing correctness, fallback behavior

---

### 1.6 Connectors Package (platform/java/connectors)

**Current Coverage:** 30%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/java/connectors/src/test/java/com/ghatana/platform/connectors/s3/S3ConnectorIntegrationTest.java` ✅
- Test S3 bucket operations (create, delete, list)
- Test S3 object operations (put, get, delete, copy)
- Test S3 multipart upload
- Test S3 presigned URLs
- Test S3 error handling (404, 403, 500)
- Test S3 retry logic
- **Assertions:** Validate S3 integration, error handling

**File:** `platform/java/connectors/src/test/java/com/ghatana/platform/connectors/kafka/KafkaConnectorIntegrationTest.java` ✅
- Test Kafka producer (send, batching)
- Test Kafka consumer (subscribe, poll, commit)
- Test Kafka topic management
- Test Kafka consumer group management
- Test Kafka error handling (broker down, partition loss)
- Test Kafka exactly-once semantics
- **Assertions:** Validate Kafka integration, message delivery

**File:** `platform/java/connectors/src/test/java/com/ghatana/platform/connectors/redis/RedisConnectorIntegrationTest.java` ✅
- Test Redis string operations
- Test Redis hash operations
- Test Redis list operations
- Test Redis set operations
- Test Redis sorted set operations
- Test Redis pub/sub
- Test Redis transactions
- Test Redis error handling
- **Assertions:** Validate Redis integration, data consistency

**File:** `platform/java/connectors/src/test/java/com/ghatana/platform/connectors/ConnectorFailureHandlingTest.java` ✅
- Test connector failure detection
- Test connector retry logic
- Test connector circuit breaker
- Test connector fallback
- Test connector recovery
- **Assertions:** Validate failure handling, resilience

---

### 1.7 Agent Memory Package (platform/java/agent-memory)

**Current Coverage:** 20%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/java/agent-memory/src/test/java/com/ghatana/agent/memory/lifecycle/MemoryLifecycleTest.java` ✅
- Test memory creation
- Test memory update
- Test memory deletion
- Test memory archival
- Test memory retrieval
- **Assertions:** Validate memory lifecycle, persistence

**File:** `platform/java/agent-memory/src/test/java/com/ghatana/agent/memory/procedure/ProcedureExecutionTest.java` ✅
- Test procedure invocation
- Test procedure parameters
- Test procedure return values
- Test procedure error handling
- Test procedure chaining
- **Assertions:** Validate procedure execution, error propagation

**File:** `platform/java/agent-memory/src/test/java/com/ghatana/agent/memory/provenance/ProvenanceTrackingTest.java` ✅
- Test provenance recording
- Test provenance query
- Test provenance chain traversal
- Test provenance validation
- **Assertions:** Validate provenance correctness, chain integrity

---

### 1.8 Billing Package (platform/java/billing)

**Current Coverage:** 15%  
**Target Coverage:** 70%  
**Priority:** P2

#### Test Files to Create

**File:** `platform/java/billing/src/test/java/com/ghatana/platform/billing/transaction/TransactionCalculationTest.java` ✅
- Test transaction amount calculation
- Test tax calculation
- Test discount calculation
- Test currency conversion
- Test fee calculation
- Test rounding behavior
- **Assertions:** Validate calculation correctness, precision

**File:** `platform/java/billing/src/test/java/com/ghatana/platform/billing/transaction/TransactionRollbackTest.java` ✅
- Test transaction rollback
- Test partial refund
- Test full refund
- Test refund fee calculation
- Test refund idempotency
- **Assertions:** Validate rollback correctness, idempotency

---

### 1.9 Cache Package (platform/java/cache)

**Current Coverage:** 10%  
**Target Coverage:** 70%  
**Priority:** P2

#### Test Files to Create

**File:** `platform/java/cache/src/test/java/com/ghatana/platform/cache/consistency/CacheConsistencyTest.java` ✅
- Test cache write-through
- Test cache write-back
- Test cache invalidation
- Test cache refresh
- Test cache eviction
- **Assertions:** Validate cache consistency, invalidation

**File:** `platform/java/cache/src/test/java/com/ghatana/platform/cache/ttl/TtlExpirationTest.java` ✅
- Test TTL expiration
- Test TTL refresh
- Test TTL on access
- Test TTL on write
- **Assertions:** Validate TTL correctness, expiration

---

### 1.10 Identity Package (platform/java/identity)

**Current Coverage:** 20%  
**Target Coverage:** 70%  
**Priority:** P2

#### Test Files to Create

**File:** `platform/java/identity/src/test/java/com/ghatana/platform/identity/lifecycle/IdentityLifecycleTest.java` ✅
- Test identity creation
- Test identity update
- Test identity deletion
- Test identity activation/deactivation
- Test identity recovery
- **Assertions:** Validate identity lifecycle, state transitions

**File:** `platform/java/identity/src/test/java/com/ghatana/platform/identity/token/TokenManagementTest.java` ✅
- Test token generation
- Test token validation
- Test token refresh
- Test token revocation
- Test token expiration
- **Assertions:** Validate token management, security

---

### 1.11 Plugin Package (platform/java/plugin)

**Current Coverage:** 15%  
**Target Coverage:** 70%  
**Priority:** P2

#### Test Files to Create

**File:** `platform/java/plugin/src/test/java/com/ghatana/platform/plugin/lifecycle/PluginLifecycleTest.java` ✅
- Test plugin discovery
- Test plugin installation
- Test plugin activation
- Test plugin deactivation
- Test plugin uninstallation
- **Assertions:** Validate plugin lifecycle, isolation

**File:** `platform/java/plugin/src/test/java/com/ghatana/platform/plugin/isolation/PluginIsolationTest.java` ✅
- Test plugin classloader isolation
- Test plugin resource isolation
- Test plugin dependency isolation
- Test plugin security isolation
- **Assertions:** Validate isolation, security

---

### 1.12 Policy-as-Code Package (platform/java/policy-as-code)

**Current Coverage:** 5%  
**Target Coverage:** 65%  
**Priority:** P2

#### Test Files to Create

**File:** `platform/java/policy-as-code/src/test/java/com/ghatana/platform/policy/compilation/PolicyCompilationTest.java` ✅
- Test policy parsing
- Test policy compilation
- Test policy validation
- Test policy error reporting
- **Assertions:** Validate compilation correctness, error messages

**File:** `platform/java/policy-as-code/src/test/java/com/ghatana/platform/policy/evaluation/PolicyEvaluationTest.java` ✅
- Test policy evaluation
- Test policy context binding
- Test policy result calculation
- Test policy caching
- **Assertions:** Validate evaluation correctness, performance

---

## 2. Platform TypeScript

### 2.1 i18n Package (platform/typescript/i18n)

**Current Coverage:** 0%  
**Target Coverage:** 80%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/typescript/i18n/src/__tests__/translation.test.ts` ✅
- Test translation for all locales (en, es, fr, de, ja, zh, etc.)
- Test missing translation keys
- Test translation parameter interpolation
- Test translation fallback (missing key → default locale)
- Test pluralization
- **Assertions:** Validate translation correctness, fallback behavior

**File:** `platform/typescript/i18n/src/__tests__/localeSwitching.test.ts` ✅
- Test locale switching
- Test locale persistence
- Test locale detection from browser
- Test locale detection from URL
- Test locale detection from user preferences
- **Assertions:** Validate locale switching, detection

**File:** `platform/typescript/i18n/src/__tests__/missingTranslations.test.ts` ✅
- Test detection of missing translations
- Test validation of translation completeness
- Test validation of translation parameter consistency
- **Assertions:** Validate translation completeness, consistency

---

### 2.2 Accessibility-Audit Package (platform/typescript/accessibility-audit)

**Current Coverage:** 5%  
**Target Coverage:** 85%  
**Priority:** P0

#### Test Files to Create

**File:** `platform/typescript/accessibility-audit/src/__tests__/wcagCompliance.test.ts` ✅
- Test WCAG 2.1 Level A compliance
- Test WCAG 2.1 Level AA compliance
- Test WCAG 2.1 Level AAA compliance
- Test color contrast ratios (4.5:1 for normal text, 3:1 for large text)
- Test focus indicators
- Test alt text for images
- Test form labels
- Test semantic HTML
- **Assertions:** Validate WCAG compliance, specific criteria

**File:** `platform/typescript/accessibility-audit/src/__tests__/screenReader.test.ts` ✅
- Test screen reader compatibility
- Test ARIA labels
- Test ARIA roles
- Test ARIA live regions
- Test keyboard navigation
- Test skip links
- **Assertions:** Validate screen reader support, ARIA correctness

**File:** `platform/typescript/accessibility-audit/src/__tests__/keyboardNavigation.test.ts` ✅
- Test tab order
- Test focus management
- Test keyboard shortcuts
- Test focus traps
- Test focus restoration
- **Assertions:** Validate keyboard navigation, focus management

**File:** `platform/typescript/accessibility-audit/src/__tests__/colorContrast.test.ts` ✅
- Test normal text contrast (minimum 4.5:1)
- Test large text contrast (minimum 3:1)
- Test graphical objects contrast (minimum 3:1)
- Test text on images contrast
- Test focus indicator contrast
- **Assertions:** Validate contrast ratios, WCAG compliance

---

### 2.3 Realtime Package (platform/typescript/realtime)

**Current Coverage:** 0%  
**Target Coverage:** 75%  
**Priority:** P0

#### Test Files to Create

**File:** `platform/typescript/realtime/src/__tests__/webSocketConnection.test.ts` ✅
- Test WebSocket connection establishment
- Test WebSocket connection failure
- Test WebSocket reconnection logic
- Test WebSocket heartbeat
- Test WebSocket close
- **Assertions:** Validate connection lifecycle, reconnection

**File:** `platform/typescript/realtime/src/__tests__/eventHandling.test.ts` ✅
- Test event reception
- Test event parsing
- Test event filtering
- Test event batching
- Test event ordering
- **Assertions:** Validate event handling, ordering

**File:** `platform/typescript/realtime/src/__tests__/reconnectionLogic.test.ts` ✅
- Test exponential backoff reconnection
- Test max reconnection attempts
- Test reconnection on network recovery
- Test reconnection on server restart
- **Assertions:** Validate reconnection strategy, backoff

**File:** `platform/typescript/realtime/src/__tests__/eventLoss.test.ts` ✅
- Test event loss detection
- Test event replay
- Test event deduplication
- **Assertions:** Validate event loss handling, replay

---

### 2.4 SSO-Client Package (platform/typescript/sso-client)

**Current Coverage:** 0%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/typescript/sso-client/src/__tests__/oauthFlow.test.ts` ✅
- Test OAuth 2.0 authorization code flow
- Test OAuth 2.0 implicit flow
- Test PKCE flow
- Test token exchange
- Test token refresh
- Test token validation
- **Assertions:** Validate OAuth flow correctness, security

**File:** `platform/typescript/sso-client/src/__tests__/tokenManagement.test.ts`
- Test token storage
- Test token retrieval
- Test token expiration
- Test token refresh
- Test token revocation
- **Assertions:** Validate token management, security

**File:** `platform/typescript/sso-client/src/__tests__/providerIntegration.test.ts`
- Test Auth0 integration
- Test Firebase integration
- Test Okta integration
- Test custom provider integration
- **Assertions:** Validate provider integration, compatibility

---

### 2.5 Code-Editor Package (platform/typescript/code-editor)

**Current Coverage:** 0%  
**Target Coverage:** 70%  
**Priority:** P2

#### Test Files to Create

**File:** `platform/typescript/code-editor/src/__tests__/editorFunctionality.test.ts`
- Test editor initialization
- Test text insertion
- Test text deletion
- Test text selection
- Test undo/redo
- Test find/replace
- **Assertions:** Validate editor functionality, state management

**File:** `platform/typescript/code-editor/src/__tests__/syntaxHighlighting.test.ts`
- Test syntax highlighting for JavaScript
- Test syntax highlighting for TypeScript
- Test syntax highlighting for Python
- Test syntax highlighting for Java
- Test syntax highlighting for SQL
- **Assertions:** Validate highlighting correctness, performance

---

### 2.6 Charts Package (platform/typescript/charts)

**Current Coverage:** 15%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/typescript/charts/src/__tests__/realDataRendering.test.ts`
- Test line chart with real data
- Test bar chart with real data
- Test pie chart with real data
- Test scatter plot with real data
- Test area chart with real data
- **Assertions:** Validate chart rendering, data visualization

**File:** `platform/typescript/charts/src/__tests__/dataAggregation.test.ts`
- Test data aggregation (sum, avg, min, max, count)
- Test data grouping
- Test data filtering
- Test data sorting
- **Assertions:** Validate aggregation correctness, performance

**File:** `platform/typescript/charts/src/__tests__/axisScaling.test.ts`
- Test linear axis scaling
- Test logarithmic axis scaling
- Test time axis scaling
- Test categorical axis scaling
- Test axis bounds calculation
- **Assertions:** Validate axis scaling, bounds

**File:** `platform/typescript/charts/src/__tests__/tooltipCalculations.test.ts`
- Test tooltip positioning
- Test tooltip content
- Test tooltip collision detection
- Test tooltip hiding
- **Assertions:** Validate tooltip behavior, positioning

---

### 2.7 Canvas Package (platform/typescript/canvas)

**Current Coverage:** 10%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `platform/typescript/canvas/src/__tests__/multiLayerRendering.test.ts`
- Test layer composition
- Test layer ordering
- Test layer visibility
- Test layer opacity
- Test layer blending modes
- **Assertions:** Validate layer rendering, composition

**File:** `platform/typescript/canvas/src/__tests__/layerInteractions.test.ts`
- Test layer selection
- Test layer drag and drop
- Test layer resize
- Test layer rotation
- Test layer grouping
- **Assertions:** Validate layer interactions, transformations

**File:** `platform/typescript/canvas/src/__tests__/hitDetection.test.ts`
- Test point hit detection
- Test rectangle hit detection
- Test circle hit detection
- Test polygon hit detection
- Test hit detection with transformations
- **Assertions:** Validate hit detection accuracy, performance

**File:** `platform/typescript/canvas/src/__tests__/coordinateTransformations.test.ts`
- Test screen to canvas coordinate conversion
- Test canvas to screen coordinate conversion
- Test coordinate scaling
- Test coordinate rotation
- **Assertions:** Validate coordinate transformations, accuracy

---

### 2.8 Design-System Package (platform/typescript/design-system)

**Current Coverage:** 55%  
**Target Coverage:** 80%  
**Priority:** P1

#### Test Files to Enhance

**File:** `platform/typescript/design-system/src/atoms/__tests__/Button.test.tsx`
- Add invalid props tests
- Add missing required props tests
- Add null/undefined props tests
- Add complex interaction tests (click, double-click, right-click)
- Add accessibility tests (keyboard navigation, ARIA)
- **Assertions:** Validate error handling, interactions, accessibility

**File:** `platform/typescript/design-system/src/atoms/__tests__/Input.test.tsx`
- Add invalid input validation tests
- Add input masking tests
- Add input debouncing tests
- Add accessibility tests (labels, error messages)
- **Assertions:** Validate validation, masking, accessibility

**File:** `platform/typescript/design-system/src/atoms/__tests__/Checkbox.test.tsx`
- Add indeterminate state tests
- Add keyboard navigation tests
- Add accessibility tests (ARIA)
- **Assertions:** Validate state management, accessibility

**File:** `platform/typescript/design-system/src/atoms/__tests__/Avatar.test.tsx`
- Add image loading failure tests
- Add fallback rendering tests
- Add accessibility tests (alt text)
- **Assertions:** Validate error handling, fallback, accessibility

#### Test Files to Create

**File:** `platform/typescript/design-system/src/molecules/__tests__/Form.test.tsx`
- Test form validation
- Test form submission
- Test form reset
- Test form accessibility
- **Assertions:** Validate form behavior, accessibility

**File:** `platform/typescript/design-system/src/molecules/__tests__/Modal.test.tsx`
- Test modal open/close
- Test modal backdrop click
- Test modal escape key
- Test modal focus trap
- Test modal accessibility
- **Assertions:** Validate modal behavior, focus management, accessibility

**File:** `platform/typescript/design-system/src/organisms/__tests__/Table.test.tsx`
- Test table sorting
- Test table filtering
- Test table pagination
- Test table selection
- Test table accessibility
- **Assertions:** Validate table behavior, accessibility

---

## 3. Shared-Services

### 3.1 Auth-Gateway (shared-services/auth-gateway)

**Current Coverage:** 70%  
**Target Coverage:** 85%  
**Priority:** P0

#### Test Files to Create

**File:** `shared-services/auth-gateway/src/test/java/com/ghatana/services/auth/oauth/OAuthEndToEndIntegrationTest.java`
- Test OAuth 2.0 authorization code flow end-to-end
- Test OAuth 2.0 implicit flow end-to-end
- Test PKCE flow end-to-end
- Test token exchange with real providers (Auth0, Firebase)
- Test callback handling
- Test error handling (invalid state, expired code)
- **Assertions:** Validate OAuth flow correctness, provider integration

**File:** `shared-services/auth-gateway/src/test/java/com/ghatana/services/auth/rbac/RbacMatrixIntegrationTest.java`
- Test all role-permission combinations
- Test role hierarchy enforcement
- Test permission inheritance
- Test wildcard permissions
- Test negative permissions (deny)
- Test RBAC with multi-tenancy
- **Assertions:** Validate RBAC matrix correctness, enforcement

**File:** `shared-services/auth-gateway/src/test/java/com/ghatana/services/auth/database/PostgreSQLIntegrationTest.java`
- Use testcontainers for real PostgreSQL
- Test credential storage
- Test token storage
- Test session storage
- Test connection pool behavior
- Test transaction behavior
- **Assertions:** Validate PostgreSQL integration, persistence

**File:** `shared-services/auth-gateway/src/test/java/com/ghatana/services/auth/concurrency/ConcurrentSessionManagementTest.java`
- Test concurrent login attempts
- Test concurrent token refresh
- Test concurrent session invalidation
- Test session conflict resolution
- **Assertions:** Validate concurrent session handling, conflict resolution

---

### 3.2 User-Profile-Service (shared-services/user-profile-service)

**Current Coverage:** 40%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Create

**File:** `shared-services/user-profile-service/src/test/java/com/ghatana/services/profile/crud/ComprehensiveCrudTest.java`
- Test profile creation with all fields
- Test profile update with all fields
- Test profile deletion
- Test profile retrieval by ID
- Test profile retrieval by tenant
- Test profile search
- **Assertions:** Validate CRUD completeness, field handling

**File:** `shared-services/user-profile-service/src/test/java/com/ghatana/services/profile/database/PostgreSQLIntegrationTest.java`
- Use testcontainers for real PostgreSQL
- Test profile persistence
- Test profile query
- Test profile update
- Test profile deletion
- Test transaction behavior
- **Assertions:** Validate PostgreSQL integration, persistence

**File:** `shared-services/user-profile-service/src/test/java/com/ghatana/services/profile/tenant/TenantIsolationTest.java`
- Test tenant-scoped profile creation
- Test tenant-scoped profile retrieval
- Test tenant isolation (no cross-tenant access)
- Test tenant deletion cascades
- Test tenant migration
- **Assertions:** Validate tenant isolation, data segregation

**File:** `shared-services/user-profile-service/src/test/java/com/ghatana/services/profile/validation/ProfileValidationTest.java`
- Test required field validation
- Test field type validation
- Test field length validation
- Test field format validation (email, phone)
- Test custom validation rules
- **Assertions:** Validate validation rules, error messages

**File:** `shared-services/user-profile-service/src/test/java/com/ghatana/services/profile/concurrency/ConcurrentUpdateTest.java`
- Test concurrent profile updates
- Test optimistic locking
- Test conflict resolution
- Test update ordering
- **Assertions:** Validate concurrent update handling, conflict resolution

---

## 4. Audio-Video

### 4.1 STT Service (products/audio-video/modules/speech/stt-service)

**Current Coverage:** 20%  
**Target Coverage:** 75% (after implementation)  
**Priority:** P0 (Implementation Gap)

#### Implementation Required First

**Note:** Core transcription algorithms must be implemented before tests can validate correctness.

**File:** `products/audio-video/modules/speech/stt-service/src/main/java/com/ghatana/stt/engine/WhisperTranscriptionEngine.java` (IMPLEMENT)
- Implement Whisper model integration
- Implement audio format handling (PCM, WAV, MP3, FLAC, OGG, AAC)
- Implement transcription logic
- Implement confidence scoring
- Implement language detection
- Implement speaker diarization

#### Test Files to Create (After Implementation)

**File:** `products/audio-video/modules/speech/stt-service/src/test/java/com/ghatana/stt/engine/WhisperTranscriptionEngineTest.java`
- Test transcription with PCM audio
- Test transcription with WAV audio
- Test transcription with MP3 audio
- Test transcription with FLAC audio
- Test transcription with OGG audio
- Test transcription with AAC audio
- Test transcription accuracy with known samples
- Test confidence scoring
- Test language detection
- Test speaker diarization
- **Assertions:** Validate transcription accuracy, format handling, confidence

**File:** `products/audio-video/modules/speech/stt-service/src/test/java/com/ghatana/stt/engine/AudioFormatHandlingTest.java`
- Test PCM decoding
- Test WAV decoding
- Test MP3 decoding
- Test FLAC decoding
- Test OGG decoding
- Test AAC decoding
- Test invalid format handling
- Test corrupt audio handling
- **Assertions:** Validate format decoding, error handling

**File:** `products/audio-video/modules/speech/stt-service/src/test/java/com/ghatana/stt/quality/TranscriptionAccuracyTest.java`
- Test transcription accuracy with benchmark dataset
- Test transcription accuracy with noise
- Test transcription accuracy with accents
- Test transcription accuracy with multiple speakers
- Test transcription accuracy with background music
- **Assertions:** Validate accuracy, robustness

---

### 4.2 TTS Service (products/audio-video/modules/speech/tts-service)

**Current Coverage:** 15%  
**Target Coverage:** 75% (after implementation)  
**Priority:** P0 (Implementation Gap)

#### Implementation Required First

**File:** `products/audio-video/modules/speech/tts-service/src/main/java/com/ghatana/tts/engine/TtsSynthesisEngine.java` (IMPLEMENT)
- Implement TTS model integration
- Implement voice model support (multiple voices)
- Implement synthesis logic
- Implement prosody control
- Implement SSML support

#### Test Files to Create (After Implementation)

**File:** `products/audio-video/modules/speech/tts-service/src/test/java/com/ghatana/tts/engine/TtsSynthesisEngineTest.java`
- Test synthesis with default voice
- Test synthesis with multiple voices
- Test synthesis with SSML
- Test synthesis with prosody control
- Test synthesis quality with known samples
- **Assertions:** Validate synthesis correctness, voice support, SSML

**File:** `products/audio-video/modules/speech/tts-service/src/test/java/com/ghatana/tts/quality/SynthesisQualityTest.java`
- Test synthesis quality with benchmark dataset
- Test synthesis quality with prosody
- Test synthesis quality with SSML tags
- Test synthesis quality with long text
- **Assertions:** Validate synthesis quality, prosody, SSML

---

### 4.3 Vision Service (products/audio-video/modules/vision/vision-service)

**Current Coverage:** 25%  
**Target Coverage:** 75% (after implementation)  
**Priority:** P0 (Implementation Gap)

#### Implementation Required First

**File:** `products/audio-video/modules/vision/vision-service/src/main/java/com/ghatana/vision/engine/VisionModelEngine.java` (IMPLEMENT)
- Implement vision model integration (YOLO, ResNet, etc.)
- Implement object detection
- Implement image classification
- Implement OCR
- Implement face detection

#### Test Files to Create (After Implementation)

**File:** `products/audio-video/modules/vision/vision-service/src/test/java/com/ghatana/vision/engine/VisionModelEngineTest.java`
- Test object detection with known images
- Test image classification with known images
- Test OCR with known text
- Test face detection with known images
- Test confidence scoring
- **Assertions:** Validate detection accuracy, classification, OCR

**File:** `products/audio-video/modules/vision/vision-service/src/test/java/com/ghatana/vision/quality/DetectionAccuracyTest.java`
- Test detection accuracy with COCO dataset
- Test detection accuracy with noise
- Test detection accuracy with lighting variations
- Test detection accuracy with occlusions
- **Assertions:** Validate detection accuracy, robustness

---

### 4.4 Multimodal Service (products/audio-video/modules/intelligence/multimodal-service)

**Current Coverage:** 10%  
**Target Coverage:** 75% (after implementation)  
**Priority:** P0 (Implementation Gap)

#### Implementation Required First

**File:** `products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/multimodal/engine/CrossModalFusionEngine.java` (IMPLEMENT)
- Implement cross-modal fusion algorithms
- Implement multimodal model integration
- Implement audio-visual fusion
- Implement text-image fusion

#### Test Files to Create (After Implementation)

**File:** `products/audio-video/modules/intelligence/multimodal-service/src/test/java/com/ghatana/multimodal/engine/CrossModalFusionEngineTest.java`
- Test audio-visual fusion with known samples
- Test text-image fusion with known samples
- Test cross-modal attention
- Test fusion accuracy
- **Assertions:** Validate fusion correctness, accuracy

**File:** `products/audio-video/modules/intelligence/multimodal-service/src/test/java/com/ghatana/multimodal/quality/CrossModalAccuracyTest.java`
- Test fusion accuracy with benchmark dataset
- Test fusion accuracy with missing modalities
- Test fusion accuracy with noise
- **Assertions:** Validate fusion accuracy, robustness

---

### 4.5 Audio-Video Client (products/audio-video/libs/audio-video-client)

**Current Coverage:** 30%  
**Target Coverage:** 75%  
**Priority:** P1

#### Test Files to Enhance

**File:** `products/audio-video/libs/audio-video-client/src/__tests__/AudioVideoClient.test.ts`
- Add real service integration tests
- Add real gRPC call tests
- Add real WebSocket tests
- Add end-to-end workflow tests
- **Assertions:** Validate real service integration, gRPC calls

#### Test Files to Create

**File:** `products/audio-video/libs/audio-video-client/src/__tests__/RealServiceIntegration.test.ts`
- Test real STT service integration
- Test real TTS service integration
- Test real Vision service integration
- Test real Multimodal service integration
- Test service error handling
- **Assertions:** Validate real service integration, error handling

---

## 5. Data-Cloud

### 5.1 Platform-API Module (products/data-cloud/platform-api)

**Current Coverage:** 62%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/api/reports/ReportGenerationTest.java`
- Test report generation for all report types
- Test report filtering
- Test report pagination
- Test report export (CSV, PDF, JSON)
- Test report caching
- **Assertions:** Validate report correctness, export format

**File:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/api/reports/ReportCacheConsistencyTest.java`
- Test report cache hit
- Test report cache miss
- Test report cache invalidation
- Test report cache refresh
- **Assertions:** Validate cache consistency, invalidation

**File:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/api/memory/MemorySemanticSearchTest.java`
- Test semantic search with embeddings
- Test semantic search ranking
- Test semantic search with filters
- Test semantic search pagination
- **Assertions:** Validate search correctness, ranking

**File:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/api/brain/BrainWorkspaceStreamingTest.java`
- Test brain workspace streaming
- Test brain workspace threshold processing
- Test brain workspace aggregation
- Test brain workspace error handling
- **Assertions:** Validate streaming correctness, threshold logic

**File:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/api/models/ModelRegistryTest.java`
- Test model registration
- Test model versioning
- Test model promotion
- Test model deprecation
- Test model deletion
- **Assertions:** Validate model lifecycle, versioning

---

### 5.2 Platform-Event Module (products/data-cloud/platform-event)

**Current Coverage:** 44%  
**Target Coverage:** 85%  
**Priority:** P0

#### Test Files to Create

**File:** `products/data-cloud/platform-event/src/test/java/com/ghatana/datacloud/event/durability/EventReplayTest.java`
- Test event replay from offset
- Test event replay with filters
- Test event replay with time range
- Test event replay performance
- **Assertions:** Validate replay correctness, performance

**File:** `products/data-cloud/platform-event/src/test/java/com/ghatana/datacloud/event/cdc/CdcStreamAccuracyTest.java`
- Test CDC stream accuracy
- Test CDC stream ordering
- Test CDC stream filtering
- Test CDC stream latency
- **Assertions:** Validate CDC accuracy, ordering, latency

---

### 5.3 Platform-Analytics Module (products/data-cloud/platform-analytics)

**Current Coverage:** 38%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/platform-analytics/src/test/java/com/ghatana/datacloud/analytics/query/QueryCorrectnessFixturesTest.java`
- Create deterministic fixtures with known results
- Test aggregation functions (sum, avg, min, max, count)
- Test grouping with multiple columns
- Test filtering with complex conditions
- Test sorting with multiple columns
- Test pagination with offset/limit
- **Assertions:** Validate query results against known fixtures

**File:** `products/data-cloud/platform-analytics/src/test/java/com/ghatana/datacloud/analytics/anomaly/AnomalyDetectionRegressionTest.java`
- Test anomaly detection with known anomalous data
- Test anomaly detection with normal data
- Test anomaly detection thresholds
- Test anomaly detection performance
- **Assertions:** Validate anomaly detection correctness, thresholds

**File:** `products/data-cloud/platform-analytics/src/test/java/com/ghatana/datacloud/analytics/cache/CacheConsistencyTest.java` ✅
- Test query cache hit
- Test query cache miss
- Test query cache invalidation
- Test query cache refresh
- Test cache consistency across tenants
- **Assertions:** Validate cache consistency, invalidation

---

### 5.4 Platform-Launcher Module (products/data-cloud/platform-launcher)

**Current Coverage:** 58%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/platform-launcher/src/test/java/com/ghatana/datacloud/workflow/optimization/PipelineOptimizationTest.java`
- Test pipeline optimization hints
- Test pipeline parallelization
- Test pipeline batching
- Test pipeline caching
- **Assertions:** Validate optimization correctness, performance

**File:** `products/data-cloud/platform-launcher/src/test/java/com/ghatana/datacloud/workflow/audit/PipelineAuditabilityTest.java`
- Test pipeline audit trail
- Test pipeline execution logging
- Test pipeline error logging
- Test pipeline performance logging
- **Assertions:** Validate audit completeness, logging

---

### 5.5 Platform-Governance Module (products/data-cloud/platform-governance)

**Current Coverage:** 44%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/platform-governance/src/test/java/com/ghatana/datacloud/governance/retention/RetentionClassificationTest.java`
- Test retention classification by data type
- Test retention classification by sensitivity
- Test retention classification by regulation
- Test retention policy application
- **Assertions:** Validate classification correctness, policy application

**File:** `products/data-cloud/platform-governance/src/test/java/com/ghatana/datacloud/governance/purge/PurgeAndRollbackTest.java`
- Test data purge execution
- Test data purge rollback
- Test data purge logging
- Test data purge verification
- **Assertions:** Validate purge correctness, rollback

**File:** `products/data-cloud/platform-governance/src/test/java/com/ghatana/datacloud/governance/redaction/FieldMaskingTest.java`
- Test field masking by sensitivity
- Test field masking by regulation
- Test field masking verification
- Test field masking performance
- **Assertions:** Validate masking correctness, verification

**File:** `products/data-cloud/platform-governance/src/test/java/com/ghatana/datacloud/governance/audit/AuditLoggingTest.java`
- Test audit log creation
- Test audit log query
- Test audit log retention
- Test audit log export
- **Assertions:** Validate audit logging, retention

---

### 5.6 Feature-Store-Ingest Module (products/data-cloud/feature-store-ingest)

**Current Coverage:** 44%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/feature-store-ingest/src/test/java/com/ghatana/datacloud/features/retrieve/FeatureRetrievalTest.java`
- Test feature retrieval by ID
- Test feature retrieval by version
- Test feature retrieval with filters
- Test feature retrieval caching
- **Assertions:** Validate retrieval correctness, versioning, caching

**File:** `products/data-cloud/feature-store-ingest/src/test/java/com/ghatana/datacloud/features/ingest/FeatureIngestionErrorHandlingTest.java`
- Test ingestion with invalid data
- Test ingestion with duplicate features
- Test ingestion with overwrite
- Test ingestion rollback
- **Assertions:** Validate error handling, rollback

---

### 5.7 SPI Module (products/data-cloud/spi)

**Current Coverage:** 45%  
**Target Coverage:** 85%  
**Priority:** P0

#### Test Files to Create

**File:** `products/data-cloud/spi/src/test/java/com/ghatana/datacloud/plugin/lifecycle/PluginLifecycleIntegrationTest.java`
- Test plugin discovery
- Test plugin installation
- Test plugin activation
- Test plugin deactivation
- Test plugin uninstallation
- Test plugin isolation
- **Assertions:** Validate plugin lifecycle, isolation

**File:** `products/data-cloud/spi/src/test/java/com/ghatana/datacloud/storage/profile/StorageProfileTest.java`
- Test storage profile creation
- Test storage profile validation
- Test storage profile application
- Test storage profile migration
- **Assertions:** Validate profile correctness, migration

**File:** `products/data-cloud/spi/src/test/java/com/ghatana/datacloud/storage/connector/ConnectorTest.java`
- Test connector registration
- Test connector activation
- Test connector error handling
- Test connector fallback
- **Assertions:** Validate connector lifecycle, error handling

**File:** `products/data-cloud/spi/src/test/java/com/ghatana/datacloud/agent/registry/AgentRegistryContractTest.java`
- Test agent registration
- Test agent lookup
- Test agent versioning
- Test agent deprecation
- **Assertions:** Validate agent registry, versioning

---

### 5.8 Agent-Registry Module (products/data-cloud/agent-registry)

**Current Coverage:** 0%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/agent-registry/src/test/java/com/ghatana/datacloud/agent/registry/AgentRegistrationTest.java`
- Test agent registration
- Test agent lookup by ID
- Test agent lookup by type
- Test agent lookup by capability
- Test agent versioning
- Test agent deprecation
- Test agent deletion
- **Assertions:** Validate agent registry, lifecycle

**File:** `products/data-cloud/agent-registry/src/test/java/com/ghatana/datacloud/agent/registry/AgentCatalogQueryTest.java`
- Test agent catalog query
- Test agent catalog filtering
- Test agent catalog sorting
- Test agent catalog pagination
- **Assertions:** Validate catalog query, filtering, pagination

---

### 5.9 API Module (products/data-cloud/api)

**Current Coverage:** 28%  
**Target Coverage:** 85%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/api/src/test/java/com/ghatana/datacloud/api/openapi/OpenApiDriftTest.java`
- Test OpenAPI spec completeness
- Test OpenAPI spec accuracy
- Test OpenAPI spec versioning
- Test OpenAPI spec generation
- **Assertions:** Validate OpenAPI correctness, drift detection

**File:** `products/data-cloud/api/src/test/java/com/ghatana/datacloud/api/routes/RouteCompletionTest.java`
- Test all registered routes
- Test route parameter validation
- Test route error handling
- Test route documentation
- **Assertions:** Validate route completeness, documentation

---

### 5.10 SDK Module (products/data-cloud/sdk)

**Current Coverage:** 0%  
**Target Coverage:** 70%  
**Priority:** P2

#### Test Files to Create

**File:** `products/data-cloud/sdk/src/test/java/com/ghatana/datacloud/sdk/generation/SDKGenerationTest.java`
- Test SDK generation for Java
- Test SDK generation for TypeScript
- Test SDK generation for Python
- Test SDK validation
- **Assertions:** Validate SDK generation, correctness

**File:** `products/data-cloud/sdk/src/test/java/com/ghatana/datacloud/sdk/smoke/SDKSmokeTest.java`
- Test Java SDK smoke tests
- Test TypeScript SDK smoke tests
- Test Python SDK smoke tests
- **Assertions:** Validate SDK functionality, basic operations

---

### 5.11 UI Module (products/data-cloud/ui)

**Current Coverage:** 0%  
**Target Coverage:** 70%  
**Priority:** P1

#### Test Files to Create

**File:** `products/data-cloud/ui/src/__tests__/pages/CollectionPage.test.tsx`
- Test collection list view
- Test collection detail view
- Test collection creation
- Test collection edit
- Test collection delete
- Test collection search
- **Assertions:** Validate UI functionality, user flows

**File:** `products/data-cloud/ui/src/__tests__/pages/EventPage.test.tsx`
- Test event list view
- Test event detail view
- Test event replay
- Test event filtering
- Test event search
- **Assertions:** Validate UI functionality, event handling

**File:** `products/data-cloud/ui/src/__tests__/pages/PipelinePage.test.tsx`
- Test pipeline list view
- Test pipeline detail view
- Test pipeline creation
- Test pipeline execution
- Test pipeline monitoring
- **Assertions:** Validate UI functionality, pipeline management

**File:** `products/data-cloud/ui/src/__tests__/pages/WorkflowPage.test.tsx`
- Test workflow list view
- Test workflow detail view
- Test workflow creation
- Test workflow execution
- Test workflow monitoring
- **Assertions:** Validate UI functionality, workflow management

**File:** `products/data-cloud/ui/src/__tests__/pages/AnalyticsPage.test.tsx`
- Test analytics dashboard
- Test query builder
- Test report generation
- Test chart rendering
- **Assertions:** Validate UI functionality, analytics

**File:** `products/data-cloud/ui/src/__tests__/pages/MemoryPage.test.tsx`
- Test memory list view
- Test memory detail view
- Test memory search
- Test memory semantic search
- **Assertions:** Validate UI functionality, memory management

**File:** `products/data-cloud/ui/src/__tests__/pages/BrainPage.test.tsx`
- Test brain workspace view
- Test brain configuration
- Test brain monitoring
- **Assertions:** Validate UI functionality, brain management

**File:** `products/data-cloud/ui/src/__tests__/pages/ModelPage.test.tsx`
- Test model registry view
- Test model versioning
- Test model promotion
- **Assertions:** Validate UI functionality, model management

**File:** `products/data-cloud/ui/src/__tests__/pages/FeaturePage.test.tsx`
- Test feature list view
- Test feature detail view
- Test feature ingestion
- **Assertions:** Validate UI functionality, feature management

**File:** `products/data-cloud/ui/src/__tests__/pages/PluginPage.test.tsx`
- Test plugin list view
- Test plugin installation
- Test plugin configuration
- **Assertions:** Validate UI functionality, plugin management

**File:** `products/data-cloud/ui/src/__tests__/pages/GovernancePage.test.tsx`
- Test governance dashboard
- Test retention policy configuration
- Test purge execution
- **Assertions:** Validate UI functionality, governance

**File:** `products/data-cloud/ui/src/__tests__/pages/SettingsPage.test.tsx`
- Test settings configuration
- Test tenant settings
- Test user settings
- **Assertions:** Validate UI functionality, settings

**File:** `products/data-cloud/ui/src/__tests__/e2e/CriticalPathJourney.test.tsx`
- Test critical user journey: Create collection → Add events → Run pipeline → View analytics
- Test critical user journey: Configure workflow → Execute workflow → Monitor results
- Test critical user journey: Ingest features → Train model → Deploy model
- **Assertions:** Validate end-to-end user flows

**File:** `products/data-cloud/ui/src/__tests__/accessibility/AccessibilityAudit.test.tsx`
- Test keyboard navigation
- Test screen reader compatibility
- Test color contrast
- Test focus indicators
- **Assertions:** Validate WCAG 2.1 AA compliance

---

## 6. Integration Test Files to Create

### 6.1 Platform Integration Tests

**File:** `platform/java/integration-tests/src/test/java/com/ghatana/platform/integration/DatabaseIntegrationTest.java`
- Test real PostgreSQL integration
- Test real MySQL integration
- Test transaction behavior across services
- Test connection pool sharing
- **Assertions:** Validate DB integration, transaction correctness

**File:** `platform/java/integration-tests/src/test/java/com/ghatana/platform/integration/SecurityIntegrationTest.java`
- Test JWT validation across services
- Test RBAC enforcement across services
- Test OAuth flow across services
- **Assertions:** Validate security integration, enforcement

**File:** `platform/java/integration-tests/src/test/java/com/ghatana/platform/integration/ObservabilityIntegrationTest.java`
- Test metrics propagation across services
- Test trace propagation across services
- Test correlation ID propagation across services
- **Assertions:** Validate observability integration, propagation

---

### 6.2 Data-Cloud Integration Tests

**File:** `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java`
- Test end-to-end workflow: Create collection → Add events → Run pipeline → Query analytics
- Test end-to-end workflow with real PostgreSQL
- Test end-to-end workflow with real Kafka
- **Assertions:** Validate end-to-end correctness, persistence

**File:** `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java`
- Test multi-tenant data isolation
- Test multi-tenant query isolation
- Test multi-tenant security isolation
- **Assertions:** Validate tenant isolation, security

**File:** `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/PluginIntegrationTest.java`
- Test plugin installation and activation
- Test plugin integration with core services
- Test plugin isolation
- **Assertions:** Validate plugin integration, isolation

---

## 7. Summary

### Total Test Files to Create: ~200

**Platform Java:** ~80 test files  
**Platform TypeScript:** ~30 test files  
**Shared-Services:** ~10 test files  
**Audio-Video:** ~20 test files (after implementation)  
**Data-Cloud:** ~60 test files

### Total Test Files to Enhance: ~20

**Platform Java:** ~5 test files  
**Platform TypeScript:** ~10 test files  
**Shared-Services:** ~2 test files  
**Audio-Video:** ~2 test files  
**Data-Cloud:** ~1 test file

### Estimated Timeline

**Week 1-4:** Platform Java unit tests (40 files)  
**Week 5-8:** Platform Java integration tests (40 files)  
**Week 1-2:** Platform TypeScript unit tests (15 files)  
**Week 3-4:** Platform TypeScript integration tests (15 files)  
**Week 1:** Shared-Services tests (10 files)  
**Week 9-16:** Audio-Video implementation + tests (20 files)  
**Week 1-16:** Data-Cloud tests (60 files - aligned with existing roadmap)

### Priority Order

1. **P0 (Critical):** Data-Cloud event durability/CDC, Platform Java DB/security, Platform TypeScript accessibility/realtime, Shared-Services OAuth/RBAC
2. **P1 (High):** Platform Java AI/connectors, Platform TypeScript i18n/charts/canvas, Audio-Video (after implementation), Data-Cloud analytics/governance/plugins
3. **P2 (Medium):** Platform Java billing/cache/identity/plugin, Platform TypeScript code-editor, Data-Cloud SDK

### Success Criteria

- All test files follow ultra-strict standards (validate truth, not implementation)
- All test files have deterministic fixtures
- All test files have strong assertions (outputs, state changes, side effects)
- All test files have intent-based naming (@DisplayName)
- All test files have no flaky tests
- All test files achieve 100% requirement/use case coverage
