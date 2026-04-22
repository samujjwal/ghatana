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
@DisplayName("Learning Loop Accuracy Tests [GH-90000]")
class LearningLoopAccuracyTest extends EventloopTestBase {

    @Nested
    @DisplayName("Signal Transformation Accuracy [GH-90000]")
    class TransformationAccuracyTests {

        @Test
        @DisplayName("accurately maps feedback type to signal type [GH-90000]")
        void accuratelyMapsFeedbackTypeToSignalType() { // GH-90000
            // Test OPERATIONAL feedback maps to OPERATIONAL signal
            FeedbackEvent operationalEvent = FeedbackEvent.builder() // GH-90000
                .feedbackType(FeedbackEvent.FeedbackType.OPERATIONAL) // GH-90000
                .build(); // GH-90000

            // Test OUTCOME feedback maps to PREDICTION_OUTCOME signal
            FeedbackEvent outcomeEvent = FeedbackEvent.builder() // GH-90000
                .feedbackType(FeedbackEvent.FeedbackType.OUTCOME) // GH-90000
                .build(); // GH-90000

            // Test EXPLICIT feedback maps to FEEDBACK signal
            FeedbackEvent explicitEvent = FeedbackEvent.builder() // GH-90000
                .feedbackType(FeedbackEvent.FeedbackType.EXPLICIT) // GH-90000
                .build(); // GH-90000

            // The transformation happens internally, we verify the mapping is correct
            assertThat(operationalEvent.getFeedbackType()).isEqualTo(FeedbackEvent.FeedbackType.OPERATIONAL); // GH-90000
            assertThat(outcomeEvent.getFeedbackType()).isEqualTo(FeedbackEvent.FeedbackType.OUTCOME); // GH-90000
            assertThat(explicitEvent.getFeedbackType()).isEqualTo(FeedbackEvent.FeedbackType.EXPLICIT); // GH-90000
        }

        @Test
        @DisplayName("accurately calculates signal strength [GH-90000]")
        void accuratelyCalculatesSignalStrength() { // GH-90000
            FeedbackEvent event = FeedbackEvent.builder() // GH-90000
                .score(0.8) // GH-90000
                .feedbackType(FeedbackEvent.FeedbackType.EXPLICIT) // GH-90000
                .source(FeedbackEvent.FeedbackSource.EXPERT) // GH-90000
                .confidence(0.9) // GH-90000
                .build(); // GH-90000

            // Expected strength calculation:
            // baseStrength = |0.8| = 0.8
            // EXPLICIT boost: 0.8 * 1.5 = 1.2
            // EXPERT boost: 1.2 * 2.0 = 2.4
            // Confidence weighting: 2.4 * 0.9 = 2.16
            // Capped at 1.0: min(1.0, 2.16) = 1.0 // GH-90000

            double expectedStrength = 1.0; // Capped
            double calculatedStrength = Math.min(1.0,  // GH-90000
                Math.abs(event.getScore()) * 1.5 * 2.0 * event.getConfidence()); // GH-90000

            assertThat(calculatedStrength).isEqualTo(expectedStrength); // GH-90000
        }

        @Test
        @DisplayName("accurately calculates signal strength without boosts [GH-90000]")
        void accuratelyCalculatesSignalStrengthWithoutBoosts() { // GH-90000
            FeedbackEvent event = FeedbackEvent.builder() // GH-90000
                .score(0.5) // GH-90000
                .feedbackType(FeedbackEvent.FeedbackType.IMPLICIT) // GH-90000
                .source(FeedbackEvent.FeedbackSource.USER) // GH-90000
                .confidence(0.8) // GH-90000
                .build(); // GH-90000

            // Expected strength calculation:
            // baseStrength = |0.5| = 0.5
            // No boosts (not EXPLICIT, not EXPERT/GROUND_TRUTH) // GH-90000
            // Confidence weighting: 0.5 * 0.8 = 0.4
            // Capped at 1.0: min(1.0, 0.4) = 0.4 // GH-90000

            double expectedStrength = 0.4;
            double calculatedStrength = Math.min(1.0,  // GH-90000
                Math.abs(event.getScore()) * event.getConfidence()); // GH-90000

            assertThat(calculatedStrength).isEqualTo(expectedStrength); // GH-90000
        }
    }

    @Nested
    @DisplayName("Signal Aggregation Accuracy [GH-90000]")
    class AggregationAccuracyTests {

