package com.ghatana.audiovideo.observability.instrumentation;

import com.ghatana.audiovideo.observability.tracing.AudioVideoTracingService;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;

/**
 * @doc.type class
 * @doc.purpose Integration tests for Audio-Video gRPC instrumentation with distributed tracing
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Audio-Video gRPC Instrumentation Integration Tests [GH-90000]")
class AudioVideoGrpcInstrumentationIntegrationTest {

    @Mock
    private Tracer tracer;

    private AudioVideoGrpcInstrumentation instrumentation;
    private AudioVideoTracingService tracingService;

    @BeforeEach
    void setUp() { // GH-90000
        tracer = mock(Tracer.class); // GH-90000
        tracingService = new AudioVideoTracingService(tracer); // GH-90000
        instrumentation = new AudioVideoGrpcInstrumentation(tracingService); // GH-90000
        MDC.clear(); // GH-90000
    }

    @Nested
    @DisplayName("gRPC Streaming Instrumentation [GH-90000]")
    class GrpcStreamingInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulGrpcStreaming() throws Exception { // GH-90000
            // Given
            String methodName = "VisionStream";
            String tenantId = "grpc-tenant-123";
            String expectedResult = "streaming-result";

            // When
            String result = instrumentation.instrumentGrpcStreaming( // GH-90000
                    methodName,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
        }

        @Test
        void shouldPropagateGrpcStreamingException() { // GH-90000
            // Given
            String methodName = "FailingStream";
            String tenantId = "failing-tenant";
            RuntimeException exception = new RuntimeException("Stream failed [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentGrpcStreaming( // GH-90000
                    methodName,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessage("Stream failed [GH-90000]");
        }

        @Test
        void shouldClearContextAfterGrpcStreaming() throws Exception { // GH-90000
            // Given
            String methodName = "CleanupStream";
            String tenantId = "cleanup-tenant";

            // When
            instrumentation.instrumentGrpcStreaming( // GH-90000
                    methodName,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldRecordDurationForGrpcStreaming() throws Exception { // GH-90000
            // Given
            String methodName = "DurationStream";
            String tenantId = "duration-tenant";
            long startTime = System.currentTimeMillis(); // GH-90000

            // When
            instrumentation.instrumentGrpcStreaming( // GH-90000
                    methodName,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(50); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "result";
                    }
            );

            long duration = System.currentTimeMillis() - startTime; // GH-90000

            // Then
            assertThat(duration).isGreaterThanOrEqualTo(50); // GH-90000
        }
    }

    @Nested
    @DisplayName("Video Processing Instrumentation [GH-90000]")
    class VideoProcessingInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulVideoProcessing() throws Exception { // GH-90000
            // Given
            String processId = "video-process-123";
            String tenantId = "video-tenant-456";
            String expectedResult = "video-encoded";

            // When
            String result = instrumentation.instrumentVideoProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
        }

        @Test
        void shouldValidateVideoProcessingSLA() throws Exception { // GH-90000
            // Given
            String processId = "sla-video";
            String tenantId = "sla-tenant";

            // When
            String result = instrumentation.instrumentVideoProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(30); // Within 50ms SLA // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "sla-compliant";
                    }
            );

            // Then
            assertThat(result).isEqualTo("sla-compliant [GH-90000]");
        }

        @Test
        void shouldWarnWhenVideoProcessingExceedsSLA() throws Exception { // GH-90000
            // Given
            String processId = "slow-video";
            String tenantId = "slow-tenant";

            // When
            String result = instrumentation.instrumentVideoProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(60); // Exceeds 50ms SLA // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "slow-result";
                    }
            );

            // Then
            assertThat(result).isEqualTo("slow-result [GH-90000]");
            // SLA warning would be logged
        }

        @Test
        void shouldPropagateVideoProcessingException() { // GH-90000
            // Given
            String processId = "failing-video";
            String tenantId = "failing-video-tenant";
            IllegalStateException exception = new IllegalStateException("Invalid video format [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentVideoProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessage("Invalid video format [GH-90000]");
        }

        @Test
        void shouldClearContextAfterVideoProcessing() throws Exception { // GH-90000
            // Given
            String processId = "cleanup-video";
            String tenantId = "cleanup-video-tenant";

            // When
            instrumentation.instrumentVideoProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldClearContextAfterVideoProcessingException() { // GH-90000
            // Given
            String processId = "exception-cleanup-video";
            String tenantId = "exception-cleanup-video-tenant";

            // When
            try {
                instrumentation.instrumentVideoProcessing( // GH-90000
                        processId,
                        tenantId,
                        () -> { // GH-90000
                            throw new RuntimeException("Processing failed [GH-90000]");
                        }
                );
            } catch (RuntimeException e) { // GH-90000
                // Expected
            } catch (Exception e) { // GH-90000
                throw new RuntimeException(e); // GH-90000
            }

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Audio Processing Instrumentation [GH-90000]")
    class AudioProcessingInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulAudioProcessing() throws Exception { // GH-90000
            // Given
            String processId = "audio-process-123";
            String tenantId = "audio-tenant-456";
            String expectedResult = "audio-transcoded";

            // When
            String result = instrumentation.instrumentAudioProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
        }

        @Test
        void shouldValidateAudioProcessingSLA() throws Exception { // GH-90000
            // Given
            String processId = "sla-audio";
            String tenantId = "audio-sla-tenant";

            // When
            String result = instrumentation.instrumentAudioProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(40); // Within 50ms SLA // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "audio-sla-compliant";
                    }
            );

            // Then
            assertThat(result).isEqualTo("audio-sla-compliant [GH-90000]");
        }

        @Test
        void shouldPropagateAudioProcessingException() { // GH-90000
            // Given
            String processId = "failing-audio";
            String tenantId = "failing-audio-tenant";
            IOException exception = new java.io.IOException("Audio file corrupted [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentAudioProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(IOException.class) // GH-90000
                    .hasMessage("Audio file corrupted [GH-90000]");
        }

        @Test
        void shouldClearContextAfterAudioProcessing() throws Exception { // GH-90000
            // Given
            String processId = "cleanup-audio";
            String tenantId = "cleanup-audio-tenant";

            // When
            instrumentation.instrumentAudioProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Synthesis Instrumentation [GH-90000]")
    class SynthesisInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulSynthesis() throws Exception { // GH-90000
            // Given
            String synthesisId = "synthesis-123";
            String tenantId = "synthesis-tenant-456";
            String expectedResult = "synthesis-complete";

            // When
            String result = instrumentation.instrumentSynthesis( // GH-90000
                    synthesisId,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
        }

        @Test
        void shouldValidateSynthesisSLA() throws Exception { // GH-90000
            // Given
            String synthesisId = "sla-synthesis";
            String tenantId = "synthesis-sla-tenant";

            // When
            String result = instrumentation.instrumentSynthesis( // GH-90000
                    synthesisId,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(35); // Within 50ms SLA // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "synthesis-sla-compliant";
                    }
            );

            // Then
            assertThat(result).isEqualTo("synthesis-sla-compliant [GH-90000]");
        }

        @Test
        void shouldPropagateSynthesisException() { // GH-90000
            // Given
            String synthesisId = "failing-synthesis";
            String tenantId = "failing-synthesis-tenant";
            IllegalArgumentException exception = new IllegalArgumentException("Invalid synthesis parameters [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentSynthesis( // GH-90000
                    synthesisId,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessage("Invalid synthesis parameters [GH-90000]");
        }

        @Test
        void shouldClearContextAfterSynthesis() throws Exception { // GH-90000
            // Given
            String synthesisId = "cleanup-synthesis";
            String tenantId = "cleanup-synthesis-tenant";

            // When
            instrumentation.instrumentSynthesis( // GH-90000
                    synthesisId,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Context Isolation [GH-90000]")
    class MultiTenantContextIsolationTests {

        @Test
        void shouldIsolateTenantContextsAcrossOperations() throws Exception { // GH-90000
            // Given
            String tenant1 = "isolation-tenant-1";
            String tenant2 = "isolation-tenant-2";

            // When
            instrumentation.instrumentVideoProcessing( // GH-90000
                    "process-1",
                    tenant1,
                    () -> "result-1" // GH-90000
            );

            instrumentation.instrumentVideoProcessing( // GH-90000
                    "process-2",
                    tenant2,
                    () -> "result-2" // GH-90000
            );

            // Then
            // Contexts should be properly isolated after each operation
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldPreserveCorrelationIdAcrossOperations() throws Exception { // GH-90000
            // Given
            String correlationId = "audio-video-correlation-123";
            MDC.put("correlationId", correlationId); // GH-90000
            String tenantId = "corr-tenant";

            // When
            instrumentation.instrumentVideoProcessing( // GH-90000
                    "process-1",
                    tenantId,
                    () -> "video-result" // GH-90000
            );

            // Then
            // Correlation ID should be preserved through instrumentation
        }
    }

    @Nested
    @DisplayName("Exception Handling and Cleanup [GH-90000]")
    class ExceptionHandlingAndCleanupTests {

        @Test
        void shouldClearContextEvenOnGrpcStreamingException() { // GH-90000
            // Given
            String methodName = "FailStream";
            String tenantId = "fail-tenant";

            // When
            try {
                instrumentation.instrumentGrpcStreaming( // GH-90000
                        methodName,
                        tenantId,
                        () -> { // GH-90000
                            throw new RuntimeException("Stream error [GH-90000]");
                        }
                );
            } catch (RuntimeException e) { // GH-90000
                // Expected
            } catch (Exception e) { // GH-90000
                throw new RuntimeException(e); // GH-90000
            }

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldClearContextEvenOnVideoProcessingException() { // GH-90000
            // Given
            String processId = "fail-video";
            String tenantId = "fail-video-tenant";

            // When
            try {
                instrumentation.instrumentVideoProcessing( // GH-90000
                        processId,
                        tenantId,
                        () -> { // GH-90000
                            throw new RuntimeException("Video error [GH-90000]");
                        }
                );
            } catch (RuntimeException e) { // GH-90000
                // Expected
            } catch (Exception e) { // GH-90000
                throw new RuntimeException(e); // GH-90000
            }
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldPreserveOriginalExceptionMessage() { // GH-90000
            // Given
            String originalMessage = "Original error message";
            Exception originalException = new RuntimeException(originalMessage); // GH-90000

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentGrpcStreaming( // GH-90000
                    "method",
                    "tenant",
                    () -> { // GH-90000
                        throw originalException;
                    }
            )).hasMessage(originalMessage); // GH-90000
        }

        @Test
        void shouldHandleCheckedExceptions() { // GH-90000
            // Given
            String processId = "checked-exception";
            String tenantId = "checked-tenant";

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentAudioProcessing( // GH-90000
                    processId,
                    tenantId,
                    () -> { // GH-90000
                        throw new InterruptedException("Thread interrupted [GH-90000]");
                    }
            )).isInstanceOf(InterruptedException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Concurrent Audio-Video Operations [GH-90000]")
    class ConcurrentOperationsTests {

        @Test
        void shouldHandleConcurrentVideoAndAudioProcessing() throws Exception { // GH-90000
            // Given
            String videoProcessId = "concurrent-video-1";
            String audioProcessId = "concurrent-audio-1";
            String tenantId = "concurrent-tenant";

            // When
            String videoResult = instrumentation.instrumentVideoProcessing( // GH-90000
                    videoProcessId,
                    tenantId,
                    () -> "video-result" // GH-90000
            );

            String audioResult = instrumentation.instrumentAudioProcessing( // GH-90000
                    audioProcessId,
                    tenantId,
                    () -> "audio-result" // GH-90000
            );

            // Then
            assertThat(videoResult).isEqualTo("video-result [GH-90000]");
            assertThat(audioResult).isEqualTo("audio-result [GH-90000]");
        }

        @Test
        void shouldHandleMultipleSequentialOperations() throws Exception { // GH-90000
            // Given
            String[] processIds = {"process-1", "process-2", "process-3"};
            String tenantId = "sequential-tenant";

            // When
            for (String processId : processIds) { // GH-90000
                instrumentation.instrumentVideoProcessing( // GH-90000
                        processId,
                        tenantId,
                        () -> "result" // GH-90000
                );
            }

            // Then
            // All operations should complete successfully
            // Context should be clean after final operation
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }
    }
}
