/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningDeltaType;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import com.ghatana.agent.obsolescence.ObsolescenceTransitionService;
import com.ghatana.agent.promotion.PromotionEngine;
import com.ghatana.datacloud.governance.approval.ApprovalService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MasteryController Tests")
@ExtendWith(MockitoExtension.class)
class MasteryControllerTest extends EventloopTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private MasteryRegistry masteryRegistry;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private ObsolescenceDetector obsolescenceDetector;

    @Mock
    private ObsolescenceTransitionService obsolescenceTransitionService;

    @Mock
    private LearningDeltaRepository learningDeltaRepository;

    @Mock
    private PromotionEngine promotionEngine;

    private MasteryController controller;

    @BeforeEach
    void setUp() {
        controller = new MasteryController(
                masteryRegistry,
                approvalService,
                obsolescenceDetector,
                obsolescenceTransitionService,
                learningDeltaRepository,
                promotionEngine);
    }

    @Test
    @DisplayName("serve routes preview decision endpoint")
    void serveRoutesPreviewDecisionEndpoint() throws Exception {
        when(approvalService.checkAccess("tenant-1", "mastery:read")).thenReturn(io.activej.promise.Promise.of(true));
        when(masteryRegistry.decide(any(MasteryQuery.class))).thenReturn(io.activej.promise.Promise.of(
                MasteryDecision.allow(
                        "mastery-1",
                        "skill-1",
                        MasteryState.MASTERED,
                        new MasteryScore(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9),
                        VersionScope.empty(),
                        "approved")));

        HttpRequest request = HttpRequest.get(
                "http://localhost/api/v1/mastery/preview/decision?tenantId=tenant-1&agentId=agent-1&skillId=skill-1&versionContext=java-21")
                .build();

        HttpResponse response = runPromise(() -> controller.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = responseBody(response);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("skillId").asText()).isEqualTo("skill-1");
        assertThat(body.path("data").path("versionContext").asText()).isEqualTo("java-21");
        assertThat(body.path("data").path("executable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("serve routes preview retrieval endpoint")
    void serveRoutesPreviewRetrievalEndpoint() throws Exception {
        when(approvalService.checkAccess("tenant-1", "mastery:read")).thenReturn(io.activej.promise.Promise.of(true));
        when(masteryRegistry.query(any(MasteryQuery.class))).thenReturn(io.activej.promise.Promise.of(List.of(
                masteryItem("mastery-1", "tenant-1", "skill-1", "agent-1"))));

        HttpRequest request = HttpRequest.get(
                "http://localhost/api/v1/mastery/preview/retrieval?tenantId=tenant-1&agentId=agent-1&skillId=skill-1&limit=5")
                .build();

        HttpResponse response = runPromise(() -> controller.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = responseBody(response);
        assertThat(body.path("data").path("count").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("items").get(0).path("masteryId").asText()).isEqualTo("mastery-1");
    }

    @Test
    @DisplayName("dry run promotion returns evaluation diagnostics")
    void dryRunPromotionReturnsEvaluationDiagnostics() throws Exception {
        LearningDelta delta = learningDelta("delta-1", "tenant-1");
        EvaluationResult result = new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-1",
                "delta-1",
                Instant.now(),
                Instant.now(),
                4,
                4,
                0,
                0,
                1.0,
                List.of(),
                Map.of("mode", "dry-run"));

        when(approvalService.checkAccess("tenant-1", "learning:evaluate")).thenReturn(io.activej.promise.Promise.of(true));
        when(learningDeltaRepository.findById("tenant-1", "delta-1")).thenReturn(io.activej.promise.Promise.of(Optional.of(delta)));
        when(promotionEngine.evaluate(delta)).thenReturn(io.activej.promise.Promise.of(result));

        HttpRequest request = HttpRequest.post(
                "http://localhost/api/v1/mastery/learning-deltas/delta-1/dry-run-promotion?tenantId=tenant-1")
                .build();

        HttpResponse response = runPromise(() -> controller.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = responseBody(response);
        assertThat(body.path("data").path("dryRun").asBoolean()).isTrue();
        assertThat(body.path("data").path("wouldPromote").asBoolean()).isTrue();
        assertThat(body.path("data").path("passRate").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("process obsolescence event enforces tenant ownership")
    void processObsolescenceEventEnforcesTenantOwnership() {
        when(approvalService.checkAccess("tenant-1", "mastery:transition")).thenReturn(io.activej.promise.Promise.of(true));

        HttpRequest request = HttpRequest.post(
                "http://localhost/api/v1/mastery/obsolescence-events/process?tenantId=tenant-1")
                .withBody(obsolescenceEventJson("tenant-2").getBytes(StandardCharsets.UTF_8))
                .build();

        HttpResponse response = runPromise(() -> controller.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(responseBodyText(response)).contains("another tenant");
    }

    @Test
    @DisplayName("process obsolescence event returns transition result")
    void processObsolescenceEventReturnsTransitionResult() throws Exception {
        when(approvalService.checkAccess("tenant-1", "mastery:transition")).thenReturn(io.activej.promise.Promise.of(true));
        when(obsolescenceTransitionService.processObsolescenceEvent(any(ObsolescenceEvent.class)))
                .thenReturn(io.activej.promise.Promise.of(
                        MasteryTransitionResult.success("mastery-1", MasteryState.MASTERED, MasteryState.OBSOLETE, "transition-1")));

        HttpRequest request = HttpRequest.post(
                "http://localhost/api/v1/mastery/obsolescence-events/process?tenantId=tenant-1")
                .withBody(obsolescenceEventJson("tenant-1").getBytes(StandardCharsets.UTF_8))
                .build();

        HttpResponse response = runPromise(() -> controller.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = responseBody(response);
        assertThat(body.path("data").path("masteryId").asText()).isEqualTo("mastery-1");
        assertThat(body.path("data").path("newState").asText()).isEqualTo("OBSOLETE");
        verify(obsolescenceTransitionService).processObsolescenceEvent(any(ObsolescenceEvent.class));
    }

    private static LearningDelta learningDelta(String deltaId, String tenantId) {
        return new LearningDelta(
                deltaId,
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.EVALUATED,
                "agent-1",
                "release-1",
                "skill-1",
                tenantId,
                null,
                null,
                null,
                "digest-1",
                Map.of("change", "tighten policy"),
                List.of("evidence-1"),
                List.of(),
                List.of("episode-1"),
                null,
                0.2,
                0.9,
                false,
                "tester",
                Instant.now(),
                Instant.now(),
                null,
                null,
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static com.ghatana.agent.mastery.MasteryItem masteryItem(
            String masteryId,
            String tenantId,
            String skillId,
            String agentId) {
        return new com.ghatana.agent.mastery.MasteryItem(
                masteryId,
                tenantId,
                skillId,
                "domain-1",
                agentId,
                "release-1",
                MasteryState.MASTERED,
                VersionScope.empty(),
                ApplicabilityScope.minimal(tenantId, "production"),
                new MasteryScore(0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of(),
                0.95);
    }

    private static JsonNode responseBody(HttpResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(responseBodyText(response));
    }

    private static String responseBodyText(HttpResponse response) {
        return new String(response.getBody().array(), StandardCharsets.UTF_8);
    }

    private static String obsolescenceEventJson(String tenantId) {
        return "{" +
                "\"eventId\":\"event-1\"," +
                "\"masteryId\":\"mastery-1\"," +
                "\"tenantId\":\"" + tenantId + "\"," +
                "\"reason\":\"DEPRECATED_DEPENDENCY\"," +
                "\"description\":\"dependency drift\"," +
                "\"detectedAt\":\"2026-01-01T00:00:00Z\"," +
                "\"evidenceRefs\":[]," +
                "\"metadata\":{}," +
                "\"severity\":\"MEDIUM\"," +
                "\"recommendedTransition\":\"MAINTENANCE_ONLY\"," +
                "\"dependencyChanges\":[]," +
                "\"apiChanges\":[]," +
                "\"securityAdvisories\":[]," +
                "\"documentationSources\":[]}";
    }
}