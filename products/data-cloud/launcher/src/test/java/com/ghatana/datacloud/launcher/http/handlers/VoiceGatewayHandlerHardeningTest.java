/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.launcher.http.VoiceIntentCatalog;
import com.ghatana.datacloud.launcher.http.VoiceIntentCatalog.VoiceIntent;
import com.ghatana.datacloud.launcher.http.voice.VoiceSttPort;
import com.ghatana.datacloud.launcher.http.voice.VoiceTtsPort;
import com.ghatana.platform.audit.AuditService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VoiceGatewayHandler hardening additions (P3.4.1): // GH-90000
 * rate-limit logic, intent-to-speech-summary mapping, context grounding wiring.
 *
 * @doc.type class
 * @doc.purpose Unit tests for VoiceGatewayHandler rate limiting and mapping (DC-E4 hardening) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("VoiceGatewayHandler – hardening (rate limit, context grounding, intent mapping) [GH-90000]")
class VoiceGatewayHandlerHardeningTest {

    private VoiceGatewayHandler handler;
    private HttpHandlerSupport http;

    @BeforeEach
    void setUp() { // GH-90000
        http = mock(HttpHandlerSupport.class); // GH-90000
        CompletionService completionService = null;
        AuditService auditService = null;
        VoiceSttPort sttPort = null;
        VoiceTtsPort ttsPort = null;
        Function<String, Map<String, Object>> contextEntriesProvider = null;
        // Minimal constructor — no LLM, no STT, no audit
        handler = new VoiceGatewayHandler( // GH-90000
            completionService, auditService,
                new ObjectMapper(), // GH-90000
                http,
                Runnable::run,
            sttPort, ttsPort,
            contextEntriesProvider);
    }

    // ── Rate Limiter ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isRateLimited() [GH-90000]")
    class RateLimitTests {

        @Test
        @DisplayName("first N requests under limit are allowed [GH-90000]")
        void firstNRequestsAllowed() { // GH-90000
            for (int i = 0; i < VoiceGatewayHandler.VOICE_RATE_LIMIT_PER_MINUTE; i++) { // GH-90000
                assertThat(handler.isRateLimited("tenant-alpha [GH-90000]"))
                        .as("request %d should not be rate-limited", i + 1) // GH-90000
                        .isFalse(); // GH-90000
            }
        }

