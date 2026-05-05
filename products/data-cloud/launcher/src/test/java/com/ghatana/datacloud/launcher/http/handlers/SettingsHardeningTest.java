/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.settings.InMemorySettingsStore;
import com.ghatana.datacloud.launcher.settings.SettingsStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DC-P2-009 — Settings storage hardening tests.
 *
 * <p>Verifies:
 * <ul>
 *   <li>In-memory store is blocked for write operations in strict/production profile.</li>
 *   <li>API key list never exposes secrets.</li>
 *   <li>Invalid general settings (unknown theme) are rejected with HTTP 400.</li>
 *   <li>Invalid security settings (out-of-range sessionTimeout) are rejected with HTTP 400.</li>
 *   <li>Malformed JSON body returns HTTP 400.</li>
 *   <li>Settings write operations are accepted against a persistent store in strict mode.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-009 settings storage hardening tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-P2-009 — Settings storage hardening")
@ExtendWith(MockitoExtension.class)
@Tag("production")
class SettingsHardeningTest extends EventloopTestBase {

    private static final String TENANT = "tenant-settings-test";

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @BeforeEach
    void setUpCommon() {
        lenient().when(http.requireTenantIdOrFail(any())).thenReturn(TENANT);
        lenient().when(http.jsonResponse(any())).thenReturn(mock(HttpResponse.class));
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(mock(HttpResponse.class));
    }

    // ─── STRICT MODE GUARD ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Strict-mode guard: block in-memory writes in production profile")
    class StrictModeGuard {

        @Test
        @DisplayName("updateGeneralSettings returns HTTP 503 in strict mode with in-memory store")
        void updateGeneralSettings_strictMode_returns503() {
            SettingsHandler handler = new SettingsHandler(http, new InMemorySettingsStore(), true);
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(503), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateGeneralSettings(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("updateSecuritySettings returns HTTP 503 in strict mode with in-memory store")
        void updateSecuritySettings_strictMode_returns503() {
            SettingsHandler handler = new SettingsHandler(http, new InMemorySettingsStore(), true);
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(503), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateSecuritySettings(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("Reads are allowed in strict mode regardless of store type")
        void readOperations_allowedInStrictMode() {
            SettingsHandler handler = new SettingsHandler(http, new InMemorySettingsStore(), true);
            HttpResponse okResp = mock(HttpResponse.class);
            when(http.jsonResponse(any())).thenReturn(okResp);

            HttpResponse response = runPromise(() -> handler.handleGetGeneralSettings(request));

            assertThat(response).isSameAs(okResp);
        }

        @Test
        @DisplayName("Writes succeed in strict mode when store is not in-memory")
        void writesSucceed_withPersistentStore_inStrictMode() {
            // Mock a store whose getStorageMode() returns "persistent"
            SettingsStore persistentStore = mock(SettingsStore.class);
            when(persistentStore.getStorageMode()).thenReturn("persistent");
            when(persistentStore.getGeneralSettings(anyString())).thenReturn(Map.of("theme", "light"));
            when(persistentStore.updateGeneralSettings(anyString(), any())).thenReturn(Map.of("theme", "dark"));
            when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

            SettingsHandler handler = new SettingsHandler(http, persistentStore, true);
            HttpResponse okResp = mock(HttpResponse.class);
            when(http.jsonResponse(any())).thenReturn(okResp);

            String body = "{\"theme\":\"dark\"}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));

            HttpResponse response = runPromise(() -> handler.handleUpdateGeneralSettings(request));

            assertThat(response).isSameAs(okResp);
        }
    }

    // ─── SECRET MASKING ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("API key secret masking")
    class SecretMasking {

        @Test
        @DisplayName("listApiKeys never exposes the secret field in the response")
        @SuppressWarnings("unchecked")
        void listApiKeys_secretNotInResponse() {
            // Create a key with a known secret
            InMemorySettingsStore store = new InMemorySettingsStore();
            SettingsHandler handler = new SettingsHandler(http, store);
            when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

            // Create an API key first
            String createBody = "{\"name\":\"test-key\",\"scopes\":[\"read\"]}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(createBody.getBytes(StandardCharsets.UTF_8))));
            runPromise(() -> handler.handleCreateApiKey(request));

            // Now list keys and verify secret is absent
            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleListApiKeys(request));

            Map<String, Object> envelope = bodyCaptor.getValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) envelope.get("keys");
            assertThat(keys).hasSize(1);
            // The secret must not be revealed in the list endpoint
            assertThat(keys.get(0)).doesNotContainKey("secret");
            assertThat(keys.get(0).get("secretRevealed")).isEqualTo(false);
        }

        @Test
        @DisplayName("listApiKeys returns empty list when no keys exist")
        @SuppressWarnings("unchecked")
        void listApiKeys_emptyWhenNoKeys() {
            SettingsHandler handler = new SettingsHandler(http, new InMemorySettingsStore());

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleListApiKeys(request));

            Map<String, Object> envelope = bodyCaptor.getValue();
            assertThat(((List<?>) envelope.get("keys"))).isEmpty();
            assertThat(envelope.get("count")).isEqualTo(0);
        }
    }

    // ─── GENERAL SETTINGS VALIDATION ────────────────────────────────────────

    @Nested
    @DisplayName("General settings validation")
    class GeneralSettingsValidation {

        private SettingsHandler handler;

        @BeforeEach
        void setUp() {
            handler = new SettingsHandler(http, new InMemorySettingsStore());
            when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Test
        @DisplayName("Valid theme 'dark' is accepted")
        void validThemeDark_accepted() {
            String body = "{\"theme\":\"dark\"}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
            HttpResponse okResp = mock(HttpResponse.class);
            when(http.jsonResponse(any())).thenReturn(okResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateGeneralSettings(request));

            assertThat(response).isSameAs(okResp);
        }

        @Test
        @DisplayName("Invalid theme returns HTTP 400")
        void invalidTheme_returns400() {
            String body = "{\"theme\":\"rainbow\"}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateGeneralSettings(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("Malformed JSON body returns HTTP 400")
        void malformedJson_returns400() {
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading("not-json".getBytes(StandardCharsets.UTF_8))));
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateGeneralSettings(request));

            assertThat(response).isSameAs(errorResp);
        }
    }

    // ─── SECURITY SETTINGS VALIDATION ───────────────────────────────────────

    @Nested
    @DisplayName("Security settings validation")
    class SecuritySettingsValidation {

        private SettingsHandler handler;

        @BeforeEach
        void setUp() {
            handler = new SettingsHandler(http, new InMemorySettingsStore());
            when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Test
        @DisplayName("Valid sessionTimeout is accepted")
        void validSessionTimeout_accepted() {
            String body = "{\"sessionTimeout\":60}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
            HttpResponse okResp = mock(HttpResponse.class);
            when(http.jsonResponse(any())).thenReturn(okResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateSecuritySettings(request));

            assertThat(response).isSameAs(okResp);
        }

        @Test
        @DisplayName("sessionTimeout of 0 is rejected (must be >= 1 minute)")
        void sessionTimeoutZero_returns400() {
            String body = "{\"sessionTimeout\":0}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateSecuritySettings(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("sessionTimeout exceeding max (>10080 minutes = 7 days) is rejected")
        void sessionTimeoutExceedsMax_returns400() {
            String body = "{\"sessionTimeout\":99999}";
            when(request.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleUpdateSecuritySettings(request));

            assertThat(response).isSameAs(errorResp);
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> bodyCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
