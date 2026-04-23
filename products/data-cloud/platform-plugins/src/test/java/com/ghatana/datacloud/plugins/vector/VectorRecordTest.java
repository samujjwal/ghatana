package com.ghatana.datacloud.plugins.vector;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.RecordType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorRecordTest {

    @Test
    @DisplayName("of normalizes embeddings and keeps record identity")
    void ofNormalizesEmbeddingsAndKeepsRecordIdentity() { // GH-90000
        UUID recordId = UUID.randomUUID(); // GH-90000
        TestDataRecord record = new TestDataRecord(recordId); // GH-90000

        VectorRecord vectorRecord = VectorRecord.of(record, new float[]{3f, 4f}, "test-model"); // GH-90000

        assertThat(vectorRecord.isNormalized()).isTrue(); // GH-90000
        assertThat(vectorRecord.getDimension()).isEqualTo(2); // GH-90000
        assertThat(vectorRecord.getEmbeddingModel()).isEqualTo("test-model");
        assertThat(vectorRecord.id()).isEqualTo(recordId.toString()); // GH-90000
        assertThat(vectorRecord.recordType()).isEqualTo("TestDataRecord");
        assertThat(vectorRecord.getEmbedding()).containsExactly(0.6f, 0.8f); // GH-90000
    }

    @Test
    @DisplayName("cosine similarity uses normalized dot product")
    void cosineSimilarityUsesNormalizedDotProduct() { // GH-90000
        VectorRecord vectorRecord = VectorRecord.builder() // GH-90000
                .record(new TestDataRecord(UUID.randomUUID())) // GH-90000
                .embedding(new float[]{1f, 0f}) // GH-90000
                .dimension(2) // GH-90000
                .normalized(true) // GH-90000
                .build(); // GH-90000

        assertThat(vectorRecord.cosineSimilarity(new float[]{0.5f, 0.5f})).isEqualTo(0.5f); // GH-90000
        assertThat(vectorRecord.dotProduct(new float[]{0.5f, 0.5f})).isEqualTo(0.5f); // GH-90000
        assertThat(vectorRecord.euclideanDistance(new float[]{0f, 1f})) // GH-90000
                .isCloseTo((float) Math.sqrt(2), org.assertj.core.data.Offset.offset(0.0001f)); // GH-90000
    }

    @Test
    @DisplayName("normalize returns the same instance for zero vectors")
    void normalizeReturnsSameInstanceForZeroVectors() { // GH-90000
        VectorRecord vectorRecord = VectorRecord.builder() // GH-90000
                .record(new TestDataRecord(UUID.randomUUID())) // GH-90000
                .embedding(new float[]{0f, 0f}) // GH-90000
                .dimension(2) // GH-90000
                .normalized(false) // GH-90000
                .build(); // GH-90000

        assertThat(vectorRecord.normalize()).isSameAs(vectorRecord); // GH-90000
        assertThat(vectorRecord.magnitude()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("vector operations reject mismatched dimensions")
    void vectorOperationsRejectMismatchedDimensions() { // GH-90000
        VectorRecord vectorRecord = VectorRecord.builder() // GH-90000
                .record(new TestDataRecord(UUID.randomUUID())) // GH-90000
                .embedding(new float[]{1f, 2f, 3f}) // GH-90000
                .dimension(3) // GH-90000
                .normalized(false) // GH-90000
                .build(); // GH-90000

        assertThatThrownBy(() -> vectorRecord.cosineSimilarity(new float[]{1f, 2f})) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Embedding dimensions must match");

        assertThatThrownBy(() -> vectorRecord.euclideanDistance(new float[]{1f, 2f})) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Embedding dimensions must match");
    }

    private static final class TestDataRecord extends DataRecord {

        private TestDataRecord(UUID id) { // GH-90000
            this.id = id;
        }

        @Override
        public RecordType getRecordType() { // GH-90000
            return RecordType.DOCUMENT;
        }
    }
}
