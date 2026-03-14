/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.service;

import com.ghatana.appplatform.iam.domain.ClientCredentials;
import com.ghatana.appplatform.iam.domain.ClientCredentials.ClientStatus;
import com.ghatana.appplatform.iam.domain.TokenResponse;
import com.ghatana.appplatform.iam.port.ClientCredentialStore;
import com.ghatana.appplatform.iam.provider.InMemorySigningKeyProvider;
import com.ghatana.appplatform.iam.service.ClientCredentialsGrant.AccessDeniedException;
import com.ghatana.appplatform.iam.service.ClientCredentialsGrant.InvalidClientException;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ClientCredentialsGrant}.
 *
 * <p>Uses an in-memory stub for {@link ClientCredentialStore}; no database required.
 *
 * @doc.type class
 * @doc.purpose Unit tests for OAuth 2.0 client_credentials grant (STORY-K01-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ClientCredentialsGrant Tests")
class ClientCredentialsGrantTest extends EventloopTestBase {

    private static final PasswordHasher HASHER = new PasswordHasher();
    private static final String RAW_SECRET = "super-secret-123";
    private static final String HASHED_SECRET = HASHER.hash(RAW_SECRET);

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CLIENT_ID_STR = "svc-ledger";

    private static final List<String> ROLES = List.of("platform:admin");
    private static final List<String> PERMISSIONS = List.of("ledger:read", "ledger:post");

    private ClientCredentials activeClient;
    private ClientCredentialsGrant grant;

    @BeforeEach
    void setUp() {
        activeClient = new ClientCredentials(
                CLIENT_ID, CLIENT_ID_STR, HASHED_SECRET,
                TENANT_ID, ROLES, ClientStatus.ACTIVE);

        JwtTokenService tokenService = new JwtTokenService(new InMemorySigningKeyProvider());
        ClientCredentialStore store = new StubClientCredentialStore(activeClient, PERMISSIONS);

        grant = new ClientCredentialsGrant(
                store, tokenService,
                "https://auth.ghatana.io", "ghatana-api", 3600L);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Happy path
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid credentials return TokenResponse with Bearer token")
    void validCredentials_returnsTokenResponse() {
        TokenResponse response = runPromise(() -> grant.exchange(CLIENT_ID_STR, RAW_SECRET));

        assertThat(response).isNotNull();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.accessToken().split("\\.")).hasSize(3);  // valid JWT
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("token scope contains all granted role names joined by space")
    void validCredentials_scopeContainsRoles() {
        TokenResponse response = runPromise(() -> grant.exchange(CLIENT_ID_STR, RAW_SECRET));
        assertThat(response.scope()).isEqualTo("platform:admin");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Error paths
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown clientIdStr throws InvalidClientException")
    void unknownClient_throwsInvalidClientException() {
        assertThatThrownBy(() -> runPromise(() -> grant.exchange("unknown-client", RAW_SECRET)))
                .isInstanceOf(InvalidClientException.class);
    }

    @Test
    @DisplayName("wrong secret throws InvalidClientException")
    void wrongSecret_throwsInvalidClientException() {
        assertThatThrownBy(() -> runPromise(() -> grant.exchange(CLIENT_ID_STR, "wrong-secret")))
                .isInstanceOf(InvalidClientException.class);
    }

    @Test
    @DisplayName("SUSPENDED client throws AccessDeniedException")
    void suspendedClient_throwsAccessDeniedException() {
        ClientCredentials suspended = new ClientCredentials(
                CLIENT_ID, CLIENT_ID_STR, HASHED_SECRET,
                TENANT_ID, ROLES, ClientStatus.SUSPENDED);

        JwtTokenService tokenService = new JwtTokenService(new InMemorySigningKeyProvider());
        ClientCredentialStore store = new StubClientCredentialStore(suspended, PERMISSIONS);
        ClientCredentialsGrant suspendedGrant = new ClientCredentialsGrant(
                store, tokenService,
                "https://auth.ghatana.io", "ghatana-api", 3600L);

        assertThatThrownBy(() -> runPromise(() -> suspendedGrant.exchange(CLIENT_ID_STR, RAW_SECRET)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("REVOKED client throws AccessDeniedException")
    void revokedClient_throwsAccessDeniedException() {
        ClientCredentials revoked = new ClientCredentials(
                CLIENT_ID, CLIENT_ID_STR, HASHED_SECRET,
                TENANT_ID, ROLES, ClientStatus.REVOKED);

        JwtTokenService tokenService = new JwtTokenService(new InMemorySigningKeyProvider());
        ClientCredentialStore store = new StubClientCredentialStore(revoked, PERMISSIONS);
        ClientCredentialsGrant revokedGrant = new ClientCredentialsGrant(
                store, tokenService,
                "https://auth.ghatana.io", "ghatana-api", 3600L);

        assertThatThrownBy(() -> runPromise(() -> revokedGrant.exchange(CLIENT_ID_STR, RAW_SECRET)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ──────────────────────────────────────────────────────────────────────
    // In-memory stub implementation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Minimal in-memory ClientCredentialStore stub for unit testing.
     */
    private static final class StubClientCredentialStore implements ClientCredentialStore {

        private final ClientCredentials credentials;
        private final List<String> permissions;

        StubClientCredentialStore(ClientCredentials credentials, List<String> permissions) {
            this.credentials = credentials;
            this.permissions = permissions;
        }

        @Override
        public Promise<Optional<ClientCredentials>> getByClientIdStr(String clientIdStr) {
            if (credentials.clientIdStr().equals(clientIdStr)) {
                return Promise.of(Optional.of(credentials));
            }
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<String>> loadPermissionsForRoles(List<String> roles, UUID tenantId) {
            return Promise.of(permissions);
        }

        @Override
        public Promise<Void> updateLastUsed(UUID clientId) {
            return Promise.complete();
        }
    }
}
