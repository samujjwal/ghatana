package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataCloudColumnNames}.
 */
@DisplayName("DataCloudColumnNames")
class DataCloudColumnNamesTest {

    @Test
    @DisplayName("core identity constants are defined")
    void coreIdentityConstants_areDefined() {
        assertThat(DataCloudColumnNames.ID).isEqualTo("id");
        assertThat(DataCloudColumnNames.TENANT_ID).isEqualTo("tenant_id");
        assertThat(DataCloudColumnNames.NAME).isEqualTo("name");
        assertThat(DataCloudColumnNames.LABEL).isEqualTo("label");
        assertThat(DataCloudColumnNames.DESCRIPTION).isEqualTo("description");
        assertThat(DataCloudColumnNames.STATUS).isEqualTo("status");
        assertThat(DataCloudColumnNames.ACTIVE).isEqualTo("active");
        assertThat(DataCloudColumnNames.VERSION).isEqualTo("version");
    }

    @Test
    @DisplayName("collection schema constants are defined")
    void collectionSchemaConstants_areDefined() {
        assertThat(DataCloudColumnNames.COLLECTION_ID).isEqualTo("collection_id");
        assertThat(DataCloudColumnNames.COLLECTION_NAME).isEqualTo("collection_name");
        assertThat(DataCloudColumnNames.RECORD_TYPE).isEqualTo("record_type");
        assertThat(DataCloudColumnNames.FIELDS).isEqualTo("fields");
        assertThat(DataCloudColumnNames.VALIDATION_SCHEMA).isEqualTo("validation_schema");
        assertThat(DataCloudColumnNames.SCHEMA_VERSION).isEqualTo("schema_version");
    }

    @Test
    @DisplayName("data payload constants are defined")
    void dataPayloadConstants_areDefined() {
        assertThat(DataCloudColumnNames.DATA).isEqualTo("data");
        assertThat(DataCloudColumnNames.METADATA).isEqualTo("metadata");
        assertThat(DataCloudColumnNames.ENTITY_DATA).isEqualTo("entity_data");
    }

    @Test
    @DisplayName("event log constants are defined")
    void eventLogConstants_areDefined() {
        assertThat(DataCloudColumnNames.EVENT_ID).isEqualTo("event_id");
        assertThat(DataCloudColumnNames.EVENT_TYPE).isEqualTo("event_type");
        assertThat(DataCloudColumnNames.PAYLOAD).isEqualTo("payload");
        assertThat(DataCloudColumnNames.CONTENT_TYPE).isEqualTo("content_type");
        assertThat(DataCloudColumnNames.IDEMPOTENCY_KEY).isEqualTo("idempotency_key");
        assertThat(DataCloudColumnNames.OFFSET_VALUE).isEqualTo("offset_value");
    }

    @Test
    @DisplayName("audit constants are defined")
    void auditConstants_areDefined() {
        assertThat(DataCloudColumnNames.CREATED_AT).isEqualTo("created_at");
        assertThat(DataCloudColumnNames.CREATED_BY).isEqualTo("created_by");
        assertThat(DataCloudColumnNames.UPDATED_AT).isEqualTo("updated_at");
        assertThat(DataCloudColumnNames.UPDATED_BY).isEqualTo("updated_by");
    }
}
