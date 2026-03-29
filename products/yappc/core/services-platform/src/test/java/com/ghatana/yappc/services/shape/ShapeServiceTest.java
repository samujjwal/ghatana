/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.shape;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ShapeService and shape management.
 */
class ShapeServiceTest extends EventloopTestBase {

    private ShapeService shapeService;

    @BeforeEach
    void setUp() {
        shapeService = new MockShapeService();
    }

    @Test
    @DisplayName("Should create shape from specification")
    void testCreateShape() throws Exception {
        ShapeSpec spec = ShapeSpec.builder()
                .name("UserDTO")
                .type("DTO")
                .properties(Map.of("id", "Long", "email", "String", "active", "Boolean"))
                .build();

        Promise<Shape> promise = shapeService.createShape(spec);
        Shape shape = runPromise(() -> promise);

        assertThat(shape).isNotNull();
        assertThat(shape.name()).isEqualTo("UserDTO");
        assertThat(shape.type()).isEqualTo("DTO");
        assertThat(shape.properties()).hasSize(3);
    }

    @Test
    @DisplayName("Should validate shape properties")
    void testValidateShape() throws Exception {
        Shape shape = Shape.builder()
                .name("OrderDTO")
                .type("DTO")
                .properties(Map.of("id", "Long", "total", "BigDecimal"))
                .build();

        Promise<ValidationResult> promise = shapeService.validateShape(shape);
        ValidationResult result = runPromise(() -> promise);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject shape with reserved keywords")
    void testRejectReservedKeywords() throws Exception {
        Shape shape = Shape.builder()
                .name("Class") // Reserved keyword
                .type("DTO")
                .properties(Map.of("id", "Long"))
                .build();

        Promise<ValidationResult> promise = shapeService.validateShape(shape);
        ValidationResult result = runPromise(() -> promise);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Shape name 'Class' is a reserved keyword");
    }

    @Test
    @DisplayName("Should generate TypeScript interface from shape")
    void testGenerateTypescript() throws Exception {
        Shape shape = Shape.builder()
                .name("ProductDTO")
                .type("DTO")
                .properties(Map.of(
                    "sku", "String",
                    "price", "BigDecimal",
                    "inStock", "Boolean"
                ))
                .build();

        Promise<String> promise = shapeService.generateTypescript(shape);
        String typescript = runPromise(() -> promise);

        assertThat(typescript).contains("interface ProductDTO");
        assertThat(typescript).contains("sku: string");
        assertThat(typescript).contains("price: number");
        assertThat(typescript).contains("inStock: boolean");
    }

    @Test
    @DisplayName("Should generate Java class from shape")
    void testGenerateJava() throws Exception {
        Shape shape = Shape.builder()
                .name("CustomerDTO")
                .type("DTO")
                .properties(Map.of(
                    "id", "Long",
                    "email", "String",
                    "loyaltyPoints", "Integer"
                ))
                .build();

        Promise<String> promise = shapeService.generateJava(shape);
        String java = runPromise(() -> promise);

        assertThat(java).contains("public class CustomerDTO");
        assertThat(java).contains("private Long id");
        assertThat(java).contains("private String email");
        assertThat(java).contains("private Integer loyaltyPoints");
    }

    @Test
    @DisplayName("Should convert shape to JSON Schema")
    void testGenerateJsonSchema() throws Exception {
        Shape shape = Shape.builder()
                .name("AddressDTO")
                .type("DTO")
                .properties(Map.of(
                    "street", "String",
                    "city", "String",
                    "zipCode", "String"
                ))
                .build();

        Promise<String> promise = shapeService.generateJsonSchema(shape);
        String schema = runPromise(() -> promise);

        assertThat(schema).contains("\"type\": \"object\"");
        assertThat(schema).contains("\"street\": {\"type\": \"string\"}");
    }

    @Test
    @DisplayName("Should transform shape to different type")
    void testTransformShape() throws Exception {
        Shape source = Shape.builder()
                .name("UserInput")
                .type("Input")
                .properties(Map.of("name", "String", "email", "String"))
                .build();

        Promise<Shape> promise = shapeService.transformTo(source, "Entity");
        Shape transformed = runPromise(() -> promise);

        assertThat(transformed.name()).isEqualTo("UserInput");
        assertThat(transformed.type()).isEqualTo("Entity");
        assertThat(transformed.properties()).hasSize(2);
    }

    @Test
    @DisplayName("Should detect shape conflicts")
    void testDetectConflicts() throws Exception {
        Shape shape1 = Shape.builder()
                .name("OrderSummary")
                .type("DTO")
                .properties(Map.of("orderId", "Long"))
                .build();

        Shape shape2 = Shape.builder()
                .name("OrderSummary")
                .type("DTO")
                .properties(Map.of("orderId", "String")) // Different type
                .build();

        Promise<ConflictResult> promise = shapeService.detectConflicts(shape1, shape2);
        ConflictResult conflicts = runPromise(() -> promise);

        assertThat(conflicts.hasConflicts()).isTrue();
        assertThat(conflicts.conflicts()).contains("Property 'orderId' type mismatch: Long vs String");
    }

    @Test
    @DisplayName("Should merge compatible shapes")
    void testMergeShapes() throws Exception {
        Shape base = Shape.builder()
                .name("UserBase")
                .type("DTO")
                .properties(Map.of("id", "Long", "name", "String"))
                .build();

        Shape extension = Shape.builder()
                .name("UserExtended")
                .type("DTO")
                .properties(Map.of("email", "String", "phone", "String"))
                .build();

        Promise<Shape> promise = shapeService.mergeShapes(base, extension, "UserComplete");
        Shape merged = runPromise(() -> promise);

        assertThat(merged.name()).isEqualTo("UserComplete");
        assertThat(merged.properties()).hasSize(4);
        assertThat(merged.properties()).containsKeys("id", "name", "email", "phone");
    }

    @Test
    @DisplayName("Should validate property names")
    void testValidatePropertyNames() throws Exception {
        Shape shape = Shape.builder()
                .name("TestDTO")
                .type("DTO")
                .properties(Map.of(
                    "validName", "String",
                    "123invalid", "String", // Invalid: starts with number
                    "", "String" // Invalid: empty
                ))
                .build();

        Promise<ValidationResult> promise = shapeService.validateShape(shape);
        ValidationResult result = runPromise(() -> promise);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("123invalid"));
    }

