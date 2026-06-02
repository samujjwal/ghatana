package com.ghatana.platform.http.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Parser for Kernel route contract JSON files.
 *
 * <p>This parser reads route contract JSON files and produces type-safe
 * RouteContract objects. The parser uses Jackson's built-in validation
 * through the model's @JsonProperty annotations and builder pattern.</p>
 *
 * @doc.type class
 * @doc.purpose Parses Kernel route contract JSON files
 * @doc.layer platform
 * @doc.pattern Parser
 */
public class RouteContractParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectMapper objectMapper;

    public RouteContractParser() {
        this(JSON);
    }

    public RouteContractParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Parses a route contract from a JSON file path.
     *
     * @param path the path to the route contract JSON file
     * @return the parsed RouteContract
     * @throws IOException if the file cannot be read or is invalid
     */
    public RouteContract parse(Path path) throws IOException {
        String content = Files.readString(path);
        return parse(content);
    }

    /**
     * Parses a route contract from a JSON string.
     *
     * @param json the JSON string
     * @return the parsed RouteContract
     * @throws IOException if the JSON cannot be parsed
     */
    public RouteContract parse(String json) throws IOException {
        return objectMapper.readValue(json, RouteContract.class);
    }

    /**
     * Parses a route contract from an input stream.
     *
     * @param inputStream the input stream containing the JSON
     * @return the parsed RouteContract
     * @throws IOException if the stream cannot be read or is invalid
     */
    public RouteContract parse(InputStream inputStream) throws IOException {
        return objectMapper.readValue(inputStream, RouteContract.class);
    }

    /**
     * Validates a route contract JSON node for basic structure.
     *
     * <p>This performs basic validation that required fields are present.
     * Full schema validation can be added via JSON Schema if needed.</p>
     *
     * @param jsonNode the JSON node to validate
     * @throws RouteContractValidationException if validation fails
     */
    public void validate(JsonNode jsonNode) throws RouteContractValidationException {
        if (!jsonNode.has("schemaVersion")) {
            throw new RouteContractValidationException("Missing required field: schemaVersion");
        }
        if (!jsonNode.has("product")) {
            throw new RouteContractValidationException("Missing required field: product");
        }
        if (!jsonNode.has("routes")) {
            throw new RouteContractValidationException("Missing required field: routes");
        }
        if (!jsonNode.has("roleOrder")) {
            throw new RouteContractValidationException("Missing required field: roleOrder");
        }
    }

    /**
     * Exception thrown when route contract validation fails.
     */
    public static class RouteContractValidationException extends Exception {
        public RouteContractValidationException(String message) {
            super(message);
        }
    }
}
