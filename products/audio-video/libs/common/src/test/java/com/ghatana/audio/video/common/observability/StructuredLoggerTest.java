package com.ghatana.audio.video.common.observability;

import com.ghatana.media.common.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StructuredLogger}.
 *
 * @doc.type class
 * @doc.purpose Tests for structured logging helpers and MDC lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("StructuredLogger")
class StructuredLoggerTest {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(StructuredLoggerTest.class); // GH-90000

    // ─── newCorrelationId() ─────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("newCorrelationId() returns 8-char non-blank string")
    void newCorrelationId_returnsEightChars() { // GH-90000
        String id = StructuredLogger.newCorrelationId(); // GH-90000

        assertThat(id).hasSize(8).doesNotContainAnyWhitespaces(); // GH-90000
    }

    @Test
    @DisplayName("two successive newCorrelationId() calls return different values")
    void newCorrelationId_uniqueValues() { // GH-90000
        assertThat(StructuredLogger.newCorrelationId()) // GH-90000
                .isNotEqualTo(StructuredLogger.newCorrelationId()); // GH-90000
    }

    // ─── withContext(Runnable) ──────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("withContext(Runnable) sets MDC keys while block runs")
    void withContext_runnable_setsMdcDuringExecution() { // GH-90000
        AtomicReference<String> capturedCid = new AtomicReference<>(); // GH-90000
        AtomicReference<String> capturedSvc = new AtomicReference<>(); // GH-90000
        AtomicReference<String> capturedOp  = new AtomicReference<>(); // GH-90000

        StructuredLogger.withContext("abc12345", "stt-service", "transcribe", () -> { // GH-90000
            capturedCid.set(MDC.get(StructuredLogger.MDC_CORRELATION_ID)); // GH-90000
            capturedSvc.set(MDC.get(StructuredLogger.MDC_SERVICE)); // GH-90000
            capturedOp.set(MDC.get(StructuredLogger.MDC_OPERATION)); // GH-90000
        });

        assertThat(capturedCid.get()).isEqualTo("abc12345");
        assertThat(capturedSvc.get()).isEqualTo("stt-service");
        assertThat(capturedOp.get()).isEqualTo("transcribe");
    }

    @Test
    @DisplayName("withContext(Runnable) clears MDC keys after block completes")
    void withContext_runnable_clearsMdcAfterExecution() { // GH-90000
        StructuredLogger.withContext("abc12345", "stt-service", "transcribe", () -> { // GH-90000
            // no-op
        });

        assertThat(MDC.get(StructuredLogger.MDC_CORRELATION_ID)).isNull(); // GH-90000
        assertThat(MDC.get(StructuredLogger.MDC_SERVICE)).isNull(); // GH-90000
        assertThat(MDC.get(StructuredLogger.MDC_OPERATION)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("withContext(Runnable) clears MDC even when block throws")
    void withContext_runnable_clearsMdcOnException() { // GH-90000
        try {
            StructuredLogger.withContext("err-id", "vision-service", "detect", () -> { // GH-90000
                throw new RuntimeException("simulated failure");
            });
        } catch (RuntimeException ignored) { // GH-90000
            // expected
        }

        assertThat(MDC.get(StructuredLogger.MDC_CORRELATION_ID)).isNull(); // GH-90000
    }

    // ─── withContext(Supplier) ──────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("withContext(Supplier) returns the supplier result")
    void withContext_supplier_returnsResult() { // GH-90000
        String result = StructuredLogger.withContext("abc", "svc", "op", () -> "hello"); // GH-90000

        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("withContext(Supplier) clears MDC after returning result")
    void withContext_supplier_clearsMdcAfterReturn() { // GH-90000
        StructuredLogger.withContext("abc", "svc", "op", () -> "result"); // GH-90000

        assertThat(MDC.get(StructuredLogger.MDC_CORRELATION_ID)).isNull(); // GH-90000
    }

    // ─── log methods — smoke tests (no assertion on log output, just verify no NPE) ── // GH-90000

    @Test
    @DisplayName("logStarted() does not throw")
    void logStarted_doesNotThrow() { // GH-90000
        StructuredLogger.logStarted(LOG, "transcribe"); // GH-90000
    }

    @Test
    @DisplayName("logSucceeded() does not throw")
    void logSucceeded_doesNotThrow() { // GH-90000
        StructuredLogger.logSucceeded(LOG, "transcribe", 42L); // GH-90000
    }

    @Test
    @DisplayName("logFailed() with retryable error does not throw")
    void logFailed_retryableError_doesNotThrow() { // GH-90000
        StructuredLogger.logFailed(LOG, "transcribe", // GH-90000
                new com.ghatana.media.common.InferenceError("timeout", null, true)); // GH-90000
    }

    @Test
    @DisplayName("logFailed() with non-retryable error does not throw")
    void logFailed_nonRetryableError_doesNotThrow() { // GH-90000
        StructuredLogger.logFailed(LOG, "transcribe", new ValidationError("bad input"));
    }

    @Test
    @DisplayName("logFailed() with null throwable does not throw")
    void logFailed_nullThrowable_doesNotThrow() { // GH-90000
        StructuredLogger.logFailed(LOG, "transcribe", null); // GH-90000
    }

    @Test
    @DisplayName("logInvalid() does not throw")
    void logInvalid_doesNotThrow() { // GH-90000
        StructuredLogger.logInvalid(LOG, "transcribe", "audio data empty"); // GH-90000
    }
}
