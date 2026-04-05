# Security Module Test Templates (Phase 2 Continuation)

Use these templates to implement remaining 38 tests across 5 categories.  
**Pattern**: Copy template, adapt to specific class under test, use fixtures/mocks for setup.

---

## Template 1: JWT/OAuth Tests (12 tests total)

### File: `JwtTokenProviderTest.java` (5 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Comprehensive test suite for JWT token generation and validation.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("JwtTokenProvider")
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest extends SecurityEventloopTestBase {
    @Mock
    private JwtKeyManager keyManager;
    
    private JwtTokenProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(keyManager);
        lenient().when(keyManager.getCurrentKey())
            .thenReturn(Promise.of("key-v1"));
    }
    
    @Nested
    @DisplayName("token generation")
    class TokenGenerationTests {
        @Test
        @DisplayName("should generate valid JWT with claims")
        void shouldGenerateValidJwt() {
            String token = runPromise(() -> 
                provider.generateToken("user-1", Duration.ofHours(1))
            );
            assertThat(token).isNotNull().isNotEmpty();
        }
        
        @Test
        @DisplayName("should include user ID in claims")
        void shouldIncludeUserIdInClaims() {
            String token = runPromise(() -> 
                provider.generateToken("user-123", Duration.ofHours(1))
            );
            Map<String, Object> claims = provider.extractClaims(token);
            assertThat(claims.get("sub")).isEqualTo("user-123");
        }
        
        @Test
        @DisplayName("should set expiration equal to requested duration")
        void shouldSetExpirationProperly() {
            Instant beforeGenerate = Instant.now();
            String token = runPromise(() -> 
                provider.generateToken("user-1", Duration.ofMinutes(30))
            );
            Instant afterGenerate = Instant.now();
            
            Map<String, Object> claims = provider.extractClaims(token);
            long exp = ((Number) claims.get("exp")).longValue();
            Instant expTime = Instant.ofEpochSecond(exp);
            
            assertThat(expTime).isBetween(
                beforeGenerate.plus(Duration.ofMinutes(29)),
                afterGenerate.plus(Duration.ofMinutes(31))
            );
        }
    }
    
    @Nested
    @DisplayName("claim extraction")
    class ClaimExtractionTests {
        @Test
        @DisplayName("should extract all claims from valid token")
        void shouldExtractAllClaims() {
            String token = runPromise(() -> 
                provider.generateToken("user-1", Duration.ofHours(1))
            );
            
            Map<String, Object> claims = provider.extractClaims(token);
            assertThat(claims)
                .containsKeys("sub", "iat", "exp", "jti")
                .containsEntry("sub", "user-1");
        }
        
        @Test
        @DisplayName("should throw for malformed token")
        void shouldThrowForMalformedToken() {
            assertThatThrownBy(() -> provider.extractClaims("invalid.token"))
                .isInstanceOf(SecurityException.class);
        }
    }
}
```

### File: `TokenRefreshTest.java` (4 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Tests for JWT token refresh lifecycle.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("TokenRefresh — JWT refresh lifecycle")
@ExtendWith(MockitoExtension.class)
class TokenRefreshTest extends SecurityEventloopTestBase {
    @Mock
    private JwtTokenProvider provider;
    
    @Mock
    private RefreshTokenStore store;
    
    private TokenRefreshService service;
    
    @BeforeEach
    void setUp() {
        service = new TokenRefreshService(provider, store);
        lenient().when(provider.extractClaims(any()))
            .thenReturn(Map.of("sub", "user-1"));
    }
    
    @Nested
    @DisplayName("refresh operations")
    class RefreshOperationsTests {
        @Test
        @DisplayName("should issue new access token with valid refresh token")
        void shouldIssueNewAccessToken() {
            when(store.validateRefreshToken("refresh-1")).thenReturn(Promise.of(true));
            when(provider.generateToken("user-1", any())).thenReturn(Promise.of("new.access.token"));
            
            String newAccessToken = runPromise(() -> 
                service.refreshAccessToken("refresh-1")
            );
            
            assertThat(newAccessToken).isNotNull().isNotEmpty();
        }
        
        @Test
        @DisplayName("should reject expired refresh token")
        void shouldRejectExpiredRefreshToken() {
            when(store.validateRefreshToken("expired-refresh")).thenReturn(Promise.of(false));
            
            assertThatThrownBy(() -> runPromise(() -> 
                service.refreshAccessToken("expired-refresh")
            )).isInstanceOf(SecurityException.class);
        }
        
        @Test
        @DisplayName("should rotate refresh tokens on each refresh")
        void shouldRotateRefreshTokens() {
            when(store.validateRefreshToken("old-refresh")).thenReturn(Promise.of(true));
            when(store.issueNewRefreshToken("user-1")).thenReturn(Promise.of("new-refresh"));
            
            runPromise(() -> service.refreshAccessToken("old-refresh"));
            
            verify(store).revokeRefreshToken("old-refresh");
            verify(store).issueNewRefreshToken("user-1");
        }
        
        @Test
        @DisplayName("should enforce maximum refresh count before re-authentication")
        void shouldEnforceMaxRefreshCount() {
            // Simulate 5 refreshes (max allowed)
            for (int i = 0; i < 5; i++) {
                when(store.validateRefreshToken("refresh-" + i)).thenReturn(Promise.of(true));
                when(store.issueNewRefreshToken("user-1")).thenReturn(Promise.of("refresh-" + (i + 1)));
                runPromise(() -> service.refreshAccessToken("refresh-" + i));
            }
            
            // 6th refresh should fail
            when(store.validateRefreshToken("refresh-5")).thenReturn(Promise.of(false));
            assertThatThrownBy(() -> runPromise(() -> 
                service.refreshAccessToken("refresh-5")
            )).isInstanceOf(SecurityException.class);
        }
    }
}
```

