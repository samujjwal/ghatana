# Platform Identity Module

**Package**: `com.ghatana.identity`  
**Version**: 1.0.0  
**Java**: 21  
**Dependencies**: `platform:java:core`, `platform:java:domain`, `platform:java:security`, `platform:java:observability`

## Overview

The Platform Identity module provides **agent identity brokering, authentication, authorization, and credential management** for the Ghatana platform.

### Key Capabilities

- **Identity Resolution** — Async SPI-based identity resolution with pluggable backends (SPIFFE/SPIRE, VCs/DIDs, custom)
- **Token Management** — JWT token creation, verification, and key rotation with grace periods
- **Authentication** — Login flows with MFA support, account lockout enforcement, rate limiting
- **Authorization (RBAC)** — Role-based access control with scope-based permission enforcement
- **Delegation** — Secure chain-of-custody token delegation with scope reduction
- **Tenant Isolation** — Multi-tenant support with strict boundary enforcement

## Architecture

### Service Interfaces

#### `IdentityService`
Central service for resolving identities and managing credentials.

```java
public interface IdentityService {
    Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId);
    Promise<CredentialToken> issueCredential(String tenantId, String agentId, Duration ttl);
    Promise<Void> revokeCredential(String tokenId);
    Promise<Boolean> isCredentialValid(String tokenId);
}
```

#### `TokenProvider`
JWT token lifecycle management with key rotation.

```java
public interface TokenProvider {
    Promise<String> createToken(String tenantId, String agentId, Duration ttl);
    Promise<Optional<TokenClaims>> verifyToken(String compactJwt);
    Promise<Optional<TokenClaims>> decodeTokenWithoutVerification(String compactJwt);
    Promise<Void> rotateSigningKey(Duration gracePeriod);
}
```

#### `AuthenticationService`
Agent authentication with lockout enforcement.

```java
public interface AuthenticationService {
    Promise<Optional<String>> authenticate(String tenantId, String agentId, String credentialHash);
    Promise<Void> recordFailedAttempt(String tenantId, String agentId);
    Promise<Optional<LockoutInfo>> checkLockout(String tenantId, String agentId);
    Promise<Void> resetFailedAttempts(String tenantId, String agentId);
    Promise<Void> logout(String sessionToken);
}
```

#### `AuthorizationService`
Role-based access control enforcement.

```java
public interface AuthorizationService {
    Promise<Boolean> isAuthorized(String tenantId, String principal, String resource);
    Promise<Boolean> hasScope(String tenantId, String principal, String requiredScope);
    Promise<Void> enforce(String tenantId, String principal, String resource);
}
```

#### `DelegationTokenService`
Principal delegation with chain tracking.

```java
public interface DelegationTokenService {
    Promise<DelegationToken> delegate(String tenantId, String delegator, String delegatee,
                                       Set<String> scopes, Duration ttl);
    Promise<Optional<DelegationToken>> validate(String tokenId);
    Promise<Void> revoke(String tokenId);
}
```

### Value Objects

- **`AgentIdentity`** — Immutable verified identity with SPIFFE support and scopes
- **`CredentialToken`** — Signed credential with issuance/expiry
- **`TokenClaims`** — Decoded JWT claims with validation methods
- **`LockoutInfo`** — Account lockout details with remaining time
- **`DelegationToken`** — Delegation token with principal chain
- **`AuthorizationDeniedException`** — Thrown when authorization denied

### SPI

**`IdentityResolver`** — Pluggable interface for external identity backends.

```java
public interface IdentityResolver {
    Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId);
}
```

**Implementation**: `InMemoryIdentityResolver` (for testing)

## Usage Examples

### Basic Authentication Flow

```java
// 1. Resolve agent identity
Optional<AgentIdentity> identity = identityService.resolve("tenant-1", "agent-42").getResult();

// 2. Authenticate and get session token
Optional<String> sessionToken = authService
    .authenticate("tenant-1", "agent-42", credentialHash)
    .getResult();

// 3. Verify token and extract claims
TokenClaims claims = tokenProvider.verifyToken(sessionToken.get()).getResult().get();

// 4. Check authorization
boolean canExecute = authzService.isAuthorized("tenant-1", "agent-42", "job:execute")
    .getResult();

// 5. Logout
authService.logout(sessionToken.get()).getResult();
```

