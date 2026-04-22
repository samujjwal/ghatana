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
@DisplayName("CompositeAuthenticationProvider — delegate chain and priority ordering [GH-90000]")
class CompositeAuthenticationProviderTest extends EventloopTestBase {

    private static final User TEST_USER = new User("user-1", "alice", Set.of("USER [GH-90000]"));
    private static final Credentials TOKEN_CRED = new TokenCredentials("test-token [GH-90000]");

    // ── Factory helpers ──────────────────────────────────────────────────────

    private static AuthenticationProvider successProvider(String type, int priority) { // GH-90000
        return new AuthenticationProvider() { // GH-90000
            @Override
            public Promise<Optional<User>> authenticate(Credentials credentials) { // GH-90000
                return Promise.of(Optional.of(TEST_USER)); // GH-90000
            }

            @Override
            public boolean supports(String t) { // GH-90000
                return type.equals(t); // GH-90000
            }

            @Override
            public int getPriority() { // GH-90000
                return priority;
            }
        };
    }

    private static AuthenticationProvider failProvider(String type, int priority) { // GH-90000
        return new AuthenticationProvider() { // GH-90000
            @Override
            public Promise<Optional<User>> authenticate(Credentials credentials) { // GH-90000
                return Promise.of(Optional.empty()); // GH-90000
            }

            @Override
            public boolean supports(String t) { // GH-90000
                return type.equals(t); // GH-90000
            }

            @Override
            public int getPriority() { // GH-90000
                return priority;
            }
        };
    }

    private static AuthenticationProvider unsupportedProvider(int priority) { // GH-90000
        return new AuthenticationProvider() { // GH-90000
            @Override
            public Promise<Optional<User>> authenticate(Credentials credentials) { // GH-90000
                return Promise.of(Optional.of(TEST_USER)); // GH-90000
            }

            @Override
            public boolean supports(String t) { // GH-90000
                return false; // never supports anything
            }

            @Override
            public int getPriority() { // GH-90000
                return priority;
            }
        };
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor with no providers throws IllegalArgumentException [GH-90000]")
    void constructorThrowsForEmptyProviders() { // GH-90000
        assertThatThrownBy(() -> new CompositeAuthenticationProvider(List.of())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor with null array throws NullPointerException [GH-90000]")
    void constructorThrowsForNullArray() { // GH-90000
        assertThatThrownBy(() -> new CompositeAuthenticationProvider((AuthenticationProvider[]) null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── authenticate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("authenticate returns user when delegate provider succeeds [GH-90000]")
    void authenticateReturnsUserFromSuccessfulDelegate() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                successProvider("token", 1)); // GH-90000

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED)); // GH-90000

        assertThat(result).contains(TEST_USER); // GH-90000
    }

    @Test
    @DisplayName("authenticate returns empty when no provider supports the credentials type [GH-90000]")
    void authenticateReturnsEmptyWhenNoProviderSupports() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                unsupportedProvider(1)); // GH-90000

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED)); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("authenticate returns empty when supporting provider returns empty [GH-90000]")
    void authenticateReturnsEmptyWhenProviderFails() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                failProvider("token", 1)); // GH-90000

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED)); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("authenticate throws NullPointerException for null credentials [GH-90000]")
    void authenticateThrowsForNullCredentials() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                successProvider("token", 1)); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> composite.authenticate(null))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── Priority ordering ─────────────────────────────────────────────────────

    @Test
    @DisplayName("providers are ordered by priority (highest first) [GH-90000]")
    void providersOrderedByPriorityDescending() { // GH-90000
        // Low-priority unsupported first; high-priority supportive second
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                unsupportedProvider(1), // GH-90000
                successProvider("token", 10)); // GH-90000

        Optional<User> result = runPromise(() -> composite.authenticate(TOKEN_CRED)); // GH-90000

        assertThat(result).contains(TEST_USER); // GH-90000
    }

    @Test
    @DisplayName("getPriority returns the highest priority among all delegates [GH-90000]")
    void getPriorityReturnsHighest() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                successProvider("token", 3), // GH-90000
                successProvider("basic", 7)); // GH-90000

        assertThat(composite.getPriority()).isEqualTo(7); // GH-90000
    }

    // ── supports ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("supports returns true when any delegate supports the type [GH-90000]")
    void supportsTrueWhenAnyDelegateSupports() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                successProvider("token", 1), // GH-90000
                successProvider("basic", 2)); // GH-90000

        assertThat(composite.supports("basic [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("supports returns false when no delegate supports the type [GH-90000]")
    void supportsFalseWhenNoDelegateSupports() { // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider( // GH-90000
                successProvider("token", 1)); // GH-90000

        assertThat(composite.supports("oauth2 [GH-90000]")).isFalse();
    }

    // ── getProviders ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProviders returns unmodifiable list of all delegates [GH-90000]")
    void getProvidersReturnsUnmodifiableList() { // GH-90000
        AuthenticationProvider p1 = successProvider("token", 1); // GH-90000
        AuthenticationProvider p2 = successProvider("basic", 2); // GH-90000
        CompositeAuthenticationProvider composite = new CompositeAuthenticationProvider(p1, p2); // GH-90000

        List<AuthenticationProvider> providers = composite.getProviders(); // GH-90000

        assertThat(providers).hasSize(2); // GH-90000
        assertThatThrownBy(() -> providers.add(p1)) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder creates a valid composite with added providers [GH-90000]")
    void builderCreatesValidComposite() { // GH-90000
        CompositeAuthenticationProvider composite = CompositeAuthenticationProvider.builder() // GH-90000
                .withProvider(successProvider("token", 5)) // GH-90000
                .build(); // GH-90000

        assertThat(composite.supports("token [GH-90000]")).isTrue();
        assertThat(composite.getProviders()).hasSize(1); // GH-90000
    }
}
