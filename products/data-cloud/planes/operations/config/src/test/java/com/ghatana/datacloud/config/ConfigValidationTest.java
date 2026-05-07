/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.RawCollectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ConfigValidator}.
 *
 * <p>Validates configuration validation logic including:
 * <ul>
 *   <li>Metadata validation (name, namespace)</li> 
 *   <li>Spec validation (recordType, schema, fields)</li> 
 *   <li>Index validation (field references)</li> 
 *   <li>Storage validation (partition/sort keys)</li> 
 *   <li>Event collection validation (event model alignment)</li> 
 *   <li>Tenancy validation (isolation, cross-tenant checks)</li> 
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for ConfigValidator with property, default, and validation coverage
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("[Config]: validation_rules_and_behavior")
class ConfigValidationTest {

    private ConfigValidator validator;

    @BeforeEach
    void setUp() { 
        validator = new ConfigValidator(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Metadata]: missing_metadata_section_returns_error")
    void missingMetadataSectionReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withMetadata((RawCollectionConfig.RawMetadata) null) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Missing 'metadata' section");
    }

    @Test
    @DisplayName("[Metadata]: blank_collection_name_returns_error")
    void blankCollectionNameReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withMetadata(createMetadata().withName(""))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("metadata.name is required");
    }

    @Test
    @DisplayName("[Metadata]: invalid_collection_name_format_returns_error")
    void invalidCollectionNameFormatReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withMetadata(createMetadata().withName("Invalid_Name"))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("lowercase alphanumeric"));
    }

    @Test
    @DisplayName("[Metadata]: missing_namespace_returns_error")
    void missingNamespaceReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withMetadata(createMetadata().withNamespace(""))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("metadata.namespace (tenant ID) is required");
    }

    @Test
    @DisplayName("[Metadata]: valid_metadata_passes_validation")
    void validMetadataPassesValidation() { 
        RawCollectionConfig config = createMinimalConfig().build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isTrue(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spec Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Spec]: missing_spec_section_returns_error")
    void missingSpecSectionReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec((RawCollectionConfig.RawSpec) null) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Missing 'spec' section");
    }

    @Test
    @DisplayName("[Spec]: missing_record_type_returns_error")
    void missingRecordTypeReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec().withRecordType(""))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("spec.recordType is required");
    }

    @Test
    @DisplayName("[Spec]: invalid_record_type_returns_error")
    void invalidRecordTypeReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec().withRecordType("INVALID"))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("ENTITY, EVENT, TIMESERIES, GRAPH, DOCUMENT"));
    }

    @Test
    @DisplayName("[Spec]: missing_schema_returns_error")
    void missingSchemaReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec().withSchema((RawCollectionConfig.RawSchema) null)) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("spec.schema is required");
    }

    @Test
    @DisplayName("[Spec]: empty_schema_fields_returns_error")
    void emptySchemaFieldsReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec().withSchema(createSchema().withFields(List.of()))) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("spec.schema.fields must have at least one field");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Field]: missing_field_name_returns_error")
    void missingFieldNameReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of(createField().withName("").build()))))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Field name is required");
    }

    @Test
    @DisplayName("[Field]: missing_field_type_returns_error")
    void missingFieldTypeReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of(createField().withName("testField").withType("").build()))))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Field 'testField' must have a type");
    }

    @Test
    @DisplayName("[Field]: duplicate_field_names_return_error")
    void duplicateFieldNamesReturnError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of( 
                        createField().withName("duplicateField").withType("string").build(),
                        createField().withName("duplicateField").withType("string").build()
                    ))))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Duplicate field name: duplicateField");
    }

    @Test
    @DisplayName("[Field]: unknown_field_type_returns_error")
    void unknownFieldTypeReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of(createField() 
                        .withName("unknownField")
                        .withType("UNKNOWN_TYPE")
                        .build())))) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown type"));
    }

    @Test
    @DisplayName("[Field]: valid_field_passes_validation")
    void validFieldPassesValidation() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of(createField() 
                        .withName("validField")
                        .withType("string")
                        .build())))) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isTrue(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Index Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Index]: missing_index_name_returns_error")
    void missingIndexNameReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withIndexes(List.of(createIndex().withName("").build())))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Index must have a name");
    }

    @Test
    @DisplayName("[Index]: duplicate_index_names_return_error")
    void duplicateIndexNamesReturnError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withIndexes(List.of( 
                    createIndex().withName("idx_duplicate").withFields(List.of("id")).build(),
                    createIndex().withName("idx_duplicate").withFields(List.of("name")).build()
                )))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Duplicate index name: idx_duplicate");
    }

    @Test
    @DisplayName("[Index]: empty_index_fields_returns_error")
    void emptyIndexFieldsReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withIndexes(List.of(createIndex() 
                    .withName("idx_empty")
                    .withFields(List.of()) 
                    .build()))) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).contains("Index 'idx_empty' must have at least one field");
    }

    @Test
    @DisplayName("[Index]: index_referencing_unknown_field_returns_error")
    void indexReferencingUnknownFieldReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of(createField().withName("id").withType("uuid").build())))
                .withIndexes(List.of(createIndex() 
                    .withName("idx_unknown")
                    .withFields(List.of("nonExistentField"))
                    .build()))) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("references unknown field"));
    }

    @Test
    @DisplayName("[Index]: valid_index_passes_validation")
    void validIndexPassesValidation() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withSchema(createSchema() 
                    .withFields(List.of( 
                        createField().withName("id").withType("uuid").build(),
                        createField().withName("name").withType("string").build()
                    )))
                .withIndexes(List.of(createIndex() 
                    .withName("idx_name")
                    .withFields(List.of("name"))
                    .build()))) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isTrue(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Collection Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Event]: missing_required_event_fields_return_errors")
    void missingRequiredEventFieldsReturnErrors() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createSpec() 
                .withRecordType("EVENT")
                .withSchema(createSchema() 
                    .withFields(List.of(createField().withName("eventId").withType("uuid").build()))))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("Event collection MUST have field"));
    }

    @Test
    @DisplayName("[Event]: valid_event_collection_passes_validation")
    void validEventCollectionPassesValidation() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withSpec(createEventSpec()) 
            .build(); 

        ConfigValidator.ValidationResult result = validator.validate(config); 

        // Should have warnings about immutability but be valid
        assertThat(result.isValid()).isTrue(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenancy Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Tenancy]: valid_tenant_id_passes_validation")
    void validTenantIdPassesValidation() { 
        RawCollectionConfig config = createMinimalConfig().build(); 

        ConfigValidator.ValidationResult result = validator.validateTenancy(config, "tenant-alpha"); 

        assertThat(result.isValid()).isTrue(); 
    }

    @Test
    @DisplayName("[Tenancy]: tenant_id_mismatch_returns_error")
    void tenantIdMismatchReturnsError() { 
        RawCollectionConfig config = createMinimalConfig().build(); 

        ConfigValidator.ValidationResult result = validator.validateTenancy(config, "different-tenant"); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("Tenant ID mismatch"));
    }

    @Test
    @DisplayName("[Tenancy]: invalid_tenant_id_format_returns_error")
    void invalidTenantIdFormatReturnsError() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withMetadata(createMetadata().withNamespace("invalid tenant!"))
            .build(); 

        ConfigValidator.ValidationResult result = validator.validateTenancy(config, null); 

        assertThat(result.isValid()).isFalse(); 
        assertThat(result.errors()).anyMatch(e -> e.contains("Invalid tenant ID format"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateOrFail Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[validateOrFail]: valid_config_does_not_throw")
    void validConfigDoesNotThrow() { 
        RawCollectionConfig config = createMinimalConfig().build(); 

        validator.validateOrFail(config); // Should not throw 
    }

    @Test
    @DisplayName("[validateOrFail]: invalid_config_throws_exception")
    void invalidConfigThrowsException() { 
        RawCollectionConfig config = createMinimalConfig() 
            .withMetadata((RawCollectionConfig.RawMetadata) null) 
            .build(); 

        assertThatThrownBy(() -> validator.validateOrFail(config)) 
            .isInstanceOf(com.ghatana.platform.core.exception.ConfigurationException.class) 
            .hasMessageContaining("Configuration validation failed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private RawCollectionConfigBuilder createMinimalConfig() { 
        return new RawCollectionConfigBuilder() 
            .withMetadata(createMetadata()) 
            .withSpec(createSpec()); 
    }

    private RawMetadataBuilder createMetadata() { 
        return new RawMetadataBuilder() 
            .withName("test-collection")
            .withNamespace("tenant-alpha");
    }

    private RawSpecBuilder createSpec() { 
        return new RawSpecBuilder() 
            .withRecordType("ENTITY")
            .withSchema(createSchema() 
                .withFields(List.of(createField() 
                    .withName("id")
                    .withType("uuid")
                    .build()))); 
    }

    private RawEventSpecBuilder createEventSpec() { 
        return new RawEventSpecBuilder() 
            .withRecordType("EVENT")
            .withSchema(createSchema() 
                .withFields(List.of( 
                    createField().withName("eventId").withType("uuid").build(),
                    createField().withName("eventType").withType("string").build(),
                    createField().withName("aggregateId").withType("uuid").build(),
                    createField().withName("aggregateVersion").withType("long").build(),
                    createField().withName("correlationId").withType("uuid").build(),
                    createField().withName("timestamp").withType("timestamp").build(),
                    createField().withName("tenantId").withType("string").build(),
                    createField().withName("payload").withType("object").build()
                )));
    }

    private RawSchemaBuilder createSchema() { 
        return new RawSchemaBuilder().withVersion("1.0");
    }

    private RawFieldBuilder createField() { 
        return new RawFieldBuilder(); 
    }

    private RawIndexBuilder createIndex() { 
        return new RawIndexBuilder(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder Classes for Test Data
    // ─────────────────────────────────────────────────────────────────────────

    static class RawCollectionConfigBuilder {
        private RawCollectionConfig.RawMetadata metadata;
        private RawCollectionConfig.RawSpec spec;

        RawCollectionConfigBuilder withMetadata(RawMetadataBuilder metadata) { 
            this.metadata = metadata.build(); 
            return this;
        }

        RawCollectionConfigBuilder withMetadata(RawCollectionConfig.RawMetadata metadata) { 
            this.metadata = metadata;
            return this;
        }

        RawCollectionConfigBuilder withSpec(RawSpecBuilder spec) { 
            this.spec = spec.build(); 
            return this;
        }

        RawCollectionConfigBuilder withSpec(RawEventSpecBuilder spec) { 
            this.spec = spec.build(); 
            return this;
        }

        RawCollectionConfigBuilder withSpec(RawCollectionConfig.RawSpec spec) { 
            this.spec = spec;
            return this;
        }

        RawCollectionConfig build() { 
            return new RawCollectionConfig("v1", "Collection", metadata, spec); 
        }
    }

    static class RawMetadataBuilder {
        private String name = "test-collection";
        private String namespace = "tenant-alpha";

        RawMetadataBuilder withName(String name) { 
            this.name = name;
            return this;
        }

        RawMetadataBuilder withNamespace(String namespace) { 
            this.namespace = namespace;
            return this;
        }

        RawCollectionConfig.RawMetadata build() { 
            return new RawCollectionConfig.RawMetadata(name, namespace, Map.of(), Map.of()); 
        }
    }

    static class RawSpecBuilder {
        private String recordType = "ENTITY";
        private RawCollectionConfig.RawSchema schema;
        private List<RawCollectionConfig.RawIndex> indexes = List.of(); 
        private RawCollectionConfig.RawStorage storage = null;

        RawSpecBuilder withRecordType(String recordType) { 
            this.recordType = recordType;
            return this;
        }

        RawSpecBuilder withSchema(RawSchemaBuilder schema) { 
            this.schema = schema.build(); 
            return this;
        }

        RawSpecBuilder withSchema(RawCollectionConfig.RawSchema schema) { 
            this.schema = schema;
            return this;
        }

        RawSpecBuilder withIndexes(List<RawCollectionConfig.RawIndex> indexes) { 
            this.indexes = indexes;
            return this;
        }

        RawCollectionConfig.RawSpec build() { 
            return new RawCollectionConfig.RawSpec(recordType, null, null, null, schema, storage, indexes, null, null, null, null, null, null); 
        }
    }

    static class RawEventSpecBuilder {
        private String recordType = "EVENT";
        private RawCollectionConfig.RawSchema schema;
        private List<RawCollectionConfig.RawIndex> indexes = List.of(); 
        private RawCollectionConfig.RawEventSourcing eventSourcing =
            new RawCollectionConfig.RawEventSourcing(true, true, null, null); 

        RawEventSpecBuilder withRecordType(String recordType) { 
            this.recordType = recordType;
            return this;
        }

        RawEventSpecBuilder withSchema(RawSchemaBuilder schema) { 
            this.schema = schema.build(); 
            return this;
        }

        RawCollectionConfig.RawSpec build() { 
            return new RawCollectionConfig.RawSpec(recordType, null, null, null, schema, null, indexes, null, null, null, eventSourcing, null, null); 
        }
    }

    static class RawSchemaBuilder {
        private String version = "1.0";
        private List<RawCollectionConfig.RawField> fields = List.of(); 

        RawSchemaBuilder withVersion(String version) { 
            this.version = version;
            return this;
        }

        RawSchemaBuilder withFields(List<RawCollectionConfig.RawField> fields) { 
            this.fields = fields;
            return this;
        }

        RawCollectionConfig.RawSchema build() { 
            return new RawCollectionConfig.RawSchema(version, null, fields); 
        }
    }

    static class RawFieldBuilder {
        private String name = "id";
        private String type = "string";
        private boolean required = true;
        private boolean immutable = false;
        private Boolean unique = null;
        private Boolean indexed = null;
        private RawCollectionConfig.RawArrayItems items = null;
        private List<String> values = null;
        private String reference = null;
        private String join = null;

        RawFieldBuilder withName(String name) { 
            this.name = name;
            return this;
        }

        RawFieldBuilder withType(String type) { 
            this.type = type;
            return this;
        }

        RawFieldBuilder withRequired(boolean required) { 
            this.required = required;
            return this;
        }

        RawFieldBuilder withImmutable(boolean immutable) { 
            this.immutable = immutable;
            return this;
        }

        RawCollectionConfig.RawField build() { 
            return new RawCollectionConfig.RawField( 
                name,
                type,
                null,
                required,
                Boolean.TRUE.equals(unique), 
                Boolean.TRUE.equals(indexed), 
                false,
                false,
                immutable,
                null,
                null,
                null,
                null,
                values,
                null,
                items,
                null,
                reference,
                join
            );
        }
    }

    static class RawIndexBuilder {
        private String name = "idx_test";
        private List<String> fields = List.of("id");
        private String type = "btree";

        RawIndexBuilder withName(String name) { 
            this.name = name;
            return this;
        }

        RawIndexBuilder withFields(List<String> fields) { 
            this.fields = fields;
            return this;
        }

        RawCollectionConfig.RawIndex build() { 
            return new RawCollectionConfig.RawIndex(name, fields, false, type); 
        }
    }
}
