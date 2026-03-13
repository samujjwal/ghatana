package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.auth.impl.JwtAuthenticationProvider;
import com.ghatana.platform.security.auth.impl.TokenCredentials;
import com.ghatana.platform.security.auth.impl.UsernamePasswordCredentials;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtAuthenticationProvider}.
 *
 * Covers: SEC-01 fix (no downcast), CORR-02 fix (getRemainingValidity),
 * CORR-03 fix (roles accumulated correctly), delegate flow, unsupported type.
 */
class JwtAuthenticationProviderTest extends EventloopTestBase {

    private static final String VALID_TOKEN   = "valid.jwt.token";
    private static final String EXPIRED_TOKEN = "expired.jwt.token";
    private static final String BAD_TOKEN     = "bad.jwt.token";
    private static final String USER_ID       = "user-42";
    private static final List<String> ROLES   = List.of("ROLE_ADMIN", "ROLE_USER");

    private StubJwtTokenProvider stubJwt;
    private JwtAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        stubJwt = new StubJwtTokenProvider();
        provider = new JwtAuthenticationProvider(stubJwt);
    }

    // -------------------------------------------------------------------------
    // SEC-01: provider must work with any JwtTokenProvider impl — no downcast
    // -------------------------------------------------------------------------

    @Test
    void authenticate_usesPortInterface_notConcreteImpl() {
        // StubJwtTokenProvider is NOT the concrete jwt.JwtTokenProvider —
        // if SEC-01 downcast were still present this would throw ClassCastException
        TokenCredentials creds = new TokenCredentials(VALID_TOKEN);
        Optional<User> result = runPromise(() -> provider.authenticate(creds));
        assertThat(result).isPresent();
    }

    // -------------------------------------------------------------------------
    // CORR-03: all roles must be present on the returned User
    // -------------------------------------------------------------------------

    @Test
    void authenticate_tokenCredentials_rolesApplied() {
        TokenCredentials creds = new TokenCredentials(VALID_TOKEN);

        Optional<User> result = runPromise(() -> provider.authenticate(creds));

        assertThat(result).isPresent();
        User user = result.get();
        assertThat(user.getRoles()).containsExactlyInAnyOrderElementsOf(ROLES);
    }

    @Test
    void authenticate_tokenWithNoRoles_returnsUserWithEmptyRoles() {
        stubJwt.roles = List.of();
        TokenCredentials creds = new TokenCredentials(VALID_TOKEN);

        Optional<User> result = runPromise(() -> provider.authenticate(creds));

        assertThat(result).isPresent();
        assertThat(result.get().getRoles()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // CORR-02: getRemainingValidity logic
    // -------------------------------------------------------------------------

    @Test
    void getRemainingValidity_validToken_returnsPositiveMs() {
        long remaining = provider.getRemainingValidity(VALID_TOKEN);
        assertThat(remaining).isGreaterThan(0L);
    }

    @Test
    void getRemainingValidity_expiredToken_returnsMinusOne() {
        long remaining = provider.getRemainingValidity(EXPIRED_TOKEN);
        assertThat(remaining).isEqualTo(-1L);
    }

    @Test
    void getRemainingValidity_invalidToken_returnsMinusOne() {
        long remaining = provider.getRemainingValidity(BAD_TOKEN);
        assertThat(remaining).isEqualTo(-1L);
    }

    // -------------------------------------------------------------------------
    // Token validation paths
    // -------------------------------------------------------------------------

    @Test
    void authenticate_invalidToken_returnsEmpty() {
        Optional<User> result = runPromise(() -> provider.authenticate(new TokenCredentials(BAD_TOKEN)));
        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_noUserIdInToken_returnsEmpty() {
        stubJwt.userId = null;
        Optional<User> result = runPromise(() -> provider.authenticate(new TokenCredentials(VALID_TOKEN)));
        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_userId_setOnReturnedUser() {
        Optional<User> result = runPromise(() -> provider.authenticate(new TokenCredentials(VALID_TOKEN)));
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(USER_ID);
        assertThat(result.get().isAuthenticated()).isTrue();
        assertThat(result.get().getAuthToken()).isEqualTo(VALID_TOKEN);
    }

    // -------------------------------------------------------------------------
    // Unsupported credential type
    // -------------------------------------------------------------------------

    @Test
    void authenticate_unsupportedType_returnsEmpty() {
        Credentials unknownCreds = new Credentials("magic") {};
        Optional<User> result = runPromise(() -> provider.authenticate(unknownCreds));
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Delegate flow: non-token credentials delegated → token generated
    // -------------------------------------------------------------------------

    @Test
    void authenticate_withDelegate_nonTokenCreds_generatesToken() {
        User delegateUser = User.builder()
                .userId("delegate-user")
                .username("delegate-user")
                .authenticated(true)
                .build();
        AuthenticationProvider delegate = new AuthenticationProvider() {
            @Override
            public Promise<Optional<User>> authenticate(Credentials c) {
                return Promise.of(Optional.of(delegateUser));
            }
            @Override
            public boolean supports(String type) { return true; }
        };

        JwtAuthenticationProvider chained = new JwtAuthenticationProvider(stubJwt, delegate);

        UsernamePasswordCredentials creds = new UsernamePasswordCredentials("u", "p");
        Optional<User> result = runPromise(() -> chained.authenticate(creds));

        assertThat(result).isPresent();
        assertThat(result.get().getAuthToken()).isNotNull();
    }

    @Test
    void authenticate_withDelegate_delegateReturnsEmpty_propagatesEmpty() {
        AuthenticationProvider delegate = new AuthenticationProvider() {
            @Override
            public Promise<Optional<User>> authenticate(Credentials c) {
                return Promise.of(Optional.empty());
            }
            @Override
            public boolean supports(String type) { return true; }
        };
        JwtAuthenticationProvider chained = new JwtAuthenticationProvider(stubJwt, delegate);

        Optional<User> result = runPromise(() -> chained.authenticate(new UsernamePasswordCredentials("u", "p")));
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // generateToken / refreshToken
    // -------------------------------------------------------------------------

    @Test
    void generateToken_usesPortCreateToken() {
        User user = User.builder().userId(USER_ID).username(USER_ID).build();
        String token = provider.generateToken(user);
        assertThat(token).isEqualTo("generated:" + USER_ID);
    }

    @Test
    void refreshToken_validToken_returnsNewToken() {
        String refreshed = provider.refreshToken(VALID_TOKEN);
        assertThat(refreshed).isEqualTo("generated:" + USER_ID);
    }

    @Test
    void refreshToken_badToken_throwsRuntime() {
        assertThatThrownBy(() -> provider.refreshToken(BAD_TOKEN))
                .isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // supports()
    // -------------------------------------------------------------------------

    @Test
    void supports_tokenType_returnsTrue() {
        assertThat(provider.supports("token")).isTrue();
    }

    @Test
    void supports_otherType_returnsFalse() {
        assertThat(provider.supports("basic")).isFalse();
    }

    // =========================================================================
    // Test double — implements the PORT interface only (not the concrete class)
    // =========================================================================

    private static class StubJwtTokenProvider implements JwtTokenProvider {

        String userId = USER_ID;
        List<String> roles = ROLES;
        long expiryEpochSeconds = (System.currentTimeMillis() / 1000L) + 3600; // 1 hour ahead

        @Override
        public String createToken(String uid, List<String> r, Map<String, Object> extra) {
            return "generated:" + uid;
        }

        @Override
        public boolean validateToken(String token) {
            return VALID_TOKEN.equals(token);
        }

        @Override
        public Optional<String> getUserIdFromToken(String token) {
            if (!VALID_TOKEN.equals(token)) return Optional.empty();
            return Optional.ofNullable(userId);
        }

        @Override
        public List<String> getRolesFromToken(String token) {
            return VALID_TOKEN.equals(token) ? roles : List.of();
        }

        @Override
        public Optional<Map<String, Object>> extractClaims(String token) {
            if (!VALID_TOKEN.equals(token)) return Optional.empty();
            return Optional.of(Map.of(
                    "sub", USER_ID,
                    "exp", expiryEpochSeconds,
                    "roles", roles
            ));
        }
    }
}
