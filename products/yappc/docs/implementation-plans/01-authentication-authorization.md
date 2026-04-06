# Authentication & Authorization — Detailed Implementation Plan

**Priority:** P0 BLOCKER  
**Current State:** 4/10 — BFF JWT secrets are hardened, auth middleware is tested, Java JWT/auth moved to product-level AI API packages, but session/rotation and product-wide enforcement are still incomplete  
**Target State:** 10/10 — Real OAuth + JWT unified across all Java services; RBAC enforced from API to domain  
**Estimated Effort:** 5 sprints (L=10 days each = ~50 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `JwtService.java` | `core/ai/src/.../ai/api/security/` | Exists, moved to product-level AI API package |
| `AuthUtil.java` | Same package | Exists, product-level |
| `auth.middleware.ts` + tests | `frontend/apps/api/src/middleware/` | Exists, tested |
| `rbac.middleware.ts` + tests | `frontend/apps/api/src/middleware/` | Exists, tested |
| `devAuth.ts` | `frontend/apps/api/src/middleware/` | Still needs production gating audit |
| `auth.service.ts` | `frontend/apps/api/src/services/auth/` | Exists, now backed by validated JWT runtime config |
| `rbac.service.ts` | `frontend/apps/api/src/services/auth/` | Exists |
| `OAuthButton.tsx` | `frontend/libs/auth/src/oauth/` | Exists |
| `useOAuth.ts` | `frontend/libs/auth/src/oauth/hooks/` | Exists |
| `rbac/types.ts`, `rbac/utils.ts` | `frontend/libs/auth/src/rbac/` | Exists |
| `jwt-config.ts` | `frontend/apps/api/src/services/auth/` | Exists, validates access/refresh secrets |
| Session management | — | **MISSING** |
| 2FA | — | **MISSING** |

### Critical Gaps

1. **Unified Java auth is only partially propagated** — the AI API has product-level auth, but other Java surfaces still need the same baseline
2. **`devAuth.ts` still needs a hard production guard** — current repo state should not rely on convention alone
3. **Some controllers still need principal cleanup** — hardcoded-user assumptions must be removed across remaining services
4. **RBAC is not consistently enforced end to end** — the BFF path is stronger than the downstream Java path today
5. **No session or refresh token management** — JWTs still lack rotation and revocation workflows

---

## 2. Target Architecture

```
Browser (React)
    │  OAuth 2.0 Authorization Code Flow
    ▼
OAuth Provider (Google / GitHub / Custom SSO)
    │  authorization_code
    ▼
BFF (Fastify - frontend/apps/api)
    │  Issues signed JWT (access_token + refresh_token)
    │  Stores session in Prisma (sessions table)
    │  Enforces RBAC via rbac.middleware.ts
    ▼  JWT in Authorization header
Java Backend (ActiveJ services)
    │  JwtAuthFilter (new - platform-wide)
    │  Extracts TenantContext + UserPrincipal
    │  RBACEvaluator verifies permission per endpoint
    ▼
Domain Services (YappcLifecycleService, etc.)
    │  UserPrincipal available via TenantContext
    │  Audit log written for sensitive actions
```

---

## 3. Implementation Tasks

### Sprint 1 — Secure the BFF (8 days)

#### T1.1 — Audit and Gate `devAuth.ts` [MOD] [S]
**File:** `frontend/apps/api/src/middleware/devAuth.ts`

```typescript
// BEFORE: unguarded bypass
// AFTER: hard fail in non-development environments
import { FastifyRequest, FastifyReply } from 'fastify';

export function devAuthMiddleware(request: FastifyRequest, reply: FastifyReply, done: () => void): void {
  if (process.env.NODE_ENV !== 'development') {
    throw new Error('devAuth middleware must not be loaded in non-development environments');
  }
  done();
}
```

Add CI lint rule in ESLint config to fail if `devAuth` is imported outside `NODE_ENV === 'development'`:
```js
// eslint-rules/no-dev-auth-in-prod.js
module.exports = {
  rules: {
    'no-restricted-imports': ['error', { paths: [{ name: '../middleware/devAuth', message: 'devAuth must not be used in production code paths' }] }]
  }
};
```

#### T1.2 — Implement Session Table in Prisma [NEW] [M]
**File:** `frontend/apps/api/prisma/schema.prisma` [MOD]

```prisma
model UserSession {
  id           String   @id @default(cuid())
  userId       String
  tenantId     String
  refreshToken String   @unique
  accessToken  String
  expiresAt    DateTime
  createdAt    DateTime @default(now())
  revokedAt    DateTime?
  ipAddress    String?
  userAgent    String?
  user         User     @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@index([userId])
  @@index([refreshToken])
  @@map("user_sessions")
}
```

#### T1.3 — Implement Token Rotation in `auth.service.ts` [MOD] [M]
**File:** `frontend/apps/api/src/services/auth/auth.service.ts`

Add methods:
- `issueTokenPair(user: UserPrincipal, tenantId: string): Promise<TokenPair>` — returns short-lived access (15min) + long-lived refresh (7d)
- `refreshAccessToken(refreshToken: string): Promise<TokenPair>` — validates refresh, issues new pair, rotates old
- `revokeSession(refreshToken: string): Promise<void>`
- `revokeAllSessions(userId: string): Promise<void>` — for password change / security events

#### T1.4 — Add Refresh Token Endpoint [NEW] [S]
**File:** `frontend/apps/api/src/routes/auth.ts` [MOD]

```typescript
// POST /api/v1/auth/refresh
fastify.post('/api/v1/auth/refresh', async (request, reply) => {
  const { refreshToken } = request.body as { refreshToken: string };
  const tokenPair = await authService.refreshAccessToken(refreshToken);
  return reply.send(tokenPair);
});

// POST /api/v1/auth/logout
fastify.post('/api/v1/auth/logout', async (request, reply) => {
  const { refreshToken } = request.body as { refreshToken: string };
  await authService.revokeSession(refreshToken);
  return reply.status(204).send();
});
```

#### T1.5 — Enforce RBAC Middleware on All Routes [MOD] [M]
**File:** `frontend/apps/api/src/routes/*.ts`

Apply `rbac.middleware.ts` to every protected route using Fastify hooks. Unprotected routes must be explicitly declared in an allowlist.

```typescript
// Explicit allowlist — any route NOT in this list requires authentication
const PUBLIC_ROUTES = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/healthz'];

fastify.addHook('onRequest', async (request, reply) => {
  if (PUBLIC_ROUTES.includes(request.routerPath)) return;
  await authMiddleware(request, reply);
  await rbacMiddleware(request, reply);
});
```

---

### Sprint 2 — Unified Java Auth Layer (10 days)

#### T2.1 — Extract `JwtService` to Platform-Wide Location [MOD] [M]
**Current:** `core/ai/src/.../requirements/api/security/JwtService.java`  
**Target:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/JwtService.java`

Move class, update all import references. This service must:
- Validate JWT signed by BFF (`HS256` or `RS256`)
- Extract `userId`, `tenantId`, `roles`, `permissions` claims
- Return `UserPrincipal` value object

```java
/**
 * @doc.type class
 * @doc.purpose Validates JWTs issued by the BFF and extracts user principal for downstream authorization.
 * @doc.layer platform
 * @doc.pattern Security
 */
public final class JwtService {
    private final String jwtSecret;  // loaded from config, never hardcoded
    
    public Optional<UserPrincipal> validate(String bearerToken) { ... }
    public UserPrincipal validateOrThrow(String bearerToken) { ... }
}
```

#### T2.2 — Create `UserPrincipal` Value Object [NEW] [S]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/UserPrincipal.java`

```java
/**
 * @doc.type class
 * @doc.purpose Immutable authenticated user identity extracted from a validated JWT.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record UserPrincipal(
    String userId,
    String tenantId,
    Set<String> roles,
    Set<String> permissions
) {
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
    
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
```

#### T2.3 — Create `JwtAuthFilter` for ActiveJ [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/JwtAuthFilter.java`

```java
/**
 * @doc.type class
 * @doc.purpose ActiveJ AsyncServlet decorator that validates JWT and populates TenantContext with UserPrincipal.
 * @doc.layer platform
 * @doc.pattern Decorator / Filter
 */
public final class JwtAuthFilter implements AsyncServlet {
    private final AsyncServlet delegate;
    private final JwtService jwtService;
    private final Set<String> publicPaths;
    
    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        if (publicPaths.contains(request.getPath())) {
            return delegate.serve(request);
        }
        
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Promise.of(HttpResponse.ofCode(401)
                .withBody("{\"error\": \"Missing or invalid Authorization header\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        }
        
        String token = authHeader.substring(7);
        return jwtService.validate(token)
            .map(principalOpt -> principalOpt.orElseThrow(() -> new UnauthorizedException("Invalid token")))
            .then(principal -> {
                TenantContext.setCurrentTenantId(principal.tenantId());
                TenantContext.setCurrentUser(principal);
                return delegate.serve(request)
                    .whenComplete((res, ex) -> TenantContext.clear());
            });
    }
}
```

#### T2.4 — Create `RBACEvaluator` [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/RBACEvaluator.java`

```java
/**
 * @doc.type class
 * @doc.purpose Evaluates RBAC permissions for a given action on a resource using the current UserPrincipal.
 * @doc.layer platform
 * @doc.pattern Policy Evaluator
 */
public final class RBACEvaluator {
    public void requirePermission(UserPrincipal principal, String permission) {
        if (!principal.hasPermission(permission)) {
            throw new ForbiddenException("Permission denied: " + permission);
        }
    }
    
    public void requireRole(UserPrincipal principal, String role) {
        if (!principal.hasRole(role)) {
            throw new ForbiddenException("Role required: " + role);
        }
    }
}
```

#### T2.5 — Wire `JwtAuthFilter` into all Java HTTP Servers [MOD] [M]
Locate all ActiveJ `HttpServer` or routing bootstrap files in:
- `core/services-lifecycle/src/.../bootstrap/`
- `core/ai/src/.../requirements/api/`
- `platform/src/.../`

Wrap the root servlet with `JwtAuthFilter`.

#### T2.6 — Remove Hardcoded User "John Doe" Globally [MOD] [M]
Search all Java files for hardcoded user strings. Replace with `TenantContext.getCurrentUser()` calls.

```bash
# Find all occurrences
grep -r "John Doe\|hardcoded\|mockUser\|\"mock\"" products/yappc/core/
```

All controllers must read user from `TenantContext`, not literals.

---

### Sprint 3 — Frontend Integration (8 days)

#### T3.1 — Implement Real OAuth Login Flow [NEW] [M]
**File:** `frontend/apps/web/src/auth/LoginPage.tsx` [NEW if not existing]

Connect `OAuthButton.tsx` to the BFF `/api/v1/auth/login` and handle the callback:

```typescript
interface LoginPageProps {}

const LoginPage: React.FC<LoginPageProps> = () => {
  const { login, isLoading, error } = useAuth();

  const handleOAuthLogin = useCallback(async (provider: OAuthProvider) => {
    await login(provider);
  }, [login]);

  return (
    <div className="flex flex-col items-center justify-center min-h-screen">
      <OAuthButton provider="google" onClick={() => handleOAuthLogin('google')} disabled={isLoading} />
      <OAuthButton provider="github" onClick={() => handleOAuthLogin('github')} disabled={isLoading} />
      {error && <div role="alert">{error.message}</div>}
    </div>
  );
};
```

#### T3.2 — Create `useAuth` Hook [NEW] [M]
**File:** `frontend/libs/auth/src/hooks/useAuth.ts`

```typescript
interface AuthState {
  user: UserPrincipal | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: Error | null;
}

interface UseAuthReturn extends AuthState {
  login: (provider: OAuthProvider) => Promise<void>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<void>;
}

export function useAuth(): UseAuthReturn { ... }
```

#### T3.3 — Implement Route Guards [NEW] [M]
**File:** `frontend/apps/web/src/auth/AuthGuard.tsx`

```typescript
interface AuthGuardProps {
  requiredPermission?: string;
  requiredRole?: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

const AuthGuard: React.FC<AuthGuardProps> = ({
  requiredPermission,
  requiredRole,
  children,
  fallback = <UnauthorizedPage />,
}) => {
  const { user, isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <LoadingSpinner />;
  if (!isAuthenticated) return <Navigate to="/login" />;
  if (requiredPermission && !user?.permissions.has(requiredPermission)) return <>{fallback}</>;
  if (requiredRole && !user?.roles.has(requiredRole)) return <>{fallback}</>;

  return <>{children}</>;
};
```

#### T3.4 — Add Token Auto-Refresh [MOD] [M]
**File:** `frontend/apps/api/src/services/auth/auth.service.ts`

Add an Axios/Fetch interceptor that detects `401` responses and automatically refreshes the token before retrying the request. Use a mutex to prevent multiple simultaneous refresh calls.

---

### Sprint 4 — RBAC Persona Integration (8 days)

The audit identified 21 personas in YAPPC. Each persona maps to a role with specific permissions.

#### T4.1 — Define Permission Constants [NEW] [M]
**File:** `frontend/libs/auth/src/rbac/permissions.ts`

```typescript
export const Permissions = {
  // Project management
  PROJECT_CREATE: 'project:create',
  PROJECT_READ: 'project:read',
  PROJECT_UPDATE: 'project:update',
  PROJECT_DELETE: 'project:delete',
  // Phase transitions
  PHASE_ADVANCE: 'phase:advance',
  PHASE_ROLLBACK: 'phase:rollback',
  // Approvals
  APPROVAL_SUBMIT: 'approval:submit',
  APPROVAL_REVIEW: 'approval:review',
  APPROVAL_OVERRIDE: 'approval:override',
  // Code generation
  CODE_GENERATE: 'code:generate',
  CODE_REVIEW: 'code:review',
  // Agent management
  AGENT_CONFIGURE: 'agent:configure',
  AGENT_EXECUTE: 'agent:execute',
  // Admin
  ADMIN_USER_MANAGE: 'admin:user:manage',
  ADMIN_TENANT_MANAGE: 'admin:tenant:manage',
} as const;

export type Permission = typeof Permissions[keyof typeof Permissions];
```

#### T4.2 — Implement Role-to-Permission Mapper [NEW] [M]
**File:** `frontend/libs/auth/src/rbac/rolePermissionMap.ts`

Map all 21 personas to role-permission sets. Example:

```typescript
export const ROLE_PERMISSIONS: Record<string, Permission[]> = {
  'product_owner': [
    Permissions.PROJECT_CREATE, Permissions.PROJECT_READ,
    Permissions.PHASE_ADVANCE, Permissions.APPROVAL_REVIEW,
  ],
  'developer': [
    Permissions.PROJECT_READ, Permissions.CODE_GENERATE,
    Permissions.CODE_REVIEW, Permissions.APPROVAL_SUBMIT,
  ],
  'architect': [
    Permissions.PROJECT_READ, Permissions.CODE_REVIEW,
    Permissions.AGENT_CONFIGURE, Permissions.PHASE_ADVANCE,
  ],
  'admin': Object.values(Permissions),
};
```

Equivalent Java enum in:
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/YappcPermission.java`

#### T4.3 — Wire Persona from JWT Claims [MOD] [S]
Update `JwtService.java` to extract `persona` from the JWT `roles` claim and resolve it to `Set<String> permissions` using the role map.

---

### Sprint 5 — Audit Logging & 2FA Foundation (6 days)

#### T5.1 — Implement Security Audit Log [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/SecurityAuditLogger.java`

```java
/**
 * @doc.type class
 * @doc.purpose Records security-significant events for audit trail and anomaly detection.
 * @doc.layer platform
 * @doc.pattern Audit Log
 */
public final class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);
    
    public void logLogin(String userId, String tenantId, String ipAddress, boolean success) {
        log.info("security.auth.login tenantId={} userId={} ip={} success={}", 
                 tenantId, userId, ipAddress, success);
    }
    
    public void logPermissionDenied(String userId, String permission, String resource) {
        log.warn("security.auth.denied userId={} permission={} resource={}", 
                 userId, permission, resource);
    }
    
    public void logTokenRefresh(String userId, String tenantId) {
        log.info("security.auth.token_refresh tenantId={} userId={}", tenantId, userId);
    }
}
```

#### T5.2 — TOTP 2FA Foundation [NEW] [L]
**File:** `frontend/apps/api/src/services/auth/totp.service.ts`

Implement TOTP generation and verification:
- `generateTOTPSecret(userId: string): Promise<TOTPSetup>` — returns secret + QR code URL
- `verifyTOTP(userId: string, code: string): Promise<boolean>`
- Store encrypted TOTP secret in Prisma `UserProfile` table

Mark 2FA as `optional` initially; enforce for `admin` role immediately.

---

## 4. Testing Requirements

### Unit Tests

| Test Class | Coverage Target | Location |
|-----------|----------------|----------|
| `JwtServiceTest` | 100% paths (valid, expired, tampered, missing) | `core/services-platform/src/test/.../security/` |
| `JwtAuthFilterTest` | Protected vs public routes; 401/403 responses | Same package |
| `RBACEvaluatorTest` | All persona-permission combinations | Same package |
| `AuthServiceTest.ts` | Token issue, refresh, revoke flows | `frontend/apps/api/src/__tests__/services/auth/` |
| `authMiddleware.test.ts` | Missing/expired/invalid token rejection | `frontend/apps/api/src/middleware/__tests__/` |
| `useAuth.test.ts` | Login, logout, refresh, state transitions | `frontend/libs/auth/src/__tests__/` |

### Integration Tests

| Scenario | Test Location |
|----------|--------------|
| Full OAuth login → JWT → protected Java endpoint | `integration-tests/auth/` |
| RBAC: `developer` cannot call `APPROVAL_OVERRIDE` endpoint | `integration-tests/auth/rbac/` |
| Token expiry → auto-refresh → request retried | `frontend/apps/api/src/__tests__/integration/` |
| Hardcoded user eliminated: all endpoints return 401 without auth | `tools/validation-tests/` |

### Security Tests

- [ ] JWT tampering rejected (HMAC signature invalid)
- [ ] Expired JWT rejected
- [ ] `devAuth.ts` not loadable in `NODE_ENV=production`
- [ ] Refresh token cannot be reused after rotation (rotation enforcement)
- [ ] RBAC bypass attempt rejected (role escalation)

---

## 5. AI Enhancement Layer

Once foundational auth is solid, layer in AI-powered security:

### AI-Powered Risk-Based Auth (Phase 2)

**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/AuthRiskEvaluator.java` [NEW]

```java
/**
 * @doc.type class
 * @doc.purpose Uses ML models to evaluate authentication risk score and trigger step-up auth.
 * @doc.layer platform
 * @doc.pattern Risk Evaluator
 */
public final class AuthRiskEvaluator {
    // Signals: IP reputation, time-of-day, device fingerprint, login velocity
    public Promise<RiskScore> evaluate(AuthContext context) { ... }
    
    // If risk > threshold → require 2FA even for users without 2FA enabled
    public boolean requiresStepUp(RiskScore score) { ... }
}
```

### Anomaly Detection (Phase 3)
- Detect unusual permission usage patterns (e.g., user suddenly accessing admin endpoints)
- Fire alert event to `AepEventBridge` when anomaly detected
- Model trained on historical access patterns per tenant

---

## 6. Observability

### Metrics to Expose (`/metrics` endpoint)

```
yappc_auth_logins_total{provider, status}           counter
yappc_auth_token_refresh_total{status}              counter
yappc_auth_rbac_denied_total{permission, role}      counter
yappc_auth_session_active_count{tenant_id}          gauge
yappc_auth_totp_validations_total{status}           counter
yappc_auth_risk_score_histogram                     histogram
```

### Grafana Dashboard Panels
- Login success/failure rate by provider
- Active sessions per tenant
- RBAC denial heatmap by permission
- Token refresh frequency (indicates short expiry tuning needed)

### Structured Log Fields
All security log entries must include: `correlationId`, `tenantId`, `userId`, `action`, `resource`, `outcome`, `ipAddress`.

---

## 7. File Checklist Summary

### New Files

- [ ] `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/JwtService.java` [NEW - moved + expanded]
- [ ] `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/UserPrincipal.java`
- [ ] `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/JwtAuthFilter.java`
- [ ] `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/RBACEvaluator.java`
- [ ] `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/YappcPermission.java`
- [ ] `core/services-platform/src/main/java/com/ghatana/yappc/platform/security/SecurityAuditLogger.java`
- [ ] `frontend/libs/auth/src/rbac/permissions.ts`
- [ ] `frontend/libs/auth/src/rbac/rolePermissionMap.ts`
- [ ] `frontend/libs/auth/src/hooks/useAuth.ts`
- [ ] `frontend/apps/web/src/auth/AuthGuard.tsx`
- [ ] `frontend/apps/api/src/services/auth/totp.service.ts`

### Modified Files

- [ ] `frontend/apps/api/src/middleware/devAuth.ts` — gate behind `NODE_ENV` check
- [ ] `frontend/apps/api/src/routes/auth.ts` — add refresh + logout endpoints
- [ ] `frontend/apps/api/prisma/schema.prisma` — add `UserSession` table
- [ ] `frontend/apps/api/src/services/auth/auth.service.ts` — token rotation
- [ ] All Java HTTP bootstrap files — wire `JwtAuthFilter`
- [ ] All Java controllers with hardcoded user data — replace with `TenantContext.getCurrentUser()`

### Deleted After Replacement

- [ ] `core/ai/src/.../requirements/api/security/JwtService.java` — after moving to platform
- [ ] `core/ai/src/.../requirements/api/security/AuthUtil.java` — consolidate into `RBACEvaluator`
