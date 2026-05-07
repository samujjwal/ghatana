package com.ghatana.pipeline.registry.service;

import java.util.List;
import java.util.Map;

/**
 * Service for querying platform capabilities.
 *
 * <p>Purpose: Provides runtime information about available schema formats,
 * encodings, connector types, and transforms. Enables dynamic UI configuration
 * based on what capabilities are enabled in the current deployment.</p>
 *
 * @doc.type class
 * @doc.purpose Provides platform capability discovery for dynamic configuration
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class CapabilitiesService {

    public Map<String, Object> getSchemaFormats() {
        return Map.of(
                "schemaFormats",
                List.of(
                        Map.of("id", "JSON_SCHEMA", "display", "JSON Schema", "enabled", true),
                        Map.of("id", "AVRO", "display", "Avro", "enabled", true),
                        Map.of("id", "PROTOBUF", "display", "Protobuf", "enabled", false),
                        Map.of("id", "CSV", "display", "CSV", "enabled", true)));
    }

    public Map<String, Object> getEncodings() {
        return Map.of(
                "encodings",
                List.of("JSON", "AVRO_BINARY", "PROTOBUF_BINARY", "CSV", "TEXT"));
    }

    public Map<String, Object> getConnectors() {
        return Map.of(
                "connectors",
                List.of(
                        Map.of("type", "HTTP_INGRESS", "direction", "INGRESS", "enabled", true),
                        Map.of("type", "HTTP_EGRESS", "direction", "EGRESS", "enabled", true),
                        Map.of("type", "KAFKA", "direction", "BOTH", "enabled", false),
                        Map.of("type", "SQS", "direction", "BOTH", "enabled", true),
                        Map.of("type", "WEBHOOK", "direction", "EGRESS", "enabled", true)));
    }

    public Map<String, Object> getTransforms() {
        return Map.of(
                "transforms",
                List.of(
                        Map.of("id", "uuid()", "description", "Generate a new UUID"),
                        Map.of("id", "now()", "description", "Current timestamp in RFC3339"),
                        Map.of("id", "uppercase", "description", "Convert string to uppercase")));
    }
}
