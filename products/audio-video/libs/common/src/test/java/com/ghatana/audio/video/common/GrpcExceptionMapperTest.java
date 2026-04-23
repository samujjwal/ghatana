package com.ghatana.audio.video.common;

import com.ghatana.media.common.InferenceError;
import com.ghatana.media.common.ModelLoadingError;
import com.ghatana.media.common.ResourceExhaustedError;
import com.ghatana.media.common.ValidationError;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GrpcExceptionMapper}.
 *
 * @doc.type class
 * @doc.purpose Tests for shared gRPC exception mapping logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GrpcExceptionMapper")
class GrpcExceptionMapperTest {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(GrpcExceptionMapperTest.class); // GH-90000
    private static final String OP = "test-op";

    // ─── toStatusException() ────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("toStatusException()")
    class ToStatusException {

        @Test
        @DisplayName("ValidationError maps to INVALID_ARGUMENT with original message")
        void validationError_invalidArgument() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new ValidationError("bad input"), OP, LOG);

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT); // GH-90000
            assertThat(ex.getStatus().getDescription()).contains("bad input");
        }

        @Test
        @DisplayName("ModelLoadingError maps to INTERNAL with generic message")
        void modelLoadingError_internal() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new ModelLoadingError("model.onnx not found"), OP, LOG);

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL); // GH-90000
            assertThat(ex.getStatus().getDescription()).doesNotContain("model.onnx"); // no leak
        }

        @Test
        @DisplayName("Retryable InferenceError maps to UNAVAILABLE")
        void inferenceError_retryable_unavailable() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new InferenceError("GPU busy", null, true), OP, LOG); // GH-90000

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE); // GH-90000
        }

        @Test
        @DisplayName("Non-retryable InferenceError maps to INTERNAL")
        void inferenceError_nonRetryable_internal() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new InferenceError("model corrupted", null, false), OP, LOG); // GH-90000

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL); // GH-90000
        }

        @Test
        @DisplayName("ResourceExhaustedError maps to RESOURCE_EXHAUSTED")
        void resourceExhausted_resourceExhausted() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new ResourceExhaustedError("queue full"), OP, LOG);

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED); // GH-90000
        }

        @Test
        @DisplayName("IllegalArgumentException maps to INVALID_ARGUMENT with message")
        void illegalArgument_invalidArgument() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new IllegalArgumentException("null not allowed"), OP, LOG);

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT); // GH-90000
            assertThat(ex.getStatus().getDescription()).contains("null not allowed");
        }

        @Test
        @DisplayName("IllegalStateException maps to FAILED_PRECONDITION")
        void illegalState_failedPrecondition() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new IllegalStateException("not initialized"), OP, LOG);

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION); // GH-90000
        }

        @Test
        @DisplayName("Unknown RuntimeException maps to INTERNAL with generic message")
        void unknownException_internal() { // GH-90000
            StatusRuntimeException ex = GrpcExceptionMapper.toStatusException( // GH-90000
                    new RuntimeException("unexpected NullPointerException"), OP, LOG);

            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL); // GH-90000
            assertThat(ex.getStatus().getDescription()).doesNotContain("NullPointerException"); // no leak
        }

        @Test
        @DisplayName("null throwable maps to INTERNAL without NPE")
        void nullThrowable_internal() { // GH-90000
            // Verify the mapper handles null gracefully (via toStatus) // GH-90000
            Status status = GrpcExceptionMapper.toStatus(null, OP, LOG); // GH-90000

            assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL); // GH-90000
        }
    }

    // ─── isRetryable() ──────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("isRetryable()")
    class IsRetryable {

        @Test
        @DisplayName("UNAVAILABLE is retryable")
        void unavailable_retryable() { // GH-90000
            assertThat(GrpcExceptionMapper.isRetryable(Status.UNAVAILABLE)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("RESOURCE_EXHAUSTED is retryable")
        void resourceExhausted_retryable() { // GH-90000
            assertThat(GrpcExceptionMapper.isRetryable(Status.RESOURCE_EXHAUSTED)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("INTERNAL is not retryable")
        void internal_notRetryable() { // GH-90000
            assertThat(GrpcExceptionMapper.isRetryable(Status.INTERNAL)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("INVALID_ARGUMENT is not retryable")
        void invalidArgument_notRetryable() { // GH-90000
            assertThat(GrpcExceptionMapper.isRetryable(Status.INVALID_ARGUMENT)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("FAILED_PRECONDITION is not retryable")
        void failedPrecondition_notRetryable() { // GH-90000
            assertThat(GrpcExceptionMapper.isRetryable(Status.FAILED_PRECONDITION)).isFalse(); // GH-90000
        }
    }
}
