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
@DisplayName("VectorSearchResult [GH-90000]")
class VectorSearchResultTest {

    private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f};

    @Nested
    @DisplayName("construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("creates with all fields [GH-90000]")
        void createsWithAllFields() { // GH-90000
            VectorSearchResult r = new VectorSearchResult( // GH-90000
                    "id-1", "some content", SAMPLE_VECTOR, 0.95, 1,
                    Map.of("source", "test")); // GH-90000
            assertThat(r.getId()).isEqualTo("id-1 [GH-90000]");
            assertThat(r.getContent()).isEqualTo("some content [GH-90000]");
            assertThat(r.getSimilarity()).isEqualTo(0.95); // GH-90000
            assertThat(r.getRank()).isEqualTo(1); // GH-90000
            assertThat(r.getMetadata()).containsEntry("source", "test"); // GH-90000
        }

        @Test
        @DisplayName("creates with backward-compatible constructor (no metadata) [GH-90000]")
        void backwardCompatibleConstructor() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id-1", "content", SAMPLE_VECTOR, 0.8, 0); // GH-90000
            assertThat(r.getMetadata()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null metadata defaults to empty map [GH-90000]")
        void nullMetadataDefaults() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id-1", "content", SAMPLE_VECTOR, 0.5, 0, null); // GH-90000
            assertThat(r.getMetadata()).isNotNull().isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation [GH-90000]")
    class NullValidation {

        @Test
        @DisplayName("null id throws NullPointerException [GH-90000]")
        void nullId() { // GH-90000
            assertThatThrownBy(() -> new VectorSearchResult(null, "content", SAMPLE_VECTOR, 0.5, 0)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("id [GH-90000]");
        }

        @Test
        @DisplayName("null content throws NullPointerException [GH-90000]")
        void nullContent() { // GH-90000
            assertThatThrownBy(() -> new VectorSearchResult("id", null, SAMPLE_VECTOR, 0.5, 0)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("content [GH-90000]");
        }

        @Test
        @DisplayName("null vector throws NullPointerException [GH-90000]")
        void nullVector() { // GH-90000
            assertThatThrownBy(() -> new VectorSearchResult("id", "content", null, 0.5, 0)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("vector [GH-90000]");
        }
    }

    @Nested
    @DisplayName("range validation [GH-90000]")
    class RangeValidation {

        @Test
        @DisplayName("similarity below 0 throws IllegalArgumentException [GH-90000]")
        void similarityBelowZero() { // GH-90000
            assertThatThrownBy(() -> new VectorSearchResult("id", "c", SAMPLE_VECTOR, -0.1, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("similarity [GH-90000]");
        }

        @Test
        @DisplayName("similarity above 1 throws IllegalArgumentException [GH-90000]")
        void similarityAboveOne() { // GH-90000
            assertThatThrownBy(() -> new VectorSearchResult("id", "c", SAMPLE_VECTOR, 1.1, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("similarity [GH-90000]");
        }

        @Test
        @DisplayName("similarity at boundaries (0.0 and 1.0) is valid [GH-90000]")
        void similarityBoundaries() { // GH-90000
            VectorSearchResult zero = new VectorSearchResult("id1", "c", SAMPLE_VECTOR, 0.0, 0); // GH-90000
            VectorSearchResult one = new VectorSearchResult("id2", "c", SAMPLE_VECTOR, 1.0, 0); // GH-90000
            assertThat(zero.getSimilarity()).isEqualTo(0.0); // GH-90000
            assertThat(one.getSimilarity()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("negative rank throws IllegalArgumentException [GH-90000]")
        void negativeRank() { // GH-90000
            assertThatThrownBy(() -> new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, -1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("rank [GH-90000]");
        }

        @Test
        @DisplayName("zero rank is valid [GH-90000]")
        void zeroRank() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            assertThat(r.getRank()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("defensive vector cloning [GH-90000]")
    class VectorCloning {

        @Test
        @DisplayName("constructor clones input vector [GH-90000]")
        void constructorClones() { // GH-90000
            float[] original = {1.0f, 2.0f};
            VectorSearchResult r = new VectorSearchResult("id", "c", original, 0.5, 0); // GH-90000
            original[0] = 99.0f;
            assertThat(r.getVector()[0]).isEqualTo(1.0f); // GH-90000
        }

        @Test
        @DisplayName("getVector returns a clone [GH-90000]")
        void getVectorClones() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            float[] v1 = r.getVector(); // GH-90000
            float[] v2 = r.getVector(); // GH-90000
            assertThat(v1).isNotSameAs(v2); // GH-90000
            assertThat(v1).isEqualTo(v2); // GH-90000
        }
    }

    @Nested
    @DisplayName("metadata immutability [GH-90000]")
    class MetadataImmutability {

        @Test
        @DisplayName("metadata map is unmodifiable [GH-90000]")
        void metadataUnmodifiable() { // GH-90000
            VectorSearchResult r = new VectorSearchResult( // GH-90000
                    "id", "c", SAMPLE_VECTOR, 0.5, 0, Map.of("k", "v")); // GH-90000
            assertThatThrownBy(() -> r.getMetadata().put("new", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("equality and hashCode [GH-90000]")
    class Equality {

        @Test
        @DisplayName("equal results are equal [GH-90000]")
        void equalResults() { // GH-90000
            float[] v = {1.0f, 2.0f};
            VectorSearchResult r1 = new VectorSearchResult("id", "content", v, 0.9, 1); // GH-90000
            VectorSearchResult r2 = new VectorSearchResult("id", "content", v, 0.9, 1); // GH-90000
            assertThat(r1).isEqualTo(r2); // GH-90000
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("different id not equal [GH-90000]")
        void differentId() { // GH-90000
            VectorSearchResult r1 = new VectorSearchResult("id1", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            VectorSearchResult r2 = new VectorSearchResult("id2", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("different similarity not equal [GH-90000]")
        void differentSimilarity() { // GH-90000
            VectorSearchResult r1 = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            VectorSearchResult r2 = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.6, 0); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("different vector not equal [GH-90000]")
        void differentVector() { // GH-90000
            VectorSearchResult r1 = new VectorSearchResult("id", "c", new float[]{1.0f}, 0.5, 0); // GH-90000
            VectorSearchResult r2 = new VectorSearchResult("id", "c", new float[]{2.0f}, 0.5, 0); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("equal to itself [GH-90000]")
        void equalToItself() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            assertThat(r).isEqualTo(r); // GH-90000
        }

        @Test
        @DisplayName("not equal to null [GH-90000]")
        void notEqualToNull() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id", "c", SAMPLE_VECTOR, 0.5, 0); // GH-90000
            assertThat(r).isNotEqualTo(null); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString [GH-90000]")
    class ToString {

        @Test
        @DisplayName("toString contains id, similarity, rank [GH-90000]")
        void containsKeyInfo() { // GH-90000
            VectorSearchResult r = new VectorSearchResult("id-1", "hello", SAMPLE_VECTOR, 0.9512, 2); // GH-90000
            String str = r.toString(); // GH-90000
            assertThat(str).contains("id-1 [GH-90000]").contains("0.9512 [GH-90000]").contains("rank=2 [GH-90000]");
        }
    }
}
