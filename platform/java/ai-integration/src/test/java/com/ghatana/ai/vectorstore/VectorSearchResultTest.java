package com.ghatana.ai.vectorstore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link VectorSearchResult}.
 *
 * Covers construction, validation, defensive vector cloning, metadata,
 * equality, and edge cases.
 */
@DisplayName("VectorSearchResult")
class VectorSearchResultTest {

    private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f};

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("creates with all fields")
        void createsWithAllFields() {
            VectorSearchResult r = new VectorSearchResult(
                    "id-1", "some content", SAMPLE_VECTOR, 0.95, 1,
                    Map.of("source", "test"));
            assertThat(r.getId()).isEqualTo("id-1");
            assertThat(r.getContent()).isEqualTo("some content");
            assertThat(r.getSimilarity()).isEqualTo(0.95);
            assertThat(r.getRank()).isEqualTo(1);
            assertThat(r.getMetadata()).containsEntry("source", "test");
        }

        @Test
        @DisplayName("creates with backward-compatible constructor (no metadata)")
        void backwardCompatibleConstructor() {
            VectorSearchResult r = new VectorSearchResult("id-1", "content", SAMPLE_VECTOR, 0.8, 0);
            assertThat(r.getMetadata()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("null metadata defaults to empty map")
        void nullMetadataDefaults() {
            VectorSearchResult r = new VectorSearchResult("id-1", "content", SAMPLE_VECTOR, 0.5, 0, null);
            assertThat(r.getMetadata()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null id throws NullPointerException")
        void nullId() {
            assertThatThrownBy(() -> new VectorSearchResult(null, "content", SAMPLE_VECTOR, 0.5, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("null content throws NullPointerException")
        void nullContent() {
            assertThatThrownBy(() -> new VectorSearchResult("id", null, SAMPLE_VECTOR, 0.5, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("content");
        }

        @Test
        @DisplayName("null vector throws NullPointerException")
        void nullVector() {
            assertThatThrownBy(() -> new VectorSearchResult("id", "content", null, 0.5, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("vector");
        }
    }

    @Nested
    @DisplayName("range validation")
    class RangeValidation {

        @Test
        @DisplayName("similarity below 0 throws IllegalArgumentException")
        void similarityBelowZero() {
            assertThatThrownBy(() -> new VectorSearchResult("id", "c", SAMPLE_VECTOR, -0.1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("similarity");
        }

        @Test
        @DisplayName("similarity above 1 throws IllegalArgumentException")
        void similarityAboveOne() {
            assertThatThrownBy(() -> new VectorSearchResult("id", "c", SAMPLE_VECTOR, 1.1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("similarity");
        }

        @Test
        @DisplayName("similarity at boundaries (0.0 and 1.0) is valid")
        void similarityBoundaries() {
            VectorSearchResult zero = new VectorSearchResult("id1", "c", SAMPLE_VECTOR, 0.0, 0);
            VectorSearchResult one = new VectorSearchResult("id2", "c", SAMPLE_VECTOR, 1.0, 0);
            assertThat(zero.getSimilarity()).isEqualTo(0.0);
            assertThat(one.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("negative rank throws IllegalArgumentException")
        void negativeRank() {
            assertThatThrownBy(() -> new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rank");
        }

        @Test
        @DisplayName("zero rank is valid")
        void zeroRank() {
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0);
            assertThat(r.getRank()).isZero();
        }
    }

    @Nested
    @DisplayName("defensive vector cloning")
    class VectorCloning {

        @Test
        @DisplayName("constructor clones input vector")
        void constructorClones() {
            float[] original = {1.0f, 2.0f};
            VectorSearchResult r = new VectorSearchResult("id", "c", original, 0.5, 0);
            original[0] = 99.0f;
            assertThat(r.getVector()[0]).isEqualTo(1.0f);
        }

        @Test
        @DisplayName("getVector returns a clone")
        void getVectorClones() {
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0);
            float[] v1 = r.getVector();
            float[] v2 = r.getVector();
            assertThat(v1).isNotSameAs(v2);
            assertThat(v1).isEqualTo(v2);
        }
    }

    @Nested
    @DisplayName("metadata immutability")
    class MetadataImmutability {

        @Test
        @DisplayName("metadata map is unmodifiable")
        void metadataUnmodifiable() {
            VectorSearchResult r = new VectorSearchResult(
                    "id", "c", SAMPLE_VECTOR, 0.5, 0, Map.of("k", "v"));
            assertThatThrownBy(() -> r.getMetadata().put("new", "val"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal results are equal")
        void equalResults() {
            float[] v = {1.0f, 2.0f};
            VectorSearchResult r1 = new VectorSearchResult("id", "content", v, 0.9, 1);
            VectorSearchResult r2 = new VectorSearchResult("id", "content", v, 0.9, 1);
            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("different id not equal")
        void differentId() {
            VectorSearchResult r1 = new VectorSearchResult("id1", "c", SAMPLE_VECTOR, 0.5, 0);
            VectorSearchResult r2 = new VectorSearchResult("id2", "c", SAMPLE_VECTOR, 0.5, 0);
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("different similarity not equal")
        void differentSimilarity() {
            VectorSearchResult r1 = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0);
            VectorSearchResult r2 = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.6, 0);
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("different vector not equal")
        void differentVector() {
            VectorSearchResult r1 = new VectorSearchResult("id", "c", new float[]{1.0f}, 0.5, 0);
            VectorSearchResult r2 = new VectorSearchResult("id", "c", new float[]{2.0f}, 0.5, 0);
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("equal to itself")
        void equalToItself() {
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0);
            assertThat(r).isEqualTo(r);
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualToNull() {
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0);
            assertThat(r).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains id, similarity, rank")
        void containsKeyInfo() {
            VectorSearchResult r = new VectorSearchResult("id-1", "hello", SAMPLE_VECTOR, 0.9512, 2);
            String str = r.toString();
            assertThat(str).contains("id-1").contains("0.9512").contains("rank=2");
        }
    }
}
