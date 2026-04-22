package com.ghatana.pattern.api.mapper;

import com.ghatana.pattern.api.model.PatternSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the new window-spec and operator-spec mapping methods in {@link PatternProtoMapper}.
 *
 * @doc.type class
 * @doc.purpose Verifies window/operator-spec proto-string conversions
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("PatternProtoMapper — window & operator spec mapping [GH-90000]")
class PatternProtoMapperWindowOperatorTest {

    // ─────────────────────────────────────────────────────────────
    // mapWindowSpecToProtoString
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null spec → empty string [GH-90000]")
    void windowSpec_nullSpec_returnsEmpty() { // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(null)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("spec with null window → empty string [GH-90000]")
    void windowSpec_nullWindow_returnsEmpty() { // GH-90000
        PatternSpecification spec = PatternSpecification.builder() // GH-90000
                .id(java.util.UUID.randomUUID()) // GH-90000
                .name("test [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .windowDuration(null) // GH-90000
                .build(); // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("spec with zero duration → empty string [GH-90000]")
    void windowSpec_zeroDuration_returnsEmpty() { // GH-90000
        PatternSpecification spec = PatternSpecification.builder() // GH-90000
                .id(java.util.UUID.randomUUID()) // GH-90000
                .name("test [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .windowDuration(Duration.ZERO) // GH-90000
                .build(); // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("spec with 30s window → ISO-8601 PT30S [GH-90000]")
    void windowSpec_thirtySeconds_returnsIsoDuration() { // GH-90000
        PatternSpecification spec = PatternSpecification.builder() // GH-90000
                .id(java.util.UUID.randomUUID()) // GH-90000
                .name("test [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .windowDuration(Duration.ofSeconds(30)) // GH-90000
                .build(); // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEqualTo("PT30S [GH-90000]");
    }

    @Test
    @DisplayName("spec with 5 min window → ISO-8601 PT5M [GH-90000]")
    void windowSpec_fiveMinutes_returnsIsoDuration() { // GH-90000
        PatternSpecification spec = PatternSpecification.builder() // GH-90000
                .id(java.util.UUID.randomUUID()) // GH-90000
                .name("test [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .windowDuration(Duration.ofMinutes(5)) // GH-90000
                .build(); // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEqualTo("PT5M [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────
    // mapWindowSpecFromProtoString
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null string → Duration.ZERO [GH-90000]")
    void windowFromProto_null_returnsZero() { // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString(null)).isEqualTo(Duration.ZERO); // GH-90000
    }

    @Test
    @DisplayName("blank string → Duration.ZERO [GH-90000]")
    void windowFromProto_blank_returnsZero() { // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString(" [GH-90000]")).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("invalid ISO-8601 → Duration.ZERO (no exception thrown) [GH-90000]")
    void windowFromProto_invalid_returnsZero() { // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString("not-a-duration [GH-90000]")).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("valid ISO-8601 PT30S → 30 seconds [GH-90000]")
    void windowFromProto_pt30s_returns30Seconds() { // GH-90000
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString("PT30S [GH-90000]"))
                .isEqualTo(Duration.ofSeconds(30)); // GH-90000
    }

    @Test
    @DisplayName("round-trip: toProtoString → fromProtoString [GH-90000]")
    void windowSpec_roundTrip() { // GH-90000
        Duration original = Duration.ofMinutes(7).plusSeconds(45); // GH-90000
        PatternSpecification spec = PatternSpecification.builder() // GH-90000
                .id(java.util.UUID.randomUUID()) // GH-90000
                .name("rt-test [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .windowDuration(original) // GH-90000
                .build(); // GH-90000

        String protoStr = PatternProtoMapper.mapWindowSpecToProtoString(spec); // GH-90000
        Duration recovered = PatternProtoMapper.mapWindowSpecFromProtoString(protoStr); // GH-90000

        assertThat(recovered).isEqualTo(original); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // mapOperatorSpecToMetadataString
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null operatorSpec → empty string [GH-90000]")
    void operatorSpec_null_returnsEmpty() { // GH-90000
        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(null)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("leaf operator (no children) → type:1 [GH-90000]")
    void operatorSpec_leafOperator_returnsOne() { // GH-90000
        PatternSpecification.OperatorSpec leaf = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]")
                .children(java.util.List.of()) // GH-90000
                .build(); // GH-90000
        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(leaf)).isEqualTo("MATCH:1 [GH-90000]");
    }

    @Test
    @DisplayName("AND operator with 3 leaf children → AND:3 [GH-90000]")
    void operatorSpec_andWithThreeLeaves_returnsThree() { // GH-90000
        PatternSpecification.OperatorSpec leafA = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafB = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafC = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]").children(java.util.List.of()).build();

        PatternSpecification.OperatorSpec and = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("AND [GH-90000]")
                .children(java.util.List.of(leafA, leafB, leafC)) // GH-90000
                .build(); // GH-90000

        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(and)).isEqualTo("AND:3 [GH-90000]");
    }

    @Test
    @DisplayName("nested OR(AND(leaf, leaf), leaf) → OR:3 (sum of all leaves) [GH-90000]")
    void operatorSpec_nestedOperators_sumAllLeaves() { // GH-90000
        PatternSpecification.OperatorSpec leafA = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafB = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafC = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("MATCH [GH-90000]").children(java.util.List.of()).build();

        PatternSpecification.OperatorSpec and = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("AND [GH-90000]")
                .children(java.util.List.of(leafA, leafB)) // GH-90000
                .build(); // GH-90000
        PatternSpecification.OperatorSpec or = PatternSpecification.OperatorSpec.builder() // GH-90000
                .type("OR [GH-90000]")
                .children(java.util.List.of(and, leafC)) // GH-90000
                .build(); // GH-90000

        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(or)).isEqualTo("OR:3 [GH-90000]");
    }
}
