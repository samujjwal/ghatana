/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.CompiledCollectionConfig;
import com.ghatana.datacloud.config.model.RawCollectionConfig;
import com.ghatana.datacloud.config.model.RawCollectionConfig.*;
import com.ghatana.platform.core.exception.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CollectionConfigCompiler}.
 *
 * @doc.type class
 * @doc.purpose Test compilation of raw YAML config into runtime objects
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("CollectionConfigCompiler")
class CollectionConfigCompilerTest {

    private CollectionConfigCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new CollectionConfigCompiler();
    }

    @Nested
    @DisplayName("Standard Collection Compilation")
    class StandardCollectionTests {

        @Test
        @DisplayName("compiles a minimal entity collection")
        void compilesMinimalEntityCollection() {
            RawCollectionConfig raw = buildRaw("users", "ENTITY");

            CompiledCollectionConfig compiled = compiler.compile(raw);

            assertThat(compiled).isNotNull();
            assertThat(compiled.name()).isEqualTo("users");
        }

        @Test
        @DisplayName("compiles document collection type")
        void compilesDocumentCollection() {
            RawCollectionConfig raw = buildRaw("docs", "DOCUMENT");

            CompiledCollectionConfig compiled = compiler.compile(raw);

            assertThat(compiled.name()).isEqualTo("docs");
        }

        @Test
        @DisplayName("throws ConfigurationException for event record type")
        void throwsForEventRecordType() {
            RawCollectionConfig raw = buildRaw("events", "EVENT");

            assertThatThrownBy(() -> compiler.compile(raw))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("compileEventCollection");
        }

        @Test
        @DisplayName("throws NullPointerException when null is passed")
        void throwsForNullInput() {
            assertThatThrownBy(() -> compiler.compile(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("compiled result has fields from schema")
        void compiledResultHasFields() {
            RawSchema schema = new RawSchema("1.0", null, List.of(
                    new RawField("id", "string", null, true, true, true,
                            false, true, true, null, null, null, null,
                            List.of(), null, null, "Primary ID", null, null),
                    new RawField("name", "string", null, false, false, false,
                            false, false, false, null, 1, 255, null,
                            List.of(), null, null, "Display name", null, null)
            ));
            RawCollectionConfig raw = buildRawWithSchema("customers", "ENTITY", schema);

            CompiledCollectionConfig compiled = compiler.compile(raw);

            assertThat(compiled.fields()).isNotNull();
            assertThat(compiled.fields()).hasSize(2);
            assertThat(compiled.fieldsByName()).containsKey("id");
            assertThat(compiled.fieldsByName()).containsKey("name");
        }

        @Test
        @DisplayName("compiled config has version number")
        void compiledConfigHasVersion() {
            RawCollectionConfig raw = buildRaw("versioned", "ENTITY");
            CompiledCollectionConfig compiled = compiler.compile(raw);
            assertThat(compiled.configVersion()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("version increments across compilations")
        void versionIncrementsAcrossCompilations() {
            CompiledCollectionConfig first = compiler.compile(buildRaw("col1", "ENTITY"));
            CompiledCollectionConfig second = compiler.compile(buildRaw("col2", "ENTITY"));
            assertThat(second.configVersion()).isGreaterThan(first.configVersion());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static RawCollectionConfig buildRaw(String name, String recordType) {
        return buildRawWithSchema(name, recordType,
                new RawSchema("1.0", null, List.of()));
    }

    private static RawCollectionConfig buildRawWithSchema(String name, String recordType, RawSchema schema) {
        RawMetadata metadata = new RawMetadata(name, "default", Map.of(), Map.of());
        RawSpec spec = new RawSpec(
                recordType,     // recordType
                name,           // displayName
                null,           // description
                null,           // icon
                schema,         // schema
                new RawStorage("default", "id", null, List.of(), false), // storage
                List.of(),      // indexes
                null,           // lifecycle
                null,           // permissions
                List.of(),      // policies
                null,           // eventSourcing
                null,           // streaming
                null            // replay
        );
        return new RawCollectionConfig("v1", "Collection", metadata, spec);
    }
}
