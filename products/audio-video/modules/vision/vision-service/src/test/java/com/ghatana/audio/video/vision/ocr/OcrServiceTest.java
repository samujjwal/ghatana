package com.ghatana.audio.video.vision.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OcrService} — AV-009.3.
 *
 * @doc.type class
 * @doc.purpose OCR accuracy and structural output tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OcrService")
class OcrServiceTest {

    private static final OcrService.BoundingBox BOX = new OcrService.BoundingBox(0.0, 0.0, 1.0, 0.1); // GH-90000

    /** Stub model: returns two regions — one high confidence, one low confidence. */
    private static final OcrService.OcrModel STUB_MODEL = imageBytes -> List.of( // GH-90000
            new OcrService.TextRegion("Hello World", BOX, 0.97, 0), // GH-90000
            new OcrService.TextRegion("Low confidence", BOX, 0.50, 1) // GH-90000
    );

    // ─── of() ───────────────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("of(null) throws NullPointerException")
    void of_null_throwsNPE() { // GH-90000
        assertThatNullPointerException().isThrownBy(() -> OcrService.of(null)); // GH-90000
    }

    @Test
    @DisplayName("of(model, threshold > 1) throws IllegalArgumentException")
    void of_invalidThreshold_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> OcrService.of(STUB_MODEL, 1.5)); // GH-90000
    }

    // ─── extract() ──────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("extract()")
    class Extract {

        @Test
        @DisplayName("returns only regions above the confidence threshold")
        void extract_filtersLowConfidence() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL, 0.70); // GH-90000
            List<OcrService.TextRegion> regions = service.extract(new byte[]{1, 2, 3}); // GH-90000
            assertThat(regions).hasSize(1); // GH-90000
            assertThat(regions.get(0).text()).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("default threshold of 0.70 filters low-confidence region")
        void extract_defaultThreshold_filters() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL); // GH-90000
            List<OcrService.TextRegion> regions = service.extract(new byte[]{1}); // GH-90000
            assertThat(regions).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("threshold of 0.0 includes all regions")
        void extract_zeroThreshold_includesAll() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL, 0.0); // GH-90000
            List<OcrService.TextRegion> regions = service.extract(new byte[]{1}); // GH-90000
            assertThat(regions).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("null imageBytes throws NullPointerException")
        void extract_null_throwsNPE() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> service.extract(null)); // GH-90000
        }

        @Test
        @DisplayName("empty imageBytes throws IllegalArgumentException")
        void extract_empty_throwsIAE() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.extract(new byte[0])); // GH-90000
        }

        @Test
        @DisplayName("returned list is unmodifiable")
        void extract_unmodifiable() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL); // GH-90000
            var regions = service.extract(new byte[]{1}); // GH-90000
            assertThatThrownBy(() -> regions.add( // GH-90000
                    new OcrService.TextRegion("x", BOX, 0.99, 0))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─── extractText() ──────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("extractText()")
    class ExtractText {

        @Test
        @DisplayName("concatenates recognised text regions with newlines")
        void extractText_concatenatesRegions() { // GH-90000
            OcrService service = OcrService.of(STUB_MODEL, 0.0); // include all // GH-90000
            String text = service.extractText(new byte[]{1, 2}); // GH-90000
            assertThat(text).contains("Hello World").contains("Low confidence");
        }

        @Test
        @DisplayName("returns empty string for image with no regions above threshold")
        void extractText_noneAboveThreshold_empty() { // GH-90000
            OcrService noMatch = OcrService.of(STUB_MODEL, 0.99); // GH-90000
            String text = noMatch.extractText(new byte[]{1}); // GH-90000
            assertThat(text).isEmpty(); // GH-90000
        }
    }

    // ─── TextRegion validation ────────────────────────────────────────────────

    @Nested
    @DisplayName("TextRegion")
    class TextRegionTests {

        @Test
        @DisplayName("null text throws NullPointerException")
        void textRegion_nullText_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new OcrService.TextRegion(null, BOX, 0.9, 0)); // GH-90000
        }

        @Test
        @DisplayName("confidence > 1 throws IllegalArgumentException")
        void textRegion_invalidConfidence_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new OcrService.TextRegion("t", BOX, 1.1, 0)); // GH-90000
        }

        @Test
        @DisplayName("readingOrder < 0 throws IllegalArgumentException")
        void textRegion_negativeReadingOrder_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new OcrService.TextRegion("t", BOX, 0.9, -1)); // GH-90000
        }
    }
}
