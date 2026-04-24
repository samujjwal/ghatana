# Unified Authentication & Tenant Middleware Standards

**Established:** 2026-04-23  
**Status:** ACTIVE  
**Scope:** All gateway layers, services, and frontends  
**Owner:** Platform Security + Auth Gateway  

---

## Overview

All authentication and tenant context management must comply with unified standards to ensure:
- **Single tenant isolation model** across all products
- **Consistent authentication flows** (JWT, OAuth2, mTLS)
- **Transparent tenant propagation** through request chain
- **Audit trail** for all auth decisions
- **Frontend-backend state consistency**

---

## Standard 1: Tenant Context Propagation

### Java Backend (ActiveJ)

**Location**: `platform:java:security` → `TenantContextHolder`  
**Lifecycle**: RequestFilter (extract) → Handler (propagate) → Filter (clear)

```java
/**
 * Thread-local tenant context holder.
 * Extracted once per request, propagated through entire call chain.
 */
public class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();
    
    public static void setCurrentTenant(String tenantId) {
        CURRENT.set(new TenantContext(tenantId, clock.instant()));
    }
    
    public static TenantContext getCurrentTenant() {
        TenantContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new MissingTenantContextException("No tenant context set for request");
        }
        return ctx;
    }
    
    public static void clear() {
        CURRENT.remove();
    }
}

public record TenantContext(
    String tenantId,
    Instant requestTimestamp
) {}
```

**Usage Pattern** (in service classes):
```java
@Service
public class UserService {
    public User getUser(String userId) {
        var tenant = TenantContextHolder.getCurrentTenant();
        
        // Always filter by tenant in queries
        return userRepository.findByIdAndTenantId(userId, tenant.tenantId())
            .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
```

### Gateway Middleware (Java - HTTP)

**Location**: `shared-services:auth-gateway` → `TenantContextFilter`

```java
public class TenantContextFilter implements AsyncServlet {
    private final JwtTokenProvider tokenProvider;
    private final Logger logger = LoggerFactory.getLogger(TenantContextFilter.class);
    
    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        try {
            // Extract tenant from JWT or header
            String tenantId = extractTenantId(request);
            
            if (tenantId == null || tenantId.isBlank()) {
                logger.warn("Request missing tenant context: {}", 
                    request.getPath());
                return Promise.of(HttpResponse.ok401());
            }
            
            // Set in thread-local for handler execution
            TenantContextHolder.setCurrentTenant(tenantId);
            
            // Log tenant in MDC for observability
            MDC.put("tenantId", tenantId);
            
            // Proceed with request
            return next.serve(request)
                .then(response -> {
                    // Propagate tenant in response headers (optional, for correlation)
                    return response.withHeader("X-Tenant-ID", tenantId);
                }, exception -> {
                    logger.error("Request failed for tenant: {}", tenantId, exception);
                    throw exception;
                })
                .whenComplete((response, exception) -> {
                    // ALWAYS clear tenant context
                    TenantContextHolder.clear();
                    MDC.remove("tenantId");
                });
        } catch (Exception e) {
            logger.error("TenantContextFilter error", e);
            TenantContextHolder.clear();
            MDC.remove("tenantId");
            return Promise.of(HttpResponse.ok500());
        }
    }
    
    private String extractTenantId(HttpRequest request) {
        // Priority 1: JWT claim
        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            var token = authHeader.substring(7);
            try {
                return tokenProvider.extractTenantClaim(token);
            } catch (InvalidTokenException e) {
                return null;
            }
        }
        
        // Priority 2: Explicit header
        return request.getHeader("X-Tenant-ID");
    }
}
```

### TypeScript Frontend

**Location**: `platform:typescript:state` (Jotai atoms)  
**Lifecycle**: AuthContext (extract from JWT) → TenantAtom (global state) → API interceptor (propagate)

```typescript
import { atom } from 'jotai';

// Singleton tenant context
export const tenantIdAtom = atom<string | null>(null);

// Extract from JWT on app startup
export function initializeTenantContext() {
  const token = localStorage.getItem('authToken');
  if (!token) return;
  
  const decoded = jwtDecode<TokenPayload>(token);
  useAtom(tenantIdAtom)[1](decoded.tenantId);  // Set atom
}

// API interceptor ensures X-Tenant-ID on every request
export const apiClient = createClient({
  baseURL: API_BASE,
  interceptors: {
    request: (config) => {
      const tenant = useAtomValue(tenantIdAtom);
      if (tenant) {
        config.headers['X-Tenant-ID'] = tenant;
      }
      return config;
    },
  },
});
```

