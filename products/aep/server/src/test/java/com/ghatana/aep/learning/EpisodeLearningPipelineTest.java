/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * <p>All I/O is mocked. {@link Promise#of(Object)} resolves synchronously, so // GH-90000
 * {@code .getResult()} is safe without an Eventloop. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for the episode-to-policy learning pipeline
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EpisodeLearningPipeline [GH-90000]")
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
    void setUp() { // GH-90000
        pipelineDefault = new EpisodeLearningPipeline(dataCloud, gate, queue); // GH-90000
    }

    // ─── computeMetrics (static, package-visible) ───────────────────────── // GH-90000

    @Nested
    @DisplayName("computeMetrics() [GH-90000]")
    class ComputeMetricsTest {

        @Test
        @DisplayName("returns zero metrics for empty episode list [GH-90000]")
        void computeMetrics_emptyEpisodes_returnsZeroMetrics() { // GH-90000
            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(List.of()); // GH-90000

            assertThat(metrics.get("successRate [GH-90000]")).isZero();
            assertThat(metrics.get("errorRate [GH-90000]")).isZero();
            assertThat(metrics.get("avgLatencyMs [GH-90000]")).isZero();
            assertThat(metrics.get("count [GH-90000]")).isZero();
        }

        @Test
        @DisplayName("computes correct rates for all-success episodes [GH-90000]")
        void computeMetrics_allSuccess_successRateOne() { // GH-90000
            List<Entity> episodes = List.of( // GH-90000
                    episode("e1", AGENT_A, "SUCCESS", 100), // GH-90000
                    episode("e2", AGENT_A, "SUCCESS", 200), // GH-90000
                    episode("e3", AGENT_A, "SUCCESS", 150)); // GH-90000

            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(episodes); // GH-90000

            assertThat(metrics.get("successRate [GH-90000]")).isEqualTo(1.0);
            assertThat(metrics.get("errorRate [GH-90000]")).isZero();
            assertThat(metrics.get("count [GH-90000]")).isEqualTo(3.0);
            assertThat(metrics.get("avgLatencyMs [GH-90000]")).isEqualTo(150.0);
        }

        @Test
        @DisplayName("computes correct rates for mixed outcomes [GH-90000]")
        void computeMetrics_mixedOutcomes_correctRates() { // GH-90000
            List<Entity> episodes = List.of( // GH-90000
                    episode("e1", AGENT_A, "SUCCESS",  100), // GH-90000
                    episode("e2", AGENT_A, "FAILURE",  200), // GH-90000
                    episode("e3", AGENT_A, "SUCCESS",  300), // GH-90000
                    episode("e4", AGENT_A, "TIMEOUT",  400), // GH-90000
                    episode("e5", AGENT_A, "CANCELLED", 500)); // GH-90000

            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics(episodes); // GH-90000

            // 2/5 success
            assertThat(metrics.get("successRate [GH-90000]")).isEqualTo(2.0 / 5.0);
            // 2/5 failures (FAILURE + TIMEOUT); CANCELLED not counted as failure // GH-90000
            assertThat(metrics.get("errorRate [GH-90000]")).isEqualTo(2.0 / 5.0);
            assertThat(metrics.get("count [GH-90000]")).isEqualTo(5.0);
            assertThat(metrics.get("avgLatencyMs [GH-90000]")).isEqualTo(300.0);  // (100+200+300+400+500)/5
        }

        @Test
        @DisplayName("handles episodes with no latencyMs field gracefully [GH-90000]")
        void computeMetrics_missingLatency_treatsAsZero() { // GH-90000
            Entity noLatency = Entity.of("e1", "dc_memory", // GH-90000
                    Map.of("agentId", AGENT_A, "outcome", "SUCCESS", "type", "EPISODIC")); // GH-90000
            Map<String, Double> metrics = EpisodeLearningPipeline.computeMetrics( // GH-90000
                    List.of(noLatency)); // GH-90000

            assertThat(metrics.get("avgLatencyMs [GH-90000]")).isZero();
        }
    }

    // ─── run() ─────────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("run() [GH-90000]")
    class RunTest {

        @Test
        @DisplayName("returns empty result when DataCloud returns no episodes [GH-90000]")
        void run_noEpisodes_returnsEmptyResult() { // GH-90000
            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.episodesRead()).isZero(); // GH-90000
            assertThat(result.skillsEvaluated()).isZero(); // GH-90000
            assertThat(result.policiesQueued()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("skips all skills when episode count is below minimum [GH-90000]")
        void run_insufficientEpisodes_skipsAllSkills() { // GH-90000
            // 5 episodes for one skill — below default minEpisodeCount (10) // GH-90000
            List<Entity> fewEpisodes = List.of( // GH-90000
                    episode("e1", AGENT_A, "SUCCESS", 100), // GH-90000
                    episode("e2", AGENT_A, "SUCCESS", 100), // GH-90000
                    episode("e3", AGENT_A, "FAILURE", 100), // GH-90000
                    episode("e4", AGENT_A, "SUCCESS", 100), // GH-90000
                    episode("e5", AGENT_A, "SUCCESS", 100)); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(fewEpisodes)); // GH-90000

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.episodesRead()).isEqualTo(5); // GH-90000
            assertThat(result.skillsEvaluated()).isEqualTo(1); // GH-90000
            assertThat(result.skillsSkipped()).isEqualTo(1); // GH-90000
            assertThat(result.policiesQueued()).isZero(); // GH-90000
            // Gate should NOT be called for insufficient episodes
            verifyNoInteractions(gate); // GH-90000
        }

        @Test
        @DisplayName("queues policy when gate passes with sufficient confidence [GH-90000]")
        void run_passingEvaluation_queuesForReview() { // GH-90000
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(enoughEpisodes)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.85, 0.70, "ok"))); // GH-90000
            when(queue.enqueue(any(ReviewItem.class))) // GH-90000
                    .thenReturn(Promise.of(reviewItem)); // GH-90000

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.policiesQueued()).isEqualTo(1); // GH-90000
            assertThat(result.gateFailures()).isZero(); // GH-90000
            verify(queue).enqueue(any(ReviewItem.class)); // GH-90000
        }

        @Test
        @DisplayName("does not queue when gate confidence is below reviewThreshold [GH-90000]")
        void run_lowConfidence_doesNotQueue() { // GH-90000
            // Use a pipeline with reviewThreshold=0.70 (default) but gate returns 0.60 // GH-90000
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(enoughEpisodes)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.60, 0.70, "marginal"))); // GH-90000

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.policiesQueued()).isZero(); // GH-90000
            assertThat(result.gateFailures()).isEqualTo(1); // GH-90000
            verifyNoInteractions(queue); // GH-90000
        }

        @Test
        @DisplayName("does not queue when gate explicitly fails (passed=false) [GH-90000]")
        void run_gateFailedExplicitly_doesNotQueue() { // GH-90000
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 15); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(enoughEpisodes)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("safety", false, 0.80, 0.90, "safety violation"))); // GH-90000

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.policiesQueued()).isZero(); // GH-90000
            assertThat(result.gateFailures()).isEqualTo(1); // GH-90000
            verifyNoInteractions(queue); // GH-90000
        }

        @Test
        @DisplayName("evaluates multiple skills independently [GH-90000]")
        void run_multipleSkills_evaluatesEach() { // GH-90000
            List<Entity> mixed = new java.util.ArrayList<>(); // GH-90000
            mixed.addAll(successEpisodes(AGENT_A, 12)); // GH-90000
            mixed.addAll(successEpisodes(AGENT_B, 12)); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(mixed)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.85, 0.70, "ok"))); // GH-90000
            when(queue.enqueue(any(ReviewItem.class))) // GH-90000
                    .thenReturn(Promise.of(reviewItem)); // GH-90000

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.skillsEvaluated()).isEqualTo(2); // GH-90000
            assertThat(result.policiesQueued()).isEqualTo(2); // GH-90000
            verify(gate, times(2)).evaluate(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("returns failed result when DataCloud throws [GH-90000]")
        void run_dataCloudError_returnsFailed() { // GH-90000
            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("dc unavailable [GH-90000]")));

            EpisodeLearningPipeline.LearningPipelineResult result =
                    pipelineDefault.run(TENANT).getResult(); // GH-90000

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.errorMessage()).contains("dc unavailable [GH-90000]");
        }

        @Test
        @DisplayName("review item context includes provenance and auto-promotable flag [GH-90000]")
        void run_autoPromotableConfidence_taggedInContext() { // GH-90000
            // Use a pipeline with autoPromoteThreshold=0.85 (default); gate returns 0.90 // GH-90000
            EpisodeLearningPipeline pipeline = new EpisodeLearningPipeline( // GH-90000
                    dataCloud, gate, queue, 10, 0.70, 0.85);
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(enoughEpisodes)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.90, 0.70, "excellent"))); // GH-90000

            ArgumentCaptor<ReviewItem> captor = ArgumentCaptor.forClass(ReviewItem.class); // GH-90000
            when(queue.enqueue(captor.capture())).thenReturn(Promise.of(reviewItem)); // GH-90000

            pipeline.run(TENANT).getResult(); // GH-90000

            ReviewItem submitted = captor.getValue(); // GH-90000
            assertThat(submitted.getEvaluationSummary()).contains("auto-promotable [GH-90000]");
            assertThat(submitted.getConfidenceScore()).isGreaterThanOrEqualTo(0.90); // GH-90000
        }

        @Test
        @DisplayName("review item not tagged auto-promotable when confidence below threshold [GH-90000]")
        void run_belowAutoPromoteThreshold_notTaggedAutoPromotable() { // GH-90000
            // Use default pipeline with autoPromoteThreshold=0.85; gate returns 0.80
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(enoughEpisodes)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.80, 0.70, "good"))); // GH-90000

            ArgumentCaptor<ReviewItem> captor = ArgumentCaptor.forClass(ReviewItem.class); // GH-90000
            when(queue.enqueue(captor.capture())).thenReturn(Promise.of(reviewItem)); // GH-90000

            pipelineDefault.run(TENANT).getResult(); // GH-90000

            ReviewItem submitted = captor.getValue(); // GH-90000
            assertThat(submitted.getEvaluationSummary()).doesNotContain("auto-promotable [GH-90000]");
            assertThat(submitted.getConfidenceScore()).isLessThan(0.85); // GH-90000
        }

        @Test
        @DisplayName("enqueued review item has correct tenant, skill, and type [GH-90000]")
        void run_passingEvaluation_reviewItemHasCorrectFields() { // GH-90000
            List<Entity> enoughEpisodes = successEpisodes(AGENT_A, 12); // GH-90000

            when(dataCloud.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(enoughEpisodes)); // GH-90000
            when(gate.evaluate(any(UpdateCandidate.class), any(EvaluationContext.class))) // GH-90000
                    .thenReturn(Promise.of(new GateResult("regression", true, 0.80, 0.70, "ok"))); // GH-90000

            ArgumentCaptor<ReviewItem> captor = ArgumentCaptor.forClass(ReviewItem.class); // GH-90000
            when(queue.enqueue(captor.capture())).thenReturn(Promise.of(reviewItem)); // GH-90000

            pipelineDefault.run(TENANT).getResult(); // GH-90000

            ReviewItem submitted = captor.getValue(); // GH-90000
            assertThat(submitted.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(submitted.getSkillId()).isEqualTo(AGENT_A); // GH-90000
            assertThat(submitted.getItemType()) // GH-90000
                    .isEqualTo(com.ghatana.agent.learning.review.ReviewItemType.POLICY); // GH-90000
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Creates an Entity with the given fields, as stored in the {@code dc_memory} collection.
     */
    private static Entity episode(String id, String agentId, String outcome, long latencyMs) { // GH-90000
        return Entity.of(id, "dc_memory", Map.of( // GH-90000
                "type",      "EPISODIC",
                "agentId",   agentId,
                "outcome",   outcome,
                "latencyMs", latencyMs));
    }

    /** Creates a list of {@code count} SUCCESS episodes for the given agentId. */
    private static List<Entity> successEpisodes(String agentId, int count) { // GH-90000
        List<Entity> list = new java.util.ArrayList<>(count); // GH-90000
        for (int i = 0; i < count; i++) { // GH-90000
            list.add(episode("ep-" + agentId + "-" + i, agentId, "SUCCESS", 100 + i * 10L)); // GH-90000
        }
        return list;
    }
}
