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
    void ofNormalizesEmbeddingsAndKeepsRecordIdentity() {
        UUID recordId = UUID.randomUUID();
        TestDataRecord record = new TestDataRecord(recordId);

        VectorRecord vectorRecord = VectorRecord.of(record, new float[]{3f, 4f}, "test-model");

        assertThat(vectorRecord.isNormalized()).isTrue();
        assertThat(vectorRecord.getDimension()).isEqualTo(2);
        assertThat(vectorRecord.getEmbeddingModel()).isEqualTo("test-model");
        assertThat(vectorRecord.id()).isEqualTo(recordId.toString());
        assertThat(vectorRecord.recordType()).isEqualTo("TestDataRecord");
        assertThat(vectorRecord.getEmbedding()).containsExactly(0.6f, 0.8f);
    }

    @Test
    @DisplayName("cosine similarity uses normalized dot product")
    void cosineSimilarityUsesNormalizedDotProduct() {
        VectorRecord vectorRecord = VectorRecord.builder()
                .record(new TestDataRecord(UUID.randomUUID()))
                .embedding(new float[]{1f, 0f})
                .dimension(2)
                .normalized(true)
                .build();

        assertThat(vectorRecord.cosineSimilarity(new float[]{0.5f, 0.5f})).isEqualTo(0.5f);
        assertThat(vectorRecord.dotProduct(new float[]{0.5f, 0.5f})).isEqualTo(0.5f);
        assertThat(vectorRecord.euclideanDistance(new float[]{0f, 1f}))
                .isCloseTo((float) Math.sqrt(2), org.assertj.core.data.Offset.offset(0.0001f));
    }

    @Test
    @DisplayName("normalize returns the same instance for zero vectors")
    void normalizeReturnsSameInstanceForZeroVectors() {
        VectorRecord vectorRecord = VectorRecord.builder()
                .record(new TestDataRecord(UUID.randomUUID()))
                .embedding(new float[]{0f, 0f})
                .dimension(2)
                .normalized(false)
                .build();

        assertThat(vectorRecord.normalize()).isSameAs(vectorRecord);
        assertThat(vectorRecord.magnitude()).isZero();
    }

    @Test
    @DisplayName("vector operations reject mismatched dimensions")
    void vectorOperationsRejectMismatchedDimensions() {
        VectorRecord vectorRecord = VectorRecord.builder()
                .record(new TestDataRecord(UUID.randomUUID()))
                .embedding(new float[]{1f, 2f, 3f})
                .dimension(3)
                .normalized(false)
                .build();

        assertThatThrownBy(() -> vectorRecord.cosineSimilarity(new float[]{1f, 2f}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embedding dimensions must match");

        assertThatThrownBy(() -> vectorRecord.euclideanDistance(new float[]{1f, 2f}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embedding dimensions must match");
    }

    private static final class TestDataRecord extends DataRecord {

        private TestDataRecord(UUID id) {
            this.id = id;
        }

        @Override
        public RecordType getRecordType() {
            return RecordType.DOCUMENT;
        }
    }
}
