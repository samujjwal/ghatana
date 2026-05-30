/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.store.DataCloudPatternStore;
import com.ghatana.pattern.api.model.PatternStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for PatternController lifecycle transitions and feedback provenance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PatternController")
@ExtendWith(MockitoExtension.class)
class PatternControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AepEngine engine;

    @Mock
    private DataCloudPatternStore patternStore;

    private PatternController controller;

    @BeforeEach
    void setUp() {
        controller = new PatternController(engine, patternStore);
    }

    @Test
    @DisplayName("lifecycle activate updates status and emits provenance event")
    void lifecycleActivateUpdatesStatusAndEmitsProvenance() throws Exception {
        UUID patternId = UUID.randomUUID();
        HttpRequest request = mockLifecycleRequest(patternId.toString(), "activate", "tenant-a");

        when(patternStore.updateStatus(patternId, PatternStatus.ACTIVE)).thenReturn(Promise.complete());
        when(engine.process(eq("tenant-a"), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult("evt-activate", true, List.of(), Map.of())));

        HttpResponse response = runPromise(() -> controller.handleLifecycleTransition(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("action", "activate");
        assertThat(body).containsEntry("status", "ACTIVE");
        assertThat(body).containsEntry("transitioned", true);
        assertThat(body).containsEntry("eventId", "evt-activate");

        verify(patternStore).updateStatus(patternId, PatternStatus.ACTIVE);
        verify(engine).process(eq("tenant-a"), any());
    }

    @Test
    @DisplayName("lifecycle simulate emits provenance without status transition")
    void lifecycleSimulateDoesNotUpdateStatus() throws Exception {
        UUID patternId = UUID.randomUUID();
        HttpRequest request = mockLifecycleRequest(patternId.toString(), "simulate", "tenant-a");

        when(engine.process(eq("tenant-a"), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult("evt-sim", true, List.of(), Map.of())));

        HttpResponse response = runPromise(() -> controller.handleLifecycleTransition(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("action", "simulate");
        assertThat(body).containsEntry("simulationRequested", true);
        assertThat(body).containsEntry("eventId", "evt-sim");

        verify(patternStore, never()).updateStatus(any(), any());
        verify(engine).process(eq("tenant-a"), any());
    }

    @Test
    @DisplayName("record feedback requires signal and emits feedback provenance")
    void recordFeedbackRequiresSignalAndEmitsProvenance() throws Exception {
        UUID patternId = UUID.randomUUID();
        HttpRequest request = mockFeedbackRequest(
            patternId.toString(),
            "tenant-a",
            "{\"signal\":\"positive\",\"source\":\"review\",\"score\":0.91,\"note\":\"good precision\"}"
        );

        when(engine.process(eq("tenant-a"), any()))
            .thenReturn(Promise.of(new AepEngine.ProcessingResult("evt-feedback", true, List.of(), Map.of())));

        HttpResponse response = runPromise(() -> controller.handleRecordPatternFeedback(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).containsEntry("recorded", true);
        assertThat(body).containsEntry("patternId", patternId.toString());
        assertThat(body).containsEntry("eventId", "evt-feedback");

        verify(engine).process(eq("tenant-a"), any());
    }

    @Test
    @DisplayName("record feedback rejects missing signal")
    void recordFeedbackRejectsMissingSignal() throws Exception {
        UUID patternId = UUID.randomUUID();
        HttpRequest request = mockFeedbackRequest(patternId.toString(), "tenant-a", "{\"source\":\"review\"}");

        HttpResponse response = runPromise(() -> controller.handleRecordPatternFeedback(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(String.valueOf(body.get("message"))).contains("Feedback signal is required");
        verify(engine, never()).process(anyString(), any());
    }

    private HttpRequest mockLifecycleRequest(String patternId, String action, String tenantId) {
        HttpRequest request = org.mockito.Mockito.mock(HttpRequest.class);
        when(request.getPathParameter("patternId")).thenReturn(patternId);
        when(request.getPathParameter("action")).thenReturn(action);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        return request;
    }

    private HttpRequest mockFeedbackRequest(String patternId, String tenantId, String body) {
        HttpRequest request = org.mockito.Mockito.mock(HttpRequest.class);
        when(request.getPathParameter("patternId")).thenReturn(patternId);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
        return request;
    }

    private Map<String, Object> parseBody(HttpResponse response) throws Exception {
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(body, new TypeReference<>() {});
    }
}
