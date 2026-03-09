package com.ghatana.ai.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EmbeddingResult}.
 *
 * Covers construction, null validation, defensive vector cloning,
 * convenience methods, factories, and equality.
 */
@DisplayName("EmbeddingResult")
class EmbeddingResultTest {

    private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f, 0.4f};

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("creates with text, vector, and model")
        void createsWithAllFields() {
            EmbeddingResult r = new EmbeddingResult("hello world", SAMPLE_VECTOR, "text-embedding-ada-002");
            assertThat(r.getText()).isEqualTo("hello world");
            assertThat(r.getModel()).isEqualTo("text-embedding-ada-002");
            assertThat(r.getVector()).isEqualTo(SAMPLE_VECTOR);
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null text throws NullPointerException")
        void nullText() {
            assertThatThrownBy(() -> new EmbeddingResult(null, SAMPLE_VECTOR, "model"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("text");
        }

        @Test
        @DisplayName("null vector throws NullPointerException")
        void nullVector() {
            assertThatThrownBy(() -> new EmbeddingResult("text", null, "model"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("vector");
        }

        @Test
        @DisplayName("null model throws NullPointerException")
        void nullModel() {
            assertThatThrownBy(() -> new EmbeddingResult("text", SAMPLE_VECTOR, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("model");
        }
    }

    @Nested
    @DisplayName("defensive vector cloning")
    class VectorCloning {

        @Test
        @DisplayName("constructor clones input vector")
        void constructorClones() {
            float[] original = {1.0f, 2.0f};
            EmbeddingResult r = new EmbeddingResult("text", original, "model");
            original[0] = 99.0f;
            assertThat(r.getVector()[0]).isEqualTo(1.0f);
        }

        @Test
        @DisplayName("getVector returns a clone")
        void getVectorClones() {
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model");
            float[] v1 = r.getVector();
            float[] v2 = r.getVector();
            assertThat(v1).isNotSameAs(v2);
            assertThat(v1).isEqualTo(v2);
        }

        @Test
        @DisplayName("embedding() returns a clone (alias for getVector)")
        void embeddingClones() {
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model");
            float[] e1 = r.embedding();
            float[] e2 = r.embedding();
            assertThat(e1).isNotSameAs(e2);
            assertThat(e1).isEqualTo(SAMPLE_VECTOR);
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of(vector) creates result with empty text and 'unknown' model")
        void ofFactory() {
            float[] vec = {0.5f, 0.6f};
            EmbeddingResult r = EmbeddingResult.of(vec);
            assertThat(r.getText()).isEmpty();
            assertThat(r.getModel()).isEqualTo("unknown");
            assertThat(r.getVector()).isEqualTo(vec);
        }

        @Test
        @DisplayName("fromOpenAI throws UnsupportedOperationException")
        void fromOpenAIThrows() {
            assertThatThrownBy(() -> EmbeddingResult.fromOpenAI(new Object(), "text", "model"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("fromOpenAI");
        }
    }

    @Nested
    @DisplayName("convenience methods")
    class ConvenienceMethods {

        @Test
        @DisplayName("getModelName is alias for getModel")
        void getModelNameAlias() {
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "ada-002");
            assertThat(r.getModelName()).isEqualTo(r.getModel());
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal results are equal")
        void equalResults() {
            float[] v = {1.0f, 2.0f};
            EmbeddingResult r1 = new EmbeddingResult("hello", v, "model-a");
            EmbeddingResult r2 = new EmbeddingResult("hello", v, "model-a");
            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("different text not equal")
        void differentText() {
            EmbeddingResult r1 = new EmbeddingResult("hello", SAMPLE_VECTOR, "model");
            EmbeddingResult r2 = new EmbeddingResult("world", SAMPLE_VECTOR, "model");
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("different model not equal")
        void differentModel() {
            EmbeddingResult r1 = new EmbeddingResult("text", SAMPLE_VECTOR, "model-a");
            EmbeddingResult r2 = new EmbeddingResult("text", SAMPLE_VECTOR, "model-b");
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("different vector not equal")
        void differentVector() {
            EmbeddingResult r1 = new EmbeddingResult("text", new float[]{1.0f}, "model");
            EmbeddingResult r2 = new EmbeddingResult("text", new float[]{2.0f}, "model");
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("equal to itself")
        void equalToItself() {
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model");
            assertThat(r).isEqualTo(r);
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualToNull() {
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model");
            assertThat(r).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains text and model, hides vector")
        void toStringContent() {
            EmbeddingResult r = new EmbeddingResult("hello", SAMPLE_VECTOR, "ada-002");
            String str = r.toString();
            assertThat(str).contains("hello").contains("ada-002").contains("[...]");
        }
    }
}