---

## Standard 2: JWT Authentication Contract

### Token Structure (Required Claims)

```json
{
  "iss": "https://ghatana.example.com",
  "sub": "user-12345",
  "aud": ["product-api", "marketing-api"],
  "iat": 1682505600,
  "exp": 1682592000,
  "tenant_id": "org-acme",
  "user_id": "user-12345",
  "email": "alice@acme.com",
  "roles": ["admin", "developer"],
  "permissions": ["org:read", "org:write", "product:deploy"],
  "scope": "openid profile email"
}
```

### Validation Rules (Java)

```java
public class JwtValidator {
    
    /**
     * Validate JWT signature, expiration, and required claims.
     * Throws if invalid; returns deserialized claims if valid.
     */
    public JwtClaims validateToken(String token) throws InvalidTokenException {
        try {
            // Verify signature
            var algorithm = Algorithm.RSA256(publicKey, null);
            var verifier = JWT.require(algorithm)
                .withIssuer("https://ghatana.example.com")
                .withAudience("product-api")
                .build();
            
            var decodedJwt = verifier.verify(token);
            
            // Verify required claims
            requireClaim(decodedJwt, "tenant_id");
            requireClaim(decodedJwt, "user_id");
            requireClaim(decodedJwt, "email");
            
            return new JwtClaims(decodedJwt);
            
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException("JWT validation failed: " + e.getMessage());
        }
    }
    
    private void requireClaim(DecodedJWT jwt, String claimName) 
        throws InvalidTokenException {
        if (jwt.getClaim(claimName).isNull()) {
            throw new InvalidTokenException($"Missing required claim: {claimName}");
        }
    }
}

public record JwtClaims(
    String tenantId,
    String userId,
    String email,
    Set<String> roles,
    Set<String> permissions,
    Instant issuedAt,
    Instant expiresAt
) {
    public JwtClaims(DecodedJWT jwt) {
        this(
            jwt.getClaim("tenant_id").asString(),
            jwt.getClaim("user_id").asString(),
            jwt.getClaim("email").asString(),
            Set.copyOf(jwt.getClaim("roles").asList(String.class)),
            Set.copyOf(jwt.getClaim("permissions").asList(String.class)),
            Instant.ofEpochSecond(jwt.getClaim("iat").asLong()),
            Instant.ofEpochSecond(jwt.getClaim("exp").asLong())
        );
    }
}
```

### Validation Contract Tests

**Location**: `platform:java:testing`  
**Test Class**: `JwtValidationContractTests`

```java
@DisplayName("JWT Validation Contract Tests")
class JwtValidationContractTests {
    
    private JwtValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new JwtValidator(TEST_PUBLIC_KEY);
    }
    
    @Test
    void validToken_passesValidation() {
        var token = generateValidToken(
            Map.of("tenant_id", "org-acme", "user_id", "user-123")
        );
        
        var claims = validator.validateToken(token);
        assertThat(claims.tenantId()).isEqualTo("org-acme");
    }
    
    @Test
    void missingTenantClaim_throwsInvalidTokenException() {
        var token = generateTokenWithoutClaim("tenant_id");
        
        assertThatThrownBy(() -> validator.validateToken(token))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("tenant_id");
    }
    
    @Test
    void expiredToken_throwsInvalidTokenException() {
        var token = generateExpiredToken();
        
        assertThatThrownBy(() -> validator.validateToken(token))
            .isInstanceOf(InvalidTokenException.class);
    }
    
    @Test
    void invalidSignature_throwsInvalidTokenException() {
        var token = generateTokenWithWrongKey(WRONG_KEY);
        
        assertThatThrownBy(() -> validator.validateToken(token))
            .isInstanceOf(InvalidTokenException.class);
    }
}
```

---

## Standard 3: Request Header Extraction

### Unified Header Contract

