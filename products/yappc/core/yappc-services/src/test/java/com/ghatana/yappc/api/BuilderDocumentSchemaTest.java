package com.ghatana.yappc.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BuilderDocumentSchema}.
 *
 * <p>Verifies that the canonical JSON-Schema-backed validation and YAPPC compatibility
 * policy checks behave correctly for known-good and known-bad fixtures. The JSON schema
 * is owned by the {@code @ghatana/ui-builder} TypeScript package; tests here consume
 * it through the packaged resource rather than duplicating the schema in Java.</p>
 *
 * @doc.type test
 * @doc.purpose Verify BuilderDocument schema validation and YAPPC policy enforcement.
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("BuilderDocumentSchema Tests")
class BuilderDocumentSchemaTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  // ---------------------------------------------------------------------------
  // CURRENT_SCHEMA_VERSION constant
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CURRENT_SCHEMA_VERSION is 1.0.0")
  void currentSchemaVersionIs1_0_0() {
    assertThat(BuilderDocumentSchema.CURRENT_SCHEMA_VERSION).isEqualTo("1.0.0");
  }

  // ---------------------------------------------------------------------------
  // validateAgainstCanonicalSchema — valid documents
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("valid minimal document passes schema validation")
  void validMinimalDocumentPassesSchemaValidation() throws IOException {
    BuilderDocumentSchema.ValidationResult result = validateFixture(
        "builder-document-v1.schema.json",
        "builder-document/valid-minimal.builder-document.json"
    );

    assertThat(result.valid())
        .as("Expected valid minimal document to pass schema validation, but got errors: %s", result.errors())
        .isTrue();
    assertThat(result.errors()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // validateAgainstCanonicalSchema — invalid documents
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("document with missing root node fails schema validation")
  void documentWithMissingRootFailsValidation() throws IOException {
    BuilderDocumentSchema.ValidationResult result = validateFixture(
        "builder-document-v1.schema.json",
        "builder-document/invalid-missing-root.builder-document.json"
    );

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  @Test
  @DisplayName("document with unknown future field fails policy when schema is strict")
  void unknownFutureFieldDocumentBehavesCorrectly() throws IOException {
    // This fixture has an unexpected extra field; the strict schema should
    // reject it (additionalProperties: false) or it should pass if the schema
    // is lenient — in either case the result should be deterministic.
    BuilderDocumentSchema.ValidationResult result = validateFixture(
        "builder-document-v1.schema.json",
        "builder-document/invalid-future-field.builder-document.json"
    );

    // The fixture is named "invalid" — verify the validator surfaced at least
    // one error.
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  // ---------------------------------------------------------------------------
  // YAPPC compatibility policy
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("document with unsafe preview security policy fails YAPPC compatibility check")
  void unsafePreviewPolicyFailsYappcCompatibilityCheck() throws IOException {
    BuilderDocumentSchema.ValidationResult result = validateFixture(
        "builder-document-v1.schema.json",
        "builder-document/unsafe-preview-policy.builder-document.json"
    );

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(
        error -> error.contains("previewSecurityPolicy") && error.contains("unsafe")
    );
  }

  // ---------------------------------------------------------------------------
  // validateAgainstCanonicalSchema — in-memory document
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("minimal in-memory valid document passes validation")
  void minimalInMemoryDocumentPassesValidation() throws IOException {
    String minimalDoc = """
        {
          "schemaVersion": "1.0.0",
          "documentId": "in-memory-doc",
          "owner": "test-owner",
          "root": "root",
          "nodes": {
            "root": {
              "id": "root",
              "contractName": "layout.page",
              "props": {},
              "slots": { "default": [] },
              "bindings": [],
              "metadata": {}
            }
          },
          "bindings": [],
          "layout": {
            "type": "flow",
            "rootId": "root",
            "nodes": {
              "root": {
                "id": "root",
                "type": "root",
                "children": []
              }
            }
          },
          "metadata": {
            "createdAt": "2026-01-01T00:00:00Z",
            "updatedAt": "2026-01-01T00:00:00Z"
          }
        }
        """;

    try (InputStream schemaStream = loadResource("builder-document-v1.schema.json")) {
      JsonNode schemaNode = OBJECT_MAPPER.readTree(schemaStream);
      JsonNode documentNode = OBJECT_MAPPER.readTree(minimalDoc);
      BuilderDocumentSchema.ValidationResult result =
          BuilderDocumentSchema.validateAgainstCanonicalSchema(schemaNode, documentNode);
      assertThat(result.valid())
          .as("In-memory minimal document should be valid, errors: %s", result.errors())
          .isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static BuilderDocumentSchema.ValidationResult validateFixture(
      String schemaResourcePath,
      String documentResourcePath
  ) throws IOException {
    try (
        InputStream schemaStream = loadResource(schemaResourcePath);
        InputStream documentStream = loadResource(documentResourcePath)
    ) {
      return BuilderDocumentSchema.validateAgainstCanonicalSchema(schemaStream, documentStream);
    }
  }

  private static InputStream loadResource(String resourcePath) {
    InputStream stream = BuilderDocumentSchemaTest.class.getClassLoader()
        .getResourceAsStream(resourcePath);
    if (stream == null) {
      throw new IllegalStateException(
          "Test resource not found on classpath: " + resourcePath
              + " — ensure @ghatana/ui-builder schema is packaged into test resources."
      );
    }
    return stream;
  }
}
