package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BuilderDocument schema compatibility")
class BuilderDocumentSchemaCompatibilityTest {

    private static final String CANONICAL_SCHEMA_PATH =
            "platform/typescript/ui-builder/src/schema/builder-document-v1.schema.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("accepts a valid fixture using the canonical TypeScript schema")
    void acceptsValidFixture() throws Exception {
        BuilderDocumentSchema.ValidationResult result = validateFixture("valid-minimal.builder-document.json");

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("rejects missing root and empty document graph")
    void rejectsMissingRoot() throws Exception {
        BuilderDocumentSchema.ValidationResult result = validateFixture("invalid-missing-root.builder-document.json");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("root"));
    }

    @Test
    @DisplayName("rejects future top-level fields unless compatibility policy is explicit")
    void rejectsFutureTopLevelField() throws Exception {
        BuilderDocumentSchema.ValidationResult result = validateFixture("invalid-future-field.builder-document.json");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("additional properties"));
    }

    @Test
    @DisplayName("rejects unsafe preview policy even when metadata is schema-extensible")
    void rejectsUnsafePreviewPolicy() throws Exception {
        BuilderDocumentSchema.ValidationResult result = validateFixture("unsafe-preview-policy.builder-document.json");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("metadata.previewSecurityPolicy is unsafe for YAPPC preview execution");
    }

    @Test
    @DisplayName("Java record version stays aligned with TypeScript schema version")
    void schemaVersionMatchesCanonicalSchema() throws Exception {
        JsonNode schema = OBJECT_MAPPER.readTree(canonicalSchemaPath().toFile());

        assertThat(schema.path("properties").path("schemaVersion").path("const").asText())
                .isEqualTo(BuilderDocumentSchema.CURRENT_SCHEMA_VERSION);
    }

    private BuilderDocumentSchema.ValidationResult validateFixture(String fixtureName) throws IOException {
        try (
                InputStream schema = Files.newInputStream(canonicalSchemaPath());
                InputStream fixture = fixture(fixtureName)
        ) {
            return BuilderDocumentSchema.validateAgainstCanonicalSchema(schema, fixture);
        }
    }

    private static InputStream fixture(String name) {
        InputStream stream = BuilderDocumentSchemaCompatibilityTest.class
                .getResourceAsStream("/builder-document/" + name);
        if (stream == null) {
            throw new IllegalStateException("Missing BuilderDocument fixture: " + name);
        }
        return stream;
    }

    private static Path canonicalSchemaPath() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(CANONICAL_SCHEMA_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate canonical BuilderDocument schema at " + CANONICAL_SCHEMA_PATH);
    }
}
