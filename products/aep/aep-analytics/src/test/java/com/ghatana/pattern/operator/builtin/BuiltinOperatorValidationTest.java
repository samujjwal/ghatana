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

@DisplayName("Built-in Operator Validation Tests")
class BuiltinOperatorValidationTest {

    private static final ValidationContext CONTEXT = ValidationContext.builder() 
            .patternId("pattern-1")
            .tenantId("tenant-1")
            .build(); 

    @Test
    @DisplayName("SEQ validates valid sequence configuration")
    void seqValidatesValidSequenceConfiguration() { 
        SeqOperator operator = new SeqOperator(); 
        OperatorSpec spec = OperatorSpec.builder() 
                .type("SEQ")
                .operand(leaf("LOGIN"))
                .operand(leaf("PURCHASE"))
                .parameter("maxGap", 60_000L) 
                .parameter("strictOrder", true) 
                .build(); 

        assertDoesNotThrow(() -> operator.validate(spec, CONTEXT)); 
        assertTrue(operator.supports(spec)); 
        assertEquals("SEQ", operator.getType()); 
        assertEquals(2, operator.getMetadata().getMinOperands()); 
        assertTrue(operator.getMetadata().isSupportsStateful()); 
        assertFalse(operator.getMetadata().isSupportsStateless()); 
    }

    @Test
    @DisplayName("SEQ rejects invalid operands and parameters")
    void seqRejectsInvalidOperandsAndParameters() { 
        SeqOperator operator = new SeqOperator(); 

        PatternValidationException missingOperandException = assertThrows(PatternValidationException.class, 
                () -> operator.validate(OperatorSpec.builder().type("SEQ").operand(leaf("LOGIN")).build(), CONTEXT));
        assertTrue(missingOperandException.getMessage().contains("at least 2 operands"));

        PatternValidationException invalidGapException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("SEQ")
                                .operand(leaf("LOGIN"))
                                .operand(leaf("PURCHASE"))
                                .parameter("maxGap", -1) 
                                .build(), 
                        CONTEXT));
        assertTrue(invalidGapException.getMessage().contains("non-negative"));

        PatternValidationException invalidStrictOrderException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("SEQ")
                                .operand(leaf("LOGIN"))
                                .operand(leaf("PURCHASE"))
                                .parameter("strictOrder", "yes") 
                                .build(), 
                        CONTEXT));
        assertTrue(invalidStrictOrderException.getMessage().contains("must be a boolean"));
    }

    @Test
    @DisplayName("AND validates unordered multi-operand configuration")
    void andValidatesUnorderedMultiOperandConfiguration() { 
        AndOperator operator = new AndOperator(); 
        OperatorSpec spec = OperatorSpec.builder() 
                .type("AND")
                .operand(leaf("LOGIN"))
                .operand(leaf("IP_CHANGE"))
                .operand(leaf("LOCATION_CHANGE"))
                .parameter("maxTimeWindow", 300_000L) 
                .parameter("allowPartialMatch", false) 
                .build(); 

        assertDoesNotThrow(() -> operator.validate(spec, CONTEXT)); 
        assertTrue(operator.supports(spec)); 
        assertEquals("AND", operator.getType()); 
    }

    @Test
    @DisplayName("AND rejects invalid time window and partial match types")
    void andRejectsInvalidTimeWindowAndPartialMatchTypes() { 
        AndOperator operator = new AndOperator(); 

        PatternValidationException invalidWindowException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("AND")
                                .operand(leaf("LOGIN"))
                                .operand(leaf("IP_CHANGE"))
                                .parameter("maxTimeWindow", 0) 
                                .build(), 
                        CONTEXT));
        assertTrue(invalidWindowException.getMessage().contains("must be positive"));

        PatternValidationException invalidPartialMatchException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("AND")
                                .operand(leaf("LOGIN"))
                                .operand(leaf("IP_CHANGE"))
                                .parameter("allowPartialMatch", "sometimes") 
                                .build(), 
                        CONTEXT));
        assertTrue(invalidPartialMatchException.getMessage().contains("must be a boolean"));
    }

    @Test
    @DisplayName("WITHIN validates unary temporal constraint configuration")
    void withinValidatesUnaryTemporalConstraintConfiguration() { 
        WithinOperator operator = new WithinOperator(); 
        OperatorSpec spec = OperatorSpec.builder() 
                .type("WITHIN")
                .operand(leaf("TRANSACTION"))
                .parameter("timeWindow", 60_000L) 
                .parameter("startTime", 1_000L) 
                .parameter("endTime", 10_000L) 
                .parameter("inclusive", true) 
                .build(); 

        assertDoesNotThrow(() -> operator.validate(spec, CONTEXT)); 
        assertTrue(operator.supports(spec)); 
        assertEquals("WITHIN", operator.getType()); 
        assertEquals(1, operator.getMetadata().getMaxOperands()); 
    }

    @Test
    @DisplayName("WITHIN rejects missing operand and invalid parameters")
    void withinRejectsMissingOperandAndInvalidParameters() { 
        WithinOperator operator = new WithinOperator(); 

        PatternValidationException missingOperandException = assertThrows(PatternValidationException.class, 
                () -> operator.validate(OperatorSpec.builder().type("WITHIN").build(), CONTEXT));
        assertTrue(missingOperandException.getMessage().contains("exactly 1 operand"));

        PatternValidationException missingWindowException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("WITHIN")
                                .operand(leaf("TRANSACTION"))
                                .build(), 
                        CONTEXT));
        assertTrue(missingWindowException.getMessage().contains("requires parameters"));

        PatternValidationException invalidStartTimeException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("WITHIN")
                                .operand(leaf("TRANSACTION"))
                                .parameter("timeWindow", 10L) 
                                .parameter("startTime", -1) 
                                .build(), 
                        CONTEXT));
        assertTrue(invalidStartTimeException.getMessage().contains("startTime parameter must be non-negative"));

        PatternValidationException invalidInclusiveException = assertThrows(PatternValidationException.class, 
                () -> operator.validate( 
                        OperatorSpec.builder() 
                                .type("WITHIN")
                                .operand(leaf("TRANSACTION"))
                                .parameter("timeWindow", 10L) 
                                .parameter("inclusive", "true") 
                                .build(), 
                        CONTEXT));
        assertTrue(invalidInclusiveException.getMessage().contains("inclusive parameter must be a boolean"));
    }

    private static OperatorSpec leaf(String type) { 
        return OperatorSpec.builder() 
                .type(type) 
                .id(type.toLowerCase()) 
                .build(); 
    }
}
