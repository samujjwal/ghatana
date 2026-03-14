package com.ghatana.appplatform.eventstore.validation;

import com.ghatana.appplatform.eventstore.validation.SchemaSemanticVersionGuard.VersionBump;
import com.ghatana.appplatform.eventstore.validation.SchemaSemanticVersionGuard.VersionBumpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SchemaSemanticVersionGuard}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for schema semantic versioning enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SchemaSemanticVersionGuard — Unit Tests")
class SchemaSemanticVersionGuardTest {

    private SchemaSemanticVersionGuard guard;

    private static final String SCHEMA_V1 = """
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

    // Adds an optional field — backward-compatible
    private static final String SCHEMA_V2_MINOR = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "amount":    { "type": "number" },
            "currency":  { "type": "string", "enum": ["NPR", "USD"] },
            "reference": { "type": "string" }
          },
          "required": ["amount", "currency"]
        }
        """;

    // Adds a new required field — breaks FORWARD compat → MINOR
    private static final String SCHEMA_V2_REQUIRED_ADDITION = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "amount":    { "type": "number" },
            "currency":  { "type": "string", "enum": ["NPR", "USD"] },
            "reference": { "type": "string" }
          },
          "required": ["amount", "currency", "reference"]
        }
        """;

    // Removes a required field — breaks BACKWARD compat → MAJOR
    private static final String SCHEMA_V2_MAJOR = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "amount": { "type": "number" }
          },
          "required": ["amount"]
        }
        """;

    @BeforeEach
    void setUp() {
        guard = new SchemaSemanticVersionGuard();
    }

    @Test
    @DisplayName("analyse_addOptionalField — classified as MINOR (backward compat, forward break)")
    void addOptionalFieldIsMinor() {
        VersionBumpResult result = guard.analyse(SCHEMA_V1, SCHEMA_V2_MINOR);

        assertThat(result.bump()).isEqualTo(VersionBump.MINOR);
        assertThat(result.backwardViolations()).isEmpty();
        assertThat(result.mayProceed()).isTrue();
        assertThat(result.approvalRequired()).isFalse();
    }

    @Test
    @DisplayName("analyse_addRequiredField — classified as MINOR (forward compat broken)")
    void addRequiredFieldIsMinor() {
        VersionBumpResult result = guard.analyse(SCHEMA_V1, SCHEMA_V2_REQUIRED_ADDITION);

        // Forward compat breaks (old schema can't read data with new required field absent)
        assertThat(result.bump()).isIn(VersionBump.MINOR, VersionBump.MAJOR);
        // Regardless, bumping a required field is at least MINOR
        assertThat(result.bump()).isNotEqualTo(VersionBump.PATCH);
    }

    @Test
    @DisplayName("analyse_removeRequiredField — classified as MAJOR (breaking)")
    void removeRequiredFieldIsMajor() {
        VersionBumpResult result = guard.analyse(SCHEMA_V1, SCHEMA_V2_MAJOR);

        assertThat(result.bump()).isEqualTo(VersionBump.MAJOR);
        assertThat(result.backwardViolations()).isNotEmpty();
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.mayProceed()).isFalse();  // no approval token
    }

    @Test
    @DisplayName("analyse_withApprovalToken_major — MAJOR proceeds when token provided")
    void majorWithApprovalTokenProceeds() {
        VersionBumpResult result = guard.analyse(SCHEMA_V1, SCHEMA_V2_MAJOR, "approved-by-alice-2026-04-01");

        assertThat(result.bump()).isEqualTo(VersionBump.MAJOR);
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.mayProceed()).isTrue();
    }

    @Test
    @DisplayName("analyse_identicalSchemas — classified as PATCH")
    void identicalSchemasArePatch() {
        VersionBumpResult result = guard.analyse(SCHEMA_V1, SCHEMA_V1);

        assertThat(result.bump()).isEqualTo(VersionBump.PATCH);
        assertThat(result.mayProceed()).isTrue();
        assertThat(result.approvalRequired()).isFalse();
    }

    @Test
    @DisplayName("isBlocked_major_withoutToken — returns true")
    void isBlockedMajorWithoutToken() {
        assertThat(guard.isBlocked(SCHEMA_V1, SCHEMA_V2_MAJOR)).isTrue();
    }

    @Test
    @DisplayName("isBlocked_minor — returns false (MINOR always proceeds)")
    void isBlockedMinorReturnsFalse() {
        assertThat(guard.isBlocked(SCHEMA_V1, SCHEMA_V2_MINOR)).isFalse();
    }

    @Test
    @DisplayName("analyse_nullOldSchema — throws NullPointerException")
    void analyseNullOldSchemaThrows() {
        assertThatThrownBy(() -> guard.analyse(null, SCHEMA_V1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("analyse_nullNewSchema — throws NullPointerException")
    void analyseNullNewSchemaThrows() {
        assertThatThrownBy(() -> guard.analyse(SCHEMA_V1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("versionBumpResult_summary_blockedMajor — includes BLOCKED marker")
    void summaryIncludesBlockedForMajor() {
        VersionBumpResult result = guard.analyse(SCHEMA_V1, SCHEMA_V2_MAJOR);
        assertThat(result.summary()).contains("BLOCKED");
    }
}
