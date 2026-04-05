# Phase 2 Implementation Accelerator — Security & Observability

**Purpose**: Detailed implementation guide using identity module as template  
**Duration**: Week 5-6 execution (8 days)  
**Target**: 100 additional tests across 2 critical modules  
**Pattern Source**: Identity module (proven at 95+ tests)

---

## Security Module Implementation Guide (48 tests)

### Module Overview
- **Location**: `platform/java/security`
- **Current Production**: 67 classes
- **Current Test Coverage**: 24 test files (insufficient)
- **Target Test Coverage**: 48 test files (95+ tests total)
- **Effort**: 1 engineer × 4 days

### Test Architecture Template

```
platform/java/security/src/test/java/com/ghatana/platform/security/
├── auth/
│   ├── AuthenticationServiceTest.java
│   ├── CredentialValidatorTest.java
│   ├── MfaProviderTest.java
│   └── fixtures/
│       ├── CredentialTestFixture.java
│       └── PrincipalMockFactory.java
├── jwt/
│   ├── JwtTokenProviderTest.java
│   ├── JwtKeyManagerTest.java
│   ├── TokenRefreshTest.java
│   ├── TokenRevocationTest.java
│   └── fixtures/
│       └── JwtTestFixture.java
├── encryption/
│   ├── AesGcmEncryptionTest.java
│   ├── KeyRotationTest.java
│   └── fixtures/
│       └── EncryptionTestFixture.java
├── rbac/
│   ├── RoleAssignmentTest.java
│   ├── PermissionEvaluatorTest.java
│   └── PolicyEnforcementTest.java
├── apikey/
│   ├── ApiKeyGenerationTest.java
│   └── ApiKeyScopingTest.java
├── integration/
│   └── SecurityFrameworkIntegrationTest.java
└── base/
    └── SecurityEventloopTestBase.java
```

### Test Category Breakdown

#### 1. Authentication Service Tests (8 tests)
**File**: `auth/AuthenticationServiceTest.java`

```java
@DisplayName("AuthenticationService Tests")
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest extends SecurityEventloopTestBase {
    
    @Mock private CredentialRepository credentialRepo;
    @Mock private MfaService mfaService;
    
    private AuthenticationService service;
    
    @BeforeEach
    void setUp() {
        service = new AuthenticationService(credentialRepo, mfaService);
    }
    
    @Test
    void shouldAuthenticateValidCredentials() {
        Credential credential = CredentialTestFixture.valid();
        when(credentialRepo.findById(credential.id()))
            .thenReturn(Promise.of(Optional.of(credential)));
        
        AuthResult result = runPromise(() -> 
            service.authenticate("user@test.com", "password123"));
        
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.principal().name()).isEqualTo("user@test.com");
    }
    
    @Test
    void shouldRejectExpiredCredentials() {
        Credential expired = CredentialTestFixture.expired();
        when(credentialRepo.findById(expired.id()))
            .thenReturn(Promise.of(Optional.of(expired)));
        
        AuthResult result = runPromise(() -> 
            service.authenticate("user@test.com", "password123"));
        
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.error()).contains("expired");
    }
    
    @Test
    void shouldEnforceMfaWhenEnabled() { /* ... */ }
    @Test
    void shouldHandleAuthenticationTimeout() { /* ... */ }
    @Test
    void shouldLogFailedAuthenticationAttempts() { /* ... */ }
    @Test
    void shouldClearSensitiveDataAfterAuth() { /* ... */ }
    @Test
    void shouldPropagateSecurityContextAsync() { /* ... */ }
    @Test
    void shouldBlockBruteForceAttacks() { /* ... */ }
}
```

