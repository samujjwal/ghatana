package com.ghatana.pattern.codegen.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.platform.domain.domain.event.EventParameterSpec;
import com.ghatana.platform.domain.domain.event.EventParameterType;
import com.ghatana.platform.domain.domain.event.EventType;
// PHASE 3 TODO: Circular dependency with libs:validation - temporarily commented out
// import com.ghatana.platform.core.validation.ValidationError;
// import com.ghatana.platform.validation.ValidationResult;
// import com.ghatana.platform.core.validation.json.JsonSchemaValidator;
import com.ghatana.pattern.api.codegen.GeneratedTypeKey;
import com.ghatana.pattern.api.exception.EventClassCompilationException;
import com.ghatana.pattern.api.model.PatternSpecification;
import com.ghatana.pattern.codegen.model.FieldCategory;
import com.ghatana.pattern.codegen.model.FieldDefinition;
import com.ghatana.pattern.codegen.model.MappedEventSchema;
import com.ghatana.pattern.codegen.naming.FieldNamingStrategy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Converts event/catalog metadata into strongly-typed field definitions that can be fed to the
 * bytecode generators.
 */
public final class SchemaToJavaMapper {
    private static final String BASE_PACKAGE = "com.ghatana.event.core.gen";

    // PHASE 3 TODO: Temporarily removed to break circular dependency
    // private final JsonSchemaValidator jsonSchemaValidator;
    private final FieldNamingStrategy namingStrategy;
    private final ObjectMapper objectMapper;

