package com.ghatana.audio.video.vision.recognition;

import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * @doc.type class
 * @doc.purpose Unit tests for StructuredFacialRecognitionAuditSink (AV-P2-05) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("StructuredFacialRecognitionAuditSink Tests (AV-P2-05)")
class StructuredFacialRecognitionAuditSinkTest {

    @Mock
    private MetricsCollector metricsCollector;

    private StructuredFacialRecognitionAuditSink sink;

    @BeforeEach
    void setUp() { // GH-90000
        sink = new StructuredFacialRecognitionAuditSink(metricsCollector); // GH-90000
        lenient().doNothing().when(metricsCollector).incrementCounter( // GH-90000
                org.mockito.ArgumentMatchers.anyString(), // GH-90000
                org.mockito.ArgumentMatchers.any(String[].class)); // GH-90000
    }

    @Test
    @DisplayName("Emits counter with outcome=success and reason=none on successful recognition")
    void shouldEmitSuccessCounterForSuccessfulRecognition() { // GH-90000
        var event = FacialRecognitionService.FacialRecognitionAuditEvent.success("actor-1", "identity-1", 0.97); // GH-90000

        sink.record(event); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                "av.facial_recognition.audit",
                "outcome", "success",
                "reason", "none"
        );
    }

    @Test
    @DisplayName("Emits counter with outcome=no_match and reason=below_threshold")
    void shouldEmitNoMatchCounterWhenBelowThreshold() { // GH-90000
        var event = FacialRecognitionService.FacialRecognitionAuditEvent.noMatch("actor-2", 0.40); // GH-90000

        sink.record(event); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                "av.facial_recognition.audit",
                "outcome", "no_match",
                "reason", "below_threshold"
        );
    }

    @Test
    @DisplayName("Emits counter with outcome=denied and reason=consent_missing")
    void shouldEmitDeniedCounterForConsentMissing() { // GH-90000
        var event = FacialRecognitionService.FacialRecognitionAuditEvent.denied("consent_missing", "actor-3"); // GH-90000

        sink.record(event); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                "av.facial_recognition.audit",
                "outcome", "denied",
                "reason", "consent_missing"
        );
    }

    @Test
    @DisplayName("Emits counter with outcome=denied and reason=feature_disabled")
    void shouldEmitDeniedCounterForFeatureDisabled() { // GH-90000
        var event = FacialRecognitionService.FacialRecognitionAuditEvent.denied("feature_disabled", "actor-4"); // GH-90000

        sink.record(event); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                "av.facial_recognition.audit",
                "outcome", "denied",
                "reason", "feature_disabled"
        );
    }
}

