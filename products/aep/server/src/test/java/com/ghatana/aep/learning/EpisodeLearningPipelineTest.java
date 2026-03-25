/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.learning;

import com.ghatana.agent.learning.UpdateCandidate;
import com.ghatana.agent.learning.evaluation.CompositeEvaluationGate;
import com.ghatana.agent.learning.evaluation.EvaluationContext;
import com.ghatana.agent.learning.evaluation.EvaluationGate.GateResult;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EpisodeLearningPipeline}.
 *
 * <p>All I/O is mocked. {@link Promise#of(Object)} resolves synchronously, so
 * {@code .getResult()} is safe without an Eventloop.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the episode-to-policy learning pipeline
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EpisodeLearningPipeline")
class EpisodeLearningPipelineTest {

    private static final String TENANT  = "tenant-alpha";
    private static final String AGENT_A = "agent-001";
    private static final String AGENT_B = "agent-002";

    @Mock private DataCloudClient dataCloud;
    @Mock private CompositeEvaluationGate gate;
    @Mock private HumanReviewQueue queue;
    @Mock private ReviewItem reviewItem;

    private EpisodeLearningPipeline pipelineDefault;

    @BeforeEach
    void setUp() {
        pipelineDefault = new EpisodeLearningPipeline(dataCloud, gate, queue);
    }

    // ─── computeMetrics (static, package-visible) ─────────────────────────

    @Nested
    @DisplayName("computeMetrics()")
    class ComputeMetricsTest {

        @Test
        @DisplayName("returns zero metrics for empty episode list")
        void computeMetrics_emptyEpisodes_returnsZeroMetrics() {
            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(List.of());

            assertThat(metrics.get("successRate")).isZero();
            assertThat(metrics.get("errorRate")).isZero();
            assertThat(metrics.get("avgLatencyMs")).isZero();
            assertThat(metrics.get("count")).isZero();
        }

        @Test
        @DisplayName("computes correct rates for all-success episodes")
        void computeMetrics_allSuccess_successRateOne() {
            List<Entity> episodes = List.of(
                    episode("e1", AGENT_A, "SUCCESS", 100),
                    episode("e2", AGENT_A, "SUCCESS", 200),
                    episode("e3", AGENT_A, "SUCCESS", 150));

            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(episodes);

            assertThat(metrics.get("successRate")).isEqualTo(1.0);
            assertThat(metrics.get("errorRate")).isZero();
            assertThat(metrics.get("count")).isEqualTo(3.0);
            assertThat(metrics.get("avgLatencyMs")).isEqualTo(150.0);
        }

        @Test
        @DisplayName("computes correct rates for mixed outcomes")
        void computeMetrics_mixedOutcomes_correctRates() {
            List<Entity> episodes = List.of(
                    episode("e1", AGENT_A, "SUCCESS",  100),
                    episode("e2", AGENT_A, "FAILURE",  200),
                    episode("e3", AGENT_A, "SUCCESS",  300),
                    episode("e4", AGENT_A, "TIMEOUT",  400),
                    episode("e5", AGENT_A, "CANCELLED", 500));

            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(episodes);

            // 2/5 success
            assertThat(metrics.get("successRate")).isEqualTo(2.0 / 5.0);
            // 2/5 failures (FAILURE + TIMEOUT); CANCELLED not counted as failure
            assertThat(metrics.get("errorRate")).isEqualTo(2.0 / 5.0);
            assertThat(metrics.get("count")).isEqualTo(5.0);
            assertThat(metrics.get("avgLatencyMs")).isEqualTo(300.0);  // (100+200+300+400+500)/5
        }

        @Test
        @DisplayName("handles episodes with no latencyMs field gracefully")
        void computeMetrics_missingLatency_treatsAsZero() {
            Entity noLatency = Entity.of("e1", "dc_memory",
                    Map.of("agentId", AGENT_A, "outcome", "SUCCESS", "type", "EPISODIC"));
            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(
                    List.of(noLatency));

            assertThat(metrics.get("avgLatencyMs")).isZero();
        }
    }

    // ─── run() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("run()")
    class RunTest {

        @Test
        @DisplayName("returns empty result when DataCloud returns no episodes")
        void run_noEpisodes_returnsEmptyResult() {
            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.success()).isTrue();
            assertThat(result.episodesRead()).isZero();
            assertThat(result.skillsEvaluated()).isZero();
            assertThat(result.policiesQueued()).isZero();
        }

        @Test
        @DisplayName("skips all skills when episode count is below minimum")
        void run_insufficientEpisodes_skipsAllSkills() {
            // 5 episodes for one skill — below default minEpisodeCount (10)
            List<Entity> fewEpisodes = List.of(
                    episode("e1", AGENT_A, "SUCCESS", 100),
                    episode("e2", AGENT_A, "SUCCESS", 100),
                    episode("e3", AGENT_A, "FAILURE", 100),
                    episode("e4", AGENT_A, "SUCCESS", 100),
                    episode("e5", AGENT_A, "SUCCESS", 100));

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(fewEpisodes));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.success()).isTrue();
            assertThat(result.episodesRead()).isEqualTo(5);
            assertThat(result.skillsEvaluated()).isEqualTo(1);
            assertThat(result.skillsSkipped()).isEqualTo(1);
            assertThat(result.policiesQueued()).isZero();
            // Gate should NOT be called for insufficient episodes
            verifyNoInteractions(gate);
        }

        @Test
        @DisplayName("queues policy when gate passes with sufficient confidence")
        void run_passingEvaluation_queuesForReview() {
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12);

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(enoughEpisodes));
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class)))
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.85, 0.70, "ok")));
            when(queue.enqueue(any(ReviewItem.class)))
                    .thenReturn(Promise.of(reviewItem));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.success()).isTrue();
            assertThat(result.policiesQueued()).isEqualTo(1);
            assertThat(result.gateFailures()).isZero();
            verify(queue).enqueue(any(ReviewItem.class));
        }

        @Test
        @DisplayName("does not queue when gate confidence is below reviewThreshold")
        void run_lowConfidence_doesNotQueue() {
            // Use a pipeline with reviewThreshold=0.70 (default) but gate returns 0.60
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12);

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(enoughEpisodes));
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class)))
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.60, 0.70, "marginal")));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.policiesQueued()).isZero();
            assertThat(result.gateFailures()).isEqualTo(1);
            verifyNoInteractions(queue);
        }

        @Test
        @DisplayName("does not queue when gate explicitly fails (passed=false)")
        void run_gateFailedExplicitly_doesNotQueue() {
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 15);

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(enoughEpisodes));
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class)))
                    .thenReturn(Promise.of(new GateResult("safety", false, 0.80, 0.90, "safety violation")));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.policiesQueued()).isZero();
            assertThat(result.gateFailures()).isEqualTo(1);
            verifyNoInteractions(queue);
        }

        @Test
        @DisplayName("evaluates multiple skills independently")
        void run_multipleSkills_evaluatesEach() {
            List<Entity> mixed = new java.util.ArrayList<>();
            mixed.addAll(successEpisodes(AGENT_A, 12));
            mixed.addAll(successEpisodes(AGENT_B, 12));

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(mixed));
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class)))
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.85, 0.70, "ok")));
            when(queue.enqueue(any(ReviewItem.class)))
                    .thenReturn(Promise.of(reviewItem));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.skillsEvaluated()).isEqualTo(2);
            assertThat(result.policiesQueued()).isEqualTo(2);
            verify(gate, times(2)).evaluate(any(), any());
        }

        @Test
        @DisplayName("returns failed result when DataCloud throws")
        void run_dataCloudError_returnsFailed() {
            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("dc unavailable")));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult();

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("dc unavailable");
        }

        @Test
        @DisplayName("review item context includes provenance and auto-promotable flag")
        void run_autoPromotableConfidence_taggedInContext() {
            // Use a pipeline with autoPromoteThreshold=0.90; gate returns 0.95
            EpisodeLearningPipeline pipeline = new EpisodeLearningPipeline(
                    dataCloud, gate, queue, 10, 0.70, 0.90);
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12);

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(enoughEpisodes));
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class)))
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.95, 0.70, "excellent")));

            ArgumentCaptor<ReviewItem> captor = ArgumentCaptor.forClass(ReviewItem.class);
            when(queue.enqueue(captor.capture())).thenReturn(Promise.of(reviewItem));

            pipeline.run(TENANT).getResult();

            ReviewItem submitted = captor.getValue();
            assertThat(submitted.getEvaluationSummary()).contains("auto-promotable");
            assertThat(submitted.getConfidenceScore()).isGreaterThanOrEqualTo(0.95);
        }

        @Test
        @DisplayName("enqueued review item has correct tenant, skill, and type")
        void run_passingEvaluation_reviewItemHasCorrectFields() {
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12);

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class)))
                    .thenReturn(Promise.of(enoughEpisodes));
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class)))
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.80, 0.70, "ok")));

            ArgumentCaptor<ReviewItem> captor = ArgumentCaptor.forClass(ReviewItem.class);
            when(queue.enqueue(captor.capture())).thenReturn(Promise.of(reviewItem));

            pipelineDefault.run(TENANT).getResult();

            ReviewItem submitted = captor.getValue();
            assertThat(submitted.getTenantId()).isEqualTo(TENANT);
            assertThat(submitted.getSkillId()).isEqualTo(AGENT_A);
            assertThat(submitted.getItemType())
                    .isEqualTo(com.ghatana.agent.learning.review.ReviewItemType.POLICY);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Creates an Entity with the given fields, as stored in the {@code dc_memory} collection.
     */
    private static Entity episode(String id, String agentId, String outcome, long latencyMs) {
        return Entity.of(id, "dc_memory", Map.of(
                "type",      "EPISODIC",
                "agentId",   agentId,
                "outcome",   outcome,
                "latencyMs", latencyMs));
    }

    /** Creates a list of {@code count} SUCCESS episodes for the given agentId. */
    private static List<Entity> successEpisodes(String agentId, int count) {
        List<Entity> list = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(episode("ep-" + agentId + "-" + i, agentId, "SUCCESS", 100 + i * 10L));
        }
        return list;
    }
}
