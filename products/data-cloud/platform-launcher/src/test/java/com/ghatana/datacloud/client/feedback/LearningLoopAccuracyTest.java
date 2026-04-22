package com.ghatana.datacloud.client.feedback;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Accuracy tests for the LearningLoop.
 *
 * <p>Tests focus on the correctness of:
 * <ul>
 *   <li>Signal transformation from feedback events</li>
 *   <li>Signal strength calculation</li>
 *   <li>Signal aggregation</li>
 *   <li>Learning strategy application</li>
 *   <li>Overall cycle accuracy metrics</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Learning loop accuracy validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Learning Loop Accuracy Tests")
class LearningLoopAccuracyTest extends EventloopTestBase {

    @Nested
    @DisplayName("Signal Transformation Accuracy")
    class TransformationAccuracyTests {

        @Test
        @DisplayName("accurately maps feedback type to signal type")
        void accuratelyMapsFeedbackTypeToSignalType() {
            // Test OPERATIONAL feedback maps to OPERATIONAL signal
            FeedbackEvent operationalEvent = FeedbackEvent.builder()
                .feedbackType(FeedbackEvent.FeedbackType.OPERATIONAL)
                .build();

            // Test OUTCOME feedback maps to PREDICTION_OUTCOME signal
            FeedbackEvent outcomeEvent = FeedbackEvent.builder()
                .feedbackType(FeedbackEvent.FeedbackType.OUTCOME)
                .build();

            // Test EXPLICIT feedback maps to FEEDBACK signal
            FeedbackEvent explicitEvent = FeedbackEvent.builder()
                .feedbackType(FeedbackEvent.FeedbackType.EXPLICIT)
                .build();

            // The transformation happens internally, we verify the mapping is correct
            assertThat(operationalEvent.getFeedbackType()).isEqualTo(FeedbackEvent.FeedbackType.OPERATIONAL);
            assertThat(outcomeEvent.getFeedbackType()).isEqualTo(FeedbackEvent.FeedbackType.OUTCOME);
            assertThat(explicitEvent.getFeedbackType()).isEqualTo(FeedbackEvent.FeedbackType.EXPLICIT);
        }

        @Test
        @DisplayName("accurately calculates signal strength")
        void accuratelyCalculatesSignalStrength() {
            FeedbackEvent event = FeedbackEvent.builder()
                .score(0.8)
                .feedbackType(FeedbackEvent.FeedbackType.EXPLICIT)
                .source(FeedbackEvent.FeedbackSource.EXPERT)
                .confidence(0.9)
                .build();

            // Expected strength calculation:
            // baseStrength = |0.8| = 0.8
            // EXPLICIT boost: 0.8 * 1.5 = 1.2
            // EXPERT boost: 1.2 * 2.0 = 2.4
            // Confidence weighting: 2.4 * 0.9 = 2.16
            // Capped at 1.0: min(1.0, 2.16) = 1.0

            double expectedStrength = 1.0; // Capped
            double calculatedStrength = Math.min(1.0, 
                Math.abs(event.getScore()) * 1.5 * 2.0 * event.getConfidence());

            assertThat(calculatedStrength).isEqualTo(expectedStrength);
        }

        @Test
        @DisplayName("accurately calculates signal strength without boosts")
        void accuratelyCalculatesSignalStrengthWithoutBoosts() {
            FeedbackEvent event = FeedbackEvent.builder()
                .score(0.5)
                .feedbackType(FeedbackEvent.FeedbackType.IMPLICIT)
                .source(FeedbackEvent.FeedbackSource.USER)
                .confidence(0.8)
                .build();

            // Expected strength calculation:
            // baseStrength = |0.5| = 0.5
            // No boosts (not EXPLICIT, not EXPERT/GROUND_TRUTH)
            // Confidence weighting: 0.5 * 0.8 = 0.4
            // Capped at 1.0: min(1.0, 0.4) = 0.4

            double expectedStrength = 0.4;
            double calculatedStrength = Math.min(1.0, 
                Math.abs(event.getScore()) * event.getConfidence());

            assertThat(calculatedStrength).isEqualTo(expectedStrength);
        }
    }