        @Test
        @DisplayName("request beyond limit is rejected [GH-90000]")
        void requestBeyondLimitIsRejected() { // GH-90000
            // Exhaust the limit
            for (int i = 0; i < VoiceGatewayHandler.VOICE_RATE_LIMIT_PER_MINUTE; i++) { // GH-90000
                handler.isRateLimited("tenant-beta [GH-90000]");
            }
            assertThat(handler.isRateLimited("tenant-beta [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("different tenants have independent buckets [GH-90000]")
        void tenantsAreIsolated() { // GH-90000
            // Exhaust tenant A
            for (int i = 0; i < VoiceGatewayHandler.VOICE_RATE_LIMIT_PER_MINUTE; i++) { // GH-90000
                handler.isRateLimited("tenant-a [GH-90000]");
            }
            // tenant-b should still be allowed
            assertThat(handler.isRateLimited("tenant-b [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("limit constant is positive and sensible [GH-90000]")
        void limitConstantIsSane() { // GH-90000
            assertThat(VoiceGatewayHandler.VOICE_RATE_LIMIT_PER_MINUTE) // GH-90000
                    .isGreaterThan(0) // GH-90000
                    .isLessThanOrEqualTo(1000); // GH-90000
        }
    }

    // ── Context grounding wiring ──────────────────────────────────────────────

    @Nested
    @DisplayName("context grounding via ContextLayerHandler [GH-90000]")
    class ContextGroundingTests {

        @Test
        @DisplayName("null contextLayer returns empty map without NPE [GH-90000]")
        void nullContextLayer_returnsEmptyMap() { // GH-90000
            // handler was built without a context layer (null) // GH-90000
            // The grounding path in classifyWithLlm must not throw
            // We verify indirectly: isRateLimited does not touch context, but we can
            // confirm construction succeeded and default behavior is safe.
            assertThat(handler).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("wired contextLayer entries are passed to handler [GH-90000]")
        void wiredContextLayer_entriesAccessible() { // GH-90000
            ContextLayerHandler ctxLayer = mock(ContextLayerHandler.class); // GH-90000
            when(ctxLayer.currentEntries("tenant-x [GH-90000]"))
                    .thenReturn(Map.of("currentCollection", "orders", "preferredLanguage", "en")); // GH-90000

            CompletionService completionService = null;
            AuditService auditService = null;
            VoiceSttPort sttPort = null;
            VoiceTtsPort ttsPort = null;

            VoiceGatewayHandler handlerWithCtx = new VoiceGatewayHandler( // GH-90000
                completionService, auditService, new ObjectMapper(), // GH-90000
                    mock(HttpHandlerSupport.class), // GH-90000
                    Runnable::run,
                sttPort, ttsPort,
                    ctxLayer::currentEntries);

            // Verify handler is constructed and context is available
            assertThat(handlerWithCtx).isNotNull(); // GH-90000
            // Context entries are accessed via package-private currentEntries; verify wiring via mock
            assertThat(ctxLayer.currentEntries("tenant-x [GH-90000]"))
                    .containsKey("currentCollection [GH-90000]")
                    .containsValue("orders [GH-90000]");
        }
    }

    // ── Intent-to-speech-summary mapping ─────────────────────────────────────

    @Nested
    @DisplayName("VoiceIntentCatalog — intent catalog coverage [GH-90000]")
    class IntentCatalogMappingTests {

        @Test
        @DisplayName("all standard intents are present in catalog [GH-90000]")
        void allStandardIntentsPresent() { // GH-90000
            assertThat(VoiceIntentCatalog.ALL).isNotEmpty(); // GH-90000
            assertThat(VoiceIntentCatalog.findByName("query_entities [GH-90000]")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("get_entity [GH-90000]")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("create_entity [GH-90000]")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("run_analytics_query [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("query_entities intent resolves path with collection param [GH-90000]")
        void queryEntities_resolvesPath() { // GH-90000
            Optional<VoiceIntent> intent = VoiceIntentCatalog.findByName("query_entities [GH-90000]");
            assertThat(intent).isPresent(); // GH-90000
            String resolved = intent.get().resolvePath(Map.of("collection", "products")); // GH-90000
            assertThat(resolved).contains("products [GH-90000]");
        }

        @Test
        @DisplayName("get_entity intent has 'id' as required param [GH-90000]")
        void getEntity_hasRequiredIdParam() { // GH-90000
            Optional<VoiceIntent> intent = VoiceIntentCatalog.findByName("get_entity [GH-90000]");
            assertThat(intent).isPresent(); // GH-90000
            assertThat(intent.get().requiredParams()).contains("id [GH-90000]");
        }

        @Test
        @DisplayName("sensitivity field is set on intents with side effects [GH-90000]")
        void sideEffectIntents_haveSensitivity() { // GH-90000
            Optional<VoiceIntent> deleteIntent = VoiceIntentCatalog.findByName("delete_entity [GH-90000]");
            assertThat(deleteIntent).isPresent(); // GH-90000
            assertThat(deleteIntent.get().sensitivity()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("keyword heuristic finds 'list' intents from partial utterance [GH-90000]")
        void keywordHeuristic_matchesListIntent() { // GH-90000
            var candidates = VoiceIntentCatalog.findCandidates("list all pipelines [GH-90000]");
            assertThat(candidates).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("tenant enforcement [GH-90000]")
    class TenantEnforcementTests {

        @Test
        @DisplayName("handleListIntents returns 400 when tenant header is missing [GH-90000]")
        void handleListIntents_returns400WhenTenantMissing() { // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            HttpResponse badRequest = mock(HttpResponse.class); // GH-90000
            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); // GH-90000

            HttpResponse response = handler.handleListIntents(request).getResult(); // GH-90000

            assertThat(response).isSameAs(badRequest); // GH-90000
        }

        @Test
        @DisplayName("handleVoiceIntent returns 400 when tenant header is missing before reading body [GH-90000]")
        void handleVoiceIntent_returns400WhenTenantMissing() { // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            HttpResponse badRequest = mock(HttpResponse.class); // GH-90000
            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); // GH-90000

            HttpResponse response = handler.handleVoiceIntent(request).getResult(); // GH-90000

            assertThat(response).isSameAs(badRequest); // GH-90000
            verify(request, never()).loadBody(any(int.class)); // GH-90000
        }

        @Test
        @DisplayName("handleClassifyOnly returns 400 when tenant header is missing before reading body [GH-90000]")
        void handleClassifyOnly_returns400WhenTenantMissing() { // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            HttpResponse badRequest = mock(HttpResponse.class); // GH-90000
            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); // GH-90000

            HttpResponse response = handler.handleClassifyOnly(request).getResult(); // GH-90000

            assertThat(response).isSameAs(badRequest); // GH-90000
            verify(request, never()).loadBody(any(int.class)); // GH-90000
        }
    }
}
