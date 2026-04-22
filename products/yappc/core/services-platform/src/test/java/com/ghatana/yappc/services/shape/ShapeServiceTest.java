/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
    void setUp() { // GH-90000
        shapeService = new MockShapeService(); // GH-90000
    }

    @Test
    @DisplayName("Should create shape from specification [GH-90000]")
    void testCreateShape() throws Exception { // GH-90000
        ShapeSpec spec = ShapeSpec.builder() // GH-90000
                .name("UserDTO [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of("id", "Long", "email", "String", "active", "Boolean")) // GH-90000
                .build(); // GH-90000

        Promise<Shape> promise = shapeService.createShape(spec); // GH-90000
        Shape shape = runPromise(() -> promise); // GH-90000

        assertThat(shape).isNotNull(); // GH-90000
        assertThat(shape.name()).isEqualTo("UserDTO [GH-90000]");
        assertThat(shape.type()).isEqualTo("DTO [GH-90000]");
        assertThat(shape.properties()).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("Should validate shape properties [GH-90000]")
    void testValidateShape() throws Exception { // GH-90000
        Shape shape = Shape.builder() // GH-90000
                .name("OrderDTO [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of("id", "Long", "total", "BigDecimal")) // GH-90000
                .build(); // GH-90000

        Promise<ValidationResult> promise = shapeService.validateShape(shape); // GH-90000
        ValidationResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.valid()).isTrue(); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject shape with reserved keywords [GH-90000]")
    void testRejectReservedKeywords() throws Exception { // GH-90000
        Shape shape = Shape.builder() // GH-90000
                .name("Class [GH-90000]") // Reserved keyword
                .type("DTO [GH-90000]")
                .properties(Map.of("id", "Long")) // GH-90000
                .build(); // GH-90000

        Promise<ValidationResult> promise = shapeService.validateShape(shape); // GH-90000
        ValidationResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Shape name 'Class' is a reserved keyword [GH-90000]");
    }

    @Test
    @DisplayName("Should generate TypeScript interface from shape [GH-90000]")
    void testGenerateTypescript() throws Exception { // GH-90000
        Shape shape = Shape.builder() // GH-90000
                .name("ProductDTO [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of( // GH-90000
                    "sku", "String",
                    "price", "BigDecimal",
                    "inStock", "Boolean"
                ))
                .build(); // GH-90000

        Promise<String> promise = shapeService.generateTypescript(shape); // GH-90000
        String typescript = runPromise(() -> promise); // GH-90000

        assertThat(typescript).contains("interface ProductDTO [GH-90000]");
        assertThat(typescript).contains("sku: string [GH-90000]");
        assertThat(typescript).contains("price: number [GH-90000]");
        assertThat(typescript).contains("inStock: boolean [GH-90000]");
    }

    @Test
    @DisplayName("Should generate Java class from shape [GH-90000]")
    void testGenerateJava() throws Exception { // GH-90000
        Shape shape = Shape.builder() // GH-90000
                .name("CustomerDTO [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of( // GH-90000
                    "id", "Long",
                    "email", "String",
                    "loyaltyPoints", "Integer"
                ))
                .build(); // GH-90000

        Promise<String> promise = shapeService.generateJava(shape); // GH-90000
        String java = runPromise(() -> promise); // GH-90000

        assertThat(java).contains("public class CustomerDTO [GH-90000]");
        assertThat(java).contains("private Long id [GH-90000]");
        assertThat(java).contains("private String email [GH-90000]");
        assertThat(java).contains("private Integer loyaltyPoints [GH-90000]");
    }

    @Test
    @DisplayName("Should convert shape to JSON Schema [GH-90000]")
    void testGenerateJsonSchema() throws Exception { // GH-90000
        Shape shape = Shape.builder() // GH-90000
                .name("AddressDTO [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of( // GH-90000
                    "street", "String",
                    "city", "String",
                    "zipCode", "String"
                ))
                .build(); // GH-90000

        Promise<String> promise = shapeService.generateJsonSchema(shape); // GH-90000
        String schema = runPromise(() -> promise); // GH-90000

        assertThat(schema).contains("\"type\": \"object\""); // GH-90000
        assertThat(schema).contains("\"street\": {\"type\": \"string\"}"); // GH-90000
    }

    @Test
    @DisplayName("Should transform shape to different type [GH-90000]")
    void testTransformShape() throws Exception { // GH-90000
        Shape source = Shape.builder() // GH-90000
                .name("UserInput [GH-90000]")
                .type("Input [GH-90000]")
                .properties(Map.of("name", "String", "email", "String")) // GH-90000
                .build(); // GH-90000

        Promise<Shape> promise = shapeService.transformTo(source, "Entity"); // GH-90000
        Shape transformed = runPromise(() -> promise); // GH-90000

        assertThat(transformed.name()).isEqualTo("UserInput [GH-90000]");
        assertThat(transformed.type()).isEqualTo("Entity [GH-90000]");
        assertThat(transformed.properties()).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Should detect shape conflicts [GH-90000]")
    void testDetectConflicts() throws Exception { // GH-90000
        Shape shape1 = Shape.builder() // GH-90000
                .name("OrderSummary [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of("orderId", "Long")) // GH-90000
                .build(); // GH-90000

        Shape shape2 = Shape.builder() // GH-90000
                .name("OrderSummary [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of("orderId", "String")) // Different type // GH-90000
                .build(); // GH-90000

        Promise<ConflictResult> promise = shapeService.detectConflicts(shape1, shape2); // GH-90000
        ConflictResult conflicts = runPromise(() -> promise); // GH-90000

        assertThat(conflicts.hasConflicts()).isTrue(); // GH-90000
        assertThat(conflicts.conflicts()).contains("Property 'orderId' type mismatch: Long vs String [GH-90000]");
    }

    @Test
    @DisplayName("Should merge compatible shapes [GH-90000]")
    void testMergeShapes() throws Exception { // GH-90000
        Shape base = Shape.builder() // GH-90000
                .name("UserBase [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of("id", "Long", "name", "String")) // GH-90000
                .build(); // GH-90000

        Shape extension = Shape.builder() // GH-90000
                .name("UserExtended [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of("email", "String", "phone", "String")) // GH-90000
                .build(); // GH-90000

        Promise<Shape> promise = shapeService.mergeShapes(base, extension, "UserComplete"); // GH-90000
        Shape merged = runPromise(() -> promise); // GH-90000

        assertThat(merged.name()).isEqualTo("UserComplete [GH-90000]");
        assertThat(merged.properties()).hasSize(4); // GH-90000
        assertThat(merged.properties()).containsKeys("id", "name", "email", "phone"); // GH-90000
    }

    @Test
    @DisplayName("Should validate property names [GH-90000]")
    void testValidatePropertyNames() throws Exception { // GH-90000
        Shape shape = Shape.builder() // GH-90000
                .name("TestDTO [GH-90000]")
                .type("DTO [GH-90000]")
                .properties(Map.of( // GH-90000
                    "validName", "String",
                    "123invalid", "String", // Invalid: starts with number
                    "", "String" // Invalid: empty
                ))
                .build(); // GH-90000

        Promise<ValidationResult> promise = shapeService.validateShape(shape); // GH-90000
        ValidationResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("123invalid [GH-90000]"));
    }

    // Mock implementations

    interface ShapeService {
        Promise<Shape> createShape(ShapeSpec spec); // GH-90000
        Promise<ValidationResult> validateShape(Shape shape); // GH-90000
        Promise<String> generateTypescript(Shape shape); // GH-90000
        Promise<String> generateJava(Shape shape); // GH-90000
        Promise<String> generateJsonSchema(Shape shape); // GH-90000
        Promise<Shape> transformTo(Shape source, String targetType); // GH-90000
        Promise<ConflictResult> detectConflicts(Shape shape1, Shape shape2); // GH-90000
        Promise<Shape> mergeShapes(Shape base, Shape extension, String newName); // GH-90000
    }

    record ShapeSpec(String name, String type, Map<String, String> properties) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String name, type;
            private Map<String, String> properties;
            Builder name(String v) { name = v; return this; } // GH-90000
            Builder type(String v) { type = v; return this; } // GH-90000
            Builder properties(Map<String, String> v) { properties = v; return this; } // GH-90000
            ShapeSpec build() { return new ShapeSpec(name, type, properties); } // GH-90000
        }
    }

    record Shape(String name, String type, Map<String, String> properties) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String name, type;
            private Map<String, String> properties;
            Builder name(String v) { name = v; return this; } // GH-90000
            Builder type(String v) { type = v; return this; } // GH-90000
            Builder properties(Map<String, String> v) { properties = v; return this; } // GH-90000
            Shape build() { return new Shape(name, type, properties); } // GH-90000
        }
    }

    record ValidationResult(boolean valid, java.util.List<String> errors) { // GH-90000
        static ValidationResult success() { return new ValidationResult(true, java.util.List.of()); } // GH-90000
        static ValidationResult failure(java.util.List<String> errors) { return new ValidationResult(false, errors); } // GH-90000
    }

    record ConflictResult(boolean hasConflicts, java.util.List<String> conflicts) {} // GH-90000

    static class MockShapeService implements ShapeService {

        private static final java.util.Set<String> RESERVED_KEYWORDS = java.util.Set.of( // GH-90000
            "class", "interface", "enum", "record", "abstract", "final", "public", "private", "protected"
        );

        @Override
        public Promise<Shape> createShape(ShapeSpec spec) { // GH-90000
            return Promise.of(Shape.builder() // GH-90000
                .name(spec.name()) // GH-90000
                .type(spec.type()) // GH-90000
                .properties(spec.properties()) // GH-90000
                .build()); // GH-90000
        }

        @Override
        public Promise<ValidationResult> validateShape(Shape shape) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                java.util.List<String> errors = new java.util.ArrayList<>(); // GH-90000

                if (RESERVED_KEYWORDS.contains(shape.name().toLowerCase())) { // GH-90000
                    errors.add("Shape name '" + shape.name() + "' is a reserved keyword"); // GH-90000
                }

                for (String propName : shape.properties().keySet()) { // GH-90000
                    if (propName.isEmpty()) { // GH-90000
                        errors.add("Property name cannot be empty [GH-90000]");
                    } else if (Character.isDigit(propName.charAt(0))) { // GH-90000
                        errors.add("Property name '" + propName + "' cannot start with a digit"); // GH-90000
                    }
                }

                return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors); // GH-90000
            });
        }

        @Override
        public Promise<String> generateTypescript(Shape shape) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                StringBuilder ts = new StringBuilder(); // GH-90000
                ts.append("interface  [GH-90000]").append(shape.name()).append(" {\n [GH-90000]");

                shape.properties().forEach((name, type) -> { // GH-90000
                    ts.append("   [GH-90000]").append(name).append(":  [GH-90000]").append(mapToTypescript(type)).append(";\n [GH-90000]");
                });

                ts.append("} [GH-90000]");
                return ts.toString(); // GH-90000
            });
        }

        @Override
        public Promise<String> generateJava(Shape shape) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                StringBuilder java = new StringBuilder(); // GH-90000
                java.append("public class  [GH-90000]").append(shape.name()).append(" {\n [GH-90000]");

                shape.properties().forEach((name, type) -> { // GH-90000
                    java.append("  private  [GH-90000]").append(type).append("  [GH-90000]").append(name).append(";\n [GH-90000]");
                });

                java.append("} [GH-90000]");
                return java.toString(); // GH-90000
            });
        }

        @Override
        public Promise<String> generateJsonSchema(Shape shape) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                StringBuilder schema = new StringBuilder(); // GH-90000
                schema.append("{\n [GH-90000]");
                schema.append("  \"type\": \"object\",\n"); // GH-90000
                schema.append("  \"properties\": {\n"); // GH-90000

                shape.properties().forEach((name, type) -> { // GH-90000
                    schema.append("    \"").append(name).append("\": {\"type\": \"").append(mapToJsonType(type)).append("\"},\n"); // GH-90000
                });

                schema.append("  }\n [GH-90000]");
                schema.append("} [GH-90000]");
                return schema.toString(); // GH-90000
            });
        }

        @Override
        public Promise<Shape> transformTo(Shape source, String targetType) { // GH-90000
            return Promise.of(Shape.builder() // GH-90000
                .name(source.name()) // GH-90000
                .type(targetType) // GH-90000
                .properties(source.properties()) // GH-90000
                .build()); // GH-90000
        }

        @Override
        public Promise<ConflictResult> detectConflicts(Shape shape1, Shape shape2) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                java.util.List<String> conflicts = new java.util.ArrayList<>(); // GH-90000

                for (Map.Entry<String, String> entry : shape1.properties().entrySet()) { // GH-90000
                    String propName = entry.getKey(); // GH-90000
                    String type1 = entry.getValue(); // GH-90000
                    String type2 = shape2.properties().get(propName); // GH-90000

                    if (type2 != null && !type1.equals(type2)) { // GH-90000
                        conflicts.add("Property '" + propName + "' type mismatch: " + type1 + " vs " + type2); // GH-90000
                    }
                }

                return new ConflictResult(!conflicts.isEmpty(), conflicts); // GH-90000
            });
        }

        @Override
        public Promise<Shape> mergeShapes(Shape base, Shape extension, String newName) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                Map<String, String> mergedProps = new java.util.HashMap<>(base.properties()); // GH-90000
                mergedProps.putAll(extension.properties()); // GH-90000

                return Shape.builder() // GH-90000
                    .name(newName) // GH-90000
                    .type(base.type()) // GH-90000
                    .properties(mergedProps) // GH-90000
                    .build(); // GH-90000
            });
        }

        private String mapToTypescript(String type) { // GH-90000
            return switch (type) { // GH-90000
                case "String" -> "string";
                case "Long", "Integer", "BigDecimal" -> "number";
                case "Boolean" -> "boolean";
                default -> "any";
            };
        }

        private String mapToJsonType(String type) { // GH-90000
            return switch (type) { // GH-90000
                case "String" -> "string";
                case "Long", "Integer", "BigDecimal" -> "number";
                case "Boolean" -> "boolean";
                default -> "object";
            };
        }
    }
}
