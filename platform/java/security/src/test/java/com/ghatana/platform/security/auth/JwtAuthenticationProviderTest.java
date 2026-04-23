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
 * Covers: SEC-01 fix (no downcast), CORR-02 fix (getRemainingValidity), // GH-90000
 * CORR-03 fix (roles accumulated correctly), delegate flow, unsupported type. // GH-90000
 */
class JwtAuthenticationProviderTest extends EventloopTestBase {

    private static final String VALID_TOKEN   = "valid.jwt.token";
    private static final String EXPIRED_TOKEN = "expired.jwt.token";
    private static final String BAD_TOKEN     = "bad.jwt.token";
    private static final String USER_ID       = "user-42";
    private static final List<String> ROLES   = List.of("ROLE_ADMIN", "ROLE_USER"); // GH-90000

    private StubJwtTokenProvider stubJwt;
    private JwtAuthenticationProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        stubJwt = new StubJwtTokenProvider(); // GH-90000
        provider = new JwtAuthenticationProvider(stubJwt); // GH-90000
    }

    // -------------------------------------------------------------------------
    // SEC-01: provider must work with any JwtTokenProvider impl — no downcast
    // -------------------------------------------------------------------------

    @Test
    void authenticate_usesPortInterface_notConcreteImpl() { // GH-90000
        // StubJwtTokenProvider is NOT the concrete jwt.JwtTokenProvider —
        // if SEC-01 downcast were still present this would throw ClassCastException
        TokenCredentials creds = new TokenCredentials(VALID_TOKEN); // GH-90000
        Optional<User> result = runPromise(() -> provider.authenticate(creds)); // GH-90000
        assertThat(result).isPresent(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // CORR-03: all roles must be present on the returned User
    // -------------------------------------------------------------------------

    @Test
    void authenticate_tokenCredentials_rolesApplied() { // GH-90000
        TokenCredentials creds = new TokenCredentials(VALID_TOKEN); // GH-90000

        Optional<User> result = runPromise(() -> provider.authenticate(creds)); // GH-90000

        assertThat(result).isPresent(); // GH-90000
        User user = result.get(); // GH-90000
        assertThat(user.getRoles()).containsExactlyInAnyOrderElementsOf(ROLES); // GH-90000
    }

    @Test
    void authenticate_tokenWithNoRoles_returnsUserWithEmptyRoles() { // GH-90000
        stubJwt.roles = List.of(); // GH-90000
        TokenCredentials creds = new TokenCredentials(VALID_TOKEN); // GH-90000

        Optional<User> result = runPromise(() -> provider.authenticate(creds)); // GH-90000

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getRoles()).isEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // CORR-02: getRemainingValidity logic
    // -------------------------------------------------------------------------

    @Test
    void getRemainingValidity_validToken_returnsPositiveMs() { // GH-90000
        long remaining = provider.getRemainingValidity(VALID_TOKEN); // GH-90000
        assertThat(remaining).isGreaterThan(0L); // GH-90000
    }

    @Test
    void getRemainingValidity_expiredToken_returnsMinusOne() { // GH-90000
        long remaining = provider.getRemainingValidity(EXPIRED_TOKEN); // GH-90000
        assertThat(remaining).isEqualTo(-1L); // GH-90000
    }

    @Test
    void getRemainingValidity_invalidToken_returnsMinusOne() { // GH-90000
        long remaining = provider.getRemainingValidity(BAD_TOKEN); // GH-90000
        assertThat(remaining).isEqualTo(-1L); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Token validation paths
    // -------------------------------------------------------------------------

    @Test
    void authenticate_invalidToken_returnsEmpty() { // GH-90000
        Optional<User> result = runPromise(() -> provider.authenticate(new TokenCredentials(BAD_TOKEN))); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void authenticate_noUserIdInToken_returnsEmpty() { // GH-90000
        stubJwt.userId = null;
        Optional<User> result = runPromise(() -> provider.authenticate(new TokenCredentials(VALID_TOKEN))); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void authenticate_userId_setOnReturnedUser() { // GH-90000
        Optional<User> result = runPromise(() -> provider.authenticate(new TokenCredentials(VALID_TOKEN))); // GH-90000
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getUserId()).isEqualTo(USER_ID); // GH-90000
        assertThat(result.get().isAuthenticated()).isTrue(); // GH-90000
        assertThat(result.get().getAuthToken()).isEqualTo(VALID_TOKEN); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Unsupported credential type
    // -------------------------------------------------------------------------

    @Test
    void authenticate_unsupportedType_returnsEmpty() { // GH-90000
        Credentials unknownCreds = new Credentials("magic") {};
        Optional<User> result = runPromise(() -> provider.authenticate(unknownCreds)); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Delegate flow: non-token credentials delegated → token generated
    // -------------------------------------------------------------------------

    @Test
    void authenticate_withDelegate_nonTokenCreds_generatesToken() { // GH-90000
        User delegateUser = User.builder() // GH-90000
                .userId("delegate-user")
                .username("delegate-user")
                .authenticated(true) // GH-90000
                .build(); // GH-90000
        AuthenticationProvider delegate = new AuthenticationProvider() { // GH-90000
            @Override
            public Promise<Optional<User>> authenticate(Credentials c) { // GH-90000
                return Promise.of(Optional.of(delegateUser)); // GH-90000
            }
            @Override
            public boolean supports(String type) { return true; } // GH-90000
        };

        JwtAuthenticationProvider chained = new JwtAuthenticationProvider(stubJwt, delegate); // GH-90000

        UsernamePasswordCredentials creds = new UsernamePasswordCredentials("u", "p"); // GH-90000
        Optional<User> result = runPromise(() -> chained.authenticate(creds)); // GH-90000

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getAuthToken()).isNotNull(); // GH-90000
    }

    @Test
    void authenticate_withDelegate_delegateReturnsEmpty_propagatesEmpty() { // GH-90000
        AuthenticationProvider delegate = new AuthenticationProvider() { // GH-90000
            @Override
            public Promise<Optional<User>> authenticate(Credentials c) { // GH-90000
                return Promise.of(Optional.empty()); // GH-90000
            }
            @Override
            public boolean supports(String type) { return true; } // GH-90000
        };
        JwtAuthenticationProvider chained = new JwtAuthenticationProvider(stubJwt, delegate); // GH-90000

        Optional<User> result = runPromise(() -> chained.authenticate(new UsernamePasswordCredentials("u", "p"))); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // generateToken / refreshToken
    // -------------------------------------------------------------------------

    @Test
    void generateToken_usesPortCreateToken() { // GH-90000
        User user = User.builder().userId(USER_ID).username(USER_ID).build(); // GH-90000
        String token = provider.generateToken(user); // GH-90000
        assertThat(token).isEqualTo("generated:" + USER_ID); // GH-90000
    }

    @Test
    void refreshToken_validToken_returnsNewToken() { // GH-90000
        String refreshed = provider.refreshToken(VALID_TOKEN); // GH-90000
        assertThat(refreshed).isEqualTo("generated:" + USER_ID); // GH-90000
    }

    @Test
    void refreshToken_badToken_throwsRuntime() { // GH-90000
        assertThatThrownBy(() -> provider.refreshToken(BAD_TOKEN)) // GH-90000
                .isInstanceOf(RuntimeException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // supports() // GH-90000
    // -------------------------------------------------------------------------

    @Test
    void supports_tokenType_returnsTrue() { // GH-90000
        assertThat(provider.supports("token")).isTrue();
    }

    @Test
    void supports_otherType_returnsFalse() { // GH-90000
        assertThat(provider.supports("basic")).isFalse();
    }

    // =========================================================================
    // Test double — implements the PORT interface only (not the concrete class) // GH-90000
    // =========================================================================

    private static class StubJwtTokenProvider implements JwtTokenProvider {

        String userId = USER_ID;
        List<String> roles = ROLES;
        long expiryEpochSeconds = (System.currentTimeMillis() / 1000L) + 3600; // 1 hour ahead // GH-90000

        @Override
        public String createToken(String uid, List<String> r, Map<String, Object> extra) { // GH-90000
            return "generated:" + uid;
        }

        @Override
        public boolean validateToken(String token) { // GH-90000
            return VALID_TOKEN.equals(token); // GH-90000
        }

        @Override
        public Optional<String> getUserIdFromToken(String token) { // GH-90000
            if (!VALID_TOKEN.equals(token)) return Optional.empty(); // GH-90000
            return Optional.ofNullable(userId); // GH-90000
        }

        @Override
        public List<String> getRolesFromToken(String token) { // GH-90000
            return VALID_TOKEN.equals(token) ? roles : List.of(); // GH-90000
        }

        @Override
        public Optional<Map<String, Object>> extractClaims(String token) { // GH-90000
            if (!VALID_TOKEN.equals(token)) return Optional.empty(); // GH-90000
            return Optional.of(Map.of( // GH-90000
                    "sub", USER_ID,
                    "exp", expiryEpochSeconds,
                    "roles", roles
            ));
        }
    }
}