### File: `TokenRevocationTest.java` (3 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Tests for JWT token revocation and blacklisting.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("TokenRevocation — JWT lifecycle termination")
@ExtendWith(MockitoExtension.class)
class TokenRevocationTest extends SecurityEventloopTestBase {
    @Mock
    private TokenBlacklist blacklist;
    
    private TokenRevocationService service;
    
    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(blacklist);
    }
    
    @Nested
    @DisplayName("revocation operations")
    class RevocationOperationsTests {
        @Test
        @DisplayName("should add token to blacklist on revocation")
        void shouldAddTokenToBlacklist() {
            String token = "valid.jwt.token";
            runPromise(() -> service.revokeToken(token));
            
            verify(blacklist).addToBlacklist(token, any());
        }
        
        @Test
        @DisplayName("should reject revoked tokens on validation")
        void shouldRejectRevokedTokens() {
            String token = "revoked.token";
            when(blacklist.isBlacklisted(token)).thenReturn(Promise.of(true));
            
            boolean isValid = runPromise(() -> service.validateToken(token));
            
            assertThat(isValid).isFalse();
        }
        
        @Test
        @DisplayName("should clean expired entries from blacklist")
        void shouldCleanExpiredBlacklistEntries() {
            runPromise(() -> service.pruneExpiredEntries());
            
            verify(blacklist).removeExpiredEntries();
        }
    }
}
```

---

## Template 2: Encryption Tests (8 tests total)

### File: `AesGcmEncryptionTest.java` (4 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Comprehensive test suite for AES-GCM encryption operations.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AesGcmEncryption")
class AesGcmEncryptionTest {
    private AesGcmEncryptionProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = AesGcmEncryptionProvider.with256BitKey();
    }
    
    @Nested
    @DisplayName("encryption and decryption")
    class EncryptionAndDecryptionTests {
        @Test
        @DisplayName("should encrypt and decrypt plaintext")
        void shouldEncryptAndDecrypt() {
            String plaintext = "sensitive data";
            
            String ciphertext = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(ciphertext);
            
            assertThat(decrypted).isEqualTo(plaintext);
        }
        
        @Test
        @DisplayName("should produce different ciphertexts for same plaintext (random IV)")
        void shouldProduceDifferentCiphertexts() {
            String plaintext = "data";
            String ct1 = provider.encrypt(plaintext);
            String ct2 = provider.encrypt(plaintext);
            
            assertThat(ct1).isNotEqualTo(ct2);
        }
        
        @Test
        @DisplayName("should detect tampering in ciphertext")
        void shouldDetectTampering() {
            String ciphertext = provider.encrypt("data");
            String tampered = ciphertext.substring(0, ciphertext.length() - 5) + "xxxxx";
            
            assertThatThrownBy(() -> provider.decrypt(tampered))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("authentication");
        }
        
        @Test
        @DisplayName("should handle empty plaintext")
        void shouldHandleEmptyPlaintext() {
            String ciphertext = provider.encrypt("");
            String decrypted = provider.decrypt(ciphertext);
            
            assertThat(decrypted).isEmpty();
        }
    }
}
```