    public SchemaToJavaMapper(/* JsonSchemaValidator jsonSchemaValidator, */
                              FieldNamingStrategy namingStrategy,
                              ObjectMapper objectMapper) {
        // this.jsonSchemaValidator = Objects.requireNonNull(jsonSchemaValidator, "jsonSchemaValidator");
        this.namingStrategy = Objects.requireNonNull(namingStrategy, "namingStrategy");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Converts {@link EventType} + {@link PatternSpecification} metadata into a typed schema suitable
     * for code generation. The resulting schema contains header/payload/derived fields plus
     * a JSON Schema snapshot used for runtime validation.
     *
     * @throws EventClassCompilationException when mapping or schema validation fails
     */
    public MappedEventSchema map(EventType eventType,
                                 PatternSpecification specification,
                                 GeneratedTypeKey key) throws EventClassCompilationException {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(key, "key");

        Set<String> usedNames = new HashSet<>();

        Map<String, EventParameterSpec> headers = Optional.ofNullable(eventType.getHeaders()).orElse(Map.of());
        Map<String, EventParameterSpec> payload = Optional.ofNullable(eventType.getPayload()).orElse(Map.of());

        List<FieldDefinition> headerFields = mapSpecs(headers, FieldCategory.HEADER, usedNames);
        List<FieldDefinition> payloadFields = mapSpecs(payload, FieldCategory.PAYLOAD, usedNames);
        List<FieldDefinition> derivedFields = derivePatternFields(specification, usedNames);

        String packageName = BASE_PACKAGE;
        String simpleClassName = key.classNameToken() + "Event";

        String jsonSchema = buildJsonSchema(eventType.getName(), payload);
        validateJsonSchema(jsonSchema, payloadFields, key);

        return new MappedEventSchema(
                key,
                packageName,
                simpleClassName,
                jsonSchema,
                headerFields,
                payloadFields,
                derivedFields
        );
    }

    private List<FieldDefinition> mapSpecs(Map<String, EventParameterSpec> specs,
                                           FieldCategory category,
                                           Set<String> usedNames) {
        List<FieldDefinition> result = new ArrayList<>();
        specs.forEach((name, spec) -> result.add(buildFieldDefinition(name, spec, category, usedNames)));
        return result;
    }

    private FieldDefinition buildFieldDefinition(String originalName,
                                                 EventParameterSpec spec,
                                                 FieldCategory category,
                                                 Set<String> usedNames) {
        String javaName = uniqueFieldName(originalName, usedNames);
        Class<?> javaType = resolveJavaType(spec);
        boolean required = spec != null && spec.isRequired();
        String description = spec != null ? spec.getDescription() : "";
        return new FieldDefinition(originalName, javaName, javaType, required, category, description);
    }

    private String uniqueFieldName(String candidate, Set<String> usedNames) {
        String base = namingStrategy.toFieldName(candidate);
        String current = base;
        int counter = 1;
        while (usedNames.contains(current)) {
            current = base + counter++;
        }
        usedNames.add(current);
        return current;
    }

    private List<FieldDefinition> derivePatternFields(PatternSpecification spec,
                                                      Set<String> usedNames) {
        List<FieldDefinition> derived = new ArrayList<>();
        derived.add(new FieldDefinition("patternId", uniqueFieldName("patternId", usedNames), String.class, true, FieldCategory.DERIVED, "Pattern identifier"));
        derived.add(new FieldDefinition("patternVersion", uniqueFieldName("patternVersion", usedNames), Integer.class, false, FieldCategory.DERIVED, "Pattern version"));
        derived.add(new FieldDefinition("patternPriority", uniqueFieldName("patternPriority", usedNames), Integer.class, false, FieldCategory.DERIVED, "Pattern priority"));
        derived.add(new FieldDefinition("patternName", uniqueFieldName("patternName", usedNames), String.class, false, FieldCategory.DERIVED, "Pattern name"));
        if (spec.getTenantId() != null) {
            derived.add(new FieldDefinition("patternTenantId", uniqueFieldName("patternTenantId", usedNames), String.class, false, FieldCategory.DERIVED, "Pattern tenant id"));
        }
        return derived;
    }

    private Class<?> resolveJavaType(EventParameterSpec spec) {
        if (spec == null || spec.getType() == null) {
            return Object.class;
        }
        EventParameterType type = spec.getType();
        return switch (type) {
            case STRING, ENUM -> String.class;
            case BOOLEAN -> Boolean.class;
            case BYTE -> Byte.class;
            case SHORT -> Short.class;
            case INTEGER -> Integer.class;
            case LONG -> Long.class;
            case FLOAT -> Float.class;
            case DOUBLE -> Double.class;
            case BIG_DECIMAL -> BigDecimal.class;
            case BIG_INTEGER -> BigInteger.class;
            case DATE -> LocalDate.class;
            case TIME -> LocalTime.class;
            case DATE_TIME, TIMESTAMP -> Instant.class;
            case ARRAY, LIST, SET -> List.class;
            case MAP, OBJECT -> Map.class;
            case BINARY -> byte[].class;
            default -> Object.class;
        };
    }

    private String buildJsonSchema(String eventTypeName, Map<String, EventParameterSpec> payload) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", "Schema for " + eventTypeName);
        root.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();

        payload.forEach((name, spec) -> {
            properties.set(name, schemaForSpec(spec));
            if (spec != null && spec.isRequired()) {
                required.add(name);
            }
        });

        root.set("properties", properties);
        if (!required.isEmpty()) {
            root.set("required", required);
        }
        return root.toPrettyString();
    }