**Fixture Support** (`auth/fixtures/CredentialTestFixture.java`):
```java
public class CredentialTestFixture {
    
    public static Credential valid() {
        return Credential.builder()
            .id("cred-123")
            .userId("user-456")
            .passwordHash(hashPassword("password123"))
            .expiresAt(Clock.systemUTC().instant().plus(Duration.ofDays(90)))
            .mfaRequired(false)
            .status(CredentialStatus.ACTIVE)
            .build();
    }
    
    public static Credential expired() {
        return Credential.builder()
            .id("cred-expired")
            .userId("user-456")
            .passwordHash(hashPassword("password123"))
            .expiresAt(Clock.systemUTC().instant().minus(Duration.ofDays(1)))
            .mfaRequired(false)
            .status(CredentialStatus.EXPIRED)
            .build();
    }
    
    public static Credential mfaRequired() {
        return valid().toBuilder().mfaRequired(true).build();
    }
    
    private static String hashPassword(String raw) {
        return new Argon2PasswordHasher().hash(raw);
    }
}
```

#### 2. JWT Token Tests (12 tests)
**Files**: 
- `jwt/JwtTokenProviderTest.java` (5 tests)
- `jwt/TokenRefreshTest.java` (4 tests)
- `jwt/TokenRevocationTest.java` (3 tests)

```java
@DisplayName("JwtTokenProvider Tests")
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest extends SecurityEventloopTestBase {
    
    @Mock private JwtKeyManager keyManager;
    @Mock private TokenRevocationService revocationService;
    
    private JwtTokenProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(keyManager, revocationService);
        lenient().when(keyManager.getCurrentKey())
            .thenReturn(Promise.of(JwtTestFixture.rsaKey()));
    }
    
    @Test
    void shouldGenerateValidAccessToken() {
        Principal principal = PrincipalMockFactory.authenticated();
        
        String token = runPromise(() -> provider.generateAccessToken(principal));
        
        assertThat(token).isNotEmpty();
        DecodedJWT decoded = JWT.decode(token);
        assertThat(decoded.getSubject()).isEqualTo(principal.id());
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
    }
    
    @Test
    void shouldValidateTokenSignature() { /* ... */ }
    @Test
    void shouldRejectExpiredTokens() { /* ... */ }
    @Test
    void shouldEnforceTokenScopes() { /* ... */ }
    @Test
    void shouldAuditTokenGeneration() { /* ... */ }
}
```

#### 3. Encryption Tests (8 tests)
**File**: `encryption/AesGcmEncryptionTest.java`

```java
@DisplayName("AES-GCM Encryption Tests")
class AesGcmEncryptionTest {
    
    private AesGcmEncryptionProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new AesGcmEncryptionProvider(
            new SecureKeyGenerator(),
            new AesGcmCipherProvider()
        );
    }
    
    @Test
    void shouldEncryptAndDecryptSymmetrically() {
        String plaintext = "sensitive data";
        
        EncryptedData encrypted = provider.encrypt(plaintext);
        String decrypted = provider.decrypt(encrypted);
        
        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(encrypted.ciphertext()).isNotEqualTo(plaintext);
    }
    
    @Test
    void shouldGenerateUniqueCiphertextsForSamePlaintext() { /* ... */ }
    @Test
    void shouldEnforceAuthenticityCheck() { /* ... */ }
    @Test
    void shouldRotateKeysWithoutDataLoss() { /* ... */ }
    @Test
    void shouldRejectTamperedCiphertext() { /* ... */ }
    @Test
    void shouldEncryptWithCurrentKeyVersion() { /* ... */ }
    @Test
    void shouldDecryptWithHistoricalKeyVersions() { /* ... */ }
    @Test
    void shouldAuditEncryptionOperations() { /* ... */ }
}
```

#### 4. RBAC Tests (10 tests)
**Files**:
- `rbac/RoleAssignmentTest.java` (4 tests)
- `rbac/PermissionEvaluatorTest.java` (4 tests)
- `rbac/PolicyEnforcementTest.java` (2 tests)

