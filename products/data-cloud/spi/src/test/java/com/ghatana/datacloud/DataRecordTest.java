package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataRecord}.
 */
@DisplayName("DataRecord")
class DataRecordTest {

    @Test
    @DisplayName("RecordOperation enum contains all expected operations")
    void recordOperationEnum_containsAllExpectedOperations() {
        DataRecord.RecordOperation[] operations = DataRecord.RecordOperation.values();
        assertThat(operations).contains(
                DataRecord.RecordOperation.CREATE,
                DataRecord.RecordOperation.READ,
                DataRecord.RecordOperation.UPDATE,
                DataRecord.RecordOperation.DELETE,
                DataRecord.RecordOperation.APPEND,
                DataRecord.RecordOperation.SUBSCRIBE,
                DataRecord.RecordOperation.AGGREGATE
        );
    }

    @Test
    @DisplayName("RecordOperation enum values are correct")
    void recordOperationEnum_valuesAreCorrect() {
        assertThat(DataRecord.RecordOperation.CREATE.name()).isEqualTo("CREATE");
        assertThat(DataRecord.RecordOperation.READ.name()).isEqualTo("READ");
        assertThat(DataRecord.RecordOperation.UPDATE.name()).isEqualTo("UPDATE");
        assertThat(DataRecord.RecordOperation.DELETE.name()).isEqualTo("DELETE");
        assertThat(DataRecord.RecordOperation.APPEND.name()).isEqualTo("APPEND");
        assertThat(DataRecord.RecordOperation.SUBSCRIBE.name()).isEqualTo("SUBSCRIBE");
        assertThat(DataRecord.RecordOperation.AGGREGATE.name()).isEqualTo("AGGREGATE");
    }
}