    private ObjectNode schemaForSpec(EventParameterSpec spec) {
        ObjectNode node = objectMapper.createObjectNode();
        if (spec == null || spec.getType() == null) {
            node.put("type", "object");
            return node;
        }
        EventParameterType type = spec.getType();
        switch (type) {
            case STRING, ENUM -> node.put("type", "string");
            case BOOLEAN -> node.put("type", "boolean");
            case BYTE, SHORT, INTEGER, LONG -> node.put("type", "integer");
            case FLOAT, DOUBLE, BIG_DECIMAL, BIG_INTEGER -> node.put("type", "number");
            case DATE -> {
                node.put("type", "string");
                node.put("format", "date");
            }
            case TIME -> {
                node.put("type", "string");
                node.put("format", "time");
            }
            case DATE_TIME, TIMESTAMP -> {
                node.put("type", "string");
                node.put("format", "date-time");
            }
            case ARRAY, LIST, SET -> {
                node.put("type", "array");
                EventParameterSpec items = spec.getItemsSpec();
                node.set("items", schemaForSpec(items));
            }
            case MAP -> {
                node.put("type", "object");
                EventParameterSpec valueSpec = spec.getValueSpec();
                node.set("additionalProperties", schemaForSpec(valueSpec));
            }
            case OBJECT -> {
                node.put("type", "object");
                ObjectNode nestedProps = objectMapper.createObjectNode();
                ArrayNode nestedRequired = objectMapper.createArrayNode();
                Map<String, EventParameterSpec> children = Optional.ofNullable(spec.getProperties()).orElse(Map.of());
                children.forEach((childName, childSpec) -> {
                    nestedProps.set(childName, schemaForSpec(childSpec));
                    if (childSpec != null && childSpec.isRequired()) {
                        nestedRequired.add(childName);
                    }
                });
                node.set("properties", nestedProps);
                if (!nestedRequired.isEmpty()) {
                    node.set("required", nestedRequired);
                }
            }
            case BINARY -> {
                node.put("type", "string");
                node.put("contentEncoding", "base64");
            }
            default -> node.put("type", "object");
        }

        if (spec.getEnumValues() != null && !spec.getEnumValues().isEmpty()) {
            ArrayNode enumValues = objectMapper.createArrayNode();
            spec.getEnumValues().forEach(value -> enumValues.add(String.valueOf(value)));
            node.set("enum", enumValues);
        }

        if (spec.getFormat() != null && !spec.getFormat().isBlank()) {
            node.put("format", spec.getFormat());
        }

        return node;
    }

    private void validateJsonSchema(String schema,
                                    List<FieldDefinition> payloadFields,
                                    GeneratedTypeKey key) throws EventClassCompilationException {
        // PHASE 3 TODO: Validation temporarily disabled to break circular dependency
        // Validation logic commented out - will be re-enabled in Phase 4
        /*
        try {
            ObjectNode sample = objectMapper.createObjectNode();
            for (FieldDefinition field : payloadFields) {
                sample.set(field.originalName(), sampleValue(field.javaType()));
            }
            ValidationResult result = jsonSchemaValidator.validateSchemaString(schema, objectMapper.writeValueAsString(sample));
            if (!result.isValid()) {
                List<EventClassCompilationException.Violation> violations = result.getErrors().stream()
                        .map(this::toViolation)
                        .toList();
                throw new EventClassCompilationException(
                        EventClassCompilationException.Reason.VALIDATION,
                        key,
                        "JSON schema validation failed",
                        null,
                        violations
                );
            }
        } catch (EventClassCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new EventClassCompilationException(
                    EventClassCompilationException.Reason.SCHEMA_MAP,
                    key,
                    "Failed to validate JSON schema",
                    e,
                    List.of()
            );
        }
        */
    }

    private JsonNode sampleValue(Class<?> javaType) {
        if (javaType == String.class) {
            return objectMapper.getNodeFactory().textNode("");
        } else if (Number.class.isAssignableFrom(javaType)) {
            return objectMapper.getNodeFactory().numberNode(0);
        } else if (javaType == Boolean.class) {
            return objectMapper.getNodeFactory().booleanNode(true);
        } else if (javaType == Instant.class || javaType == LocalDate.class || javaType == LocalTime.class) {
            return objectMapper.getNodeFactory().textNode(Instant.now().toString());
        } else if (List.class.isAssignableFrom(javaType)) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            arrayNode.add("");
            return arrayNode;
        } else if (Map.class.isAssignableFrom(javaType)) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.put("sample", "");
            return objectNode;
        } else {
            return objectMapper.getNodeFactory().nullNode();
        }
    }

    // PHASE 3 TODO: Validation temporarily disabled
    // private EventClassCompilationException.Violation toViolation(ValidationError error) {
    //     String field = error.getPath() == null ? "" : error.getPath();
    //     String message = error.getMessage() == null ? "validation error" : error.getMessage();
    //     return new EventClassCompilationException.Violation(field, message);
    // }
}