```java
@DisplayName("Role Assignment Tests")
@ExtendWith(MockitoExtension.class)
class RoleAssignmentTest extends SecurityEventloopTestBase {
    
    @Mock private RoleRepository roleRepo;
    @Mock private AuditLogger auditLogger;
    
    private RoleAssignmentService service;
    
    @BeforeEach
    void setUp() {
        service = new RoleAssignmentService(roleRepo, auditLogger);
    }
    
    @Test
    void shouldAssignRoleToUser() {
        User user = UserTestFixture.standard();
        Role role = RoleTestFixture.viewer();
        
        Promise<AssignmentResult> result = service.assignRole(user.id(), role);
        
        assertThat(result.getResult().isSuccess()).isTrue();
        verify(roleRepo).save(argThat(assignment -> 
            assignment.userId().equals(user.id()) &&
            assignment.roleId().equals(role.id())
        ));
        verify(auditLogger).logRoleAssignment(user.id(), role.id());
    }
    
    @Test
    void shouldPreventDuplicateRoleAssignments() { /* ... */ }
    @Test
    void shouldRevokeRoleFromUser() { /* ... */ }
    @Test
    void shouldEnforceRoleHierarchy() { /* ... */ }
}
```

#### 5. API Key Tests (4 tests)
**File**: `apikey/ApiKeyManagementTest.java`

```java
@DisplayName("API Key Management Tests")
@ExtendWith(MockitoExtension.class)
class ApiKeyManagementTest {
    
    @Mock private ApiKeyRepository apiKeyRepo;
    
    private ApiKeyService service;
    
    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRepo);
    }
    
    @Test
    void shouldGenerateSecureApiKey() {
        String apiKey = service.generateApiKey();
        
        assertThat(apiKey).hasLengthGreaterThan(32);
        assertThat(apiKey).matches("[A-Za-z0-9_-]+");
    }
    
    @Test
    void shouldHashApiKeyForStorage() { /* ... */ }
    @Test
    void shouldRotateApiKeyWithGracePeriod() { /* ... */ }
    @Test
    void shouldEnforceApiKeyScopes() { /* ... */ }
}
```

#### 6. Integration Tests (4 tests)
**File**: `integration/SecurityFrameworkIntegrationTest.java`

```java
@DisplayName("Security Framework Integration Tests")
@ExtendWith(MockitoExtension.class)
class SecurityFrameworkIntegrationTest extends SecurityEventloopTestBase {
    
    @Mock private CredentialRepository credentialRepo;
    @Mock private RoleRepository roleRepo;
    
    private SecurityFramework framework;
    
    @BeforeEach
    void setUp() {
        framework = new SecurityFramework(credentialRepo, roleRepo);
    }
    
    @Test
    void shouldAuthenticateAndAuthorizeInOneFlow() {
        User user = UserTestFixture.withRole("admin");
        Credential credential = CredentialTestFixture.forUser(user);
        
        AuthorizationResult result = runPromise(() -> 
            framework.authenticate(credential)
                .then(principal -> framework.authorize(principal, "resource:delete"))
        );
        
        assertThat(result.isPermitted()).isTrue();
        assertThat(result.principal().name()).isEqualTo(user.email());
    }
    
    @Test
    void shouldHandleSecurityExceptionsGracefully() { /* ... */ }
    @Test
    void shouldPropagateSecurityContextThroughAsyncBoundaries() { /* ... */ }
    @Test
    void shouldAuditAllSecurityDecisions() { /* ... */ }
}
```

### Test Fixture Consolidation (Reuse from Identity)

Copy and adapt from identity module:
- ✓ `AsyncTestBase` → `SecurityEventloopTestBase`
- ✓ `TestFixture` pattern → `CredentialTestFixture`, `PrincipalMockFactory`
- ✓ `Assertion` patterns → Typed `assertThat()` chains
- ✓ `Mock` patterns → Lenient mocking with proper cascading

---

## Observability Module Implementation Guide (52 tests)

### Module Overview
- **Location**: `platform/java/observability`
- **Current Production**: 54 classes
- **Current Test Coverage**: Insufficient
- **Target Test Coverage**: 52 test files (110+ tests total)
- **Effort**: 1 engineer × 4.5 days

### Test Architecture Template