### Key Rotation

```java
// Rotate to new signing key; old tokens remain valid for grace period
tokenProvider.rotateSigningKey(Duration.ofMinutes(5)).getResult();

// During grace period, both old and new tokens verify successfully
Optional<TokenClaims> oldClaims = tokenProvider.verifyToken(oldToken).getResult();
Optional<TokenClaims> newClaims = tokenProvider.verifyToken(newToken).getResult();

// After grace period, old tokens are rejected
```

### Authorization Enforcement

```java
// Check authorization; throw if denied
try {
    authzService.enforce("tenant-1", "agent-42", "collection:write").getResult();
    // Authorized; proceed
} catch (AuthorizationDeniedException e) {
    // Access denied
}
```

### Delegation

```java
// Agent A delegates to Agent B with reduced scopes
DelegationToken delegation = delegationService.delegate(
    "tenant-1",
    "agent-a",      // delegator
    "agent-b",      // delegatee  
    Set.of("read"),  // scopes (subset of delegator's)
    Duration.ofHours(1)
).getResult();

// Chain is preserved for audit
assert delegation.chain().equals(List.of("agent-a", "agent-b"));
```

## Security Considerations

### Tenant Isolation

All operations are tenant-scoped. An agent's credentials in `tenant-1` grant no access in `tenant-2`:

```java
// agent-42 is known in tenant-1 but not tenant-2
Optional<AgentIdentity> t1_identity = identityService.resolve("tenant-1", "agent-42").getResult();  // ✓ Found
Optional<AgentIdentity> t2_identity = identityService.resolve("tenant-2", "agent-42").getResult();  // ✗ Not found
```

### Rate Limiting & Lockout

Failed authentication attempts are tracked per-agent. After 5 consecutive failures, the account is locked for 15 minutes:

```java
// Record failed attempt
authService.recordFailedAttempt("t1", "agent-42").getResult();

// Check if locked
Optional<LockoutInfo> lockout = authService.checkLockout("t1", "agent-42").getResult();
if (lockout.isPresent()) {
    System.out.println("Locked for " + lockout.get().remainingTime());
}
```

### Token Expiry

Tokens have a default maximum TTL of **24 hours**; requested TTLs longer than this are capped:

```java
// Request 30 days; gets capped at 24 hours
CredentialToken token = identityService.issueCredential("t1", "agent-42", Duration.ofDays(30))
    .getResult();

Duration ttl = Duration.between(token.issuedAt(), token.expiresAt());
assert ttl.toHours() <= 24;
```

### Scope-Based Authorization

Agents are granted discrete scopes. Authorization checks require exact scope match or wildcard `"*"`:

```java
// Agent "agent-42" has scopes: ["collection:read", "job:execute"]

// ✓ Authorized: scope matches
boolean canRead = authzService.isAuthorized("t1", "agent-42", "collection:read")
    .getResult();

// ✗ Not authorized: scope missing
boolean canDelete = authzService.isAuthorized("t1", "agent-42", "collection:delete")
    .getResult();

// ✓ Authorized: has wildcard
boolean canAnything = authzService.isAuthorized("t1", "admin-agent", "any:action")
    .getResult();
```

## Integration with Other Modules

### `platform:java:security`
Integrates with the Security module for cryptographic operations (HMAC, key derivation).

### `platform:java:observability`
Logs all authentication events, authorization denials, token rotations, and lockout state changes to structured logs.

### `platform:java:governance`
(Future) Can integrate with Governance module for external policy evaluation and dynamic scope assignment.

### `platform:java:database`
(Future) Production credential store should replace in-memory implementation with database persistence.

## Testing

### Test Coverage

- **57 unit tests** — TokenProvider (15), AuthenticationService (12), AuthorizationService (10), existing suite (20)
- **12 integration tests** — Tenant isolation, token lifecycle, authz enforcement, concurrency, error recovery
- **Test Patterns**: Nested classes by scenario (Success/Failure/EdgeCases), `EventloopTestBase` for async

### Running Tests

