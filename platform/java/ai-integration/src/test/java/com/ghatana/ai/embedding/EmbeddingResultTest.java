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
@DisplayName("EmbeddingResult [GH-90000]")
class EmbeddingResultTest {

    private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f, 0.4f};

    @Nested
    @DisplayName("construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("creates with text, vector, and model [GH-90000]")
        void createsWithAllFields() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("hello world", SAMPLE_VECTOR, "text-embedding-ada-002"); // GH-90000
            assertThat(r.getText()).isEqualTo("hello world [GH-90000]");
            assertThat(r.getModel()).isEqualTo("text-embedding-ada-002 [GH-90000]");
            assertThat(r.getVector()).isEqualTo(SAMPLE_VECTOR); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation [GH-90000]")
    class NullValidation {

        @Test
        @DisplayName("null text throws NullPointerException [GH-90000]")
        void nullText() { // GH-90000
            assertThatThrownBy(() -> new EmbeddingResult(null, SAMPLE_VECTOR, "model")) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("text [GH-90000]");
        }

        @Test
        @DisplayName("null vector throws NullPointerException [GH-90000]")
        void nullVector() { // GH-90000
            assertThatThrownBy(() -> new EmbeddingResult("text", null, "model")) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("vector [GH-90000]");
        }

        @Test
        @DisplayName("null model throws NullPointerException [GH-90000]")
        void nullModel() { // GH-90000
            assertThatThrownBy(() -> new EmbeddingResult("text", SAMPLE_VECTOR, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("model [GH-90000]");
        }
    }

    @Nested
    @DisplayName("defensive vector cloning [GH-90000]")
    class VectorCloning {

        @Test
        @DisplayName("constructor clones input vector [GH-90000]")
        void constructorClones() { // GH-90000
            float[] original = {1.0f, 2.0f};
            EmbeddingResult r = new EmbeddingResult("text", original, "model"); // GH-90000
            original[0] = 99.0f;
            assertThat(r.getVector()[0]).isEqualTo(1.0f); // GH-90000
        }

        @Test
        @DisplayName("getVector returns a clone [GH-90000]")
        void getVectorClones() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model"); // GH-90000
            float[] v1 = r.getVector(); // GH-90000
            float[] v2 = r.getVector(); // GH-90000
            assertThat(v1).isNotSameAs(v2); // GH-90000
            assertThat(v1).isEqualTo(v2); // GH-90000
        }

        @Test
        @DisplayName("embedding() returns a clone (alias for getVector) [GH-90000]")
        void embeddingClones() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model"); // GH-90000
            float[] e1 = r.embedding(); // GH-90000
            float[] e2 = r.embedding(); // GH-90000
            assertThat(e1).isNotSameAs(e2); // GH-90000
            assertThat(e1).isEqualTo(SAMPLE_VECTOR); // GH-90000
        }
    }

    @Nested
    @DisplayName("factory methods [GH-90000]")
    class FactoryMethods {

        @Test
        @DisplayName("of(vector) creates result with empty text and 'unknown' model [GH-90000]")
        void ofFactory() { // GH-90000
            float[] vec = {0.5f, 0.6f};
            EmbeddingResult r = EmbeddingResult.of(vec); // GH-90000
            assertThat(r.getText()).isEmpty(); // GH-90000
            assertThat(r.getModel()).isEqualTo("unknown [GH-90000]");
            assertThat(r.getVector()).isEqualTo(vec); // GH-90000
        }

        @Test
        @DisplayName("fromOpenAI throws IllegalArgumentException for unsupported embedding type [GH-90000]")
        void fromOpenAIThrows() { // GH-90000
            assertThatThrownBy(() -> EmbeddingResult.fromOpenAI(new Object(), "text", "model")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("embedding [GH-90000]");
        }
    }

    @Nested
    @DisplayName("convenience methods [GH-90000]")
    class ConvenienceMethods {

        @Test
        @DisplayName("getModelName is alias for getModel [GH-90000]")
        void getModelNameAlias() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "ada-002"); // GH-90000
            assertThat(r.getModelName()).isEqualTo(r.getModel()); // GH-90000
        }
    }

    @Nested
    @DisplayName("equality and hashCode [GH-90000]")
    class Equality {

        @Test
        @DisplayName("equal results are equal [GH-90000]")
        void equalResults() { // GH-90000
            float[] v = {1.0f, 2.0f};
            EmbeddingResult r1 = new EmbeddingResult("hello", v, "model-a"); // GH-90000
            EmbeddingResult r2 = new EmbeddingResult("hello", v, "model-a"); // GH-90000
            assertThat(r1).isEqualTo(r2); // GH-90000
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("different text not equal [GH-90000]")
        void differentText() { // GH-90000
            EmbeddingResult r1 = new EmbeddingResult("hello", SAMPLE_VECTOR, "model"); // GH-90000
            EmbeddingResult r2 = new EmbeddingResult("world", SAMPLE_VECTOR, "model"); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("different model not equal [GH-90000]")
        void differentModel() { // GH-90000
            EmbeddingResult r1 = new EmbeddingResult("text", SAMPLE_VECTOR, "model-a"); // GH-90000
            EmbeddingResult r2 = new EmbeddingResult("text", SAMPLE_VECTOR, "model-b"); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("different vector not equal [GH-90000]")
        void differentVector() { // GH-90000
            EmbeddingResult r1 = new EmbeddingResult("text", new float[]{1.0f}, "model"); // GH-90000
            EmbeddingResult r2 = new EmbeddingResult("text", new float[]{2.0f}, "model"); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("equal to itself [GH-90000]")
        void equalToItself() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model"); // GH-90000
            assertThat(r).isEqualTo(r); // GH-90000
        }

        @Test
        @DisplayName("not equal to null [GH-90000]")
        void notEqualToNull() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("text", SAMPLE_VECTOR, "model"); // GH-90000
            assertThat(r).isNotEqualTo(null); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString [GH-90000]")
    class ToString {

        @Test
        @DisplayName("toString contains text and model, hides vector [GH-90000]")
        void toStringContent() { // GH-90000
            EmbeddingResult r = new EmbeddingResult("hello", SAMPLE_VECTOR, "ada-002"); // GH-90000
            String str = r.toString(); // GH-90000
            assertThat(str).contains("hello [GH-90000]").contains("ada-002 [GH-90000]").contains("[...] [GH-90000]");
        }
    }
}
