package com.ghatana.audio.video.vision.scene;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SceneUnderstandingService} — AV-009.2.
 *
 * @doc.type class
 * @doc.purpose Unit tests for scene classification and scene understanding
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SceneUnderstandingService [GH-90000]")
class SceneUnderstandingServiceTest {

    /** Stub model: returns outdoor/park at 0.92 and indoor/office at 0.45. */
    private static final SceneUnderstandingService.SceneClassificationModel STUB_MODEL =
            imageBytes -> List.of( // GH-90000
                    new SceneUnderstandingService.SceneLabel("outdoor/park", 0.92), // GH-90000
                    new SceneUnderstandingService.SceneLabel("indoor/office", 0.45) // GH-90000
            );

    // ─── of() ───────────────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("of(null) throws NullPointerException [GH-90000]")
    void of_null_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> SceneUnderstandingService.of(null)); // GH-90000
    }

    @Test
    @DisplayName("of(model, threshold > 1) throws IllegalArgumentException [GH-90000]")
    void of_invalidThreshold_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> SceneUnderstandingService.of(STUB_MODEL, 1.5)); // GH-90000
    }

    // ─── classify() ─────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("classify() [GH-90000]")
    class Classify {

        @Test
        @DisplayName("returns outdoor/park as top scene (0.92 > 0.60 default threshold) [GH-90000]")
        void classify_topScene_outdoorPark() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1, 2, 3}); // GH-90000

            assertThat(desc.hasTopScene()).isTrue(); // GH-90000
            assertThat(desc.topSceneLabel()).isEqualTo("outdoor/park [GH-90000]");
        }

        @Test
        @DisplayName("low-confidence indoor/office is filtered by default threshold [GH-90000]")
        void classify_indoorOffice_filtered() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1}); // GH-90000

            assertThat(desc.qualifyingScenes()).hasSize(1); // GH-90000
            assertThat(desc.qualifyingScenes().get(0).label()).isEqualTo("outdoor/park [GH-90000]");
        }

        @Test
        @DisplayName("threshold 0.0 includes all scene candidates [GH-90000]")
        void classify_zeroThreshold_includesAll() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL, 0.0); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1}); // GH-90000

            assertThat(desc.qualifyingScenes()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("imageSizeBytes matches the input array length [GH-90000]")
        void classify_imageSizeBytes_correct() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[42]); // GH-90000
            assertThat(desc.imageSizeBytes()).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("null imageBytes throws NullPointerException [GH-90000]")
        void classify_null_throwsNPE() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> service.classify(null)); // GH-90000
        }

        @Test
        @DisplayName("empty imageBytes throws IllegalArgumentException [GH-90000]")
        void classify_empty_throwsIAE() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.classify(new byte[0])); // GH-90000
        }

        @Test
        @DisplayName("model returning no labels above threshold still returns a description [GH-90000]")
        void classify_noLabelsAboveThreshold_hasNoTopScene() { // GH-90000
            SceneUnderstandingService.SceneClassificationModel noneModel =
                    bytes -> List.of(new SceneUnderstandingService.SceneLabel("x", 0.10)); // GH-90000

            SceneUnderstandingService service = SceneUnderstandingService.of(noneModel, 0.99); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1}); // GH-90000

            // No qualifying scenes but best candidate is used as top
            assertThat(desc.hasTopScene()).isTrue(); // GH-90000
            assertThat(desc.qualifyingScenes()).isEmpty(); // GH-90000
        }
    }

    // ─── SceneLabel validation ────────────────────────────────────────────────

    @Test
    @DisplayName("SceneLabel: null label throws NullPointerException [GH-90000]")
    void sceneLabel_nullLabel_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new SceneUnderstandingService.SceneLabel(null, 0.8)); // GH-90000
    }

    @Test
    @DisplayName("SceneLabel: confidence > 1 throws IllegalArgumentException [GH-90000]")
    void sceneLabel_invalidConfidence_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> new SceneUnderstandingService.SceneLabel("outdoor", 1.1)); // GH-90000
    }
}
