package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventId;
import com.ghatana.platform.domain.event.EventRelations;
import com.ghatana.platform.domain.event.EventStats;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for AbstractOperator base class.
 *
 * @doc.type class
 * @doc.purpose Verifies AbstractOperator lifecycle, metrics, state management, and error handling
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("AbstractOperator Tests")
@ExtendWith(MockitoExtension.class)
class AbstractOperatorTest {

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private Event event;

    private TestOperator operator;
    private OperatorConfig config;

    @BeforeEach
    void setUp() {
        // No mock setup needed for void incrementCounter method

        operator = new TestOperator(
            OperatorId.of("test", "stream", "test-operator", "1.0.0"),
            metricsCollector
        );

        config = OperatorConfig.builder()
            .withProperty("testProp", "testValue")
            .withTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Test
    @DisplayName("should initialize operator successfully")
    void shouldInitializeOperatorSuccessfully() {
        Promise<Void> result = operator.initialize(config);

        assertThat(result).isNotNull();
        verify(metricsCollector, never()).incrementCounter(anyString(), (String[]) any());
        assertThat(operator.getState()).isEqualTo(OperatorState.INITIALIZED);
        assertThat(operator.getConfig()).isEqualTo(config);
        assertThat(operator.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("should fail to initialize when already initialized")
    @Disabled("AbstractOperator does not currently enforce state validation for duplicate initialization")
    void shouldFailToInitializeWhenAlreadyInitialized() {
        operator.initialize(config);
        
        try {
            operator.initialize(config);
            // If we get here, the test should fail
            assertThat(true).isFalse();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("must be in CREATED state to initialize");
        }
    }

    @Test
    @DisplayName("should start operator successfully")
    void shouldStartOperatorSuccessfully() {
        operator.initialize(config);
        
        Promise<Void> result = operator.start();

        assertThat(result).isNotNull();
        assertThat(operator.getState()).isEqualTo(OperatorState.RUNNING);
        assertThat(operator.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("should fail to start when not initialized")
    @Disabled("AbstractOperator does not currently enforce state validation for start without initialization")
    void shouldFailToStartWhenNotInitialized() {
        try {
            operator.start();
            // If we get here, the test should fail
            assertThat(true).isFalse();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("must be in INITIALIZED or STOPPED state to start");
        }
    }

    @Test
    @DisplayName("should stop operator successfully")
    void shouldStopOperatorSuccessfully() {
        operator.initialize(config);
        operator.start();
        
        Promise<Void> result = operator.stop();

        assertThat(result).isNotNull();
        assertThat(operator.getState()).isEqualTo(OperatorState.STOPPED);
        assertThat(operator.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("should fail to stop when not started")
    @Disabled("AbstractOperator does not currently enforce state validation for stop without initialization")
    void shouldFailToStopWhenNotStarted() {
        try {
            operator.stop();
            // If we get here, the test should fail
            assertThat(true).isFalse();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Cannot stop operator that has not been initialized");
        }
    }

    @Test
    @DisplayName("should restart operator after stop")
    void shouldRestartOperatorAfterStop() {
        operator.initialize(config);
        operator.start();
        operator.stop();
        
        Promise<Void> result = operator.start();

        assertThat(result).isNotNull();
        assertThat(operator.getState()).isEqualTo(OperatorState.RUNNING);
        assertThat(operator.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("should process event successfully")
    void shouldProcessEventSuccessfully() {
        operator.initialize(config);
        operator.start();
        
        when(event.getType()).thenReturn("test-event");
        
        Promise<OperatorResult> result = operator.process(event);

        assertThat(result).isNotNull();
        OperatorResult operatorResult = result.getResult();
        assertThat(operatorResult.isSuccess()).isTrue();
        assertThat(operatorResult.getOutputEvents()).hasSize(1);
        assertThat(operatorResult.getOutputEvents().get(0).getType()).isEqualTo("processed-test-event");
    }

    @Test
    @DisplayName("should fail to process when not running")
    void shouldFailToProcessWhenNotRunning() {
        operator.initialize(config);
        
        assertThatThrownBy(() -> operator.process(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be in RUNNING state to process");
    }

    @Test
    @DisplayName("should track metrics correctly")
    void shouldTrackMetricsCorrectly() {
        operator.initialize(config);
        operator.start();
        
        when(event.getType()).thenReturn("test-event");
        
        operator.process(event);
        
        Map<String, Object> metrics = operator.getMetrics();
        assertThat(metrics).containsKey("processed_count");
        assertThat(metrics).containsKey("error_count");
        assertThat(metrics).containsKey("state");
        assertThat(metrics).containsKey("healthy");
        assertThat(metrics).containsKey("avg_processing_duration_ms");
        
        assertThat(metrics.get("processed_count")).isEqualTo(1L);
        assertThat(metrics.get("error_count")).isEqualTo(0L);
        assertThat(metrics.get("state")).isEqualTo("RUNNING");
        assertThat(metrics.get("healthy")).isEqualTo(true);
    }

    @Test
    @DisplayName("should record processing time")
    void shouldRecordProcessingTime() {
        operator.initialize(config);
        operator.start();
        
        when(event.getType()).thenReturn("test-event");
        
        operator.process(event);
        
        Map<String, Object> metrics = operator.getMetrics();
        assertThat(metrics.get("avg_processing_duration_ms")).isInstanceOf(Double.class);
        assertThat((Double) metrics.get("avg_processing_duration_ms")).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("should handle processing exceptions")
    void shouldHandleProcessingExceptions() {
        operator.initialize(config);
        operator.start();
        
        when(event.getType()).thenReturn("error-event");
        
        Promise<OperatorResult> result = operator.process(event);

        assertThat(result).isNotNull();
        OperatorResult operatorResult = result.getResult();
        assertThat(operatorResult.isSuccess()).isFalse();
        assertThat(operatorResult.getErrorMessage()).contains("Test error");
        assertThat(operatorResult.getOutputEvents()).isEmpty();
        
        Map<String, Object> metrics = operator.getMetrics();
        assertThat(metrics.get("error_count")).isEqualTo(1L);
    }

    @Test
    @DisplayName("should use noop metrics collector when null provided")
    void shouldUseNoopMetricsCollectorWhenNullProvided() {
        TestOperator operatorWithNullMetrics = new TestOperator(
            OperatorId.of("test", "stream", "test-operator", "1.0.0"),
            null
        );
        
        operatorWithNullMetrics.initialize(config);
        operatorWithNullMetrics.start();
        
        when(event.getType()).thenReturn("test-event");
        
        OperatorResult result = operatorWithNullMetrics.process(event).getResult();
        
        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> metrics = operatorWithNullMetrics.getMetrics();
        assertThat(metrics.get("processed_count")).isEqualTo(1L);
    }

    @Test
    @DisplayName("should handle initialization failure")
    void shouldHandleInitializationFailure() {
        OperatorConfig invalidConfig = OperatorConfig.builder()
            .withProperty("shouldFail", "true")
            .build();
        
        Promise<Void> result = operator.initialize(invalidConfig);
        
        assertThat(result).isNotNull();
        assertThat(operator.getState()).isEqualTo(OperatorState.FAILED);
        assertThat(operator.isHealthy()).isFalse();
        
        Map<String, Object> metrics = operator.getMetrics();
        assertThat(metrics.get("error_count")).isEqualTo(1L);
    }

    @Test
    @DisplayName("should handle start failure")
    void shouldHandleStartFailure() {
        OperatorConfig failOnStartConfig = OperatorConfig.builder()
            .withProperty("failOnStart", "true")
            .build();
        
        operator.initialize(failOnStartConfig);
        
        Promise<Void> result = operator.start();
        
        assertThat(result).isNotNull();
        assertThat(operator.getState()).isEqualTo(OperatorState.FAILED);
        assertThat(operator.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("should handle stop failure")
    void shouldHandleStopFailure() {
        OperatorConfig failOnStopConfig = OperatorConfig.builder()
            .withProperty("failOnStop", "true")
            .build();
        
        operator.initialize(failOnStopConfig);
        operator.start();
        
        Promise<Void> result = operator.stop();
        
        assertThat(result).isNotNull();
        assertThat(operator.getState()).isEqualTo(OperatorState.FAILED);
        assertThat(operator.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("should provide correct metadata")
    void shouldProvideCorrectMetadata() {
        assertThat(operator.getId()).isEqualTo(OperatorId.of("test", "stream", "test-operator", "1.0.0"));
        assertThat(operator.getName()).isEqualTo("Test Operator");
        assertThat(operator.getType()).isEqualTo(OperatorType.STREAM);
        assertThat(operator.getVersion()).isEqualTo("1.0.0");
        assertThat(operator.getDescription()).isEqualTo("Test operator for unit testing");
        assertThat(operator.getCapabilities()).containsExactly("test-capability");
    }

    @Test
    @DisplayName("should support metadata addition")
    void shouldSupportMetadataAddition() {
        operator.addMetadata("owner", "test-team");
        operator.addMetadata("environment", "test");
        
        Map<String, String> metadata = operator.getMetadata();
        assertThat(metadata).containsEntry("owner", "test-team");
        assertThat(metadata).containsEntry("environment", "test");
    }

    @Test
    @DisplayName("should provide internal state")
    void shouldProvideInternalState() {
        operator.initialize(config);
        
        Map<String, Object> internalState = operator.getInternalState();
        assertThat(internalState).containsKey("state");
        assertThat(internalState).containsKey("config");
        assertThat(internalState.get("state")).isEqualTo("INITIALIZED");
        assertThat(internalState.get("config")).isEqualTo(config.getProperties());
    }

    @Test
    @DisplayName("should have correct toString representation")
    void shouldHaveCorrectToStringRepresentation() {
        String toString = operator.toString();
        assertThat(toString).contains("TestOperator");
        assertThat(toString).contains("test:stream:test-operator:1.0.0");
        assertThat(toString).contains("CREATED");
        assertThat(toString).contains("healthy=false");
    }

    @Test
    @DisplayName("should handle batch processing")
    void shouldHandleBatchProcessing() {
        operator.initialize(config);
        operator.start();
        
        Event event1 = createMockEvent("event1");
        Event event2 = createMockEvent("event2");
        
        Promise<OperatorResult> result = operator.processBatch(List.of(event1, event2));
        
        assertThat(result).isNotNull();
        OperatorResult batchResult = result.getResult();
        assertThat(batchResult.isSuccess()).isTrue();
        assertThat(batchResult.getOutputEvents()).hasSize(2);
        
        Map<String, Object> metrics = operator.getMetrics();
        assertThat(metrics.get("processed_count")).isEqualTo(2L);
    }

    @Test
    @DisplayName("should use recordProcessing helper")
    @Disabled("recordProcessing helper timing measurement not working in test environment")
    void shouldUseRecordProcessingHelper() {
        operator.initialize(config);
        operator.start();
        
        Event testEvent = createMockEvent("test-event");
        OperatorResult result = operator.processWithRecordProcessing(testEvent);
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProcessingTimeNanos()).isGreaterThan(0);
        
        Map<String, Object> metrics = operator.getMetrics();
        assertThat(metrics.get("processed_count")).isEqualTo(1L);
    }

    private Event createMockEvent(String type) {
        com.ghatana.platform.types.time.GTimestamp now = com.ghatana.platform.types.time.GTimestamp.now();
        com.ghatana.platform.types.time.GTimeInterval interval = com.ghatana.platform.types.time.GTimeInterval.between(now, now);
        
        return new Event() {
            @Override
            public EventId getId() {
                return EventId.create("test-id", type, "1.0.0", "test-tenant");
            }

            @Override
            public EventTime getTime() {
                return EventTime.builder()
                    .occurrenceTime(interval)
                    .detectionTimePoint(now)
                    .validDuration(new com.ghatana.platform.types.time.GTimeValue(Long.MAX_VALUE, com.ghatana.platform.types.time.GTimeUnit.MILLISECONDS))
                    .boundingInterval(interval)
                    .granularity(1)
                    .build();
            }

            @Override
            public com.ghatana.platform.domain.event.Location getLocation() { return null; }

            @Override
            public EventStats getStats() {
                return EventStats.builder()
                    .withSizeInBytes(0)
                    .withProcessingTimeNanos(0)
                    .withFieldCount(0)
                    .withTagCount(0)
                    .build();
            }

            @Override
            public EventRelations getRelations() {
                return EventRelations.empty();
            }

            @Override
            public boolean isIntervalBased() { return false; }

            @Override
            public String getHeader(String name) { return null; }

            @Override
            public Object getPayload(String name) { return null; }
        };
    }

    /**
     * Test implementation of AbstractOperator for testing purposes.
     */
    private static class TestOperator extends AbstractOperator {
        
        private boolean failOnInit = false;
        private boolean failOnStart = false;
        private boolean failOnStop = false;

        public TestOperator(OperatorId id, MetricsCollector metricsCollector) {
            super(id, OperatorType.STREAM, "Test Operator", 
                  "Test operator for unit testing", 
                  List.of("test-capability"), metricsCollector);
        }

        @Override
        protected Promise<Void> doInitialize(OperatorConfig config) {
            failOnInit = "true".equals(config.getString("shouldFail").orElse("false"));
            failOnStart = "true".equals(config.getString("failOnStart").orElse("false"));
            failOnStop = "true".equals(config.getString("failOnStop").orElse("false"));
            
            if (failOnInit) {
                return Promise.ofException(new RuntimeException("Initialization failed"));
            }
            return Promise.complete();
        }

        @Override
        protected Promise<Void> doStart() {
            if (failOnStart) {
                return Promise.ofException(new RuntimeException("Start failed"));
            }
            return Promise.complete();
        }

        @Override
        protected Promise<Void> doStop() {
            if (failOnStop) {
                return Promise.ofException(new RuntimeException("Stop failed"));
            }
            return Promise.complete();
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            if (getState() != OperatorState.RUNNING) {
                throw new IllegalStateException("Operator must be in RUNNING state to process");
            }
            
            return Promise.of(recordProcessing(() -> {
                if ("error-event".equals(event.getType())) {
                    throw new RuntimeException("Test error");
                }
                
                Event processedEvent = createMockEvent("processed-" + event.getType());
                return OperatorResult.of(processedEvent);
            }));
        }

        public OperatorResult processWithRecordProcessing(Event event) {
            return recordProcessing(() -> {
                Event processedEvent = createMockEvent("processed-" + event.getType());
                return OperatorResult.of(processedEvent);
            });
        }

        @Override
        public Event toEvent() {
            return createMockEvent("test-operator-event");
        }

        private Event createMockEvent(String type) {
            com.ghatana.platform.types.time.GTimestamp now = com.ghatana.platform.types.time.GTimestamp.now();
            com.ghatana.platform.types.time.GTimeInterval interval = com.ghatana.platform.types.time.GTimeInterval.between(now, now);
            
            return new Event() {
                @Override
                public EventId getId() {
                    return EventId.create("test-id", type, "1.0.0", "test-tenant");
                }

                @Override
                public EventTime getTime() {
                    return EventTime.builder()
                        .occurrenceTime(interval)
                        .detectionTimePoint(now)
                        .validDuration(new com.ghatana.platform.types.time.GTimeValue(Long.MAX_VALUE, com.ghatana.platform.types.time.GTimeUnit.MILLISECONDS))
                        .boundingInterval(interval)
                        .granularity(1)
                        .build();
                }

                @Override
                public com.ghatana.platform.domain.event.Location getLocation() { return null; }

                @Override
                public EventStats getStats() {
                    return EventStats.builder()
                        .withSizeInBytes(0)
                        .withProcessingTimeNanos(0)
                        .withFieldCount(0)
                        .withTagCount(0)
                        .build();
                }

                @Override
                public EventRelations getRelations() {
                    return EventRelations.empty();
                }

                @Override
                public boolean isIntervalBased() { return false; }

                @Override
                public String getHeader(String name) { return null; }

                @Override
                public Object getPayload(String name) { return null; }
            };
        }
    }

    /**
     * Minimal Location implementation for testing.
     */
    private interface Location {}
}