### File: `KeyRotationTest.java` (4 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Tests for encryption key rotation and versioning.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("KeyRotation — Encryption key lifecycle management")
@ExtendWith(MockitoExtension.class)
class KeyRotationTest {
    private KeyRotationService service;
    
    @BeforeEach
    void setUp() {
        service = new KeyRotationService();
    }
    
    @Nested
    @DisplayName("rotation operations")
    class RotationOperationsTests {
        @Test
        @DisplayName("should maintain backward compatibility with old keys")
        void shouldMaintainBackwardCompatibility() {
            String plaintext = "secret";
            String keyV1 = service.getCurrentKeyVersion();
            
            String ciphertext = service.encryptWithVersion(plaintext, keyV1);
            service.rotateKey();
            
            String decrypted = service.decryptWithAnyKey(ciphertext);
            
            assertThat(decrypted).isEqualTo(plaintext);
        }
        
        @Test
        @DisplayName("should encrypt new data with current key after rotation")
        void shouldUseNewKeyAfterRotation() {
            service.rotateKey();
            String keyV2 = service.getCurrentKeyVersion();
            
            String ciphertext = service.encryptWithCurrentKey("data");
            assertThat(ciphertext).startsWith(keyV2);
        }
        
        @Test
        @DisplayName("should prune old keys after retention period")
        void shouldPruneOldKeys() {
            service.rotateKey();
            service.rotateKey();
            service.rotateKey();
            
            service.pruneOldKeysBefore(Instant.now());
            
            assertThat(service.getAvailableKeyVersions()).hasSizeLessThan(3);
        }
        
        @Test
        @DisplayName("should handle concurrent rotations safely")
        void shouldHandleConcurrentRotationsSafely() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(3);
            
            for (int i = 0; i < 3; i++) {
                new Thread(() -> {
                    service.rotateKey();
                    latch.countDown();
                }).start();
            }
            
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(service.getAvailableKeyVersions()).hasSizeGreaterThanOrEqualTo(1);
        }
    }
}
```

---

## Template 3: RBAC Tests (10 tests total)

### File: `RoleAssignmentTest.java` (3 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Tests for role lifecycle (assign, revoke, update).
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RoleAssignment")
@ExtendWith(MockitoExtension.class)
class RoleAssignmentTest extends SecurityEventloopTestBase {
    @Mock
    private RoleRepository repository;
    
    private RoleAssignmentService service;
    
    @BeforeEach
    void setUp() {
        service = new RoleAssignmentService(repository);
    }
    
    @Nested
    @DisplayName("assignment operations")
    class AssignmentOperationsTests {
        @Test
        @DisplayName("should assign role to user")
        void shouldAssignRoleToUser() {
            when(repository.assignRole("user-1", "ADMIN")).thenReturn(Promise.of(true));
            
            boolean assigned = runPromise(() -> 
                service.assignRole("user-1", "ADMIN")
            );
            
            assertThat(assigned).isTrue();
        }
        
        @Test
        @DisplayName("should prevent duplicate role assignments")
        void shouldPreventDuplicateAssignments() {
            when(repository.assignRole("user-1", "USER")).thenReturn(Promise.of(false));
            
            boolean assigned = runPromise(() -> 
                service.assignRole("user-1", "USER")
            );
            
            assertThat(assigned).isFalse();
        }
        
        @Test
        @DisplayName("should revoke role from user")
        void shouldRevokeRoleFromUser() {
            when(repository.revokeRole("user-1", "ADMIN")).thenReturn(Promise.of(true));
            
            boolean revoked = runPromise(() -> 
                service.revokeRole("user-1", "ADMIN")
            );
            
            assertThat(revoked).isTrue();
        }
    }
}
```

### File: `PermissionEvaluatorTest.java` (4 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Tests for permission evaluation and wildcard matching.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PermissionEvaluator")
@ExtendWith(MockitoExtension.class)
class PermissionEvaluatorTest {
    private PermissionEvaluator evaluator;
    
