package com.ghatana.agent.learning.consolidation;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConsolidationPipeline}.
 *
 * @doc.type class
 * @doc.purpose Tests for memory consolidation pipeline
 * @doc.layer agent-learning
 * @doc.pattern Test
 */
@DisplayName("ConsolidationPipeline Tests")
@ExtendWith(MockitoExtension.class)
class ConsolidationPipelineTest extends EventloopTestBase {

    private static final String AGENT_ID = "agent-test-001";
    private static final Instant SINCE = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private EpisodicToSemanticConsolidator semanticConsolidator;

    @Mock
    private EpisodicToProceduralConsolidator proceduralConsolidator;

    @Mock
    private ConflictResolver conflictResolver;

    private ConsolidationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ConsolidationPipeline(semanticConsolidator, proceduralConsolidator, conflictResolver);
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null semanticConsolidator")
        void shouldRejectNullSemanticConsolidator() {
            assertThatThrownBy(() -> new ConsolidationPipeline(null, proceduralConsolidator, conflictResolver))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null proceduralConsolidator")
        void shouldRejectNullProceduralConsolidator() {
            assertThatThrownBy(() -> new ConsolidationPipeline(semanticConsolidator, null, conflictResolver))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null conflictResolver")
        void shouldRejectNullConflictResolver() {
            assertThatThrownBy(() -> new ConsolidationPipeline(semanticConsolidator, proceduralConsolidator, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject empty stage list")
        void shouldRejectEmptyStageList() {
            assertThatThrownBy(() -> new ConsolidationPipeline(List.of(), conflictResolver))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("consolidate()")
    class Consolidate {

        @Test
        @DisplayName("should run semantic extraction then procedural induction in order")
        void shouldRunStagesInOrder() {
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(5));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(2));

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));

            InOrder order = inOrder(semanticConsolidator, proceduralConsolidator);
            order.verify(semanticConsolidator).execute(AGENT_ID, SINCE);
            order.verify(proceduralConsolidator).execute(AGENT_ID, SINCE);
        }

        @Test
        @DisplayName("should return correct facts extracted count")
        void shouldReturnCorrectFactsExtractedCount() {
            when(semanticConsolidator.name()).thenReturn("episodic-to-semantic");
            when(proceduralConsolidator.name()).thenReturn("episodic-to-procedural");
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(7));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(3));

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));

            assertThat(result.factsExtracted()).isEqualTo(7);
        }

        @Test
        @DisplayName("should return correct procedures induced count")
        void shouldReturnCorrectProceduresInducedCount() {
            when(semanticConsolidator.name()).thenReturn("episodic-to-semantic");
            when(proceduralConsolidator.name()).thenReturn("episodic-to-procedural");
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(7));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(3));

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));

            assertThat(result.proceduresInduced()).isEqualTo(3);
        }

        @Test
        @DisplayName("should populate agentId in result")
        void shouldPopulateAgentIdInResult() {
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(0));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(0));

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));

            assertThat(result.agentId()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("should set completedAt to approximately now")
        void shouldSetCompletedAtToApproximatelyNow() {
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(1));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(1));

            Instant before = Instant.now();
            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));
            Instant after = Instant.now();

            assertThat(result.completedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("should handle zero extractions and inductions")
        void shouldHandleZeroExtractionsAndInductions() {
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(0));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(0));

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));

            assertThat(result.factsExtracted()).isZero();
            assertThat(result.proceduresInduced()).isZero();
            assertThat(result.conflictsResolved()).isZero();
        }

        @Test
        @DisplayName("should not call procedural induction if semantic extraction fails")
        void shouldNotCallProceduralInductionIfSemanticExtractionFails() {
            RuntimeException error = new RuntimeException("LLM extraction failed");
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.ofException(error));

            assertThatThrownBy(() -> runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("LLM extraction failed");

            verify(proceduralConsolidator, never()).execute(any(), any());
            clearFatalError();
        }

        @Test
        @DisplayName("should propagate procedural induction failure")
        void shouldPropagateProceduralInductionFailure() {
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(3));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.ofException(new RuntimeException("Induction failed")));

            assertThatThrownBy(() -> runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Induction failed");

            clearFatalError();
        }

        @Test
        @DisplayName("should return per-stage counts in stageResults")
        void shouldReturnPerStageCounts() {
            when(semanticConsolidator.name()).thenReturn("episodic-to-semantic");
            when(proceduralConsolidator.name()).thenReturn("episodic-to-procedural");
            when(semanticConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(10));
            when(proceduralConsolidator.execute(eq(AGENT_ID), eq(SINCE)))
                    .thenReturn(Promise.of(4));

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> pipeline.consolidate(AGENT_ID, SINCE));

            assertThat(result.stageResults()).hasSize(2);
            assertThat(result.totalItemsProduced()).isEqualTo(14);
        }
    }

    @Nested
    @DisplayName("Custom stages")
    class CustomStages {

        @Test
        @DisplayName("should support custom ConsolidationStage implementations")
        void shouldSupportCustomStages() {
            ConsolidationStage customStage = new ConsolidationStage() {
                @Override
                public String name() { return "custom-preference"; }

                @Override
                public Promise<Integer> execute(String agentId, Instant since) {
                    return Promise.of(42);
                }
            };

            ConsolidationPipeline customPipeline = new ConsolidationPipeline(
                    List.of(customStage), conflictResolver);

            ConsolidationPipeline.ConsolidationResult result =
                    runPromise(() -> customPipeline.consolidate(AGENT_ID, SINCE));

            assertThat(result.stageResults()).containsEntry("custom-preference", 42);
            assertThat(result.totalItemsProduced()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("ConsolidationResult record")
    class ConsolidationResultTests {

        @Test
        @DisplayName("should create result with all fields")
        void shouldCreateResultWithAllFields() {
            Instant now = Instant.now();
            var result = new ConsolidationPipeline.ConsolidationResult(
                    "agent-1",
                    Map.of("episodic-to-semantic", 10, "episodic-to-procedural", 3),
                    1, now);

            assertThat(result.agentId()).isEqualTo("agent-1");
            assertThat(result.factsExtracted()).isEqualTo(10);
            assertThat(result.proceduresInduced()).isEqualTo(3);
            assertThat(result.conflictsResolved()).isEqualTo(1);
            assertThat(result.completedAt()).isEqualTo(now);
        }
    }
}
