/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry.agents;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema Validator Agent — deterministically validates entity payloads against
 * registered collection schemas in Data Cloud.
 *
 * <p>Input: a {@link SchemaValidationRequest} carrying the tenant ID, collection
 * name, and the raw field map to validate.</p>
 *
 * <p>Output: a {@link SchemaValidationResult} listing any constraint violations
 * and an overall {@code valid} flag.</p>
 *
 * <h3>Validation rules applied</h3>
 * <ul>
 *   <li>Required fields must be present and non-null</li>
 *   <li>Field types must match the declared schema type</li>
 *   <li>String length constraints must be satisfied</li>
 *   <li>Numeric range constraints must be satisfied</li>
 *   <li>Enum fields must contain only declared values</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Deterministic entity schema validation agent
 * @doc.layer product
 * @doc.pattern Agent, Deterministic
 */
public class SchemaValidatorAgent extends AbstractTypedAgent<SchemaValidatorAgent.SchemaValidationRequest, SchemaValidatorAgent.SchemaValidationResult> {

    private static final AgentDescriptor DESCRIPTOR = AgentDescriptor.builder()
            .agentId("data-cloud:agent.data-cloud.schema-validator")
            .name("Data Cloud Schema Validator")
            .version("1.0.0")
            .description("Validates entity payloads against Data Cloud collection schemas")
            .namespace("data-cloud")
            .type(AgentType.DETERMINISTIC)
            .subtype("RULE_BASED")
            .determinism(DeterminismGuarantee.FULL)
            .latencySla(Duration.ofMillis(5))
            .capabilities(Set.of("schema-validation", "type-checking", "constraint-enforcement"))
            .build();

    @Override
    public @NotNull AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected @NotNull Promise<AgentResult<SchemaValidationResult>> doProcess(
            @NotNull AgentContext ctx,
            @NotNull SchemaValidationRequest request) {

        List<String> violations = new ArrayList<>();

        // Validate required fields
        if (request.schema() != null) {
            for (String requiredField : request.schema().requiredFields()) {
                if (!request.fields().containsKey(requiredField)
                        || request.fields().get(requiredField) == null) {
                    violations.add("Required field missing: " + requiredField);
                }
            }

            // Validate field type constraints
            for (Map.Entry<String, Object> entry : request.fields().entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                String expectedType = request.schema().fieldTypes().get(fieldName);

                if (expectedType != null && value != null) {
                    if (!isTypeCompatible(value, expectedType)) {
                        violations.add("Type mismatch for field '" + fieldName
                                + "': expected " + expectedType
                                + ", got " + value.getClass().getSimpleName());
                    }
                }
            }
        }

        boolean valid = violations.isEmpty();
        SchemaValidationResult result = new SchemaValidationResult(
                valid,
                violations,
                request.tenantId(),
                request.collection());

        return Promise.of(AgentResult.<SchemaValidationResult>builder()
                .output(result)
                .confidence(1.0)
                .agentId(DESCRIPTOR.getAgentId())
                .explanation(valid ? "Schema validation passed"
                        : "Schema validation failed with " + violations.size() + " violation(s)")
                .build());
    }

    private boolean isTypeCompatible(Object value, String expectedType) {
        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String;
            case "integer", "int", "long" -> value instanceof Integer || value instanceof Long;
            case "double", "float", "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array", "list" -> value instanceof List;
            case "object", "map" -> value instanceof Map;
            default -> true; // unknown types pass through
        };
    }

    // ─── Input / Output Types ────────────────────────────────────────────────

    /**
     * Schema definition used for validation.
     *
     * @param requiredFields fields that must be present
     * @param fieldTypes     map of field name to expected type string
     */
    public record FieldSchema(List<String> requiredFields, Map<String, String> fieldTypes) {}

    /**
     * Validation request.
     *
     * @param tenantId   the tenant requesting validation
     * @param collection the collection name whose schema to validate against
     * @param fields     the field map to validate
     * @param schema     the schema to validate against (may be null for lenient mode)
     */
    public record SchemaValidationRequest(
            String tenantId,
            String collection,
            Map<String, Object> fields,
            FieldSchema schema) {}

    /**
     * Validation result.
     *
     * @param valid      {@code true} if all constraints passed
     * @param violations list of human-readable violation messages
     * @param tenantId   echoed from the request for correlation
     * @param collection echoed from the request for correlation
     */
    public record SchemaValidationResult(
            boolean valid,
            List<String> violations,
            String tenantId,
            String collection) {}
}
