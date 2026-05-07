package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RecordType}.
 */
@DisplayName("RecordType")
class RecordTypeTest {

    @Test
    @DisplayName("ENTITY is mutable")
    void entity_isMutable() {
        assertThat(RecordType.ENTITY.isMutable()).isTrue();
    }

    @Test
    @DisplayName("ENTITY is not append-only")
    void entity_isNotAppendOnly() {
        assertThat(RecordType.ENTITY.isAppendOnly()).isFalse();
    }

    @Test
    @DisplayName("EVENT is append-only")
    void event_isAppendOnly() {
        assertThat(RecordType.EVENT.isAppendOnly()).isTrue();
    }

    @Test
    @DisplayName("EVENT is immutable")
    void event_isImmutable() {
        assertThat(RecordType.EVENT.isMutable()).isFalse();
    }

    @Test
    @DisplayName("TIMESERIES is aggregatable")
    void timeseries_isAggregatable() {
        assertThat(RecordType.TIMESERIES.isAggregatable()).isTrue();
    }

    @Test
    @DisplayName("TIMESERIES is timestamped")
    void timeseries_isTimestamped() {
        assertThat(RecordType.TIMESERIES.isTimestamped()).isTrue();
    }

    @Test
    @DisplayName("supportsCRUD returns true for ENTITY")
    void supportsCRUD_returnsTrueForEntity() {
        assertThat(RecordType.ENTITY.supportsCRUD()).isTrue();
    }

    @Test
    @DisplayName("supportsCRUD returns false for EVENT")
    void supportsCRUD_returnsFalseForEvent() {
        assertThat(RecordType.EVENT.supportsCRUD()).isFalse();
    }

    @Test
    @DisplayName("supportsStreaming returns true for EVENT")
    void supportsStreaming_returnsTrueForEvent() {
        assertThat(RecordType.EVENT.supportsStreaming()).isTrue();
    }

    @Test
    @DisplayName("supportsStreaming returns false for ENTITY")
    void supportsStreaming_returnsFalseForEntity() {
        assertThat(RecordType.ENTITY.supportsStreaming()).isFalse();
    }

    @Test
    @DisplayName("supportsTimeRangeQuery returns true for TIMESERIES")
    void supportsTimeRangeQuery_returnsTrueForTimeseries() {
        assertThat(RecordType.TIMESERIES.supportsTimeRangeQuery()).isTrue();
    }

    @Test
    @DisplayName("supportsTimeRangeQuery returns false for ENTITY")
    void supportsTimeRangeQuery_returnsFalseForEntity() {
        assertThat(RecordType.ENTITY.supportsTimeRangeQuery()).isFalse();
    }

    @Test
    @DisplayName("supportsOffsetRead returns true for EVENT")
    void supportsOffsetRead_returnsTrueForEvent() {
        assertThat(RecordType.EVENT.supportsOffsetRead()).isTrue();
    }

    @Test
    @DisplayName("supportsOffsetRead returns false for ENTITY")
    void supportsOffsetRead_returnsFalseForEntity() {
        assertThat(RecordType.ENTITY.supportsOffsetRead()).isFalse();
    }

    @Test
    @DisplayName("supportsSoftDelete returns true for ENTITY")
    void supportsSoftDelete_returnsTrueForEntity() {
        assertThat(RecordType.ENTITY.supportsSoftDelete()).isTrue();
    }

    @Test
    @DisplayName("supportsSoftDelete returns false for EVENT")
    void supportsSoftDelete_returnsFalseForEvent() {
        assertThat(RecordType.EVENT.supportsSoftDelete()).isFalse();
    }

    @Test
    @DisplayName("supportsVersioning returns true for ENTITY")
    void supportsVersioning_returnsTrueForEntity() {
        assertThat(RecordType.ENTITY.supportsVersioning()).isTrue();
    }

    @Test
    @DisplayName("supportsVersioning returns false for EVENT")
    void supportsVersioning_returnsFalseForEvent() {
        assertThat(RecordType.EVENT.supportsVersioning()).isFalse();
    }

    @Test
    @DisplayName("getDescription returns non-null description")
    void getDescription_returnsNonNullDescription() {
        assertThat(RecordType.ENTITY.getDescription()).isNotNull();
        assertThat(RecordType.EVENT.getDescription()).isNotNull();
        assertThat(RecordType.TIMESERIES.getDescription()).isNotNull();
        assertThat(RecordType.GRAPH.getDescription()).isNotNull();
        assertThat(RecordType.DOCUMENT.getDescription()).isNotNull();
    }

    @Test
    @DisplayName("isOrdered returns true for EVENT")
    void isOrdered_returnsTrueForEvent() {
        assertThat(RecordType.EVENT.isOrdered()).isTrue();
    }

    @Test
    @DisplayName("isOrdered returns true for TIMESERIES")
    void isOrdered_returnsTrueForTimeseries() {
        assertThat(RecordType.TIMESERIES.isOrdered()).isTrue();
    }

    @Test
    @DisplayName("isOrdered returns false for ENTITY")
    void isOrdered_returnsFalseForEntity() {
        assertThat(RecordType.ENTITY.isOrdered()).isFalse();
    }
}