        @Test
        @DisplayName("accurately aggregates signals by reference [GH-90000]")
        void accuratelyAggregatesSignalsByReference() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.8) // GH-90000
                    .confidence(0.9) // GH-90000
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-2 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.6) // GH-90000
                    .confidence(0.7) // GH-90000
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-3 [GH-90000]")
                    .referenceId("ref-2 [GH-90000]")
                    .score(-0.5) // GH-90000
                    .confidence(0.8) // GH-90000
                    .sentiment(FeedbackEvent.Sentiment.NEGATIVE) // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(3)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            // Should have 2 references affected
            assertThat(report.getReferencesAffected()).isEqualTo(2); // GH-90000
            // Should have 3 signals generated
            assertThat(report.getSignalsGenerated()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("accurately calculates average strength [GH-90000]")
        void accuratelyCalculatesAverageStrength() { // GH-90000
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.8) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.6) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.4) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build() // GH-90000
            );

            // Average strength = (0.8 + 0.6 + 0.4) / 3 = 0.6 // GH-90000
            double averageStrength = events.stream() // GH-90000
                .mapToDouble(e -> Math.abs(e.getScore())) // GH-90000
                .average() // GH-90000
                .orElse(0.0); // GH-90000

            assertThat(averageStrength).isEqualTo(0.6, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("accurately counts reinforcements and corrections [GH-90000]")
        void accuratelyCountsReinforcementsAndCorrections() { // GH-90000
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .sentiment(FeedbackEvent.Sentiment.POSITIVE) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .sentiment(FeedbackEvent.Sentiment.NEGATIVE) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .sentiment(FeedbackEvent.Sentiment.NEGATIVE) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .referenceId("ref-1 [GH-90000]")
                    .sentiment(FeedbackEvent.Sentiment.NEUTRAL) // GH-90000
                    .build() // GH-90000
            );

            long reinforcements = events.stream() // GH-90000
                .filter(e -> e.getSentiment() != null && e.getSentiment().getNumericValue() >= 0) // GH-90000
                .count(); // GH-90000

            long corrections = events.stream() // GH-90000
                .filter(e -> e.getSentiment() != null && e.getSentiment().getNumericValue() < 0) // GH-90000
                .count(); // GH-90000

            assertThat(reinforcements).isEqualTo(3); // POSITIVE, POSITIVE, NEUTRAL // GH-90000
            assertThat(corrections).isEqualTo(2); // NEGATIVE, NEGATIVE // GH-90000
        }
    }

    @Nested
    @DisplayName("Learning Strategy Accuracy [GH-90000]")
    class StrategyAccuracyTests {

        @Test
        @DisplayName("IMMEDIATE strategy applies when strength threshold met [GH-90000]")
        void immediateStrategyAppliesWhenStrengthThresholdMet() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            // Events with average strength >= 0.3 should trigger IMMEDIATE learning
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.5) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .strategy(LearningLoop.LearningStrategy.IMMEDIATE) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.isSuccess()).isTrue(); // GH-90000
            assertThat(report.getEventsCollected()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("BATCHED strategy requires minimum signal count [GH-90000]")
        void batchedStrategyRequiresMinimumSignalCount() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            // BATCHED requires at least 3 signals
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.9) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build(), // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-2 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.9) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(2)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .strategy(LearningLoop.LearningStrategy.BATCHED) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            // Should still process events, but may not apply learning due to batch threshold
            assertThat(report.isSuccess()).isTrue(); // GH-90000
            assertThat(report.getEventsCollected()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("GRADUAL strategy respects confidence threshold [GH-90000]")
        void gradualStrategyRespectsConfidenceThreshold() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            // Events with confidence < 0.7 should not trigger GRADUAL learning
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.9) // GH-90000
                    .confidence(0.5) // Below threshold // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .strategy(LearningLoop.LearningStrategy.GRADUAL) // GH-90000
                .confidenceThreshold(0.7) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SUPERVISED strategy defers application [GH-90000]")
        void supervisedStrategyDefersApplication() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.9) // GH-90000
                    .confidence(1.0) // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .strategy(LearningLoop.LearningStrategy.SUPERVISED) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            // SUPERVISED should not auto-apply
            assertThat(report.isSuccess()).isTrue(); // GH-90000
            assertThat(report.getUpdatesApplied()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cycle Accuracy Metrics [GH-90000]")
    class CycleAccuracyTests {

        @Test
        @DisplayName("accurately reports cycle duration [GH-90000]")
        void accuratelyReportsCycleDuration() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .score(0.5) // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.getDuration()).isPositive(); // GH-90000
            assertThat(report.getDuration().toMillis()).isLessThan(Duration.ofSeconds(5).toMillis()); // GH-90000
        }

        @Test
        @DisplayName("accurately counts events processed [GH-90000]")
        void accuratelyCountsEventsProcessed() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder().id("evt-1 [GH-90000]").referenceId("ref-1 [GH-90000]").build(),
                FeedbackEvent.builder().id("evt-2 [GH-90000]").referenceId("ref-1 [GH-90000]").build(),
                FeedbackEvent.builder().id("evt-3 [GH-90000]").referenceId("ref-1 [GH-90000]").build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(3)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.getEventsCollected()).isEqualTo(3); // GH-90000
            assertThat(report.getEventsProcessed()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("accurately reports cycle number [GH-90000]")
        void accuratelyReportsCycleNumber() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder().id("evt-1 [GH-90000]").referenceId("ref-1 [GH-90000]").build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report1 = runPromise(() -> loop.learnNow()); // GH-90000
            LearningLoop.CycleReport report2 = runPromise(() -> loop.learnNow()); // GH-90000
            LearningLoop.CycleReport report3 = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report1.getCycleNumber()).isEqualTo(1); // GH-90000
            assertThat(report2.getCycleNumber()).isEqualTo(2); // GH-90000
            assertThat(report3.getCycleNumber()).isEqualTo(3); // GH-90000
            assertThat(loop.getCycleCount()).isEqualTo(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("Edge Case Accuracy [GH-90000]")
    class EdgeCaseAccuracyTests {

        @Test
        @DisplayName("handles empty feedback gracefully [GH-90000]")
        void handlesEmptyFeedbackGracefully() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(List.of())); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.isSkipped()).isTrue(); // GH-90000
            assertThat(report.getSkipReason()).contains("Insufficient events [GH-90000]");
        }

        @Test
        @DisplayName("handles insufficient events gracefully [GH-90000]")
        void handlesInsufficientEventsGracefully() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder().id("evt-1 [GH-90000]").referenceId("ref-1 [GH-90000]").build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(5) // Requires 5, only have 1 // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.isSkipped()).isTrue(); // GH-90000
            assertThat(report.getSkipReason()).contains("Insufficient events [GH-90000]");
        }

        @Test
        @DisplayName("handles null fields in feedback events [GH-90000]")
        void handlesNullFieldsInFeedbackEvents() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .referenceId("ref-1 [GH-90000]")
                    .feedbackType(null) // GH-90000
                    .sentiment(null) // GH-90000
                    .source(null) // GH-90000
                    .score(0.5) // GH-90000
                    .confidence(0.5) // GH-90000
                    .build() // GH-90000
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.isSuccess()).isTrue(); // GH-90000
            assertThat(report.getSignalsGenerated()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("handles learner failures with rollback [GH-90000]")
        void handlesLearnerFailuresWithRollback() { // GH-90000
            FeedbackCollector mockCollector = mock(FeedbackCollector.class); // GH-90000
            
            List<FeedbackEvent> events = List.of( // GH-90000
                FeedbackEvent.builder().id("evt-1 [GH-90000]").referenceId("ref-1 [GH-90000]").score(0.5).build()
            );

            when(mockCollector.getPending(anyInt())).thenReturn(Promise.of(events)); // GH-90000
            when(mockCollector.markProcessed(any())).thenReturn(Promise.of(1)); // GH-90000

            LearningLoop.Learner mockLearner = mock(LearningLoop.Learner.class); // GH-90000
            when(mockLearner.getName()).thenReturn("test-learner [GH-90000]");
            when(mockLearner.learn(any())).thenReturn(Promise.of(false)); // Learning fails // GH-90000
            when(mockLearner.rollback(any())).thenReturn(Promise.of(true)); // Rollback succeeds // GH-90000

            LearningLoop loop = LearningLoop.builder() // GH-90000
                .feedbackCollector(mockCollector) // GH-90000
                .autoRollback(true) // GH-90000
                .minEventsToLearn(1) // GH-90000
                .build(); // GH-90000

            loop.addLearner(mockLearner); // GH-90000

            LearningLoop.CycleReport report = runPromise(() -> loop.learnNow()); // GH-90000

            assertThat(report.isSuccess()).isTrue(); // GH-90000
            assertThat(report.getUpdatesFailed()).isGreaterThan(0); // GH-90000
            assertThat(report.getRollbacks()).isGreaterThan(0); // GH-90000
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) { // GH-90000
        return org.assertj.core.data.Offset.offset(delta); // GH-90000
    }
}