| Header | Source | Usage | Required |
|--------|--------|-------|----------|
| `Authorization` | JWT token | Auth validation | Yes |
| `X-Tenant-ID` | JWT claim or header | Tenant isolation | Yes |
| `X-Request-ID` | Gateway | Request correlation | Yes |
| `X-Correlation-ID` | Client or generated | Cross-service tracing | Yes |
| `X-User-ID` | JWT claim | User attribution | Yes |

### Java Header Extraction

```java
public record RequestContext(
    String authorizationHeader,
    String tenantId,
    String requestId,
    String correlationId,
    String userId
) {
    public static RequestContext fromRequest(HttpRequest request) {
        return new RequestContext(
            request.getHeader("Authorization"),
            request.getHeader("X-Tenant-ID"),
            request.getHeader("X-Request-ID") ?? UUID.randomUUID().toString(),
            request.getHeader("X-Correlation-ID") ?? UUID.randomUUID().toString(),
            request.getHeader("X-User-ID")
        );
    }
}
```

---

## Standard 4: Cross-Service Propagation

### ActiveJ Promise Chaining

**Pattern**: Every async boundary must propagate tenant context

```java
public Promise<User> fetchUserDetails(String userId) {
    var tenant = TenantContextHolder.getCurrentTenant();
    
    return userService.getUser(userId)  // async call
        .then(user -> {
            // Tenant context is lost here! Re-set it.
            TenantContextHolder.setCurrentTenant(tenant.tenantId());
            return orgsService.getOrganization(user.orgId());
        })
        .then(org -> {
            TenantContextHolder.setCurrentTenant(tenant.tenantId());
            return buildUserDetailsResponse(user, org);
        })
        .whenComplete((response, exception) -> {
            TenantContextHolder.clear();
        });
}
```

### TypeScript Observable Pattern

```typescript
export function withTenantContext<T>(
  observable: Observable<T>,
  tenantId: string
): Observable<T> {
  return observable.pipe(
    tap(() => {
      // Ensure tenant is set throughout observable chain
      tenantContextAtom.set(tenantId);
    }),
    finalize(() => {
      // Clean up on complete/error
      tenantContextAtom.set(null);
    })
  );
}
```

---

## Standard 5: Backend Enforcement

### Service-Level Enforcement

Every service method MUST verify tenant before accessing data:

```java
@Repository
public class UserRepository {
    
    public Optional<User> findById(String userId) {
        var tenant = TenantContextHolder.getCurrentTenant();
        
        // Query includes tenant filter
        return database.query(
            "SELECT * FROM users WHERE id = ? AND tenant_id = ?",
            userId,
            tenant.tenantId()
        );
    }
}
```

### Controller-Level Enforcement

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @GetMapping("/{userId}")
    public Promise<User> getUser(@PathVariable String userId) {
        // TenantContextHolder populated by middleware
        var tenant = TenantContextHolder.getCurrentTenant();
        
        return userService.getUser(userId)
            .then(user -> {
                // Verify user belongs to tenant
                if (!user.tenantId().equals(tenant.tenantId())) {
                    throw new AccessDeniedException($"User {userId} not found");
                }
                return Promise.of(user);
            });
    }
}
```

---

## Standard 6: Audit Logging

Every auth-relevant action MUST be logged:

```java
public class AuthAuditLogger {
    private static final Logger logger = LoggerFactory.getLogger("AUTH_AUDIT");
    
    public void logAuthenticationAttempt(String userId, String tenantId, boolean success) {
        logger.info(
            "event=authentication user_id={} tenant_id={} success={} timestamp={}",
            userId,
            tenantId,
            success,
            Instant.now()
        );
    }
    
    public void logTenantSwitch(String userId, String fromTenant, String toTenant) {
        logger.warn(
            "event=tenant_context_change user_id={} from_tenant={} to_tenant={} timestamp={}",
            userId,
            fromTenant,
            toTenant,
            Instant.now()
        );
    }
    
    public void logAuthorizationFailure(String userId, String tenantId, String resource) {
        logger.warn(
            "event=authorization_denied user_id={} tenant_id={} resource={} timestamp={}",
            userId,
            tenantId,
            resource,
            Instant.now()
        );
    }
}
```

---

## Standard 7: UI State Consistency

### Frontend State Management

```typescript
interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
  tenantId: string | null;
  expiresAt: Date | null;
}