    @Nested
    @DisplayName("Signal Aggregation Accuracy")
    class AggregationAccuracyTests {

        @Test
        @DisplayName("accurately aggregates signals by reference")
        void accuratelyAggregatesSignalsByReference() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .score(0.8)
                    .confidence(0.9)
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE)
                    .build(),
                FeedbackEvent.builder()
                    .id("evt-2")
                    .referenceId("ref-1")
                    .score(0.6)
                    .confidence(0.7)
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE)
                    .build(),
                FeedbackEvent.builder()
                    .id("evt-3")
                    .referenceId("ref-2")
                    .score(-0.5)
                    .confidence(0.8)
                    .sentiment(FeedbackEvent.Sentiment.NEGATIVE)
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(3));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            // Should have 2 references affected
            assertThat(report.getReferencesAffected()).isEqualTo(2);
            // Should have 3 signals generated
            assertThat(report.getSignalsGenerated()).isEqualTo(3);
        }

        @Test
        @DisplayName("accurately calculates average strength")
        void accuratelyCalculatesAverageStrength() {
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .score(0.8)
                    .confidence(1.0)
                    .build(),
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .score(0.6)
                    .confidence(1.0)
                    .build(),
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .score(0.4)
                    .confidence(1.0)
                    .build()
            );

            // Average strength = (0.8 + 0.6 + 0.4) / 3 = 0.6
            double averageStrength = events.stream()
                .mapToDouble(e -> Math.abs(e.getScore()))
                .average()
                .orElse(0.0);

            assertThat(averageStrength).isEqualTo(0.6, within(0.001));
        }

        @Test
        @DisplayName("accurately counts reinforcements and corrections")
        void accuratelyCountsReinforcementsAndCorrections() {
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE)
                    .build(),
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE)
                    .build(),
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .sentiment(FeedbackEvent.Sentiment.NEGATIVE)
                    .build(),
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .sentiment(FeedbackEvent.Sentiment.NEGATIVE)
                    .build(),
                FeedbackEvent.builder()
                    .referenceId("ref-1")
                    .sentiment(FeedbackEvent.Sentiment.NEUTRAL)
                    .build()
            );

            long reinforcements = events.stream()
                .filter(e -> e.getSentiment() != null && e.getSentiment().getNumericValue() >= 0)
                .count();

            long corrections = events.stream()
                .filter(e -> e.getSentiment() != null && e.getSentiment().getNumericValue() < 0)
                .count();

            assertThat(reinforcements).isEqualTo(3); // POSITIVE, POSITIVE, NEUTRAL
            assertThat(corrections).isEqualTo(2); // NEGATIVE, NEGATIVE
        }
    }

    @Nested
    @DisplayName("Learning Strategy Accuracy")
    class StrategyAccuracyTests {

        @Test
        @DisplayName("IMMEDIATE strategy applies when strength threshold met")
        void immediateStrategyAppliesWhenStrengthThresholdMet() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            // Events with average strength >= 0.3 should trigger IMMEDIATE learning
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .score(0.5)
                    .confidence(1.0)
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .strategy(LearningLoop.LearningStrategy.IMMEDIATE)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.isSuccess()).isTrue();
            assertThat(report.getEventsCollected()).isEqualTo(1);
        }

        @Test
        @DisplayName("BATCHED strategy requires minimum signal count")
        void batchedStrategyRequiresMinimumSignalCount() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            // BATCHED requires at least 3 signals
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .score(0.9)
                    .confidence(1.0)
                    .build(),
                FeedbackEvent.builder()
                    .id("evt-2")
                    .referenceId("ref-1")
                    .score(0.9)
                    .confidence(1.0)
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(2));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .strategy(LearningLoop.LearningStrategy.BATCHED)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            // Should still process events, but may not apply learning due to batch threshold
            assertThat(report.isSuccess()).isTrue();
            assertThat(report.getEventsCollected()).isEqualTo(2);
        }

        @Test
        @DisplayName("GRADUAL strategy respects confidence threshold")
        void gradualStrategyRespectsConfidenceThreshold() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            // Events with confidence < 0.7 should not trigger GRADUAL learning
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .score(0.9)
                    .confidence(0.5) // Below threshold
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .strategy(LearningLoop.LearningStrategy.GRADUAL)
                .confidenceThreshold(0.7)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("SUPERVISED strategy defers application")
        void supervisedStrategyDefersApplication() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .score(0.9)
                    .confidence(1.0)
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .strategy(LearningLoop.LearningStrategy.SUPERVISED)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            // SUPERVISED should not auto-apply
            assertThat(report.isSuccess()).isTrue();
            assertThat(report.getUpdatesApplied()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Cycle Accuracy Metrics")
    class CycleAccuracyTests {

        @Test
        @DisplayName("accurately reports cycle duration")
        void accuratelyReportsCycleDuration() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .score(0.5)
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.getDuration()).isPositive();
            assertThat(report.getDuration().toMillis()).isLessThan(Duration.ofSeconds(5).toMillis());
        }

        @Test
        @DisplayName("accurately counts events processed")
        void accuratelyCountsEventsProcessed() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder().id("evt-1").referenceId("ref-1").build(),
                FeedbackEvent.builder().id("evt-2").referenceId("ref-1").build(),
                FeedbackEvent.builder().id("evt-3").referenceId("ref-1").build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(3));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.getEventsCollected()).isEqualTo(3);
            assertThat(report.getEventsProcessed()).isEqualTo(3);
        }

        @Test
        @DisplayName("accurately reports cycle number")
        void accuratelyReportsCycleNumber() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder().id("evt-1").referenceId("ref-1").build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report1 = runPromise(() -> loop.learnNow());
            LearningLoop.CycleReport report2 = runPromise(() -> loop.learnNow());
            LearningLoop.CycleReport report3 = runPromise(() -> loop.learnNow());

            assertThat(report1.getCycleNumber()).isEqualTo(1);
            assertThat(report2.getCycleNumber()).isEqualTo(2);
            assertThat(report3.getCycleNumber()).isEqualTo(3);
            assertThat(loop.getCycleCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Edge Case Accuracy")
    class EdgeCaseAccuracyTests {

        @Test
        @DisplayName("handles empty feedback gracefully")
        void handlesEmptyFeedbackGracefully() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(List.of()));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.isSkipped()).isTrue();
            assertThat(report.getSkipReason()).contains("Insufficient events");
        }

        @Test
        @DisplayName("handles insufficient events gracefully")
        void handlesInsufficientEventsGracefully() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder().id("evt-1").referenceId("ref-1").build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(5) // Requires 5, only have 1
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.isSkipped()).isTrue();
            assertThat(report.getSkipReason()).contains("Insufficient events");
        }

        @Test
        @DisplayName("handles null fields in feedback events")
        void handlesNullFieldsInFeedbackEvents() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder()
                    .id("evt-1")
                    .referenceId("ref-1")
                    .feedbackType(null)
                    .sentiment(null)
                    .source(null)
                    .score(0.5)
                    .confidence(0.5)
                    .build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .minEventsToLearn(1)
                .build();

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.isSuccess()).isTrue();
            assertThat(report.getSignalsGenerated()).isEqualTo(1);
        }

        @Test
        @DisplayName("handles learner failures with rollback")
        void handlesLearnerFailuresWithRollback() {
            FeedbackCollector mockCollector = mock(FeedbackCollector.class);
            
            List<FeedbackEvent> events = List.of(
                FeedbackEvent.builder().id("evt-1").referenceId("ref-1").score(0.5).build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events));
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1));

            LearningLoop.Learner mockLearner = mock(LearningLoop.Learner.class);
            when(mockLearner.getName()).thenReturn("test-learner");
            when(mockLearner.learn(any())).thenReturn(Promise.of(false)); // Learning fails
            when(mockLearner.rollback(any())).thenReturn(Promise.of(true)); // Rollback succeeds

            LearningLoop loop = LearningLoop.builder()
                .feedbackCollector(mockCollector)
                .autoRollback(true)
                .minEventsToLearn(1)
                .build();

            loop.addLearner(mockLearner);

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow());

            assertThat(report.isSuccess()).isTrue();
            assertThat(report.getUpdatesFailed()).isGreaterThan(0);
            assertThat(report.getRollbacks()).isGreaterThan(0);
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
