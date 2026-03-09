# TutorPutor SSO Architecture

> **Version**: 1.0.0  
> **Last Updated**: 2025-01-XX  
> **Status**: Block 4 Implementation (Days 36-40)

## 1. Overview

TutorPutor supports enterprise Single Sign-On (SSO) through standard identity federation protocols:

- **OIDC (OpenID Connect)** - For Google Workspace, Microsoft Entra ID, Okta
- **SAML 2.0** - For legacy enterprise IdPs

### Key Principles

1. **Reuse `libs/java/auth`**: All OIDC verification uses existing `OidcTokenVerifier`
2. **Multi-tenant Isolation**: Each tenant configures their own IdP
3. **Just-in-Time Provisioning**: Users created on first SSO login
4. **Role Mapping**: IdP claims mapped to TutorPutor roles

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL IDENTITY PROVIDERS                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Google     │  │  Microsoft   │  │    Okta      │  │  Custom      │    │
│  │  Workspace   │  │   Entra ID   │  │              │  │  SAML IdP    │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                  │                  │                  │           │
└─────────┼──────────────────┼──────────────────┼──────────────────┼───────────┘
          │                  │                  │                  │
          │    OIDC          │     OIDC         │     OIDC         │  SAML 2.0
          │                  │                  │                  │
          ▼                  ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY (Fastify)                              │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                        /auth/* ENDPOINTS                               │ │
│  │                                                                        │ │
│  │  GET  /auth/login/:providerId     → Redirect to IdP authorization     │ │
│  │  GET  /auth/callback/:providerId  → Handle IdP callback               │ │
│  │  POST /auth/logout                → Terminate session                 │ │
│  │  GET  /auth/providers             → List enabled IdPs for tenant      │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                       │                                      │
└───────────────────────────────────────┼──────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         libs/java/auth (REUSED)                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  OidcConfiguration                                                      ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  discoveryEndpoint: URI                                            │ ││
│  │  │  clientId: String                                                  │ ││
│  │  │  clientSecret: String (encrypted)                                  │ ││
│  │  │  expectedAudience: String                                          │ ││
│  │  │  expectedIssuer: String                                            │ ││
│  │  │  requestedScopes: Set<String>                                      │ ││
│  │  │  tokenCacheTtl: Duration                                           │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  OidcTokenVerifier                                                      ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  verifyToken(accessToken: String): Promise<OidcToken>              │ ││
│  │  │                                                                    │ ││
│  │  │  Steps:                                                            │ ││
│  │  │  1. Fetch JWKS from discovery endpoint (cached)                    │ ││
│  │  │  2. Extract key ID from JWT header                                 │ ││
│  │  │  3. Verify signature using RS256                                   │ ││
│  │  │  4. Validate claims: exp, iat, aud, iss                            │ ││
│  │  │  5. Extract tenant context from custom claims                      │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  OidcToken (Value Object)                                               ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  subject: String        email: String       name: String           │ ││
│  │  │  tenantId: String       roles: Set<String>  expiresAt: Instant     │ ││
│  │  │  customClaims: Map<String, Object>                                 │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      TUTORPUTOR SSO SERVICE (NEW)                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  SsoProviderRegistry                                                    ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  getProvidersForTenant(tenantId): Promise<IdentityProviderConfig[]>│ ││
│  │  │  getProviderById(providerId): Promise<IdentityProviderConfig>      │ ││
│  │  │  createProvider(config): Promise<IdentityProviderConfig>           │ ││
│  │  │  updateProvider(id, config): Promise<IdentityProviderConfig>       │ ││
│  │  │  deleteProvider(id): Promise<void>                                 │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  SsoUserMapper                                                          ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  linkExternalUser(oidcToken, providerId): Promise<SsoUserLink>     │ ││
│  │  │  findByExternalId(providerId, externalId): Promise<SsoUserLink?>   │ ││
│  │  │  provisionUser(oidcToken, tenantId): Promise<User>                 │ ││
│  │  │  mapRoles(claims, roleMappingConfig): Set<UserRole>                │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DATABASE (PostgreSQL)                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  identity_providers                                                     ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  id          UUID PRIMARY KEY                                      │ ││
│  │  │  tenant_id   UUID REFERENCES tenants(id)                           │ ││
│  │  │  type        VARCHAR(20) CHECK (type IN ('oidc', 'saml'))          │ ││
│  │  │  display_name VARCHAR(255) NOT NULL                                │ ││
│  │  │  discovery_endpoint TEXT                                           │ ││
│  │  │  client_id   TEXT NOT NULL                                         │ ││
│  │  │  client_secret_encrypted BYTEA                                     │ ││
│  │  │  icon_url    TEXT                                                  │ ││
│  │  │  enabled     BOOLEAN DEFAULT true                                  │ ││
│  │  │  role_mapping JSONB                                                │ ││
│  │  │  created_at  TIMESTAMPTZ DEFAULT now()                             │ ││
│  │  │  updated_at  TIMESTAMPTZ DEFAULT now()                             │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  sso_user_links                                                         ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │  id           UUID PRIMARY KEY                                     │ ││
│  │  │  user_id      UUID REFERENCES users(id) ON DELETE CASCADE          │ ││
│  │  │  provider_id  UUID REFERENCES identity_providers(id)               │ ││
│  │  │  external_id  TEXT NOT NULL                                        │ ││
│  │  │  email        TEXT NOT NULL                                        │ ││
│  │  │  linked_at    TIMESTAMPTZ DEFAULT now()                            │ ││
│  │  │  last_login_at TIMESTAMPTZ                                         │ ││
│  │  │  UNIQUE(provider_id, external_id)                                  │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. Authentication Flows

### 3.1 OIDC Login Flow (Google/Microsoft/Okta)

```
┌─────────┐       ┌─────────────┐       ┌───────────────┐       ┌───────────┐
│  User   │       │ TutorPutor  │       │  API Gateway  │       │   IdP     │
│ Browser │       │   Web App   │       │               │       │ (Google)  │
└────┬────┘       └──────┬──────┘       └───────┬───────┘       └─────┬─────┘
     │                   │                      │                     │
     │ 1. Click "Sign in │                      │                     │
     │    with Google"   │                      │                     │
     │──────────────────>│                      │                     │
     │                   │                      │                     │
     │                   │ 2. Redirect to       │                     │
     │                   │    /auth/login/google│                     │
     │                   │─────────────────────>│                     │
     │                   │                      │                     │
     │                   │                      │ 3. Build authz URL  │
     │                   │                      │    with PKCE        │
     │                   │                      │────────────────────>│
     │                   │                      │                     │
     │<──────────────────┼──────────────────────┼─────────────────────│
     │                   │    4. HTTP 302 to IdP authorization        │
     │                   │                      │                     │
     │ 5. User authenticates at IdP             │                     │
     │──────────────────────────────────────────────────────────────>│
     │                   │                      │                     │
     │<─────────────────────────────────────────────────────────────│
     │  6. IdP redirects with authorization code                     │
     │     /auth/callback/google?code=xxx&state=yyy                  │
     │                   │                      │                     │
     │──────────────────────────────────────────>                     │
     │                   │                      │                     │
     │                   │                      │ 7. Exchange code    │
     │                   │                      │    for tokens       │
     │                   │                      │────────────────────>│
     │                   │                      │                     │
     │                   │                      │<────────────────────│
     │                   │                      │ 8. id_token,        │
     │                   │                      │    access_token     │
     │                   │                      │                     │
     │                   │                      │ 9. OidcTokenVerifier│
     │                   │                      │    .verifyToken()   │
     │                   │                      │    (libs/java/auth) │
     │                   │                      │                     │
     │                   │                      │ 10. SsoUserMapper   │
     │                   │                      │     .linkExternalUser│
     │                   │                      │     or provision    │
     │                   │                      │                     │
     │                   │                      │ 11. Generate        │
     │                   │                      │     TutorPutor JWT  │
     │                   │                      │                     │
     │<─────────────────────────────────────────│                     │
     │  12. Set session cookie + redirect to dashboard               │
     │                   │                      │                     │
```

### 3.2 SAML 2.0 Login Flow (Enterprise IdP)

```
┌─────────┐       ┌─────────────┐       ┌───────────────┐       ┌───────────┐
│  User   │       │ TutorPutor  │       │  API Gateway  │       │ Enterprise│
│ Browser │       │   Web App   │       │               │       │ SAML IdP  │
└────┬────┘       └──────┬──────┘       └───────┬───────┘       └─────┬─────┘
     │                   │                      │                     │
     │ 1. Click "Sign in │                      │                     │
     │    with SSO"      │                      │                     │
     │──────────────────>│                      │                     │
     │                   │                      │                     │
     │                   │ 2. POST /auth/saml/  │                     │
     │                   │    init/:providerId  │                     │
     │                   │─────────────────────>│                     │
     │                   │                      │                     │
     │<──────────────────┼──────────────────────│                     │
     │  3. Return SAML AuthnRequest form (auto-submit)               │
     │                   │                      │                     │
     │ 4. POST AuthnRequest to IdP SSO endpoint                      │
     │──────────────────────────────────────────────────────────────>│
     │                   │                      │                     │
     │ 5. User authenticates at IdP             │                     │
     │──────────────────────────────────────────────────────────────>│
     │                   │                      │                     │
     │<─────────────────────────────────────────────────────────────│
     │  6. IdP sends SAML Response (POST /auth/saml/acs)             │
     │                   │                      │                     │
     │──────────────────────────────────────────>                     │
     │                   │                      │                     │
     │                   │                      │ 7. Validate SAML    │
     │                   │                      │    assertion        │
     │                   │                      │    signature        │
     │                   │                      │                     │
     │                   │                      │ 8. Extract claims   │
     │                   │                      │    (NameID, attrs)  │
     │                   │                      │                     │
     │                   │                      │ 9. SsoUserMapper    │
     │                   │                      │    .linkExternalUser│
     │                   │                      │                     │
     │<─────────────────────────────────────────│                     │
     │  10. Set session cookie + redirect to dashboard               │
     │                   │                      │                     │
```

### 3.3 Just-in-Time User Provisioning

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     JUST-IN-TIME USER PROVISIONING                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  On SSO callback, after token verification:                                  │
│                                                                              │
│  1. Extract claims from IdP token:                                          │
│     ┌──────────────────────────────────────────────────────────────────┐    │
│     │  {                                                               │    │
│     │    "sub": "103...@google",                                       │    │
│     │    "email": "teacher@school.edu",                                │    │
│     │    "name": "Jane Smith",                                         │    │
│     │    "hd": "school.edu",  // Google Workspace domain               │    │
│     │    "groups": ["teachers", "math-dept"]  // Custom claim          │    │
│     │  }                                                               │    │
│     └──────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  2. Lookup existing link:                                                    │
│     sso_user_links.findOne({ provider_id, external_id: sub })               │
│                                                                              │
│  3. If NO link exists:                                                       │
│     a. Check email domain against tenant allow-list                          │
│     b. Apply role mapping rules:                                            │
│        ┌────────────────────────────────────────────────────────────────┐   │
│        │  Role Mapping Config (identity_providers.role_mapping)         │   │
│        │  {                                                             │   │
│        │    "defaultRole": "student",                                   │   │
│        │    "rules": [                                                  │   │
│        │      { "claim": "groups", "contains": "teachers", "role": "teacher" },
│        │      { "claim": "groups", "contains": "admins", "role": "admin" }   │
│        │    ]                                                           │   │
│        │  }                                                             │   │
│        └────────────────────────────────────────────────────────────────┘   │
│     c. Create TutorPutor user:                                              │
│        users.create({ email, name, role, tenant_id })                       │
│     d. Create SSO link:                                                      │
│        sso_user_links.create({ user_id, provider_id, external_id, email }) │
│                                                                              │
│  4. If link EXISTS:                                                          │
│     a. Update last_login_at                                                  │
│     b. Optionally sync profile changes (name, email)                        │
│                                                                              │
│  5. Generate TutorPutor session JWT                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 4. Configuration

### 4.1 Environment Variables

```bash
# OIDC Provider Secrets (per-tenant, stored encrypted in DB)
# These are fallback/default values for non-tenant-specific auth

# Google
GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxx

# Microsoft Entra ID
MICROSOFT_CLIENT_ID=xxx-xxx-xxx-xxx-xxx
MICROSOFT_CLIENT_SECRET=xxx
MICROSOFT_TENANT_ID=common  # or specific tenant

# Okta
OKTA_DOMAIN=dev-xxx.okta.com
OKTA_CLIENT_ID=xxx
OKTA_CLIENT_SECRET=xxx
```

### 4.2 Provider Configuration Examples

```typescript
// Google Workspace OIDC
const googleConfig: IdentityProviderConfig = {
  id: "google-workspace",
  type: "oidc",
  displayName: "Sign in with Google",
  discoveryEndpoint:
    "https://accounts.google.com/.well-known/openid-configuration",
  clientId: process.env.GOOGLE_CLIENT_ID!,
  enabled: true,
  iconUrl: "/icons/google.svg",
};

// Microsoft Entra ID OIDC
const microsoftConfig: IdentityProviderConfig = {
  id: "microsoft-entra",
  type: "oidc",
  displayName: "Sign in with Microsoft",
  discoveryEndpoint: `https://login.microsoftonline.com/${TENANT_ID}/v2.0/.well-known/openid-configuration`,
  clientId: process.env.MICROSOFT_CLIENT_ID!,
  enabled: true,
  iconUrl: "/icons/microsoft.svg",
};

// Okta OIDC
const oktaConfig: IdentityProviderConfig = {
  id: "okta",
  type: "oidc",
  displayName: "Sign in with Okta",
  discoveryEndpoint: `https://${OKTA_DOMAIN}/.well-known/openid-configuration`,
  clientId: process.env.OKTA_CLIENT_ID!,
  enabled: true,
  iconUrl: "/icons/okta.svg",
};
```

### 4.3 Java Configuration (libs/java/auth)

```java
// Reusing existing OidcConfiguration from libs/java/auth
import com.ghatana.core.auth.oidc.OidcConfiguration;
import com.ghatana.core.auth.oidc.OidcTokenVerifier;

OidcConfiguration googleConfig = OidcConfiguration.builder()
    .discoveryEndpoint(URI.create("https://accounts.google.com/.well-known/openid-configuration"))
    .clientId(env.get("GOOGLE_CLIENT_ID"))
    .clientSecret(env.get("GOOGLE_CLIENT_SECRET"))
    .expectedAudience("tutorputor-api")
    .expectedIssuer("https://accounts.google.com")
    .requestedScopes(Set.of("openid", "profile", "email"))
    .tokenCacheTtl(Duration.ofMinutes(5))
    .build();

OidcTokenVerifier verifier = new OidcTokenVerifier(googleConfig);

// Verify token from callback
Promise<OidcToken> tokenPromise = verifier.verifyToken(accessToken);
```

## 5. Security Considerations

### 5.1 Token Security

- **PKCE Required**: All OIDC flows use Proof Key for Code Exchange
- **State Validation**: Cryptographically random state parameter prevents CSRF
- **Nonce Validation**: Prevents replay attacks
- **Token Encryption**: Access/refresh tokens encrypted at rest

### 5.2 Tenant Isolation

```sql
-- Every SSO operation is scoped to tenant
CREATE POLICY sso_tenant_isolation ON identity_providers
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY sso_user_link_tenant_isolation ON sso_user_links
    USING (
        provider_id IN (
            SELECT id FROM identity_providers
            WHERE tenant_id = current_setting('app.current_tenant_id')::uuid
        )
    );
```

### 5.3 Domain Verification

For enterprise tenants, email domain must be verified:

```typescript
// Only allow SSO for verified domains
const allowedDomains = tenant.verifiedDomains; // ["school.edu", "district.k12.us"]
const userDomain = email.split("@")[1];

if (!allowedDomains.includes(userDomain)) {
  throw new UnauthorizedError(
    "Email domain not authorized for this organization"
  );
}
```

## 6. API Endpoints

### 6.1 Public Endpoints

```typescript
// GET /auth/providers?tenantSlug=school-xyz
// Returns enabled IdPs for a tenant (for login page)
{
  providers: [
    {
      id: "google-workspace",
      displayName: "Sign in with Google",
      iconUrl: "/icons/google.svg",
    },
    {
      id: "microsoft-entra",
      displayName: "Sign in with Microsoft",
      iconUrl: "/icons/microsoft.svg",
    },
  ];
}

// GET /auth/login/:providerId?redirect_uri=/dashboard
// Initiates OIDC/SAML login flow

// GET /auth/callback/:providerId
// Handles IdP callback (internal, not called by frontend)

// POST /auth/logout
// Terminates session and optionally performs IdP logout
```

### 6.2 Admin Endpoints (Tenant Admin)

```typescript
// GET /admin/sso/providers
// List all IdPs for current tenant

// POST /admin/sso/providers
// Create new IdP configuration

// PUT /admin/sso/providers/:id
// Update IdP configuration

// DELETE /admin/sso/providers/:id
// Delete IdP (soft delete, preserves user links)

// GET /admin/sso/users
// List SSO-linked users

// DELETE /admin/sso/users/:linkId
// Unlink SSO identity from user
```

## 7. Error Handling

| Error Code                      | Description                    | User Action        |
| ------------------------------- | ------------------------------ | ------------------ |
| `SSO_PROVIDER_NOT_FOUND`        | IdP configuration not found    | Contact admin      |
| `SSO_PROVIDER_DISABLED`         | IdP is disabled                | Contact admin      |
| `SSO_DOMAIN_NOT_ALLOWED`        | Email domain not in allow-list | Use allowed domain |
| `SSO_TOKEN_VERIFICATION_FAILED` | IdP token invalid              | Retry login        |
| `SSO_USER_PROVISIONING_FAILED`  | User creation failed           | Contact support    |
| `SSO_LINK_ALREADY_EXISTS`       | External ID already linked     | Contact support    |

## 8. Monitoring & Observability

### 8.1 Metrics (via libs/java/observability)

```java
// SSO login attempts
Meter.counter("sso.login.attempts", Tags.of("provider", providerId));

// SSO login success/failure
Meter.counter("sso.login.success", Tags.of("provider", providerId));
Meter.counter("sso.login.failure", Tags.of("provider", providerId, "reason", reason));

// User provisioning
Meter.counter("sso.user.provisioned", Tags.of("tenant", tenantId));
Meter.counter("sso.user.linked", Tags.of("tenant", tenantId));

// Token verification latency
Meter.timer("sso.token.verification.latency", Tags.of("provider", providerId));
```

### 8.2 Audit Events

All SSO operations emit audit events:

```typescript
// Audit events logged via libs/java/observability
{
  action: "sso_login",
  actorId: userId,
  tenantId: tenantId,
  metadata: {
    providerId: "google-workspace",
    externalId: "103...@google",
    email: "user@school.edu",
    ipAddress: "1.2.3.4",
    userAgent: "Mozilla/5.0..."
  }
}
```

## 9. Testing

### 9.1 Unit Tests

```java
// Test OidcTokenVerifier with mock JWKS
class OidcTokenVerifierTest extends EventloopTestBase {
    @Test
    void shouldVerifyValidToken() {
        OidcTokenVerifier verifier = new OidcTokenVerifier(testConfig);
        String jwt = createTestJwt();

        OidcToken result = runPromise(() -> verifier.verifyToken(jwt));

        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }
}
```

### 9.2 Integration Tests

```typescript
// Test full SSO flow with mock IdP
describe("SSO Flow", () => {
  it("should provision new user on first login", async () => {
    const mockIdpServer = await createMockOidcProvider();

    // Simulate callback with auth code
    const response = await app.inject({
      method: "GET",
      url: `/auth/callback/test-idp?code=${authCode}&state=${state}`,
    });

    expect(response.statusCode).toBe(302);
    expect(response.headers.location).toContain("/dashboard");

    // Verify user was created
    const user = await db.users.findByEmail("new-user@test.com");
    expect(user).toBeDefined();
    expect(user.role).toBe("student");
  });
});
```

## 10. Migration & Rollout

### Phase 1: Internal Testing (Day 36-37)

- Configure test IdP instances
- Validate token verification
- Test user provisioning

### Phase 2: Beta Tenants (Day 38)

- Enable for select enterprise customers
- Monitor error rates and latency
- Gather feedback on role mapping

### Phase 3: General Availability (Day 39-40)

- Enable self-service IdP configuration
- Add SAML support
- Complete admin documentation

---

## Appendix A: Related Files

### Contracts

- `/products/tutorputor/contracts/v1/types.ts` - SSO type definitions

### Core Library (REUSE)

- `/libs/java/auth/src/main/java/com/ghatana/core/auth/oidc/OidcConfiguration.java`
- `/libs/java/auth/src/main/java/com/ghatana/core/auth/oidc/OidcTokenVerifier.java`

### Implementation (To Be Created)

- `/products/tutorputor/services/tutorputor-sso/` - SSO service (Day 37)
- `/products/tutorputor/apps/api-gateway/src/routes/auth.ts` - Auth routes (Day 37)
- `/products/tutorputor/apps/tutorputor-admin/` - Admin console (Day 38)
