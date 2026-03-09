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
        void identicalVectors() {
            float[] v = {1.0f, 2.0f, 3.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("orthogonal vectors return 0.0")
        void orthogonalVectors() {
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v1, v2)).isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("opposite vectors return -1.0")
        void oppositeVectors() {
            float[] v1 = {1.0f, 2.0f, 3.0f};
            float[] v2 = {-1.0f, -2.0f, -3.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v1, v2)).isCloseTo(-1.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("zero vector returns 0.0")
        void zeroVector() {
            float[] v1 = {1.0f, 2.0f};
            float[] zero = {0.0f, 0.0f};
            assertThat(SimilarityCalculator.cosineSimilarity(v1, zero)).isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("different length vectors throw IllegalArgumentException")
        void differentLengths() {
            float[] v1 = {1.0f, 2.0f};
            float[] v2 = {1.0f, 2.0f, 3.0f};
            assertThatThrownBy(() -> SimilarityCalculator.cosineSimilarity(v1, v2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same length");
        }

        @Test
        @DisplayName("null vector1 throws IllegalArgumentException")
        void nullVector1() {
            assertThatThrownBy(() -> SimilarityCalculator.cosineSimilarity(null, new float[]{1.0f}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("empty vector1 throws IllegalArgumentException")
        void emptyVector1() {
            assertThatThrownBy(() -> SimilarityCalculator.cosineSimilarity(new float[]{}, new float[]{1.0f}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- normalizedCosineSimilarity ----

    @Nested
    @DisplayName("normalizedCosineSimilarity")
    class NormalizedCosineSimilarity {

        @Test
        @DisplayName("identical vectors return 1.0")
        void identicalVectors() {
            float[] v = {3.0f, 4.0f};
            assertThat(SimilarityCalculator.normalizedCosineSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("orthogonal vectors return 0.5")
        void orthogonalVectors() {
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f};
            assertThat(SimilarityCalculator.normalizedCosineSimilarity(v1, v2)).isCloseTo(0.5, within(TOLERANCE));
        }

        @Test
        @DisplayName("opposite vectors return 0.0")
        void oppositeVectors() {
            float[] v1 = {1.0f, 0.0f};
            float[] v2 = {-1.0f, 0.0f};
            assertThat(SimilarityCalculator.normalizedCosineSimilarity(v1, v2)).isCloseTo(0.0, within(TOLERANCE));
        }
    }

    // ---- euclideanDistance ----

    @Nested
    @DisplayName("euclideanDistance")
    class EuclideanDistance {

        @Test
        @DisplayName("same point returns 0.0")
        void samePoint() {
            float[] v = {5.0f, 5.0f, 5.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v, v)).isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("known distance: (0,0) to (3,4) = 5.0")
        void knownDistance() {
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {3.0f, 4.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v1, v2)).isCloseTo(5.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("distance is always non-negative")
        void alwaysNonNegative() {
            float[] v1 = {10.0f, -3.0f, 7.0f};
            float[] v2 = {-2.0f, 5.0f, 1.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v1, v2)).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("null vector throws IllegalArgumentException")
        void nullVector() {
            assertThatThrownBy(() -> SimilarityCalculator.euclideanDistance(null, new float[]{1.0f}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- euclideanSimilarity ----

    @Nested
    @DisplayName("euclideanSimilarity")
    class EuclideanSimilarity {

        @Test
        @DisplayName("same point returns 1.0")
        void samePoint() {
            float[] v = {1.0f, 2.0f};
            assertThat(SimilarityCalculator.euclideanSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("distant points return value closer to 0")
        void distantPoints() {
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {100.0f, 100.0f};
            double similarity = SimilarityCalculator.euclideanSimilarity(v1, v2);
            assertThat(similarity).isBetween(0.0, 1.0);
            assertThat(similarity).isLessThan(0.1);
        }

        @Test
        @DisplayName("result is always between 0 and 1")
        void resultBounded() {
            float[] v1 = {1.0f, 2.0f, 3.0f};
            float[] v2 = {4.0f, 5.0f, 6.0f};
            double similarity = SimilarityCalculator.euclideanSimilarity(v1, v2);
            assertThat(similarity).isBetween(0.0, 1.0);
        }
    }

    // ---- dotProduct ----

    @Nested
    @DisplayName("dotProduct")
    class DotProduct {

        @Test
        @DisplayName("known vectors: [1,2,3]·[4,5,6] = 32")
        void knownDotProduct() {
            float[] v1 = {1.0f, 2.0f, 3.0f};
            float[] v2 = {4.0f, 5.0f, 6.0f};
            // 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32
            assertThat(SimilarityCalculator.dotProduct(v1, v2)).isCloseTo(32.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("orthogonal vectors have zero dot product")
        void orthogonalVectors() {
            float[] v1 = {1.0f, 0.0f, 0.0f};
            float[] v2 = {0.0f, 1.0f, 0.0f};
            assertThat(SimilarityCalculator.dotProduct(v1, v2)).isCloseTo(0.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("different length vectors throw IllegalArgumentException")
        void differentLengths() {
            assertThatThrownBy(() -> SimilarityCalculator.dotProduct(new float[]{1.0f}, new float[]{1.0f, 2.0f}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- magnitude ----

    @Nested
    @DisplayName("magnitude")
    class Magnitude {

        @Test
        @DisplayName("known vector: |[3,4]| = 5.0")
        void knownMagnitude() {
            float[] v = {3.0f, 4.0f};
            assertThat(SimilarityCalculator.magnitude(v)).isCloseTo(5.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("unit vector has magnitude 1.0")
        void unitVector() {
            float[] v = {1.0f, 0.0f, 0.0f};
            assertThat(SimilarityCalculator.magnitude(v)).isCloseTo(1.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("null vector throws IllegalArgumentException")
        void nullVector() {
            assertThatThrownBy(() -> SimilarityCalculator.magnitude(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("empty vector throws IllegalArgumentException")
        void emptyVector() {
            assertThatThrownBy(() -> SimilarityCalculator.magnitude(new float[]{}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- normalize ----

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @Test
        @DisplayName("normalized vector has magnitude 1.0")
        void normalizedHasUnitMagnitude() {
            float[] v = {3.0f, 4.0f};
            float[] normalized = SimilarityCalculator.normalize(v);
            assertThat(SimilarityCalculator.magnitude(normalized)).isCloseTo(1.0, within(TOLERANCE));
        }

        @Test
        @DisplayName("normalized [3,4] = [0.6, 0.8]")
        void knownNormalization() {
            float[] v = {3.0f, 4.0f};
            float[] normalized = SimilarityCalculator.normalize(v);
            assertThat((double) normalized[0]).isCloseTo(0.6, within(TOLERANCE));
            assertThat((double) normalized[1]).isCloseTo(0.8, within(TOLERANCE));
        }

        @Test
        @DisplayName("does not modify original vector")
        void doesNotMutateOriginal() {
            float[] v = {3.0f, 4.0f};
            SimilarityCalculator.normalize(v);
            assertThat(v[0]).isEqualTo(3.0f);
            assertThat(v[1]).isEqualTo(4.0f);
        }

        @Test
        @DisplayName("zero vector throws IllegalArgumentException")
        void zeroVector_throws() {
            assertThatThrownBy(() -> SimilarityCalculator.normalize(new float[]{0.0f, 0.0f}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zero magnitude");
        }

        @Test
        @DisplayName("null vector throws IllegalArgumentException")
        void nullVector_throws() {
            assertThatThrownBy(() -> SimilarityCalculator.normalize(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- Cross-method consistency ----

    @Nested
    @DisplayName("Cross-method consistency")
    class ConsistencyTests {

        @Test
        @DisplayName("dot product of normalized vectors equals cosine similarity")
        void dotProductOfNormalizedEqualsCosineSimilarity() {
            float[] v1 = {1.0f, 3.0f, 5.0f};
            float[] v2 = {2.0f, 4.0f, 6.0f};

            float[] n1 = SimilarityCalculator.normalize(v1);
            float[] n2 = SimilarityCalculator.normalize(v2);

            double dotOfNormalized = SimilarityCalculator.dotProduct(n1, n2);
            double cosineSim = SimilarityCalculator.cosineSimilarity(v1, v2);

            assertThat(dotOfNormalized).isCloseTo(cosineSim, within(1e-5));
        }

        @Test
        @DisplayName("euclidean distance zero implies euclidean similarity one")
        void zeroDistanceImpliesMaxSimilarity() {
            float[] v = {7.0f, 8.0f, 9.0f};
            assertThat(SimilarityCalculator.euclideanDistance(v, v)).isCloseTo(0.0, within(TOLERANCE));
            assertThat(SimilarityCalculator.euclideanSimilarity(v, v)).isCloseTo(1.0, within(TOLERANCE));
        }
    }
}
