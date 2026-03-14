package com.ghatana.appplatform.eventstore.validation;

import com.ghatana.appplatform.eventstore.validation.SchemaBreakingChangeDetector.BreakingChangeReport;
import com.ghatana.appplatform.eventstore.validation.SchemaBreakingChangeDetector.MigrationPlan;
import com.ghatana.appplatform.eventstore.validation.SchemaBreakingChangeDetector.MigrationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SchemaBreakingChangeDetector}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for schema breaking change detection and migration plan gate (STORY-K05-030)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SchemaBreakingChangeDetector — Unit Tests")
class SchemaBreakingChangeDetectorTest {

    private SchemaBreakingChangeDetector detector;

    // Baseline schema: two properties, one required
    private static final String OLD_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "orderId":  { "type": "string" },
            "amount":   { "type": "number" },
            "currency": { "type": "string", "enum": ["NPR","USD","EUR"] }
          },
          "required": ["orderId", "amount"]
        }
        """;

    @BeforeEach
    void setUp() { detector = new SchemaBreakingChangeDetector(); }

    @Test
    @DisplayName("detect — removing required field from properties is a breaking change")
    void detect_removedRequiredField_reportsBreaking() {
        // Remove 'orderId' entirely from properties
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "amount":   { "type": "number" },
                "currency": { "type": "string", "enum": ["NPR","USD","EUR"] }
              },
              "required": ["amount"]
            }
            """;

        BreakingChangeReport report = detector.detect(OLD_SCHEMA, newSchema);

        assertThat(report.hasBreakingChanges()).isTrue();
        assertThat(report.removedRequiredFields()).containsExactly("orderId");
        assertThat(report.requiresMigrationPlan()).isTrue();
    }

    @Test
    @DisplayName("detect — type change on an existing field is a breaking change")
    void detect_typeChange_reportsBreaking() {
        // Change 'amount' from number → string
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "orderId":  { "type": "string" },
                "amount":   { "type": "string" },
                "currency": { "type": "string", "enum": ["NPR","USD","EUR"] }
              },
              "required": ["orderId", "amount"]
            }
            """;

        BreakingChangeReport report = detector.detect(OLD_SCHEMA, newSchema);

        assertThat(report.hasBreakingChanges()).isTrue();
        assertThat(report.typeChangedFields()).containsExactly("amount");
    }

    @Test
    @DisplayName("detect — enum narrowing (removing a value) is a breaking change")
    void detect_enumNarrowing_reportsBreaking() {
        // Remove 'EUR' from currency enum
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "orderId":  { "type": "string" },
                "amount":   { "type": "number" },
                "currency": { "type": "string", "enum": ["NPR","USD"] }
              },
              "required": ["orderId", "amount"]
            }
            """;

        BreakingChangeReport report = detector.detect(OLD_SCHEMA, newSchema);

        assertThat(report.hasBreakingChanges()).isTrue();
        assertThat(report.narrowedEnumFields()).containsExactly("currency");
    }

    @Test
    @DisplayName("detect — adding an optional field is not a breaking change")
    void detect_addOptionalField_noBreakingChange() {
        // Add new optional field 'note'
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "orderId":  { "type": "string" },
                "amount":   { "type": "number" },
                "currency": { "type": "string", "enum": ["NPR","USD","EUR"] },
                "note":     { "type": "string" }
              },
              "required": ["orderId", "amount"]
            }
            """;

        BreakingChangeReport report = detector.detect(OLD_SCHEMA, newSchema);

        assertThat(report.hasBreakingChanges()).isFalse();
        assertThat(report.removedRequiredFields()).isEmpty();
        assertThat(report.typeChangedFields()).isEmpty();
        assertThat(report.narrowedEnumFields()).isEmpty();
    }

    @Test
    @DisplayName("detect — identical schemas produce a clean report")
    void detect_identicalSchemas_noBreakingChange() {
        BreakingChangeReport report = detector.detect(OLD_SCHEMA, OLD_SCHEMA);

        assertThat(report.hasBreakingChanges()).isFalse();
        assertThat(report.summary()).contains("No breaking changes");
    }

    @Test
    @DisplayName("mayRegister(old, new) — allows registration when no breaking changes")
    void mayRegister_noBreakingChanges_returnsTrue() {
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "orderId":  { "type": "string" },
                "amount":   { "type": "number" },
                "currency": { "type": "string", "enum": ["NPR","USD","EUR"] },
                "note":     { "type": "string" }
              },
              "required": ["orderId", "amount"]
            }
            """;

        assertThat(detector.mayRegister(OLD_SCHEMA, newSchema)).isTrue();
    }

    @Test
    @DisplayName("mayRegister(old, new) — blocks registration on breaking changes without a plan")
    void mayRegister_breakingChange_returnsFalseWithoutPlan() {
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "amount": { "type": "number" }
              },
              "required": ["amount"]
            }
            """;

        assertThat(detector.mayRegister(OLD_SCHEMA, newSchema)).isFalse();
    }

    @Test
    @DisplayName("mayRegister(old, new, plan) — allows registration when plan covers removed fields")
    void mayRegister_withValidPlan_allowsRegistration() {
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "amount": { "type": "number" }
              },
              "required": ["amount"]
            }
            """;

        MigrationPlan plan = new MigrationPlan(
            "plan-001", "compliance@ghatana.com", "OrderCreated",
            Set.of("orderId"),
            MigrationStrategy.BACKFILL,
            "orderId values will be backfilled from the orders table."
        );

        assertThat(detector.mayRegister(OLD_SCHEMA, newSchema, plan)).isTrue();
    }

    @Test
    @DisplayName("mayRegister(old, new, plan) — blocks when plan does not cover all removed fields")
    void mayRegister_planMissingField_blocks() {
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "currency": { "type": "string" }
              },
              "required": []
            }
            """;
        // orderId AND amount are removed; plan only covers orderId
        MigrationPlan plan = new MigrationPlan(
            "plan-002", "dev@ghatana.com", "OrderCreated",
            Set.of("orderId"),  // missing "amount"
            MigrationStrategy.DEFAULT_VALUE,
            "orderId backfill only."
        );

        assertThat(detector.mayRegister(OLD_SCHEMA, newSchema, plan)).isFalse();
    }

    @Test
    @DisplayName("mayRegister(old, new, null) — null plan blocks breaking changes")
    void mayRegister_nullPlan_blocks() {
        String newSchema = """
            {
              "type": "object",
              "properties": {
                "amount": { "type": "number" }
              },
              "required": ["amount"]
            }
            """;

        assertThat(detector.mayRegister(OLD_SCHEMA, newSchema, null)).isFalse();
    }

    @Test
    @DisplayName("MigrationPlan — blank planId throws IllegalArgumentException")
    void migrationPlan_blankPlanId_throws() {
        assertThatThrownBy(() -> new MigrationPlan(
            "  ", "author", "OrderCreated", Set.of("field"),
            MigrationStrategy.BACKFILL, "justification"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("planId");
    }

    @Test
    @DisplayName("BreakingChangeReport.summary — includes field names when breaking")
    void report_summary_mentionsFieldNames() {
        BreakingChangeReport report = detector.detect(OLD_SCHEMA, """
            {
              "type": "object",
              "properties": { "amount": { "type": "number" } },
              "required": ["amount"]
            }
            """);

        assertThat(report.summary())
            .contains("orderId")
            .contains("Breaking");
    }
}
