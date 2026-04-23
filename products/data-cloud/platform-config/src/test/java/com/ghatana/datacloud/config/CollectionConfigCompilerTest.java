/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        compiler = new CollectionConfigCompiler(); // GH-90000
    }

    @Nested
    @DisplayName("Standard Collection Compilation")
    class StandardCollectionTests {

        @Test
        @DisplayName("compiles a minimal entity collection")
        void compilesMinimalEntityCollection() { // GH-90000
            RawCollectionConfig raw = buildRaw("users", "ENTITY"); // GH-90000

            CompiledCollectionConfig compiled = compiler.compile(raw); // GH-90000

            assertThat(compiled).isNotNull(); // GH-90000
            assertThat(compiled.name()).isEqualTo("users");
        }

        @Test
        @DisplayName("compiles document collection type")
        void compilesDocumentCollection() { // GH-90000
            RawCollectionConfig raw = buildRaw("docs", "DOCUMENT"); // GH-90000

            CompiledCollectionConfig compiled = compiler.compile(raw); // GH-90000

            assertThat(compiled.name()).isEqualTo("docs");
        }

        @Test
        @DisplayName("throws ConfigurationException for event record type")
        void throwsForEventRecordType() { // GH-90000
            RawCollectionConfig raw = buildRaw("events", "EVENT"); // GH-90000

            assertThatThrownBy(() -> compiler.compile(raw)) // GH-90000
                    .isInstanceOf(ConfigurationException.class) // GH-90000
                    .hasMessageContaining("compileEventCollection");
        }

        @Test
        @DisplayName("throws NullPointerException when null is passed")
        void throwsForNullInput() { // GH-90000
            assertThatThrownBy(() -> compiler.compile(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("compiled result has fields from schema")
        void compiledResultHasFields() { // GH-90000
            RawSchema schema = new RawSchema("1.0", null, List.of( // GH-90000
                    new RawField("id", "string", null, true, true, true, // GH-90000
                            false, true, true, null, null, null, null,
                            List.of(), null, null, "Primary ID", null, null), // GH-90000
                    new RawField("name", "string", null, false, false, false, // GH-90000
                            false, false, false, null, 1, 255, null,
                            List.of(), null, null, "Display name", null, null) // GH-90000
            ));
            RawCollectionConfig raw = buildRawWithSchema("customers", "ENTITY", schema); // GH-90000

            CompiledCollectionConfig compiled = compiler.compile(raw); // GH-90000

            assertThat(compiled.fields()).isNotNull(); // GH-90000
            assertThat(compiled.fields()).hasSize(2); // GH-90000
            assertThat(compiled.fieldsByName()).containsKey("id");
            assertThat(compiled.fieldsByName()).containsKey("name");
        }

        @Test
        @DisplayName("compiled config has version number")
        void compiledConfigHasVersion() { // GH-90000
            RawCollectionConfig raw = buildRaw("versioned", "ENTITY"); // GH-90000
            CompiledCollectionConfig compiled = compiler.compile(raw); // GH-90000
            assertThat(compiled.configVersion()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("version increments across compilations")
        void versionIncrementsAcrossCompilations() { // GH-90000
            CompiledCollectionConfig first = compiler.compile(buildRaw("col1", "ENTITY")); // GH-90000
            CompiledCollectionConfig second = compiler.compile(buildRaw("col2", "ENTITY")); // GH-90000
            assertThat(second.configVersion()).isGreaterThan(first.configVersion()); // GH-90000
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static RawCollectionConfig buildRaw(String name, String recordType) { // GH-90000
        return buildRawWithSchema(name, recordType, // GH-90000
                new RawSchema("1.0", null, List.of())); // GH-90000
    }

    private static RawCollectionConfig buildRawWithSchema(String name, String recordType, RawSchema schema) { // GH-90000
        RawMetadata metadata = new RawMetadata(name, "default", Map.of(), Map.of()); // GH-90000
        RawSpec spec = new RawSpec( // GH-90000
                recordType,     // recordType
                name,           // displayName
                null,           // description
                null,           // icon
                schema,         // schema
                new RawStorage("default", "id", null, List.of(), false), // storage // GH-90000
                List.of(),      // indexes // GH-90000
                null,           // lifecycle
                null,           // permissions
                List.of(),      // policies // GH-90000
                null,           // eventSourcing
                null,           // streaming
                null            // replay
        );
        return new RawCollectionConfig("v1", "Collection", metadata, spec); // GH-90000
    }
}
