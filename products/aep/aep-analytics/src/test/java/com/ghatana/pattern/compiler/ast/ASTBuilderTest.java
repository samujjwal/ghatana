package com.ghatana.pattern.compiler.ast;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ASTBuilder Tests [GH-90000]")
class ASTBuilderTest {

    @Test
    @DisplayName("build creates nested AST with generated ids and correct aggregate metrics [GH-90000]")
    void buildCreatesNestedAstWithGeneratedIdsAndCorrectAggregateMetrics() throws PatternValidationException { // GH-90000
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry()); // GH-90000
        OperatorSpec spec = OperatorSpec.builder() // GH-90000
                .type("SEQ [GH-90000]")
                .operand(OperatorSpec.builder() // GH-90000
                        .type("SELECT [GH-90000]")
                        .parameter("eventType", "login") // GH-90000
                        .build()) // GH-90000
                .operand(OperatorSpec.builder() // GH-90000
                        .type("AND [GH-90000]")
                        .id("and-1 [GH-90000]")
                        .operand(OperatorSpec.builder().type("FILTER [GH-90000]").id("filter-1 [GH-90000]").build())
                        .operand(OperatorSpec.builder().type("SELECT [GH-90000]").id("select-2 [GH-90000]").build())
                        .build()) // GH-90000
                .build(); // GH-90000

        AST ast = builder.build(spec); // GH-90000

        assertEquals(2, ast.getDepth()); // GH-90000
        assertEquals(5, ast.getNodeCount()); // GH-90000
        assertEquals(3, ast.getLeafNodes().size()); // GH-90000
        assertEquals(2, ast.getNodesByType("SELECT [GH-90000]").size());
        assertEquals("SEQ", ast.getRoot().getType()); // GH-90000
        assertNotNull(ast.getRoot().getId()); // GH-90000
        assertTrue(ast.getRoot().getId().startsWith("SEQ_0_ [GH-90000]"));
        assertEquals(0, ast.getRoot().getMetadata("depth [GH-90000]"));
        assertEquals(2, ast.getRoot().getMetadata("operandCount [GH-90000]"));
        assertTrue((Boolean) ast.getRoot().getMetadata("hasParameters [GH-90000]") == false);
        assertEquals(1, ast.getRoot().getChildren().get(1).getDepth()); // GH-90000
        assertEquals("and-1", ast.getRoot().getChildren().get(1).getId()); // GH-90000
        assertTrue(ast.getMetadata().containsKey("buildTime [GH-90000]"));
    }

    @Test
    @DisplayName("buildWithPrimaryEvent wraps original tree and records event metadata [GH-90000]")
    void buildWithPrimaryEventWrapsOriginalTreeAndRecordsEventMetadata() throws PatternValidationException { // GH-90000
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry()); // GH-90000
        OperatorSpec spec = OperatorSpec.builder() // GH-90000
                .type("SELECT [GH-90000]")
                .id("root-select [GH-90000]")
                .build(); // GH-90000

        AST ast = builder.buildWithPrimaryEvent(spec, List.of("login", "purchase")); // GH-90000

        assertEquals("PRIMARY_EVENT", ast.getRoot().getType()); // GH-90000
        assertEquals("primary-event-filter", ast.getRoot().getId()); // GH-90000
        assertEquals(2, ast.getNodeCount()); // GH-90000
        assertEquals(1, ast.getDepth()); // GH-90000
        assertEquals(List.of("login", "purchase"), ast.getRoot().getParameter("eventTypes [GH-90000]"));
        assertEquals(2, ast.getMetadata().get("eventTypeCount [GH-90000]"));
        assertEquals("SELECT", ast.getMetadata().get("originalRootType [GH-90000]"));
        assertEquals("root-select", ast.getMetadata().get("originalRootId [GH-90000]"));
        assertEquals(1, ast.getRoot().getChildCount()); // GH-90000
        assertEquals("SELECT", ast.getRoot().getChildren().get(0).getType()); // GH-90000
    }

    @Test
    @DisplayName("build rejects null root empty type and null operands [GH-90000]")
    void buildRejectsNullRootEmptyTypeAndNullOperands() { // GH-90000
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry()); // GH-90000

        PatternValidationException nullRootException = assertThrows(PatternValidationException.class, // GH-90000
                () -> builder.build(null)); // GH-90000
        assertTrue(nullRootException.getMessage().contains("cannot be null [GH-90000]"));

        PatternValidationException emptyTypeException = assertThrows(PatternValidationException.class, // GH-90000
                () -> builder.build(OperatorSpec.builder().type("  [GH-90000]").build()));
        assertTrue(emptyTypeException.getMessage().contains("type cannot be null or empty [GH-90000]"));

        PatternValidationException nullOperandException = assertThrows(PatternValidationException.class, // GH-90000
                () -> { // GH-90000
                    List<OperatorSpec> operands = new ArrayList<>(); // GH-90000
                    operands.add(OperatorSpec.builder().type("SELECT [GH-90000]").build());
                    operands.add(null); // GH-90000
                    builder.build(OperatorSpec.builder() // GH-90000
                            .type("SEQ [GH-90000]")
                            .operands(operands) // GH-90000
                            .build()); // GH-90000
                });
        assertTrue(nullOperandException.getMessage().contains("operand cannot be null [GH-90000]"));
    }

    @Test
    @DisplayName("buildWithPrimaryEvent rejects missing event types [GH-90000]")
    void buildWithPrimaryEventRejectsMissingEventTypes() { // GH-90000
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry()); // GH-90000

        PatternValidationException exception = assertThrows(PatternValidationException.class, // GH-90000
                () -> builder.buildWithPrimaryEvent(OperatorSpec.builder().type("SELECT [GH-90000]").build(), List.of()));
        assertTrue(exception.getMessage().contains("Event types cannot be null or empty [GH-90000]"));
    }
}
