/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON Schema validation service for YAPPC configurations.
 * 
 * Validates:
 * - Composition definitions
 * - Pack metadata
 * - Deployment configurations
 * - Language definitions
 * - Variable definitions
 * - Plugin configurations
 * 
 * @doc.type class
 * @doc.purpose JSON Schema validation service for all YAPPC configurations
 * @doc.layer platform
 * @doc.pattern Service/Validator
 */
public class SchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationService.class);

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;
    private final Map<SchemaType, JsonSchema> schemas;

    public SchemaValidationService() {
        this.objectMapper = JsonUtils.getDefaultMapper();
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.schemas = new HashMap<>();
        
        // Load schemas
        loadSchemas();
    }

    /**
     * Validate a JSON file against a schema.
     * 
     * @param jsonPath path to JSON file
     * @param schemaType schema type to validate against
     * @return validation result
     */
    public ValidationResult validateFile(Path jsonPath, SchemaType schemaType) {
        try {
            String content = Files.readString(jsonPath);
            return validate(content, schemaType);
        } catch (IOException e) {
            return ValidationResult.error("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Validate JSON content against a schema.
     * 
     * @param jsonContent JSON content as string
     * @param schemaType schema type to validate against
     * @return validation result
     */
    public ValidationResult validate(String jsonContent, SchemaType schemaType) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            return validate(jsonNode, schemaType);
        } catch (IOException e) {
            return ValidationResult.error("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Validate JSON node against a schema.
     * 
     * @param jsonNode JSON node to validate
     * @param schemaType schema type to validate against
     * @return validation result
     */
    public ValidationResult validate(JsonNode jsonNode, SchemaType schemaType) {
        JsonSchema schema = schemas.get(schemaType);
        if (schema == null) {
            return ValidationResult.error("Schema not found: " + schemaType);
        }

        Set<ValidationMessage> errors = schema.validate(jsonNode);
        
        if (errors.isEmpty()) {
            return ValidationResult.success();
        }

        List<String> errorMessages = errors.stream()
            .map(ValidationMessage::getMessage)
            .collect(Collectors.toList());

        return ValidationResult.failure(errorMessages);
    }

    /**
     * Validate composition definition.
     */
    public ValidationResult validateComposition(Path compositionPath) {
        return validateFile(compositionPath, SchemaType.COMPOSITION);
    }

    /**
     * Validate pack metadata.
     */
    public ValidationResult validatePackMetadata(Path packJsonPath) {
        return validateFile(packJsonPath, SchemaType.PACK);
    }

    /**
     * Validate deployment configuration.
     */
    public ValidationResult validateDeployment(Path deploymentPath) {
        return validateFile(deploymentPath, SchemaType.DEPLOYMENT);
    }

    /**
     * Validate language definition.
     */
    public ValidationResult validateLanguage(Path languagePath) {
        return validateFile(languagePath, SchemaType.LANGUAGE);
    }

    /**
     * Validate variable definition.
     */
    public ValidationResult validateVariables(Path variablesPath) {
        return validateFile(variablesPath, SchemaType.VARIABLES);
    }

    /**
     * Validate plugin configuration.
     */
    public ValidationResult validatePlugin(Path pluginPath) {
        return validateFile(pluginPath, SchemaType.PLUGIN);
    }

    /**
     * Load all schemas from resources.
     */
    private void loadSchemas() {
        for (SchemaType type : SchemaType.values()) {
            try {
                JsonSchema schema = loadSchema(type.getSchemaPath());
                schemas.put(type, schema);
                log.debug("Loaded schema: {}", type);
            } catch (Exception e) {
                log.warn("Failed to load schema {}: {}", type, e.getMessage());
            }
        }
        log.info("Loaded {} schemas", schemas.size());
    }

    /**
     * Load a schema from resources.
     */
    private JsonSchema loadSchema(String schemaPath) throws IOException {
        InputStream schemaStream = getClass().getClassLoader()
            .getResourceAsStream("schemas/" + schemaPath);
        
        if (schemaStream == null) {
            throw new IOException("Schema not found: " + schemaPath);
        }

        JsonNode schemaNode = objectMapper.readTree(schemaStream);
        return schemaFactory.getSchema(schemaNode);
    }

    /**
     * Schema type enumeration.
     */
    public enum SchemaType {
        COMPOSITION("composition-v1.json"),
        PACK("pack-v1.json"),
        DEPLOYMENT("deployment-v1.json"),
        LANGUAGE("language-v1.json"),
        VARIABLES("variables-v1.json"),
        PLUGIN("plugin-v1.json");

        private final String schemaPath;

        SchemaType(String schemaPath) {
            this.schemaPath = schemaPath;
        }

        public String getSchemaPath() {
            return schemaPath;
        }
    }

    /**
     * Validation result.
     * 
     * @doc.type record
     * @doc.purpose Validation result with errors and warnings
     * @doc.layer platform
     * @doc.pattern Data Transfer Object
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String summary
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of(), "Validation successful");
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(
                false,
                errors,
                List.of(),
                "Validation failed with " + errors.size() + " error(s)"
            );
        }

        public static ValidationResult error(String error) {
            return new ValidationResult(
                false,
                List.of(error),
                List.of(),
                "Validation error: " + error
            );
        }

        public static ValidationResult withWarnings(List<String> warnings) {
            return new ValidationResult(
                true,
                List.of(),
                warnings,
                "Validation successful with " + warnings.size() + " warning(s)"
            );
        }
    }
}
