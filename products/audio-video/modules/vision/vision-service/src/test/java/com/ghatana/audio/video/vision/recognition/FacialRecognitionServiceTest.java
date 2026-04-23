package com.ghatana.audio.video.vision.recognition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FacialRecognitionService} — AV-009.1.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the facial recognition service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FacialRecognitionService")
class FacialRecognitionServiceTest {

    /** Stub model: returns 1 face per image byte array length > 0. */
    private static final FacialRecognitionService.FaceRecognitionModel STUB_MODEL =
            imageBytes -> List.of( // GH-90000
                    FacialRecognitionService.FaceDetection.of( // GH-90000
                            new FacialRecognitionService.BoundingBox(0.1, 0.1, 0.3, 0.4), // GH-90000
                            0.98,
                            new float[]{0.8f, 0.6f, 0.0f}));

    private FacialRecognitionService buildService() { // GH-90000
        return FacialRecognitionService.of(STUB_MODEL); // GH-90000
    }

    private FacialRecognitionService buildService(double detectionThreshold, boolean enabled, // GH-90000
                                                  FacialRecognitionService.FacialRecognitionAuditSink auditSink) {
        return FacialRecognitionService.of(STUB_MODEL, 0.85, detectionThreshold, enabled, auditSink); // GH-90000
    }