```
platform/java/observability/src/test/java/com/ghatana/platform/observability/
├── metrics/
│   ├── MeterRegistryTest.java
│   ├── CounterTest.java
│   ├── GaugeTest.java
│   ├── HistogramTest.java
│   ├── SummaryTest.java
│   └── fixtures/
│       └── MetricsTestFixture.java
├── tracing/
│   ├── TracerProviderTest.java
│   ├── SpanCreationTest.java
│   ├── ContextPropagationTest.java
│   ├── SamplingTest.java
│   ├── W3cTraceContextTest.java
│   └── fixtures/
│       └── TracingTestFixture.java
├── logging/
│   ├── StructuredLoggerTest.java
│   ├── LogLevelFilterTest.java
│   └── fixtures/
│       └── LoggingTestFixture.java
├── correlation/
│   ├── CorrelationIdTest.java
│   ├── ContextIsolationTest.java
│   └── HeaderPropagationTest.java
├── health/
│   ├── ReadinessProbeTest.java
│   ├── LivenessProbeTest.java
│   └── StartupProbeTest.java
├── integration/
│   ├── OpenTelemetryExportTest.java
│   ├── PrometheusExportTest.java
│   └── JaegerExportTest.java
└── base/
    └── ObservabilityEventloopTestBase.java
```

### Test Category Breakdown

#### 1. Metrics Tests (12 tests)
**Files**:
- `metrics/CounterTest.java` (3 tests)
- `metrics/GaugeTest.java` (3 tests)
- `metrics/HistogramTest.java` (3 tests)
- `metrics/SummaryTest.java` (3 tests)

```java
@DisplayName("Counter Metrics Tests")
@ExtendWith(MockitoExtension.class)
class CounterTest {
    
    private MeterRegistry meterRegistry;
    private Counter counter;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        counter = Counter.builder("requests.total")
            .description("Total HTTP requests")
            .tag("service", "api")
            .register(meterRegistry);
    }
    
    @Test
    void shouldIncrementCounterByDefault() {
        counter.increment();
        counter.increment();
        
        assertThat(counter.count()).isEqualTo(2.0);
    }
    
    @Test
    void shouldIncrementCounterBySpecifiedAmount() {
        counter.increment(5);
        
        assertThat(counter.count()).isEqualTo(5.0);
    }
    
    @Test
    void shouldRecordMetadataWithMetrics() {
        counter.increment(1, Tags.of("status", "success"));
        counter.increment(1, Tags.of("status", "failure"));
        
        double success = counter.search()
            .tag("status", "success")
            .counter()
            .count();
        
        assertThat(success).isEqualTo(1.0);
    }
}
```

#### 2. Tracing Tests (14 tests)
**Files**:
- `tracing/SpanCreationTest.java` (4 tests)
- `tracing/ContextPropagationTest.java` (5 tests)
- `tracing/SamplingTest.java` (3 tests)
- `tracing/W3cTraceContextTest.java` (2 tests)

```java
@DisplayName("Span Creation Tests")
@ExtendWith(MockitoExtension.class)
class SpanCreationTest extends ObservabilityEventloopTestBase {
    
    @Mock private SpanExporter spanExporter;
    
    private TracerProvider tracerProvider;
    private Tracer tracer;
    
    @BeforeEach
    void setUp() {
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        tracer = tracerProvider.get("test-tracer");
    }
    
    @Test
    void shouldCreateSpanWithAttributes() {
        Span span = tracer.spanBuilder("test-operation")
            .setAttribute("user.id", "123")
            .setAttribute("service.name", "api")
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Perform work
        }
        
        span.end();
        
        verify(spanExporter).export(argThat(batch -> 
            batch.getSpanData().stream()
                .anyMatch(s -> s.getName().equals("test-operation") &&
                          s.getAttributes().get(AttributeKey.stringKey("user.id")).equals("123"))
        ));
    }
    
    @Test
    void shouldRecordSpanEvents() { /* ... */ }
    @Test
    void shouldSetSpanStatusAndDescription() { /* ... */ }
    @Test
    void shouldMeasureSpanDuration() { /* ... */ }
}
```

#### 3. Logging Tests (10 tests)
**File**: `logging/StructuredLoggerTest.java`

