package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.ValidationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Built-in Operator Validation Tests [GH-90000]")
class BuiltinOperatorValidationTest {

    private static final ValidationContext CONTEXT = ValidationContext.builder() // GH-90000
            .patternId("pattern-1 [GH-90000]")
            .tenantId("tenant-1 [GH-90000]")
            .build(); // GH-90000

    @Test
    @DisplayName("SEQ validates valid sequence configuration [GH-90000]")
    void seqValidatesValidSequenceConfiguration() { // GH-90000
        SeqOperator operator = new SeqOperator(); // GH-90000
        OperatorSpec spec = OperatorSpec.builder() // GH-90000
                .type("SEQ [GH-90000]")
                .operand(leaf("LOGIN [GH-90000]"))
                .operand(leaf("PURCHASE [GH-90000]"))
                .parameter("maxGap", 60_000L) // GH-90000
                .parameter("strictOrder", true) // GH-90000
                .build(); // GH-90000

        assertDoesNotThrow(() -> operator.validate(spec, CONTEXT)); // GH-90000
        assertTrue(operator.supports(spec)); // GH-90000
        assertEquals("SEQ", operator.getType()); // GH-90000
        assertEquals(2, operator.getMetadata().getMinOperands()); // GH-90000
        assertTrue(operator.getMetadata().isSupportsStateful()); // GH-90000
        assertFalse(operator.getMetadata().isSupportsStateless()); // GH-90000
    }

    @Test
    @DisplayName("SEQ rejects invalid operands and parameters [GH-90000]")
    void seqRejectsInvalidOperandsAndParameters() { // GH-90000
        SeqOperator operator = new SeqOperator(); // GH-90000

        PatternValidationException missingOperandException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate(OperatorSpec.builder().type("SEQ [GH-90000]").operand(leaf("LOGIN [GH-90000]")).build(), CONTEXT));
        assertTrue(missingOperandException.getMessage().contains("at least 2 operands [GH-90000]"));

        PatternValidationException invalidGapException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("SEQ [GH-90000]")
                                .operand(leaf("LOGIN [GH-90000]"))
                                .operand(leaf("PURCHASE [GH-90000]"))
                                .parameter("maxGap", -1) // GH-90000
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(invalidGapException.getMessage().contains("non-negative [GH-90000]"));

        PatternValidationException invalidStrictOrderException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("SEQ [GH-90000]")
                                .operand(leaf("LOGIN [GH-90000]"))
                                .operand(leaf("PURCHASE [GH-90000]"))
                                .parameter("strictOrder", "yes") // GH-90000
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(invalidStrictOrderException.getMessage().contains("must be a boolean [GH-90000]"));
    }

    @Test
    @DisplayName("AND validates unordered multi-operand configuration [GH-90000]")
    void andValidatesUnorderedMultiOperandConfiguration() { // GH-90000
        AndOperator operator = new AndOperator(); // GH-90000
        OperatorSpec spec = OperatorSpec.builder() // GH-90000
                .type("AND [GH-90000]")
                .operand(leaf("LOGIN [GH-90000]"))
                .operand(leaf("IP_CHANGE [GH-90000]"))
                .operand(leaf("LOCATION_CHANGE [GH-90000]"))
                .parameter("maxTimeWindow", 300_000L) // GH-90000
                .parameter("allowPartialMatch", false) // GH-90000
                .build(); // GH-90000

        assertDoesNotThrow(() -> operator.validate(spec, CONTEXT)); // GH-90000
        assertTrue(operator.supports(spec)); // GH-90000
        assertEquals("AND", operator.getType()); // GH-90000
    }

    @Test
    @DisplayName("AND rejects invalid time window and partial match types [GH-90000]")
    void andRejectsInvalidTimeWindowAndPartialMatchTypes() { // GH-90000
        AndOperator operator = new AndOperator(); // GH-90000

        PatternValidationException invalidWindowException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("AND [GH-90000]")
                                .operand(leaf("LOGIN [GH-90000]"))
                                .operand(leaf("IP_CHANGE [GH-90000]"))
                                .parameter("maxTimeWindow", 0) // GH-90000
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(invalidWindowException.getMessage().contains("must be positive [GH-90000]"));

        PatternValidationException invalidPartialMatchException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("AND [GH-90000]")
                                .operand(leaf("LOGIN [GH-90000]"))
                                .operand(leaf("IP_CHANGE [GH-90000]"))
                                .parameter("allowPartialMatch", "sometimes") // GH-90000
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(invalidPartialMatchException.getMessage().contains("must be a boolean [GH-90000]"));
    }

    @Test
    @DisplayName("WITHIN validates unary temporal constraint configuration [GH-90000]")
    void withinValidatesUnaryTemporalConstraintConfiguration() { // GH-90000
        WithinOperator operator = new WithinOperator(); // GH-90000
        OperatorSpec spec = OperatorSpec.builder() // GH-90000
                .type("WITHIN [GH-90000]")
                .operand(leaf("TRANSACTION [GH-90000]"))
                .parameter("timeWindow", 60_000L) // GH-90000
                .parameter("startTime", 1_000L) // GH-90000
                .parameter("endTime", 10_000L) // GH-90000
                .parameter("inclusive", true) // GH-90000
                .build(); // GH-90000

        assertDoesNotThrow(() -> operator.validate(spec, CONTEXT)); // GH-90000
        assertTrue(operator.supports(spec)); // GH-90000
        assertEquals("WITHIN", operator.getType()); // GH-90000
        assertEquals(1, operator.getMetadata().getMaxOperands()); // GH-90000
    }

    @Test
    @DisplayName("WITHIN rejects missing operand and invalid parameters [GH-90000]")
    void withinRejectsMissingOperandAndInvalidParameters() { // GH-90000
        WithinOperator operator = new WithinOperator(); // GH-90000

        PatternValidationException missingOperandException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate(OperatorSpec.builder().type("WITHIN [GH-90000]").build(), CONTEXT));
        assertTrue(missingOperandException.getMessage().contains("exactly 1 operand [GH-90000]"));

        PatternValidationException missingWindowException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("WITHIN [GH-90000]")
                                .operand(leaf("TRANSACTION [GH-90000]"))
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(missingWindowException.getMessage().contains("requires parameters [GH-90000]"));

        PatternValidationException invalidStartTimeException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("WITHIN [GH-90000]")
                                .operand(leaf("TRANSACTION [GH-90000]"))
                                .parameter("timeWindow", 10L) // GH-90000
                                .parameter("startTime", -1) // GH-90000
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(invalidStartTimeException.getMessage().contains("startTime parameter must be non-negative [GH-90000]"));

        PatternValidationException invalidInclusiveException = assertThrows(PatternValidationException.class, // GH-90000
                () -> operator.validate( // GH-90000
                        OperatorSpec.builder() // GH-90000
                                .type("WITHIN [GH-90000]")
                                .operand(leaf("TRANSACTION [GH-90000]"))
                                .parameter("timeWindow", 10L) // GH-90000
                                .parameter("inclusive", "true") // GH-90000
                                .build(), // GH-90000
                        CONTEXT));
        assertTrue(invalidInclusiveException.getMessage().contains("inclusive parameter must be a boolean [GH-90000]"));
    }

    private static OperatorSpec leaf(String type) { // GH-90000
        return OperatorSpec.builder() // GH-90000
                .type(type) // GH-90000
                .id(type.toLowerCase()) // GH-90000
                .build(); // GH-90000
    }
}
