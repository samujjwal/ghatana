package com.ghatana.ai.vectorstore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link SimilarityCalculator}.
 *
 * Pure math utility — all methods are static and deterministic.
 */
@DisplayName("SimilarityCalculator")
class SimilarityCalculatorTest {

    private static final double TOLERANCE = 1e-6;

    // ---- cosineSimilarity ----

    @Nested
    @DisplayName("cosineSimilarity")
    class CosineSimilarity {

        @Test
        @DisplayName("identical vectors return 1.0")
        void identicalVectors() { // GH-90000
            float[] v = {1.0f, 2.0f, 3.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("orthogonal vectors return 0.0")
        void orthogonalVectors() { // GH-90000
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v1, v2)).isCloseTo(0.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("opposite vectors return -1.0")
        void oppositeVectors() { // GH-90000
            float[] v1 = {1.0f, 2.0f, 3.0f};
            float[] v2 = {-1.0f, -2.0f, -3.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v1, v2)).isCloseTo(-1.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("zero vector returns 0.0")
        void zeroVector() { // GH-90000
            float[] v1 = {1.0f, 2.0f};
            float[] zero = {0.0f, 0.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v1, zero)).isCloseTo(0.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("different length vectors throw IllegalArgumentException")
        void differentLengths() { // GH-90000
            float[] v1 = {1.0f, 2.0f};
            float[] v2 = {1.0f, 2.0f, 3.0f};
            assertThatThrownBy(() -> SimilarityCalculator.cosineSimilarity(v1, v2)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("same length");
        }

        @Test
        @DisplayName("null vector1 throws IllegalArgumentException")
        void nullVector1() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.cosineSimilarity(null, new float[]{1.0f})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("empty vector1 throws IllegalArgumentException")
        void emptyVector1() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.cosineSimilarity(new float[]{}, new float[]{1.0f})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- normalizedCosineSimilarity ----

    @Nested
    @DisplayName("normalizedCosineSimilarity")
    class NormalizedCosineSimilarity {

        @Test
        @DisplayName("identical vectors return 1.0")
        void identicalVectors() { // GH-90000
            float[] v = {3.0f, 4.0f};
            assertThat(SimilarityCalculator.normalizedCosineSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("orthogonal vectors return 0.5")
        void orthogonalVectors() { // GH-90000
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f};
            assertThat(SimilarityCalculator.normalizedCosineSimilarity(v1, v2)).isCloseTo(0.5, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("opposite vectors return 0.0")
        void oppositeVectors() { // GH-90000
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {-1.0f, 0.0f};
            assertThat(SimilarityCalculator.normalizedCosineSimilarity(v1, v2)).isCloseTo(0.0, within(TOLERANCE)); // GH-90000
        }
    }

    // ---- euclideanDistance ----

    @Nested
    @DisplayName("euclideanDistance")
    class EuclideanDistance {

        @Test
        @DisplayName("same point returns 0.0")
        void samePoint() { // GH-90000
            float[] v = {5.0f, 5.0f, 5.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v, v)).isCloseTo(0.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("known distance: (0,0) to (3,4) = 5.0")
        void knownDistance() { // GH-90000
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {3.0f, 4.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v1, v2)).isCloseTo(5.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("distance is always non-negative")
        void alwaysNonNegative() { // GH-90000
            float[] v1 = {10.0f, -3.0f, 7.0f};
            float[] v2 = {-2.0f, 5.0f, 1.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v1, v2)).isGreaterThanOrEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("null vector throws IllegalArgumentException")
        void nullVector() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.euclideanDistance(null, new float[]{1.0f})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- euclideanSimilarity ----

    @Nested
    @DisplayName("euclideanSimilarity")
    class EuclideanSimilarity {

        @Test
        @DisplayName("same point returns 1.0")
        void samePoint() { // GH-90000
            float[] v = {1.0f, 2.0f};
            assertThat(SimilarityCalculator.euclideanSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("distant points return value closer to 0")
        void distantPoints() { // GH-90000
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {100.0f, 100.0f};
            double similarity = SimilarityCalculator.euclideanSimilarity(v1, v2); // GH-90000
            assertThat(similarity).isBetween(0.0, 1.0); // GH-90000
            assertThat(similarity).isLessThan(0.1); // GH-90000
        }

        @Test
        @DisplayName("result is always between 0 and 1")
        void resultBounded() { // GH-90000
            float[] v1 = {1.0f, 2.0f, 3.0f};
            float[] v2 = {4.0f, 5.0f, 6.0f};
            double similarity = SimilarityCalculator.euclideanSimilarity(v1, v2); // GH-90000
            assertThat(similarity).isBetween(0.0, 1.0); // GH-90000
        }
    }

    // ---- dotProduct ----

    @Nested
    @DisplayName("dotProduct")
    class DotProduct {

        @Test
        @DisplayName("known vectors: [1,2,3]·[4,5,6] = 32")
        void knownDotProduct() { // GH-90000
            float[] v1 = {1.0f, 2.0f, 3.0f};
            float[] v2 = {4.0f, 5.0f, 6.0f};
            // 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32
            assertThat(SimilarityCalculator.dotProduct(v1, v2)).isCloseTo(32.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("orthogonal vectors have zero dot product")
        void orthogonalVectors() { // GH-90000
            float[] v1 = {1.0f, 0.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f, 0.0f};
            assertThat(SimilarityCalculator.dotProduct(v1, v2)).isCloseTo(0.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("different length vectors throw IllegalArgumentException")
        void differentLengths() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.dotProduct(new float[]{1.0f}, new float[]{1.0f, 2.0f})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- magnitude ----

    @Nested
    @DisplayName("magnitude")
    class Magnitude {

        @Test
        @DisplayName("known vector: |[3,4]| = 5.0")
        void knownMagnitude() { // GH-90000
            float[] v = {3.0f, 4.0f};
            assertThat(SimilarityCalculator.magnitude(v)).isCloseTo(5.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("unit vector has magnitude 1.0")
        void unitVector() { // GH-90000
            float[] v = {1.0f, 0.0f, 0.0f};
            assertThat(SimilarityCalculator.magnitude(v)).isCloseTo(1.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("null vector throws IllegalArgumentException")
        void nullVector() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.magnitude(null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("empty vector throws IllegalArgumentException")
        void emptyVector() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.magnitude(new float[]{})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- normalize ----

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @Test
        @DisplayName("normalized vector has magnitude 1.0")
        void normalizedHasUnitMagnitude() { // GH-90000
            float[] v = {3.0f, 4.0f};
            float[] normalized = SimilarityCalculator.normalize(v); // GH-90000
            assertThat(SimilarityCalculator.magnitude(normalized)).isCloseTo(1.0, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("normalized [3,4] = [0.6, 0.8]")
        void knownNormalization() { // GH-90000
            float[] v = {3.0f, 4.0f};
            float[] normalized = SimilarityCalculator.normalize(v); // GH-90000
            assertThat((double) normalized[0]).isCloseTo(0.6, within(TOLERANCE)); // GH-90000
            assertThat((double) normalized[1]).isCloseTo(0.8, within(TOLERANCE)); // GH-90000
        }

        @Test
        @DisplayName("does not modify original vector")
        void doesNotMutateOriginal() { // GH-90000
            float[] v = {3.0f, 4.0f};
            SimilarityCalculator.normalize(v); // GH-90000
            assertThat(v[0]).isEqualTo(3.0f); // GH-90000
            assertThat(v[1]).isEqualTo(4.0f); // GH-90000
        }

        @Test
        @DisplayName("zero vector throws IllegalArgumentException")
        void zeroVector_throws() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.normalize(new float[]{0.0f, 0.0f})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("zero magnitude");
        }

        @Test
        @DisplayName("null vector throws IllegalArgumentException")
        void nullVector_throws() { // GH-90000
            assertThatThrownBy(() -> SimilarityCalculator.normalize(null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- Cross-method consistency ----

    @Nested
    @DisplayName("Cross-method consistency")
    class ConsistencyTests {

        @Test
        @DisplayName("dot product of normalized vectors equals cosine similarity")
        void dotProductOfNormalizedEqualsCosineSimilarity() { // GH-90000
            float[] v1 = {1.0f, 3.0f, 5.0f};
            float[] v2 = {2.0f, 4.0f, 6.0f};

            float[] n1 = SimilarityCalculator.normalize(v1); // GH-90000
            float[] n2 = SimilarityCalculator.normalize(v2); // GH-90000

            double dotOfNormalized = SimilarityCalculator.dotProduct(n1, n2); // GH-90000
            double cosineSim = SimilarityCalculator.cosineSimilarity(v1, v2); // GH-90000

            assertThat(dotOfNormalized).isCloseTo(cosineSim, within(1e-5)); // GH-90000
        }

        @Test
        @DisplayName("euclidean distance zero implies euclidean similarity one")
        void zeroDistanceImpliesMaxSimilarity() { // GH-90000
            float[] v = {7.0f, 8.0f, 9.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v, v)).isCloseTo(0.0, within(TOLERANCE)); // GH-90000
            assertThat(SimilarityCalculator.euclideanSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE)); // GH-90000
        }
    }
}