```java
@DisplayName("Structured Logger Tests")
@ExtendWith(MockitoExtension.class)
class StructuredLoggerTest {
    
    @Mock private LogSink logSink;
    
    private StructuredLogger logger;
    
    @BeforeEach
    void setUp() {
        logger = new StructuredLogger("app.service", logSink);
    }
    
    @Test
    void shouldLogWithStructuredFields() {
        Instant timestamp = Instant.now();
        
        logger.info("User login", 
            LogField.of("user.id", "123"),
            LogField.of("method", "oauth2")
        );
        
        verify(logSink).write(argThat(entry -> 
            entry.message().equals("User login") &&
            entry.level().equals(LogLevel.INFO) &&
            entry.fields().containsKey("user.id")
        ));
    }
    
    @Test
    void shouldApplyGlobalContext() { /* ... */ }
    @Test
    void shouldFilterByLogLevel() { /* ... */ }
    @Test
    void shouldSamplingBasedOnRate() { /* ... */ }
    @Test
    void shouldHandleLargePayloads() { /* ... */ }
    @Test
    void shouldNotBlockOnLogging() { /* ... */ }
    @Test
    void shouldPreserveCauseChain() { /* ... */ }
    @Test
    void shouldSanitizeSensitiveFields() { /* ... */ }
    @Test
    void shouldSupportAsynchronousLogging() { /* ... */ }
}
```

#### 4. Correlation ID Tests (6 tests)
**Files**:
- `correlation/CorrelationIdTest.java` (2 tests)
- `correlation/ContextIsolationTest.java` (2 tests)
- `correlation/HeaderPropagationTest.java` (2 tests)

```java
@DisplayName("Correlation ID Tests")
@ExtendWith(MockitoExtension.class)
class CorrelationIdTest extends ObservabilityEventloopTestBase {
    
    private CorrelationIdProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new CorrelationIdProvider();
    }
    
    @Test
    void shouldGenerateUniqueCorrelationIds() {
        String id1 = provider.generate();
        String id2 = provider.generate();
        
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1).matches("[0-9a-f]{32}");
    }
    
    @Test
    void shouldPropagateCorrelationIdThroughAsyncChain() {
        String correlationId = provider.generate();
        
        String result = runPromise(() -> 
            CorrelationContext.with(correlationId, () -> 
                Promise.ofBlocking(() -> {
                    Thread.sleep(10);
                    return CorrelationContext.currentId();
                })
            )
        );
        
        assertThat(result).isEqualTo(correlationId);
    }
}
```

#### 5. Health Check Tests (6 tests)
**Files**:
- `health/ReadinessProbeTest.java` (2 tests)
- `health/LivenessProbeTest.java` (2 tests)
- `health/StartupProbeTest.java` (2 tests)

```java
@DisplayName("Readiness Probe Tests")
@ExtendWith(MockitoExtension.class)
class ReadinessProbeTest extends ObservabilityEventloopTestBase {
    
    @Mock private DatabaseHealthCheck dbCheck;
    @Mock private CacheHealthCheck cacheCheck;
    
    private ReadinessProbe readinessProbe;
    
    @BeforeEach
    void setUp() {
        readinessProbe = new ReadinessProbe(Arrays.asList(dbCheck, cacheCheck));
        lenient().when(dbCheck.isHealthy())
            .thenReturn(Promise.of(true));
        lenient().when(cacheCheck.isHealthy())
            .thenReturn(Promise.of(true));
    }
    
    @Test
    void shouldReportReadyWhenAllChecksPass() {
        HealthStatus status = runPromise(() -> readinessProbe.check());
        
        assertThat(status.isReady()).isTrue();
        assertThat(status.details()).containsKey("database").containsKey("cache");
    }
    
    @Test
    void shouldReportNotReadyWhenAnyCheckFails() {
        when(cacheCheck.isHealthy()).thenReturn(Promise.of(false));
        
        HealthStatus status = runPromise(() -> readinessProbe.check());
        
        assertThat(status.isReady()).isFalse();
        assertThat(status.details().get("cache")).contains("unhealthy");
    }
}
```

