package com.ghatana.platform.observability.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Kernel observability flow contract.
 *
 * @doc.type class
 * @doc.purpose Typed Java representation of the product observability flow manifest
 * @doc.layer platform
 * @doc.pattern Contract
 */
public final class ObservabilityFlowContract {

    public static final String SUPPORTED_SCHEMA_VERSION = "1.0.0";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String schemaVersion;
    private final List<String> requiredFacets;
    private final List<Flow> flows;

    @JsonCreator
    public ObservabilityFlowContract(
            @JsonProperty(value = "schemaVersion", required = true) String schemaVersion,
            @JsonProperty(value = "requiredFacets", required = true) List<String> requiredFacets,
            @JsonProperty(value = "flows", required = true) List<Flow> flows
    ) {
        this.schemaVersion = requireSupportedSchemaVersion(schemaVersion);
        this.requiredFacets = requireNonEmptyList(requiredFacets, "requiredFacets");
        this.flows = requireNonEmptyList(flows, "flows");
    }

    public static ObservabilityFlowContract fromManifest(Path manifestPath) throws IOException {
        Objects.requireNonNull(manifestPath, "manifestPath must not be null");
        return OBJECT_MAPPER.readValue(manifestPath.toFile(), ObservabilityFlowContract.class);
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public List<String> requiredFacets() {
        return requiredFacets;
    }

    public List<Flow> flows() {
        return flows;
    }

    public List<String> getRequiredFacets() {
        return requiredFacets();
    }

    public List<Flow> getFlows() {
        return flows();
    }

    private static String requireSupportedSchemaVersion(String schemaVersion) {
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion must be " + SUPPORTED_SCHEMA_VERSION + ", found " + schemaVersion);
        }
        return schemaVersion;
    }

    private static <T> List<T> requireNonEmptyList(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return List.copyOf(values);
    }

    public record Flow(
            String product,
            String flow,
            FlowKind kind,
            List<String> facets,
            List<Evidence> evidence
    ) {
        @JsonCreator
        public Flow(
                @JsonProperty(value = "product", required = true) String product,
                @JsonProperty(value = "flow", required = true) String flow,
                @JsonProperty(value = "kind", required = true) FlowKind kind,
                @JsonProperty(value = "facets", required = true) List<String> facets,
                @JsonProperty(value = "evidence", required = true) List<Evidence> evidence
        ) {
            this.product = requireNonBlank(product, "product");
            this.flow = requireNonBlank(flow, "flow");
            this.kind = Objects.requireNonNull(kind, "kind must not be null");
            this.facets = requireNonEmptyList(facets, "facets");
            this.evidence = requireNonEmptyList(evidence, "evidence");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Evidence(
            EvidenceType type,
            String file,
            List<String> tokens,
            List<String> requiredFacets
    ) {
        @JsonCreator
        public Evidence(
                @JsonProperty("type") EvidenceType type,
                @JsonProperty(value = "file", required = true) String file,
                @JsonProperty("tokens") List<String> tokens,
                @JsonProperty("requiredFacets") List<String> requiredFacets
        ) {
            this.type = type == null ? EvidenceType.SOURCE : type;
            this.file = requireNonBlank(file, "file");
            if (this.type == EvidenceType.BEHAVIOR) {
                this.requiredFacets = requireNonEmptyList(requiredFacets, "requiredFacets");
                this.tokens = List.of();
            } else {
                this.tokens = requireNonEmptyList(tokens, "tokens");
                this.requiredFacets = List.of();
            }
        }
    }

    public enum FlowKind {
        @JsonProperty("api")
        API,
        @JsonProperty("bridge")
        BRIDGE,
        @JsonProperty("background")
        BACKGROUND,
        @JsonProperty("frontend")
        FRONTEND,
        @JsonProperty("job")
        JOB
    }

    public enum EvidenceType {
        @JsonProperty("source")
        SOURCE,
        @JsonProperty("behavior")
        BEHAVIOR
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
