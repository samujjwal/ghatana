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
 * Tests for {@link BKTMasteryAgent}.
 *
 * @doc.type test
 * @doc.purpose Validates Bayesian Knowledge Tracing computation and agent descriptor
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BKTMasteryAgent")
class BKTMasteryAgentTest extends EventloopTestBase {

    private BKTMasteryAgent agent;
    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        agent = new BKTMasteryAgent("bkt-test");
        ctx = mock(AgentContext.class);
    }

    @Nested
    @DisplayName("Descriptor")
    class DescriptorTests {

        @Test
        @DisplayName("reports PROBABILISTIC agent type")
        void agentTypeIsProbabilistic() {
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.PROBABILISTIC);
        }

        @Test
        @DisplayName("reports BKT subtype")
        void subtypeIsBKT() {
            assertThat(agent.descriptor().getSubtype()).isEqualTo("BAYESIAN_KNOWLEDGE_TRACING");
        }

        @Test
        @DisplayName("declares mastery-estimation capability")
        void hasMasteryCapability() {
            assertThat(agent.descriptor().getCapabilities()).contains("mastery-estimation");
        }
    }

    @Nested
    @DisplayName("Bayesian Update")
    class BayesianUpdateTests {

        @Test
        @DisplayName("all correct observations push P(L) toward mastery")
        void allCorrectApproachesMastery() {
            boolean[] observations = {true, true, true, true, true, true, true, true, true, true};
            BKTMasteryAgent.BKTInput input = new BKTMasteryAgent.BKTInput("skill-add", observations);

            AgentResult<BKTMasteryAgent.BKTOutput> result = runPromise(() -> agent.doProcess(ctx, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().posteriorPL()).isGreaterThan(0.9);
            assertThat(result.output().mastered()).isTrue();
        }

        @Test
        @DisplayName("all incorrect observations keep P(L) low")
        void allIncorrectStaysLow() {
            boolean[] observations = {false, false, false, false, false};
            BKTMasteryAgent.BKTInput input = new BKTMasteryAgent.BKTInput("skill-div", observations);

            AgentResult<BKTMasteryAgent.BKTOutput> result = runPromise(() -> agent.doProcess(ctx, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().posteriorPL()).isLessThan(0.5);
            assertThat(result.output().mastered()).isFalse();
        }

        @Test
        @DisplayName("mixed observations produce intermediate P(L)")
        void mixedObservationsIntermediate() {
            boolean[] observations = {true, false, true, true, false, true};
            BKTMasteryAgent.BKTInput input = new BKTMasteryAgent.BKTInput("skill-mult", observations);

            AgentResult<BKTMasteryAgent.BKTOutput> result = runPromise(() -> agent.doProcess(ctx, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().posteriorPL()).isBetween(0.3, 0.95);
        }

        @Test
        @DisplayName("empty observations returns prior")
        void emptyObservationsReturnsPrior() {
            boolean[] observations = {};
            BKTMasteryAgent.BKTInput input = new BKTMasteryAgent.BKTInput("skill-empty", observations);

            AgentResult<BKTMasteryAgent.BKTOutput> result = runPromise(() -> agent.doProcess(ctx, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().observationCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("custom BKT parameters are respected")
        void customParameters() {
            boolean[] observations = {true, true, true};
            BKTMasteryAgent.BKTInput input = new BKTMasteryAgent.BKTInput(
                    "skill-custom", observations,
                    0.5, // high prior
                    0.5, // high learning rate
                    0.1, // low guess
                    0.05 // low slip
            );

            AgentResult<BKTMasteryAgent.BKTOutput> result = runPromise(() -> agent.doProcess(ctx, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().posteriorPL()).isGreaterThan(0.9);
        }

        @Test
        @DisplayName("result metadata includes skill ID and posterior")
        void resultMetadata() {
            boolean[] observations = {true, true};
            BKTMasteryAgent.BKTInput input = new BKTMasteryAgent.BKTInput("skill-meta", observations);

            AgentResult<BKTMasteryAgent.BKTOutput> result = runPromise(() -> agent.doProcess(ctx, input));

            assertThat(result.metadata()).containsKey("skillId");
            assertThat(result.metadata()).containsKey("posteriorPL");
            assertThat(result.metadata().get("skillId")).isEqualTo("skill-meta");
        }
    }
}