    // Mock implementations

    interface ShapeService {
        Promise<Shape> createShape(ShapeSpec spec);
        Promise<ValidationResult> validateShape(Shape shape);
        Promise<String> generateTypescript(Shape shape);
        Promise<String> generateJava(Shape shape);
        Promise<String> generateJsonSchema(Shape shape);
        Promise<Shape> transformTo(Shape source, String targetType);
        Promise<ConflictResult> detectConflicts(Shape shape1, Shape shape2);
        Promise<Shape> mergeShapes(Shape base, Shape extension, String newName);
    }

    record ShapeSpec(String name, String type, Map<String, String> properties) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String name, type;
            private Map<String, String> properties;
            Builder name(String v) { name = v; return this; }
            Builder type(String v) { type = v; return this; }
            Builder properties(Map<String, String> v) { properties = v; return this; }
            ShapeSpec build() { return new ShapeSpec(name, type, properties); }
        }
    }

    record Shape(String name, String type, Map<String, String> properties) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String name, type;
            private Map<String, String> properties;
            Builder name(String v) { name = v; return this; }
            Builder type(String v) { type = v; return this; }
            Builder properties(Map<String, String> v) { properties = v; return this; }
            Shape build() { return new Shape(name, type, properties); }
        }
    }

    record ValidationResult(boolean valid, java.util.List<String> errors) {
        static ValidationResult success() { return new ValidationResult(true, java.util.List.of()); }
        static ValidationResult failure(java.util.List<String> errors) { return new ValidationResult(false, errors); }
    }

    record ConflictResult(boolean hasConflicts, java.util.List<String> conflicts) {}

    static class MockShapeService implements ShapeService {

        private static final java.util.Set<String> RESERVED_KEYWORDS = java.util.Set.of(
            "class", "interface", "enum", "record", "abstract", "final", "public", "private", "protected"
        );

        @Override
        public Promise<Shape> createShape(ShapeSpec spec) {
            return Promise.of(Shape.builder()
                .name(spec.name())
                .type(spec.type())
                .properties(spec.properties())
                .build());
        }

        @Override
        public Promise<ValidationResult> validateShape(Shape shape) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                java.util.List<String> errors = new java.util.ArrayList<>();

                if (RESERVED_KEYWORDS.contains(shape.name().toLowerCase())) {
                    errors.add("Shape name '" + shape.name() + "' is a reserved keyword");
                }

                for (String propName : shape.properties().keySet()) {
                    if (propName.isEmpty()) {
                        errors.add("Property name cannot be empty");
                    } else if (Character.isDigit(propName.charAt(0))) {
                        errors.add("Property name '" + propName + "' cannot start with a digit");
                    }
                }

                return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
            });
        }

        @Override
        public Promise<String> generateTypescript(Shape shape) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                StringBuilder ts = new StringBuilder();
                ts.append("interface ").append(shape.name()).append(" {\n");

                shape.properties().forEach((name, type) -> {
                    ts.append("  ").append(name).append(": ").append(mapToTypescript(type)).append(";\n");
                });

                ts.append("}");
                return ts.toString();
            });
        }

        @Override
        public Promise<String> generateJava(Shape shape) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                StringBuilder java = new StringBuilder();
                java.append("public class ").append(shape.name()).append(" {\n");

                shape.properties().forEach((name, type) -> {
                    java.append("  private ").append(type).append(" ").append(name).append(";\n");
                });

                java.append("}");
                return java.toString();
            });
        }

        @Override
        public Promise<String> generateJsonSchema(Shape shape) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                StringBuilder schema = new StringBuilder();
                schema.append("{\n");
                schema.append("  \"type\": \"object\",\n");
                schema.append("  \"properties\": {\n");

                shape.properties().forEach((name, type) -> {
                    schema.append("    \"").append(name).append("\": {\"type\": \"").append(mapToJsonType(type)).append("\"},\n");
                });

                schema.append("  }\n");
                schema.append("}");
                return schema.toString();
            });
        }

        @Override
        public Promise<Shape> transformTo(Shape source, String targetType) {
            return Promise.of(Shape.builder()
                .name(source.name())
                .type(targetType)
                .properties(source.properties())
                .build());
        }

        @Override
        public Promise<ConflictResult> detectConflicts(Shape shape1, Shape shape2) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                java.util.List<String> conflicts = new java.util.ArrayList<>();

                for (Map.Entry<String, String> entry : shape1.properties().entrySet()) {
                    String propName = entry.getKey();
                    String type1 = entry.getValue();
                    String type2 = shape2.properties().get(propName);

                    if (type2 != null && !type1.equals(type2)) {
                        conflicts.add("Property '" + propName + "' type mismatch: " + type1 + " vs " + type2);
                    }
                }

                return new ConflictResult(!conflicts.isEmpty(), conflicts);
            });
        }

        @Override
        public Promise<Shape> mergeShapes(Shape base, Shape extension, String newName) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                Map<String, String> mergedProps = new java.util.HashMap<>(base.properties());
                mergedProps.putAll(extension.properties());

                return Shape.builder()
                    .name(newName)
                    .type(base.type())
                    .properties(mergedProps)
                    .build();
            });
        }

        private String mapToTypescript(String type) {
            return switch (type) {
                case "String" -> "string";
                case "Long", "Integer", "BigDecimal" -> "number";
                case "Boolean" -> "boolean";
                default -> "any";
            };
        }

        private String mapToJsonType(String type) {
            return switch (type) {
                case "String" -> "string";
                case "Long", "Integer", "BigDecimal" -> "number";
                case "Boolean" -> "boolean";
                default -> "object";
            };
        }
    }
}
