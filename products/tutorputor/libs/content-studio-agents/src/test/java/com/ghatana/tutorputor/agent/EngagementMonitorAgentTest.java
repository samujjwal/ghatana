package com.ghatana.tutorputor.agent;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link EngagementMonitorAgent}.
 *
 * @doc.type test
 * @doc.purpose Validates engagement scoring, state classification, and Thompson Sampling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EngagementMonitorAgent")
class EngagementMonitorAgentTest extends EventloopTestBase {

    private EngagementMonitorAgent agent;
    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        agent = new EngagementMonitorAgent("engagement-test");
        ctx = mock(AgentContext.class);
    }

    @Nested
    @DisplayName("Descriptor")
    class DescriptorTests {

        @Test
        @DisplayName("reports ADAPTIVE agent type")
        void agentTypeIsAdaptive() {
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.ADAPTIVE);
        }

        @Test
        @DisplayName("reports THOMPSON_SAMPLING subtype")
        void subtypeIsThompsonSampling() {
            assertThat(agent.descriptor().getSubtype()).isEqualTo("THOMPSON_SAMPLING");
        }

        @Test
        @DisplayName("declares engagement-monitoring capability")
        void hasEngagementCapability() {
            assertThat(agent.descriptor().getCapabilities()).contains("engagement-monitoring");
        }
    }

    @Nested
    @DisplayName("Engagement Classification")
    class ClassificationTests {

        @Test
        @DisplayName("active learner is classified as ENGAGED")
        void activeLearnerIsEngaged() {
            EngagementMonitorAgent.EngagementSnapshot snapshot = new EngagementMonitorAgent.EngagementSnapshot(
                    "learner-1", "session-1",
                    5_000,   // 5s inactivity
                    0.8,     // 80% completion
                    4.0,     // 4 interactions/min
                    0.5,     // positive sentiment
                    0.1      // low abort rate
            );

            AgentResult<EngagementMonitorAgent.EngagementDecision> result =
                    runPromise(() -> agent.doProcess(ctx, snapshot));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().state()).isEqualTo(EngagementMonitorAgent.EngagementState.ENGAGED);
            assertThat(result.getOutput().intervention()).isEqualTo(EngagementMonitorAgent.Intervention.NONE);
        }

        @Test
        @DisplayName("long inactivity classifies as DISENGAGED")
        void inactiveLearnerIsDisengaged() {
            EngagementMonitorAgent.EngagementSnapshot snapshot = new EngagementMonitorAgent.EngagementSnapshot(
                    "learner-2", "session-2",
                    200_000,  // 3+ minutes inactive
                    0.2,
                    0.0,
                    -0.5,
                    0.6
            );

            AgentResult<EngagementMonitorAgent.EngagementDecision> result =
                    runPromise(() -> agent.doProcess(ctx, snapshot));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().state()).isEqualTo(EngagementMonitorAgent.EngagementState.DISENGAGED);
            assertThat(result.getOutput().intervention()).isNotEqualTo(EngagementMonitorAgent.Intervention.NONE);
        }

        @Test
        @DisplayName("low engagement score without full inactivity classifies as AT_RISK")
        void lowEngagementIsAtRisk() {
            EngagementMonitorAgent.EngagementSnapshot snapshot = new EngagementMonitorAgent.EngagementSnapshot(
                    "learner-3", "session-3",
                    120_000,  // 2 min inactivity (below 3min threshold)
                    0.1,      // very low completion
                    0.5,      // minimal interaction
                    -0.8,     // negative sentiment
                    0.8       // high abort
            );

            AgentResult<EngagementMonitorAgent.EngagementDecision> result =
                    runPromise(() -> agent.doProcess(ctx, snapshot));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().state()).isEqualTo(EngagementMonitorAgent.EngagementState.AT_RISK);
        }
    }

    @Nested
    @DisplayName("Thompson Sampling Adaptation")
    class AdaptationTests {

        @Test
        @DisplayName("successful intervention increases selection probability")
        void successfulInterventionLearnedFromOutcome() {
            // Record many successes for GAMIFY
            for (int i = 0; i < 10; i++) {
                agent.recordOutcome(EngagementMonitorAgent.Intervention.GAMIFY, true);
            }
            // Record failures for others
            for (int i = 0; i < 10; i++) {
                agent.recordOutcome(EngagementMonitorAgent.Intervention.NUDGE, false);
                agent.recordOutcome(EngagementMonitorAgent.Intervention.BREAK, false);
            }

            // Run many trials and check that GAMIFY is selected more often
            int gamifyCount = 0;
            for (int trial = 0; trial < 100; trial++) {
                EngagementMonitorAgent.EngagementSnapshot snapshot = new EngagementMonitorAgent.EngagementSnapshot(
                        "learner-adapt", "session-adapt",
                        200_000, 0.1, 0.0, -0.5, 0.5
                );
                AgentResult<EngagementMonitorAgent.EngagementDecision> result =
                        runPromise(() -> agent.doProcess(ctx, snapshot));
                if (result.getOutput().intervention() == EngagementMonitorAgent.Intervention.GAMIFY) {
                    gamifyCount++;
                }
            }

            // GAMIFY should be selected significantly more often (>50% of the time)
            assertThat(gamifyCount).isGreaterThan(40);
        }

        @Test
        @DisplayName("engaged learner always gets NONE intervention")
        void engagedLearnerNoIntervention() {
            EngagementMonitorAgent.EngagementSnapshot snapshot = new EngagementMonitorAgent.EngagementSnapshot(
                    "learner-happy", "session-happy",
                    1_000, 0.9, 5.0, 0.8, 0.05
            );

            AgentResult<EngagementMonitorAgent.EngagementDecision> result =
                    runPromise(() -> agent.doProcess(ctx, snapshot));

            assertThat(result.getOutput().intervention()).isEqualTo(EngagementMonitorAgent.Intervention.NONE);
        }
    }

    @Nested
    @DisplayName("Output metadata")
    class MetadataTests {

        @Test
        @DisplayName("result includes engagementScore and state in metadata")
        void resultHasMetadata() {
            EngagementMonitorAgent.EngagementSnapshot snapshot = new EngagementMonitorAgent.EngagementSnapshot(
                    "learner-meta", "session-meta",
                    10_000, 0.5, 2.0, 0.0, 0.3
            );

            AgentResult<EngagementMonitorAgent.EngagementDecision> result =
                    runPromise(() -> agent.doProcess(ctx, snapshot));

            assertThat(result.getMetrics()).containsKey("learnerId");
            assertThat(result.getMetrics()).containsKey("engagementScore");
            assertThat(result.getMetrics()).containsKey("state");
            assertThat(result.getMetrics()).containsKey("intervention");
        }
    }
}