#### 6. Integration Tests (4 tests)
**Files**:
- `integration/OpenTelemetryExportTest.java` (2 tests)
- `integration/PrometheusExportTest.java` (1 test)
- `integration/JaegerExportTest.java` (1 test)

```java
@DisplayName("OpenTelemetry Export Tests")
@ExtendWith(MockitoExtension.class)
class OpenTelemetryExportTest extends ObservabilityEventloopTestBase {
    
    @Mock private OtlpHttpSpanExporter spanExporter;
    @Mock private OtlpHttpMetricExporter metricExporter;
    
    private OpenTelemetryIntegration otlp;
    
    @BeforeEach
    void setUp() {
        otlp = new OpenTelemetryIntegration(spanExporter, metricExporter);
    }
    
    @Test
    void shouldExportSpansThroughOtlpHttpProtocol() {
        Span span = otlp.tracer().spanBuilder("operation").startSpan();
        span.setStatus(StatusCode.OK);
        span.end();
        
        // Wait for async export
        runPromise(() -> Promise.ofBlocking(() -> {
            Thread.sleep(100);  // Allow exporter time
            return null;
        }));
        
        verify(spanExporter, atLeastOnce()).export(containing(span));
    }
    
    @Test
    void shouldBatchMetricsForEfficientExport() { /* ... */ }
}
```

---

## Execution Checklist

### Week 5 Security Module (4 days, 48 hours)
- [ ] Day 1: Set up test structure + authenticate tests (8 tests) — 3 hours
- [ ] Day 2: JWT tests (12 tests) + Encryption tests (8 tests) — 16 hours
- [ ] Day 3: RBAC tests (10 tests) + API Key tests (4 tests) — 14 hours
- [ ] Day 4: Integration tests (4 tests) + verify build + document patterns — 10 hours

### Week 6 Observability Module (4.5 days, 52 hours)
- [ ] Day 1: Set up test structure + metrics tests (12 tests) — 4 hours
- [ ] Day 2: Tracing tests (14 tests) — 15 hours
- [ ] Day 3: Logging tests (10 tests) — 12 hours
- [ ] Day 4: Correlation + health tests (12 tests) — 15 hours
- [ ] Day 5: Integration tests (4 tests) + verify + document — 6 hours

### Definition of Complete
✓ All tests pass in CI  
✓ ≥90% code coverage achieved  
✓ Zero linting warnings  
✓ README.md updated with test patterns  
✓ Build time < 45 seconds  
✓ ArchUnit boundary checks pass  

---

## Reusable Code Artifacts

### Base Test Classes (Copy from identity module)
```
platform/java/security/src/test/java/com/ghatana/platform/security/base/SecurityEventloopTestBase.java
platform/java/observability/src/test/java/com/ghatana/platform/observability/base/ObservabilityEventloopTestBase.java
```

**Content**: Extend from `EventloopTestBase`, add `runPromise()` harness + common setup

### Test Fixture Template
```java
public abstract class DomainTestFixture<T> {
    public static T standard() { ... }
    public static T withConfiguration(Consumer<Builder> config) { ... }
    public static List<T> batch(int count) { ... }
}
```

### Mock Factory Pattern
```java
public class <Domain>MockFactory {
    public static Mock<Service> <service>() { ... }
    public static <DataType> sample<DataType>() { ... }
    public static ArgumentMatcher<T> <domain>Matching(...) { ... }
}
```

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| **Test flakiness** | Use `EventloopTestBase`, avoid `Thread.sleep()` |
| **Async issues** | Always use `runPromise()` wrapper for test execution |
| **Mock complexity** | Leverage fixture factories + lenient mocking |
| **Build time growth** | Parallel test execution, fixture caching |
| **Pattern deviation** | Code review + ArchUnit test boundary checks |

---

## Approval Criteria

This accelerator is approved when:
- [ ] Architecture Team reviews patterns and agrees
- [ ] QA Lead approves test scope and coverage targets
- [ ] Platform Lead approves Week 5-6 resource allocation

**Next**: Stakeholder presentation (presentation date: 2026-04-12)

