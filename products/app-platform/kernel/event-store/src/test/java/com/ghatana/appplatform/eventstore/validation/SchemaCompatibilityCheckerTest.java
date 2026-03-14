package com.ghatana.appplatform.eventstore.validation;

import com.ghatana.appplatform.eventstore.domain.CompatibilityType;
import com.ghatana.appplatform.eventstore.validation.SchemaCompatibilityChecker.CompatibilityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SchemaCompatibilityChecker}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for JSON schema compatibility rules
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SchemaCompatibilityChecker — Unit Tests")
class SchemaCompatibilityCheckerTest {

    private SchemaCompatibilityChecker checker;

    /** Minimal valid JSON Schema for an event with a required "amount" number field. */
    private static final String SCHEMA_V1 = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "amount": { "type": "number" },
            "currency": { "type": "string", "enum": ["NPR", "USD", "EUR"] }
          },
          "required": ["amount", "currency"]
        }
        """;

    @BeforeEach
    void setUp() {
        checker = new SchemaCompatibilityChecker();
    }

    @Test
    @DisplayName("addOptionalField_backward_isCompatible — new optional property is backward-compatible")
    void addOptionalFieldBackwardIsCompatible() {
        String schemaV2 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "amount":    { "type": "number" },
                "currency":  { "type": "string", "enum": ["NPR", "USD", "EUR"] },
                "reference": { "type": "string" }
              },
              "required": ["amount", "currency"]
            }
            """;

        CompatibilityResult result = checker.checkCompatibility(SCHEMA_V1, schemaV2, CompatibilityType.BACKWARD);

        assertThat(result.compatible()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("removeRequiredField_backward_isBreaking — removing a required field breaks backward compat")
    void removeRequiredFieldBackwardIsBreaking() {
        // V2 drops "currency" from both properties and required
        String schemaV2 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "amount": { "type": "number" }
              },
              "required": ["amount"]
            }
            """;

        CompatibilityResult result = checker.checkCompatibility(SCHEMA_V1, schemaV2, CompatibilityType.BACKWARD);

        assertThat(result.compatible()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("currency"));
    }

    @Test
    @DisplayName("changePropertyType_backward_isBreaking — type change breaks backward compat")
    void changePropertyTypeBackwardIsBreaking() {
        // V2 changes "amount" from number to string
        String schemaV2 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "amount":   { "type": "string" },
                "currency": { "type": "string", "enum": ["NPR", "USD", "EUR"] }
              },
              "required": ["amount", "currency"]
            }
            """;

        CompatibilityResult result = checker.checkCompatibility(SCHEMA_V1, schemaV2, CompatibilityType.BACKWARD);

        assertThat(result.compatible()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("amount") && v.contains("type changed"));
    }

    @Test
    @DisplayName("narrowEnum_backward_isBreaking — removing an enum value breaks backward compat")
    void narrowEnumBackwardIsBreaking() {
        // V2 removes "EUR" from the currency enum
        String schemaV2 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "amount":   { "type": "number" },
                "currency": { "type": "string", "enum": ["NPR", "USD"] }
              },
              "required": ["amount", "currency"]
            }
            """;

        CompatibilityResult result = checker.checkCompatibility(SCHEMA_V1, schemaV2, CompatibilityType.BACKWARD);

        assertThat(result.compatible()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("EUR"));
    }

    @Test
    @DisplayName("addRequiredField_forward_isBreaking — adding a new required field breaks forward compat")
    void addRequiredFieldForwardIsBreaking() {
        // V2 adds a new required field that old consumers cannot produce
        String schemaV2 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "amount":    { "type": "number" },
                "currency":  { "type": "string", "enum": ["NPR", "USD", "EUR"] },
                "tenantId":  { "type": "string" }
              },
              "required": ["amount", "currency", "tenantId"]
            }
            """;

        CompatibilityResult result = checker.checkCompatibility(SCHEMA_V1, schemaV2, CompatibilityType.FORWARD);

        assertThat(result.compatible()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("tenantId"));
    }

    @Test
    @DisplayName("noCompatType_alwaysCompatible — NONE skips all checks")
    void noCompatTypeAlwaysCompatible() {
        // Completely incompatible schema, but NONE means no check performed
        String schemaV2 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": { "x": { "type": "boolean" } },
              "required": []
            }
            """;

        CompatibilityResult result = checker.checkCompatibility(SCHEMA_V1, schemaV2, CompatibilityType.NONE);

        assertThat(result.compatible()).isTrue();
    }
}
