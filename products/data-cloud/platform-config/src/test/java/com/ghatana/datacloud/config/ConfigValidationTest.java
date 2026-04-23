/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 *   <li>Metadata validation (name, namespace)</li> // GH-90000
 *   <li>Spec validation (recordType, schema, fields)</li> // GH-90000
 *   <li>Index validation (field references)</li> // GH-90000
 *   <li>Storage validation (partition/sort keys)</li> // GH-90000
 *   <li>Event collection validation (event model alignment)</li> // GH-90000
 *   <li>Tenancy validation (isolation, cross-tenant checks)</li> // GH-90000
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
    void setUp() { // GH-90000
        validator = new ConfigValidator(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Metadata]: missing_metadata_section_returns_error")
    void missingMetadataSectionReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withMetadata((RawCollectionConfig.RawMetadata) null) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Missing 'metadata' section");
    }

    @Test
    @DisplayName("[Metadata]: blank_collection_name_returns_error")
    void blankCollectionNameReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withMetadata(createMetadata().withName(""))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("metadata.name is required");
    }

    @Test
    @DisplayName("[Metadata]: invalid_collection_name_format_returns_error")
    void invalidCollectionNameFormatReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withMetadata(createMetadata().withName("Invalid_Name"))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("lowercase alphanumeric"));
    }

    @Test
    @DisplayName("[Metadata]: missing_namespace_returns_error")
    void missingNamespaceReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withMetadata(createMetadata().withNamespace(""))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("metadata.namespace (tenant ID) is required");
    }

    @Test
    @DisplayName("[Metadata]: valid_metadata_passes_validation")
    void validMetadataPassesValidation() { // GH-90000
        RawCollectionConfig config = createMinimalConfig().build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spec Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Spec]: missing_spec_section_returns_error")
    void missingSpecSectionReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec((RawCollectionConfig.RawSpec) null) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Missing 'spec' section");
    }

    @Test
    @DisplayName("[Spec]: missing_record_type_returns_error")
    void missingRecordTypeReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec().withRecordType(""))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("spec.recordType is required");
    }

    @Test
    @DisplayName("[Spec]: invalid_record_type_returns_error")
    void invalidRecordTypeReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec().withRecordType("INVALID"))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("ENTITY, EVENT, TIMESERIES, GRAPH, DOCUMENT"));
    }

    @Test
    @DisplayName("[Spec]: missing_schema_returns_error")
    void missingSchemaReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec().withSchema((RawCollectionConfig.RawSchema) null)) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("spec.schema is required");
    }

    @Test
    @DisplayName("[Spec]: empty_schema_fields_returns_error")
    void emptySchemaFieldsReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec().withSchema(createSchema().withFields(List.of()))) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("spec.schema.fields must have at least one field");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Field]: missing_field_name_returns_error")
    void missingFieldNameReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of(createField().withName("").build()))))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Field name is required");
    }

    @Test
    @DisplayName("[Field]: missing_field_type_returns_error")
    void missingFieldTypeReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of(createField().withName("testField").withType("").build()))))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Field 'testField' must have a type");
    }

    @Test
    @DisplayName("[Field]: duplicate_field_names_return_error")
    void duplicateFieldNamesReturnError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of( // GH-90000
                        createField().withName("duplicateField").withType("string").build(),
                        createField().withName("duplicateField").withType("string").build()
                    ))))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Duplicate field name: duplicateField");
    }

    @Test
    @DisplayName("[Field]: unknown_field_type_returns_error")
    void unknownFieldTypeReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of(createField() // GH-90000
                        .withName("unknownField")
                        .withType("UNKNOWN_TYPE")
                        .build())))) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown type"));
    }

    @Test
    @DisplayName("[Field]: valid_field_passes_validation")
    void validFieldPassesValidation() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of(createField() // GH-90000
                        .withName("validField")
                        .withType("string")
                        .build())))) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Index Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Index]: missing_index_name_returns_error")
    void missingIndexNameReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withIndexes(List.of(createIndex().withName("").build())))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Index must have a name");
    }

    @Test
    @DisplayName("[Index]: duplicate_index_names_return_error")
    void duplicateIndexNamesReturnError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withIndexes(List.of( // GH-90000
                    createIndex().withName("idx_duplicate").withFields(List.of("id")).build(),
                    createIndex().withName("idx_duplicate").withFields(List.of("name")).build()
                )))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Duplicate index name: idx_duplicate");
    }

    @Test
    @DisplayName("[Index]: empty_index_fields_returns_error")
    void emptyIndexFieldsReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withIndexes(List.of(createIndex() // GH-90000
                    .withName("idx_empty")
                    .withFields(List.of()) // GH-90000
                    .build()))) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Index 'idx_empty' must have at least one field");
    }

    @Test
    @DisplayName("[Index]: index_referencing_unknown_field_returns_error")
    void indexReferencingUnknownFieldReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of(createField().withName("id").withType("uuid").build())))
                .withIndexes(List.of(createIndex() // GH-90000
                    .withName("idx_unknown")
                    .withFields(List.of("nonExistentField"))
                    .build()))) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("references unknown field"));
    }

    @Test
    @DisplayName("[Index]: valid_index_passes_validation")
    void validIndexPassesValidation() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of( // GH-90000
                        createField().withName("id").withType("uuid").build(),
                        createField().withName("name").withType("string").build()
                    )))
                .withIndexes(List.of(createIndex() // GH-90000
                    .withName("idx_name")
                    .withFields(List.of("name"))
                    .build()))) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Collection Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Event]: missing_required_event_fields_return_errors")
    void missingRequiredEventFieldsReturnErrors() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createSpec() // GH-90000
                .withRecordType("EVENT")
                .withSchema(createSchema() // GH-90000
                    .withFields(List.of(createField().withName("eventId").withType("uuid").build()))))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("Event collection MUST have field"));
    }

    @Test
    @DisplayName("[Event]: valid_event_collection_passes_validation")
    void validEventCollectionPassesValidation() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withSpec(createEventSpec()) // GH-90000
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validate(config); // GH-90000

        // Should have warnings about immutability but be valid
        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenancy Validation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Tenancy]: valid_tenant_id_passes_validation")
    void validTenantIdPassesValidation() { // GH-90000
        RawCollectionConfig config = createMinimalConfig().build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validateTenancy(config, "tenant-alpha"); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("[Tenancy]: tenant_id_mismatch_returns_error")
    void tenantIdMismatchReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig().build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validateTenancy(config, "different-tenant"); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("Tenant ID mismatch"));
    }

    @Test
    @DisplayName("[Tenancy]: invalid_tenant_id_format_returns_error")
    void invalidTenantIdFormatReturnsError() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withMetadata(createMetadata().withNamespace("invalid tenant!"))
            .build(); // GH-90000

        ConfigValidator.ValidationResult result = validator.validateTenancy(config, null); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("Invalid tenant ID format"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateOrFail Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[validateOrFail]: valid_config_does_not_throw")
    void validConfigDoesNotThrow() { // GH-90000
        RawCollectionConfig config = createMinimalConfig().build(); // GH-90000

        validator.validateOrFail(config); // Should not throw // GH-90000
    }

    @Test
    @DisplayName("[validateOrFail]: invalid_config_throws_exception")
    void invalidConfigThrowsException() { // GH-90000
        RawCollectionConfig config = createMinimalConfig() // GH-90000
            .withMetadata((RawCollectionConfig.RawMetadata) null) // GH-90000
            .build(); // GH-90000

        assertThatThrownBy(() -> validator.validateOrFail(config)) // GH-90000
            .isInstanceOf(com.ghatana.platform.core.exception.ConfigurationException.class) // GH-90000
            .hasMessageContaining("Configuration validation failed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private RawCollectionConfigBuilder createMinimalConfig() { // GH-90000
        return new RawCollectionConfigBuilder() // GH-90000
            .withMetadata(createMetadata()) // GH-90000
            .withSpec(createSpec()); // GH-90000
    }

    private RawMetadataBuilder createMetadata() { // GH-90000
        return new RawMetadataBuilder() // GH-90000
            .withName("test-collection")
            .withNamespace("tenant-alpha");
    }

    private RawSpecBuilder createSpec() { // GH-90000
        return new RawSpecBuilder() // GH-90000
            .withRecordType("ENTITY")
            .withSchema(createSchema() // GH-90000
                .withFields(List.of(createField() // GH-90000
                    .withName("id")
                    .withType("uuid")
                    .build()))); // GH-90000
    }

    private RawEventSpecBuilder createEventSpec() { // GH-90000
        return new RawEventSpecBuilder() // GH-90000
            .withRecordType("EVENT")
            .withSchema(createSchema() // GH-90000
                .withFields(List.of( // GH-90000
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

    private RawSchemaBuilder createSchema() { // GH-90000
        return new RawSchemaBuilder().withVersion("1.0");
    }

    private RawFieldBuilder createField() { // GH-90000
        return new RawFieldBuilder(); // GH-90000
    }

    private RawIndexBuilder createIndex() { // GH-90000
        return new RawIndexBuilder(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder Classes for Test Data
    // ─────────────────────────────────────────────────────────────────────────

    static class RawCollectionConfigBuilder {
        private RawCollectionConfig.RawMetadata metadata;
        private RawCollectionConfig.RawSpec spec;

        RawCollectionConfigBuilder withMetadata(RawMetadataBuilder metadata) { // GH-90000
            this.metadata = metadata.build(); // GH-90000
            return this;
        }

        RawCollectionConfigBuilder withMetadata(RawCollectionConfig.RawMetadata metadata) { // GH-90000
            this.metadata = metadata;
            return this;
        }

        RawCollectionConfigBuilder withSpec(RawSpecBuilder spec) { // GH-90000
            this.spec = spec.build(); // GH-90000
            return this;
        }

        RawCollectionConfigBuilder withSpec(RawEventSpecBuilder spec) { // GH-90000
            this.spec = spec.build(); // GH-90000
            return this;
        }

        RawCollectionConfigBuilder withSpec(RawCollectionConfig.RawSpec spec) { // GH-90000
            this.spec = spec;
            return this;
        }

        RawCollectionConfig build() { // GH-90000
            return new RawCollectionConfig("v1", "Collection", metadata, spec); // GH-90000
        }
    }

    static class RawMetadataBuilder {
        private String name = "test-collection";
        private String namespace = "tenant-alpha";

        RawMetadataBuilder withName(String name) { // GH-90000
            this.name = name;
            return this;
        }

        RawMetadataBuilder withNamespace(String namespace) { // GH-90000
            this.namespace = namespace;
            return this;
        }

        RawCollectionConfig.RawMetadata build() { // GH-90000
            return new RawCollectionConfig.RawMetadata(name, namespace, Map.of(), Map.of()); // GH-90000
        }
    }

    static class RawSpecBuilder {
        private String recordType = "ENTITY";
        private RawCollectionConfig.RawSchema schema;
        private List<RawCollectionConfig.RawIndex> indexes = List.of(); // GH-90000
        private RawCollectionConfig.RawStorage storage = null;

        RawSpecBuilder withRecordType(String recordType) { // GH-90000
            this.recordType = recordType;
            return this;
        }

        RawSpecBuilder withSchema(RawSchemaBuilder schema) { // GH-90000
            this.schema = schema.build(); // GH-90000
            return this;
        }

        RawSpecBuilder withSchema(RawCollectionConfig.RawSchema schema) { // GH-90000
            this.schema = schema;
            return this;
        }

        RawSpecBuilder withIndexes(List<RawCollectionConfig.RawIndex> indexes) { // GH-90000
            this.indexes = indexes;
            return this;
        }

        RawCollectionConfig.RawSpec build() { // GH-90000
            return new RawCollectionConfig.RawSpec(recordType, null, null, null, schema, storage, indexes, null, null, null, null, null, null); // GH-90000
        }
    }

    static class RawEventSpecBuilder {
        private String recordType = "EVENT";
        private RawCollectionConfig.RawSchema schema;
        private List<RawCollectionConfig.RawIndex> indexes = List.of(); // GH-90000
        private RawCollectionConfig.RawEventSourcing eventSourcing =
            new RawCollectionConfig.RawEventSourcing(true, true, null, null); // GH-90000

        RawEventSpecBuilder withRecordType(String recordType) { // GH-90000
            this.recordType = recordType;
            return this;
        }

        RawEventSpecBuilder withSchema(RawSchemaBuilder schema) { // GH-90000
            this.schema = schema.build(); // GH-90000
            return this;
        }

        RawCollectionConfig.RawSpec build() { // GH-90000
            return new RawCollectionConfig.RawSpec(recordType, null, null, null, schema, null, indexes, null, null, null, eventSourcing, null, null); // GH-90000
        }
    }

    static class RawSchemaBuilder {
        private String version = "1.0";
        private List<RawCollectionConfig.RawField> fields = List.of(); // GH-90000

        RawSchemaBuilder withVersion(String version) { // GH-90000
            this.version = version;
            return this;
        }

        RawSchemaBuilder withFields(List<RawCollectionConfig.RawField> fields) { // GH-90000
            this.fields = fields;
            return this;
        }

        RawCollectionConfig.RawSchema build() { // GH-90000
            return new RawCollectionConfig.RawSchema(version, null, fields); // GH-90000
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

        RawFieldBuilder withName(String name) { // GH-90000
            this.name = name;
            return this;
        }

        RawFieldBuilder withType(String type) { // GH-90000
            this.type = type;
            return this;
        }

        RawFieldBuilder withRequired(boolean required) { // GH-90000
            this.required = required;
            return this;
        }

        RawFieldBuilder withImmutable(boolean immutable) { // GH-90000
            this.immutable = immutable;
            return this;
        }

        RawCollectionConfig.RawField build() { // GH-90000
            return new RawCollectionConfig.RawField( // GH-90000
                name,
                type,
                null,
                required,
                Boolean.TRUE.equals(unique), // GH-90000
                Boolean.TRUE.equals(indexed), // GH-90000
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

        RawIndexBuilder withName(String name) { // GH-90000
            this.name = name;
            return this;
        }

        RawIndexBuilder withFields(List<String> fields) { // GH-90000
            this.fields = fields;
            return this;
        }

        RawCollectionConfig.RawIndex build() { // GH-90000
            return new RawCollectionConfig.RawIndex(name, fields, false, type); // GH-90000
        }
    }
}