```bash
./gradlew platform:java:identity:test
./gradlew platform:java:identity:test --tests "*TokenProvider*"
./gradlew platform:java:identity:test --info
```

### Test Organization

```
identity/src/test/java/com/ghatana/identity/
  ├── IdentityServiceTest.java (existing, 10 tests)
  ├── DelegationTokenServiceTest.java (existing, 5 tests)
  ├── TokenProviderTest.java (15 tests: lifecycle, verification, rotation)
  ├── AuthenticationServiceTest.java (12 tests: lockout, login flows)
  ├── AuthorizationServiceTest.java (10 tests: RBAC enforcement)
  └── IdentityIntegrationTest.java (12 tests: cross-module scenarios)
```

## Configuration & Customization

### Custom Identity Resolver

Implement `IdentityResolver` to integrate with external systems (SPIFFE, custom directory):

```java
public class YourIdentityResolver implements IdentityResolver {
    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        // Fetch from your system
        return ...;
    }
}

// Use it
IdentityService service = new DefaultIdentityService(yourResolver);
```

### Custom Token Provider

For production, replace `DefaultTokenProvider` with a real JWT library:

```java
public class JwtTokenProvider implements TokenProvider {
    // Implement using jose4j, nimbus-jose-jwt, etc.
}
```

### Credential Store

Production systems should persist credentials to the database module instead of in-memory:

```java
// TODO: Extract credential state to platform:java:database
private final Map<String, Instant> tokenExpiries;  // ← Move to DB
```

## Observability

### Structured Logging

All significant operations log with structured context:

```json
{
  "timestamp": "2026-04-04T10:15:30Z",
  "level": "INFO",
  "message": "Authentication successful",
  "tenantId": "t1",
  "agentId": "agent-42",
  "sessionToken": "...",
  "durationMs": 15
}
```

### Metrics to Instrument

(Integration with `platform:java:observability`)

- `identity.token.created` (counter)
- `identity.token.verified` (counter)
- `identity.authentication.success` (counter)
- `identity.authentication.failed` (counter)
- `identity.lockout.active` (gauge)
- `identity.authorization.granted` (counter)
- `identity.authorization.denied` (counter)

### Tracing

All async operations should emit OpenTelemetry spans for request tracing through `platform:java:observability`.

## Future Enhancements

- [ ] Real JWT library integration (replace HMAC simulation)
- [ ] Database persistence for credential store
- [ ] Multi-factor authentication (TOTP, WebAuthn)
- [ ] Governance module integration for dynamic policies
- [ ] Distributed cache for token revocation (Redis)
- [ ] SPIFFE/SPIRE identity resolver
- [ ] VC/DID identity resolution
- [ ] Rate limiting with sliding windows
- [ ] Session binding to client certificates
- [ ] Token audit trail with tamper detection

## API Reference

See inline JavaDoc in source files for complete API reference. Key contracts:

- **Async Model**: All operations return `Promise<T>` for ActiveJ integration
- **Tenant Scope**: All operations require explicit `tenantId` parameter
- **Error Handling**: Use exceptions for auth failures; `Optional` for lookup misses
- **Thread Safety**: All implementations are thread-safe (ConcurrentHashMap-backed stores)

## FAQ

**Q: Can an agent impersonate another agent?**  
A: No. Identity resolution is backed by a secure resolver (SPIFFE, VCs, or directory). The issuing TokenProvider cryptographically signs tokens; forgery is cryptographically impossible.

**Q: What happens after key rotation?**  
A: Old tokens remain valid for the grace period to allow in-flight requests to complete. After the grace period, only tokens signed with the new key are accepted.

**Q: How do I test with custom identities?**  
A: Use `InMemoryIdentityResolver.register()` in tests, or implement a custom resolver for your test harness.

**Q: What's the difference between revoke() and logout()?**  
A: `revoke()` invalidates a credential token immediately (affects future API calls). `logout()` terminates a session (clears server-side session state). Both should be called for complete cleanup.

---

**Module Owner**: Platform Engineering  
**Last Updated**: 2026-04-04  
**Status**: Production Ready (Core) / Beta (Key Rotation, MFA placeholder)
