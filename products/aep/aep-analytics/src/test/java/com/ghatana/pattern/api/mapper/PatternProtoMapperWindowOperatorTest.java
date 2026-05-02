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
@DisplayName("PatternProtoMapper — window & operator spec mapping")
class PatternProtoMapperWindowOperatorTest {

    // ─────────────────────────────────────────────────────────────
    // mapWindowSpecToProtoString
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null spec → empty string")
    void windowSpec_nullSpec_returnsEmpty() { 
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(null)).isEmpty(); 
    }

    @Test
    @DisplayName("spec with null window → empty string")
    void windowSpec_nullWindow_returnsEmpty() { 
        PatternSpecification spec = PatternSpecification.builder() 
                .id(java.util.UUID.randomUUID()) 
                .name("test")
                .tenantId("t1")
                .windowDuration(null) 
                .build(); 
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEmpty(); 
    }

    @Test
    @DisplayName("spec with zero duration → empty string")
    void windowSpec_zeroDuration_returnsEmpty() { 
        PatternSpecification spec = PatternSpecification.builder() 
                .id(java.util.UUID.randomUUID()) 
                .name("test")
                .tenantId("t1")
                .windowDuration(Duration.ZERO) 
                .build(); 
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEmpty(); 
    }

    @Test
    @DisplayName("spec with 30s window → ISO-8601 PT30S")
    void windowSpec_thirtySeconds_returnsIsoDuration() { 
        PatternSpecification spec = PatternSpecification.builder() 
                .id(java.util.UUID.randomUUID()) 
                .name("test")
                .tenantId("t1")
                .windowDuration(Duration.ofSeconds(30)) 
                .build(); 
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEqualTo("PT30S");
    }

    @Test
    @DisplayName("spec with 5 min window → ISO-8601 PT5M")
    void windowSpec_fiveMinutes_returnsIsoDuration() { 
        PatternSpecification spec = PatternSpecification.builder() 
                .id(java.util.UUID.randomUUID()) 
                .name("test")
                .tenantId("t1")
                .windowDuration(Duration.ofMinutes(5)) 
                .build(); 
        assertThat(PatternProtoMapper.mapWindowSpecToProtoString(spec)).isEqualTo("PT5M");
    }

    // ─────────────────────────────────────────────────────────────
    // mapWindowSpecFromProtoString
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null string → Duration.ZERO")
    void windowFromProto_null_returnsZero() { 
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString(null)).isEqualTo(Duration.ZERO); 
    }

    @Test
    @DisplayName("blank string → Duration.ZERO")
    void windowFromProto_blank_returnsZero() { 
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString("")).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("invalid ISO-8601 → Duration.ZERO (no exception thrown)")
    void windowFromProto_invalid_returnsZero() { 
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString("not-a-duration")).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("valid ISO-8601 PT30S → 30 seconds")
    void windowFromProto_pt30s_returns30Seconds() { 
        assertThat(PatternProtoMapper.mapWindowSpecFromProtoString("PT30S"))
                .isEqualTo(Duration.ofSeconds(30)); 
    }

    @Test
    @DisplayName("round-trip: toProtoString → fromProtoString")
    void windowSpec_roundTrip() { 
        Duration original = Duration.ofMinutes(7).plusSeconds(45); 
        PatternSpecification spec = PatternSpecification.builder() 
                .id(java.util.UUID.randomUUID()) 
                .name("rt-test")
                .tenantId("t1")
                .windowDuration(original) 
                .build(); 

        String protoStr = PatternProtoMapper.mapWindowSpecToProtoString(spec); 
        Duration recovered = PatternProtoMapper.mapWindowSpecFromProtoString(protoStr); 

        assertThat(recovered).isEqualTo(original); 
    }

    // ─────────────────────────────────────────────────────────────
    // mapOperatorSpecToMetadataString
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null operatorSpec → empty string")
    void operatorSpec_null_returnsEmpty() { 
        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(null)).isEmpty(); 
    }

    @Test
    @DisplayName("leaf operator (no children) → type:1")
    void operatorSpec_leafOperator_returnsOne() { 
        PatternSpecification.OperatorSpec leaf = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH")
                .children(java.util.List.of()) 
                .build(); 
        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(leaf)).isEqualTo("MATCH:1");
    }

    @Test
    @DisplayName("AND operator with 3 leaf children → AND:3")
    void operatorSpec_andWithThreeLeaves_returnsThree() { 
        PatternSpecification.OperatorSpec leafA = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafB = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafC = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH").children(java.util.List.of()).build();

        PatternSpecification.OperatorSpec and = PatternSpecification.OperatorSpec.builder() 
                .type("AND")
                .children(java.util.List.of(leafA, leafB, leafC)) 
                .build(); 

        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(and)).isEqualTo("AND:3");
    }

    @Test
    @DisplayName("nested OR(AND(leaf, leaf), leaf) → OR:3 (sum of all leaves)")
    void operatorSpec_nestedOperators_sumAllLeaves() { 
        PatternSpecification.OperatorSpec leafA = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafB = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH").children(java.util.List.of()).build();
        PatternSpecification.OperatorSpec leafC = PatternSpecification.OperatorSpec.builder() 
                .type("MATCH").children(java.util.List.of()).build();

        PatternSpecification.OperatorSpec and = PatternSpecification.OperatorSpec.builder() 
                .type("AND")
                .children(java.util.List.of(leafA, leafB)) 
                .build(); 
        PatternSpecification.OperatorSpec or = PatternSpecification.OperatorSpec.builder() 
                .type("OR")
                .children(java.util.List.of(and, leafC)) 
                .build(); 

        assertThat(PatternProtoMapper.mapOperatorSpecToMetadataString(or)).isEqualTo("OR:3");
    }
}
