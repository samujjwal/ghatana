package com.ghatana.audio.video.vision.detection;

import com.ghatana.audio.video.vision.model.DetectionOptions;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the shared detector contract configuration.
 *
 * @doc.type test
 * @doc.purpose Verifies shared VisionDetector configuration is immutable and consistent
 * @doc.layer product
 */
@DisplayName("DetectionConfig Tests")
class DetectionConfigTest extends EventloopTestBase {

    @Test
    @DisplayName("Should preserve configured thresholds and target classes")
    void shouldPreserveConfiguredThresholdsAndTargetClasses() { // GH-90000
        DetectionOptions config = runPromise(() -> Promise.of( // GH-90000
            DetectionOptions.builder() // GH-90000
                .confidenceThreshold(0.65f) // GH-90000
                .nmsThreshold(0.25f) // GH-90000
                .maxDetections(12) // GH-90000
                .targetClasses(new java.util.HashSet<>(List.of("person", "dog"))) // GH-90000
                .build() // GH-90000
        ));

        assertEquals(0.65f, config.getConfidenceThreshold()); // GH-90000
        assertEquals(0.25f, config.getNmsThreshold()); // GH-90000
        assertEquals(12, config.getMaxDetections()); // GH-90000
        assertEquals(Set.of("person", "dog"), config.getTargetClasses()); // GH-90000
        assertThrows(UnsupportedOperationException.class, () -> config.getTargetClasses().add("car"));
    }
}