export const authStateAtom = atom<AuthState>({
  isAuthenticated: false,
  user: null,
  tenantId: null,
  expiresAt: null,
});

export function useAuthState() {
  return useAtom(authStateAtom);
}

// On app startup: restore from JWT
export function initializeAuth() {
  const token = localStorage.getItem('authToken');
  if (!token) return;
  
  const decoded = jwtDecode<TokenPayload>(token);
  
  setAuthState({
    isAuthenticated: true,
    user: decoded.user,
    tenantId: decoded.tenant_id,
    expiresAt: new Date(decoded.exp * 1000),
  });
}

// On logout: clear everything
export function logout() {
  localStorage.removeItem('authToken');
  setAuthState({
    isAuthenticated: false,
    user: null,
    tenantId: null,
    expiresAt: null,
  });
}
```

---

## Standard 8: Cross-Root Contract Tests

**Location**: `integration-tests/auth-contract/`

```java
@DisplayName("Auth Contract Tests (Cross-Root)")
@ExtendWith(DockerComposeExtension.class)
class AuthContractTests {
    
    private HttpClient apiClient;
    private HttpClient gatewayClient;
    
    @Test
    void jwtPayloadValidation_tenantClaimRequired() {
        var token = generateTokenWithoutClaim("tenant_id");
        
        var response = gatewayClient.get("/api/v1/users", 
            headers("Authorization", "Bearer " + token)
        );
        
        assertThat(response.status()).isEqualTo(401);
    }
    
    @Test
    void requestHeaderExtraction_propagatesTenantThroughGateway() {
        var token = generateValidToken("org-acme");
        
        var response = gatewayClient.get("/api/v1/users/me",
            headers("Authorization", "Bearer " + token)
        );
        
        // Verify backend received X-Tenant-ID header
        assertThat(response.header("X-Tenant-ID")).isEqualTo("org-acme");
    }
    
    @Test
    void gatewayPropagation_tenantIsolationEnforced() {
        // User1 tries to access User2's data
        var user1Token = generateValidToken("org-acme", "user-1");
        var user2Id = "user-2";  // Belongs to org-acme, but assigned to diff team
        
        var response = apiClient.get($"/api/v1/users/{user2Id}",
            headers("Authorization", "Bearer " + user1Token)
        );
        
        // Should fail if different tenant
        assertThat(response.status()).isIn(403, 404);
    }
    
    @Test
    void backendEnforcement_rejectorMissingTenantContext() {
        // Call backend directly (bypass gateway)
        var directResponse = apiClient.get("/internal/admin/users");
        
        // Should fail: no tenant context
        assertThat(directResponse.status()).isEqualTo(400, 403);
    }
    
    @Test
    void auditLogging_logsAuthenticationEvents() {
        var token = generateValidToken("org-acme");
        
        gatewayClient.get("/api/v1/users/me",
            headers("Authorization", "Bearer " + token)
        );
        
        // Verify audit log contains entry
        var auditLogs = auditLogRepository.findRecent("AUTH", Duration.ofSeconds(5));
        assertThat(auditLogs).anySatisfy(log -> 
            log.event().equals("authentication") &&
            log.tenantId().equals("org-acme")
        );
    }
}
```

---

## Implementation Checklist

- [ ] **Phase 1 (Week 1)**: Audit existing auth code; identify duplicates
- [ ] **Phase 2 (Week 2-3)**: Implement unified TenantContextHolder in platform:java:security
- [ ] **Phase 3 (Week 3-4)**: Implement JWT validator contract in platform:java:security
- [ ] **Phase 4 (Week 4-5)**: Update all gateways (AEP, YAPPC, shared-services) to use TenantContextFilter
- [ ] **Phase 5 (Week 5-6)**: Update all services to enforce tenant filtering
- [ ] **Phase 6 (Week 6)**: Create contract tests (integration-tests/auth-contract)
- [ ] **Phase 7 (Week 7-8)**: Update TypeScript API client + auth state management
- [ ] **Phase 8 (Week 8)**: Deploy & monitor; update on-call runbooks

---

**Owner**: Platform Security Lead  
**Status**: Draft (awaiting implementation kickoff)  
**Review Date**: 2026-05-07