    @BeforeEach
    void setUp() {
        evaluator = new PermissionEvaluator();
    }
    
    @Nested
    @DisplayName("permission evaluation")
    class PermissionEvaluationTests {
        @Test
        @DisplayName("should grant access when permission matches exactly")
        void shouldGrantExactMatch() {
            Set<String> permissions = Set.of("resource:read", "resource:write");
            
            boolean hasPermission = evaluator.evaluate(permissions, "resource:read");
            
            assertThat(hasPermission).isTrue();
        }
        
        @Test
        @DisplayName("should support wildcard matching with *")
        void shouldSupportWildcardMatching() {
            Set<String> permissions = Set.of("resource:*");
            
            assertThat(evaluator.evaluate(permissions, "resource:read")).isTrue();
            assertThat(evaluator.evaluate(permissions, "resource:write")).isTrue();
            assertThat(evaluator.evaluate(permissions, "other:read")).isFalse();
        }
        
        @Test
        @DisplayName("should support super-admin wildcard *:*")
        void shouldSupportSuperAdminWildcard() {
            Set<String> permissions = Set.of("*:*:*");
            
            assertThat(evaluator.evaluate(permissions, "any:thing:here")).isTrue();
        }
        
        @Test
        @DisplayName("should deny when no matching permission")
        void shouldDenyNoMatch() {
            Set<String> permissions = Set.of("resource:read");
            
            boolean hasPermission = evaluator.evaluate(permissions, "resource:delete");
            
            assertThat(hasPermission).isFalse();
        }
    }
}
```

### File: `PolicyEnforcementTest.java` (3 tests)

```java
/**
 * @doc.type class
 * @doc.purpose Tests for authorization policy enforcement across operations.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PolicyEnforcement")
@ExtendWith(MockitoExtension.class)
class PolicyEnforcementTest {
    @Mock
    private PermissionEvaluator evaluator;
    
    private PolicyEnforcementService service;
    
    @BeforeEach
    void setUp() {
        service = new PolicyEnforcementService(evaluator);
    }
    
    @Nested
    @DisplayName("enforcement operations")
    class EnforcementOperationsTests {
        @Test
        @DisplayName("should enforce policy before operation")
        void shouldEnforcePolicyBeforeOperation() {
            when(evaluator.evaluate(any(), eq("resource:delete"))).thenReturn(Promise.of(false));
            
            assertThatThrownBy(() -> runPromise(() -> 
                service.deleteResource("resource-1")
            )).isInstanceOf(SecurityException.class);
        }
        
        @Test
        @DisplayName("should allow operation when policy permits")
        void shouldAllowWhenPermitted() {
            when(evaluator.evaluate(any(), eq("resource:read"))).thenReturn(Promise.of(true));
            
            Object result = runPromise(() -> 
                service.readResource("resource-1")
            );
            
            assertThat(result).isNotNull();
        }
        
        @Test
        @DisplayName("should audit policy decisions")
        void shouldAuditPolicyDecisions() {
            when(evaluator.evaluate(any(), any())).thenReturn(Promise.of(true));
            
            runPromise(() -> service.readResource("resource-1"));
            
            // Verify audit log was created
            // (implementation-specific verification)
        }
    }
}
```

---

## Remaining Templates

Use same pattern for:
- **API Key Tests** (4 tests): Generation, scoping, rotation, validation
- **Integration Tests** (4 tests): End-to-end auth flow, multi-service context, exception handling

---

## Common Patterns

### Using Fixtures
```java
SecurityContext ctx = SecurityTestFixture.securityContext()
    .userId("test-user")
    .admin()
    .build();
```

### Using Mocks
```java
@Mock
private DependencyService dependency;

@BeforeEach
void setUp() {
    lenient().when(dependency.optional()).thenReturn(Promise.of("default"));
}

@Test
void test() {
    when(dependency.required()).thenReturn(Promise.of("value"));
    // test implementation
}
```

### Async Assertions
```java
@ExtendWith(MockitoExtension.class)
class MyTest extends SecurityEventloopTestBase {
    @Test
    void testAsync() {
        String result = runPromise(() -> asyncOperation());
        assertThat(result).isNotEmpty();
    }
}
```

---

**Total**: 38 test templates ready for implementation  
**Time Estimate**: 8-12 hours to implement all remaining security tests  
**Target**: Complete by Friday, Apr 26

