package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.auth.impl.TokenCredentials;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for CompositeAuthenticationProvider delegate ordering and fallback
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CompositeAuthenticationProvider — delegate chain and priority ordering")
class CompositeAuthenticationProviderTest extends EventloopTestBase {

    private static final User TEST_USER = new User("user-1", "alice", Set.of("USER"));
    private static final Credentials TOKEN_CRED = new TokenCredentials("test-token");

    // ── Factory helpers ──────────────────────────────────────────────────────

    private static AuthenticationProvider successProvider(String type, int priority) {
        return new AuthenticationProvider() {
            @Override
            public Promise<Optional<User>> authenticate(Credentials credentials) {
                return Promise.of(Optional.of(TEST_USER));
            }

            @Override
            public boolean supports(String t) {
                return type.equals(t);
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    private static AuthenticationProvider failProvider(String type, int priority) {
        return new AuthenticationProvider() {
            @Override
            public Promise<Optional<User>> authenticate(Credentials credentials) {
                return Promise.of(Optional.empty());
            }

            @Override
            public boolean supports(String t) {
                return type.equals(t);
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    private static AuthenticationProvider unsupportedProvider(int priority) {
        return new AuthenticationProvider() {
            @Override
            public Promise<Optional<User>> authenticate(Credentials credentials) {
                return Promise.of(Optional.of(TEST_USER));
            }

            @Override
            public boolean supports(String t) {
                return false; // never supports anything
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor with no providers throws IllegalArgumentException")
    void constructorThrowsForEmptyProviders() {
        assertThatThrownBy(() -> new CompositeAuthenticationProvider(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constructor with null array throws NullPointerException")
    void constructorThrowsForNullArray() {
        assertThatThrownBy(() -> new CompositeAuthenticationProvider((AuthenticationProvider[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── authenticate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("authenticate returns user when delegate provider succeeds")
    void authenticateReturnsUserFromSuccessfulDelegate() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                successProvider("token", 1));

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED));

        assertThat(result).contains(TEST_USER);
    }

    @Test
    @DisplayName("authenticate returns empty when no provider supports the credentials type")
    void authenticateReturnsEmptyWhenNoProviderSupports() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                unsupportedProvider(1));

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("authenticate returns empty when supporting provider returns empty")
    void authenticateReturnsEmptyWhenProviderFails() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                failProvider("token", 1));

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("authenticate throws NullPointerException for null credentials")
    void authenticateThrowsForNullCredentials() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                successProvider("token", 1));

        assertThatThrownBy(() -> runPromise(() -> composite.authenticate(null)))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Priority ordering ─────────────────────────────────────────────────────

    @Test
    @DisplayName("providers are ordered by priority (highest first)")
    void providersOrderedByPriorityDescending() {
        // Low-priority unsupported first; high-priority supportive second
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                unsupportedProvider(1),
                successProvider("token", 10));

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED));

        assertThat(result).contains(TEST_USER);
    }

    @Test
    @DisplayName("getPriority returns the highest priority among all delegates")
    void getPriorityReturnsHighest() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                successProvider("token", 3),
                successProvider("basic", 7));

        assertThat(composite.getPriority()).isEqualTo(7);
    }

    // ── supports ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("supports returns true when any delegate supports the type")
    void supportsTrueWhenAnyDelegateSupports() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                successProvider("token", 1),
                successProvider("basic", 2));

        assertThat(composite.supports("basic")).isTrue();
    }

    @Test
    @DisplayName("supports returns false when no delegate supports the type")
    void supportsFalseWhenNoDelegateSupports() {
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(
                successProvider("token", 1));

        assertThat(composite.supports("oauth2")).isFalse();
    }

    // ── getProviders ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProviders returns unmodifiable list of all delegates")
    void getProvidersReturnsUnmodifiableList() {
        AuthenticationProvider p1 = successProvider("token", 1);
        AuthenticationProvider p2 = successProvider("basic", 2);
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(p1, p2);

        List<AuthenticationProvider> providers = composite.getProviders();

        assertThat(providers).hasSize(2);
        assertThatThrownBy(() -> providers.add(p1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder creates a valid composite with added providers")
    void builderCreatesValidComposite() {
        CompositeAuthenticationProvider composite = CompositeAuthenticationProvider.builder()
                .withProvider(successProvider("token", 5))
                .build();

        assertThat(composite.supports("token")).isTrue();
        assertThat(composite.getProviders()).hasSize(1);
    }
}
