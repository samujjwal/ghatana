/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk.generation;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SDK client generation — schema discovery, client stub rendering,
 * template substitution, and multi-language output.
 *
 * @doc.type    class
 * @doc.purpose Tests for SDK client generation from schema definitions
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SDK Generation Tests")
class SDKGenerationTest extends EventloopTestBase {

    // ── Schema model ──────────────────────────────────────────────────────────

    enum LanguageTarget { JAVA, TYPESCRIPT, PYTHON }

    record FieldDef(String name, String type, boolean required) {}
    record ResourceSchema(String resourceName, List<FieldDef> fields, List<String> operations) {}
    record GeneratedStub(LanguageTarget language, String resourceName, String code) {}

    private SDKGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SDKGenerator();
    }

    // ── Schema discovery ──────────────────────────────────────────────────────

    @Test
    @DisplayName("discover returns registered schemas")
    void discoverReturnsRegisteredSchemas() {
        ResourceSchema schema = new ResourceSchema(
                "Collection",
                List.of(new FieldDef("id", "string", true),
                        new FieldDef("name", "string", true),
                        new FieldDef("tenantId", "string", true)),
                List.of("create", "read", "update", "delete", "list"));

        generator.registerSchema(schema);
        List<ResourceSchema> discovered = generator.discoverSchemas();

        assertThat(discovered).hasSize(1);
        assertThat(discovered.get(0).resourceName()).isEqualTo("Collection");
    }

    @Test
    @DisplayName("discover returns all registered schemas")
    void discoverReturnsAllRegisteredSchemas() {
        generator.registerSchema(new ResourceSchema("Collection", List.of(), List.of("create")));
        generator.registerSchema(new ResourceSchema("Entity", List.of(), List.of("read")));
        generator.registerSchema(new ResourceSchema("Event", List.of(), List.of("list")));

        assertThat(generator.discoverSchemas()).hasSize(3);
    }

    // ── Client stub generation ────────────────────────────────────────────────

    @Test
    @DisplayName("generate TypeScript stub includes resource class and method signatures")
    void generateTypeScriptStubIncludesClassAndMethods() {
        ResourceSchema schema = new ResourceSchema(
                "Entity",
                List.of(new FieldDef("id", "string", true)),
                List.of("create", "read", "delete"));
        generator.registerSchema(schema);

        GeneratedStub stub = generator.generate("Entity", LanguageTarget.TYPESCRIPT);

        assertThat(stub.language()).isEqualTo(LanguageTarget.TYPESCRIPT);
        assertThat(stub.resourceName()).isEqualTo("Entity");
        assertThat(stub.code()).contains("EntityClient");
        assertThat(stub.code()).contains("create");
        assertThat(stub.code()).contains("read");
        assertThat(stub.code()).contains("delete");
    }

    @Test
    @DisplayName("generate Java stub contains class declaration and method stubs")
    void generateJavaStubContainsClassDeclarationAndMethods() {
        ResourceSchema schema = new ResourceSchema(
                "Pipeline",
                List.of(new FieldDef("pipelineId", "string", true)),
                List.of("create", "list"));
        generator.registerSchema(schema);

        GeneratedStub stub = generator.generate("Pipeline", LanguageTarget.JAVA);

        assertThat(stub.code()).contains("PipelineClient");
        assertThat(stub.code()).contains("create");
        assertThat(stub.code()).contains("list");
    }

    @Test
    @DisplayName("generate Python stub contains class definition with methods")
    void generatePythonStubContainsClassDefinitionWithMethods() {
        ResourceSchema schema = new ResourceSchema(
                "Model",
                List.of(new FieldDef("modelId", "string", true)),
                List.of("register", "promote", "deprecate"));
        generator.registerSchema(schema);

        GeneratedStub stub = generator.generate("Model", LanguageTarget.PYTHON);

        assertThat(stub.code()).contains("ModelClient");
        assertThat(stub.code()).contains("register");
        assertThat(stub.code()).contains("promote");
    }

    @Test
    @DisplayName("generate fails when schema is not registered")
    void generateFailsForUnregisteredSchema() {
        assertThatThrownBy(() -> generator.generate("Ghost", LanguageTarget.JAVA))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Ghost");
    }

    // ── Field inclusion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("required fields are marked as non-optional in generated stub")
    void requiredFieldsMarkedAsNonOptionalInGeneratedStub() {
        ResourceSchema schema = new ResourceSchema(
                "Record",
                List.of(new FieldDef("id", "string", true),
                        new FieldDef("description", "string", false)),
                List.of("create"));
        generator.registerSchema(schema);

        GeneratedStub stub = generator.generate("Record", LanguageTarget.TYPESCRIPT);

        // Required field "id" should appear without optional marker, optional field with ?
        assertThat(stub.code()).contains("id: string");
        assertThat(stub.code()).contains("description?: string");
    }

    // ── Multi-language support ────────────────────────────────────────────────

    @Test
    @DisplayName("generating for all targets from the same schema produces distinct stubs")
    void generatingAllTargetsFromSameSchemaProducesDistinctStubs() {
        ResourceSchema schema = new ResourceSchema(
                "Tenant",
                List.of(new FieldDef("tenantId", "string", true)),
                List.of("create", "delete"));
        generator.registerSchema(schema);

        List<GeneratedStub> stubs = generator.generateAll("Tenant");

        assertThat(stubs).hasSize(LanguageTarget.values().length);
        Set<LanguageTarget> languages = new HashSet<>();
        for (GeneratedStub stub : stubs) { languages.add(stub.language()); }
        assertThat(languages).containsExactlyInAnyOrder(LanguageTarget.values());
    }

    // ── SDK generator implementation (for tests) ──────────────────────────────

    static class SDKGenerator {
        private final Map<String, ResourceSchema> schemas = new LinkedHashMap<>();

        void registerSchema(ResourceSchema schema) {
            schemas.put(schema.resourceName(), schema);
        }

        List<ResourceSchema> discoverSchemas() {
            return new ArrayList<>(schemas.values());
        }

        GeneratedStub generate(String resourceName, LanguageTarget target) {
            ResourceSchema schema = schemas.get(resourceName);
            if (schema == null) throw new NoSuchElementException("Schema not found: " + resourceName);
            String code = renderStub(schema, target);
            return new GeneratedStub(target, resourceName, code);
        }

        List<GeneratedStub> generateAll(String resourceName) {
            return Arrays.stream(LanguageTarget.values())
                    .map(t -> generate(resourceName, t))
                    .toList();
        }

        private String renderStub(ResourceSchema schema, LanguageTarget target) {
            String className = schema.resourceName() + "Client";
            StringBuilder sb = new StringBuilder();
            switch (target) {
                case TYPESCRIPT -> {
                    sb.append("class ").append(className).append(" {\n");
                    for (FieldDef f : schema.fields()) {
                        sb.append("  ").append(f.name());
                        if (!f.required()) sb.append("?");
                        sb.append(": ").append(f.type()).append(";\n");
                    }
                    for (String op : schema.operations()) {
                        sb.append("  ").append(op).append("(): Promise<void> {};\n");
                    }
                    sb.append("}\n");
                }
                case JAVA -> {
                    sb.append("public class ").append(className).append(" {\n");
                    for (String op : schema.operations()) {
                        sb.append("  public Promise<Void> ").append(op).append("() { return null; }\n");
                    }
                    sb.append("}\n");
                }
                case PYTHON -> {
                    sb.append("class ").append(className).append(":\n");
                    for (String op : schema.operations()) {
                        sb.append("    def ").append(op).append("(self): pass\n");
                    }
                }
            }
            return sb.toString();
        }
    }
}