    // ─── of() ───────────────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("of(model): null model throws NullPointerException")
    void of_nullModel_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> FacialRecognitionService.of(null)); // GH-90000
    }

    @Test
    @DisplayName("of(model, threshold > 1): throws IllegalArgumentException")
    void of_invalidThreshold_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> FacialRecognitionService.of(STUB_MODEL, 1.5)); // GH-90000
    }

    // ─── detect() ───────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("detect()")
    class Detect {

        @Test
        @DisplayName("returns detected faces from model")
        void detect_returnsDetections() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            List<FacialRecognitionService.FaceDetection> faces = service.detect(new byte[]{1, 2, 3}); // GH-90000
            assertThat(faces).hasSize(1); // GH-90000
            assertThat(faces.get(0).confidence()).isEqualTo(0.98); // GH-90000
        }

        @Test
        @DisplayName("null imageBytes throws NullPointerException")
        void detect_null_throwsNPE() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.detect(null)); // GH-90000
        }

        @Test
        @DisplayName("empty imageBytes throws IllegalArgumentException")
        void detect_empty_throwsIAE() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.detect(new byte[0])); // GH-90000
        }

        @Test
        @DisplayName("returned list is unmodifiable")
        void detect_unmodifiableList() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            var faces = service.detect(new byte[]{1, 2}); // GH-90000
            assertThatThrownBy(() -> faces.add(FacialRecognitionService.FaceDetection.of( // GH-90000
                    new FacialRecognitionService.BoundingBox(0, 0, 1, 1), 0.5, new float[0]))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("filters detections below configured confidence threshold")
        void detect_filtersByConfiguredThreshold() { // GH-90000
            FacialRecognitionService service = buildService(0.99, true, // GH-90000
                    FacialRecognitionService.FacialRecognitionAuditSink.noop()); // GH-90000

            List<FacialRecognitionService.FaceDetection> faces = service.detect(new byte[]{1, 2, 3}); // GH-90000

            assertThat(faces).isEmpty(); // GH-90000
        }
    }

    // ─── identify() ─────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("identify()")
    class Identify {

        private static final float[] QUERY = new float[]{0.8f, 0.6f, 0.0f};
        private static final float[] ENROLLED_MATCH = new float[]{0.8f, 0.6f, 0.0f}; // identical
        private static final float[] ENROLLED_NO_MATCH = new float[]{-0.8f, -0.6f, 0.0f}; // opposite

        @Test
        @DisplayName("identical embeddings → identity matched")
        void identify_identical_matchFound() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            Optional<FacialRecognitionService.IdentityMatch> match =
                    service.identify(QUERY, Map.of("person-1", ENROLLED_MATCH)); // GH-90000

            assertThat(match).isPresent(); // GH-90000
            assertThat(match.get().identityId()).isEqualTo("person-1");
            assertThat(match.get().similarity()).isGreaterThan(0.98); // GH-90000
        }

        @Test
        @DisplayName("opposite embeddings → no match")
        void identify_opposite_noMatch() { // GH-90000
            FacialRecognitionService service = FacialRecognitionService.of(STUB_MODEL, 0.85); // GH-90000
            Optional<FacialRecognitionService.IdentityMatch> match =
                    service.identify(QUERY, Map.of("person-2", ENROLLED_NO_MATCH)); // GH-90000

            assertThat(match).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("empty enrolled map → no match")
        void identify_emptyEnrolled_noMatch() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            Optional<FacialRecognitionService.IdentityMatch> match =
                    service.identify(QUERY, Map.of()); // GH-90000
            assertThat(match).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null faceEmbedding throws NullPointerException")
        void identify_nullEmbedding_throwsNPE() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.identify(null, Map.of())); // GH-90000
        }

        @Test
        @DisplayName("null enrolledIdentities throws NullPointerException")
        void identify_nullEnrolled_throwsNPE() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.identify(QUERY, null)); // GH-90000
        }

        @Test
        @DisplayName("best match among multiple enrolled identities is returned")
        void identify_multipleEnrolled_bestMatch() { // GH-90000
            FacialRecognitionService service = buildService(); // GH-90000
            Optional<FacialRecognitionService.IdentityMatch> match = service.identify( // GH-90000
                    QUERY,
                    Map.of( // GH-90000
                            "person-near", new float[]{0.79f, 0.59f, 0.01f},
                            "person-exact", ENROLLED_MATCH,
                            "person-far", ENROLLED_NO_MATCH));
            assertThat(match).isPresent(); // GH-90000
            assertThat(match.get().identityId()).isEqualTo("person-exact");
        }

        @Test
        @DisplayName("identify denied when recognition feature is disabled")
        void identify_disabledFeature_returnsEmptyAndAudits() { // GH-90000
            AtomicReference<FacialRecognitionService.FacialRecognitionAuditEvent> auditEvent = new AtomicReference<>(); // GH-90000
            FacialRecognitionService service = buildService(0.5, false, auditEvent::set); // GH-90000

            Optional<FacialRecognitionService.IdentityMatch> match = service.identify( // GH-90000
                    QUERY,
                    Map.of("person-1", ENROLLED_MATCH), // GH-90000
                    true,
                    "actor-1");

            assertThat(match).isEmpty(); // GH-90000
            assertThat(auditEvent.get()).isNotNull(); // GH-90000
            assertThat(auditEvent.get().outcome()).isEqualTo("denied");
            assertThat(auditEvent.get().reason()).isEqualTo("feature_disabled");
        }

        @Test
        @DisplayName("identify denied when consent is missing")
        void identify_missingConsent_returnsEmptyAndAudits() { // GH-90000
            AtomicReference<FacialRecognitionService.FacialRecognitionAuditEvent> auditEvent = new AtomicReference<>(); // GH-90000
            FacialRecognitionService service = buildService(0.5, true, auditEvent::set); // GH-90000

            Optional<FacialRecognitionService.IdentityMatch> match = service.identify( // GH-90000
                    QUERY,
                    Map.of("person-1", ENROLLED_MATCH), // GH-90000
                    false,
                    "actor-2");

            assertThat(match).isEmpty(); // GH-90000
            assertThat(auditEvent.get()).isNotNull(); // GH-90000
            assertThat(auditEvent.get().outcome()).isEqualTo("denied");
            assertThat(auditEvent.get().reason()).isEqualTo("consent_missing");
        }

        @Test
        @DisplayName("identify emits success audit for accepted match")
        void identify_successMatch_emitsAudit() { // GH-90000
            AtomicReference<FacialRecognitionService.FacialRecognitionAuditEvent> auditEvent = new AtomicReference<>(); // GH-90000
            FacialRecognitionService service = buildService(0.5, true, auditEvent::set); // GH-90000

            Optional<FacialRecognitionService.IdentityMatch> match = service.identify( // GH-90000
                    QUERY,
                    Map.of("person-1", ENROLLED_MATCH), // GH-90000
                    true,
                    "actor-3");

            assertThat(match).isPresent(); // GH-90000
            assertThat(auditEvent.get()).isNotNull(); // GH-90000
            assertThat(auditEvent.get().outcome()).isEqualTo("success");
            assertThat(auditEvent.get().actorId()).isEqualTo("actor-3");
            assertThat(auditEvent.get().identityId()).isEqualTo("person-1");
        }
    }

    // ─── FaceDetection record validation ─────────────────────────────────────

    @Test
    @DisplayName("FaceDetection: confidence > 1 throws IllegalArgumentException")
    void faceDetection_invalidConfidence_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> FacialRecognitionService.FaceDetection.of( // GH-90000
                        new FacialRecognitionService.BoundingBox(0, 0, 1, 1), 1.1, new float[0])); // GH-90000
    }

    @Test
    @DisplayName("FaceDetection.of: generates unique IDs for each call")
    void faceDetection_of_uniqueIds() { // GH-90000
        var d1 = FacialRecognitionService.FaceDetection.of( // GH-90000
                new FacialRecognitionService.BoundingBox(0, 0, 1, 1), 0.9, new float[1]); // GH-90000
        var d2 = FacialRecognitionService.FaceDetection.of( // GH-90000
                new FacialRecognitionService.BoundingBox(0, 0, 1, 1), 0.9, new float[1]); // GH-90000
        assertThat(d1.faceId()).isNotEqualTo(d2.faceId()); // GH-90000
    }
}
