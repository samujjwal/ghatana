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

@DisplayName("ASTBuilder Tests")
class ASTBuilderTest {

    @Test
    @DisplayName("build creates nested AST with generated ids and correct aggregate metrics")
    void buildCreatesNestedAstWithGeneratedIdsAndCorrectAggregateMetrics() throws PatternValidationException {
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry());
        OperatorSpec spec = OperatorSpec.builder()
                .type("SEQ")
                .operand(OperatorSpec.builder()
                        .type("SELECT")
                        .parameter("eventType", "login")
                        .build())
                .operand(OperatorSpec.builder()
                        .type("AND")
                        .id("and-1")
                        .operand(OperatorSpec.builder().type("FILTER").id("filter-1").build())
                        .operand(OperatorSpec.builder().type("SELECT").id("select-2").build())
                        .build())
                .build();

        AST ast = builder.build(spec);

        assertEquals(2, ast.getDepth());
        assertEquals(5, ast.getNodeCount());
        assertEquals(3, ast.getLeafNodes().size());
        assertEquals(2, ast.getNodesByType("SELECT").size());
        assertEquals("SEQ", ast.getRoot().getType());
        assertNotNull(ast.getRoot().getId());
        assertTrue(ast.getRoot().getId().startsWith("SEQ_0_"));
        assertEquals(0, ast.getRoot().getMetadata("depth"));
        assertEquals(2, ast.getRoot().getMetadata("operandCount"));
        assertTrue((Boolean) ast.getRoot().getMetadata("hasParameters") == false);
        assertEquals(1, ast.getRoot().getChildren().get(1).getDepth());
        assertEquals("and-1", ast.getRoot().getChildren().get(1).getId());
        assertTrue(ast.getMetadata().containsKey("buildTime"));
    }

    @Test
    @DisplayName("buildWithPrimaryEvent wraps original tree and records event metadata")
    void buildWithPrimaryEventWrapsOriginalTreeAndRecordsEventMetadata() throws PatternValidationException {
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry());
        OperatorSpec spec = OperatorSpec.builder()
                .type("SELECT")
                .id("root-select")
                .build();

        AST ast = builder.buildWithPrimaryEvent(spec, List.of("login", "purchase"));

        assertEquals("PRIMARY_EVENT", ast.getRoot().getType());
        assertEquals("primary-event-filter", ast.getRoot().getId());
        assertEquals(2, ast.getNodeCount());
        assertEquals(1, ast.getDepth());
        assertEquals(List.of("login", "purchase"), ast.getRoot().getParameter("eventTypes"));
        assertEquals(2, ast.getMetadata().get("eventTypeCount"));
        assertEquals("SELECT", ast.getMetadata().get("originalRootType"));
        assertEquals("root-select", ast.getMetadata().get("originalRootId"));
        assertEquals(1, ast.getRoot().getChildCount());
        assertEquals("SELECT", ast.getRoot().getChildren().get(0).getType());
    }

    @Test
    @DisplayName("build rejects null root empty type and null operands")
    void buildRejectsNullRootEmptyTypeAndNullOperands() {
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry());

        PatternValidationException nullRootException = assertThrows(PatternValidationException.class,
                () -> builder.build(null));
        assertTrue(nullRootException.getMessage().contains("cannot be null"));

        PatternValidationException emptyTypeException = assertThrows(PatternValidationException.class,
                () -> builder.build(OperatorSpec.builder().type(" ").build()));
        assertTrue(emptyTypeException.getMessage().contains("type cannot be null or empty"));

        PatternValidationException nullOperandException = assertThrows(PatternValidationException.class,
                () -> {
                    List<OperatorSpec> operands = new ArrayList<>();
                    operands.add(OperatorSpec.builder().type("SELECT").build());
                    operands.add(null);
                    builder.build(OperatorSpec.builder()
                            .type("SEQ")
                            .operands(operands)
                            .build());
                });
        assertTrue(nullOperandException.getMessage().contains("operand cannot be null"));
    }

    @Test
    @DisplayName("buildWithPrimaryEvent rejects missing event types")
    void buildWithPrimaryEventRejectsMissingEventTypes() {
        ASTBuilder builder = new ASTBuilder(null, new SimpleMeterRegistry());

        PatternValidationException exception = assertThrows(PatternValidationException.class,
                () -> builder.buildWithPrimaryEvent(OperatorSpec.builder().type("SELECT").build(), List.of()));
        assertTrue(exception.getMessage().contains("Event types cannot be null or empty"));
    }
}