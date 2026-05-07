package com.ghatana.services.featurestore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * YAML-configurable feature transformation specification for the feature-store-ingest pipeline.
 *
 * <p>Controls which events are ingested and which fields are materialized as ML features.
 *
 * <h3>Example YAML config</h3>
 * <pre>{@code
 * # feature-transforms.yaml
 * eventTypes:
 *   - ENTITY_CREATED
 *   - ENTITY_UPDATED
 * includeFields:
 *   - amount
 *   - score
 *   - status
 * excludeFields:
 *   - password
 *   - secret_key
 * derivedTimeFeatures: true
 * }</pre>
 *
 * <p>Load via {@link #fromYamlFile(String)} or {@link #fromYamlStream(InputStream)}.
 * When no config is provided the pipeline accepts all event types and all fields.
 *
 * @doc.type class
 * @doc.purpose YAML-configurable feature extraction rules for the ingest pipeline
 * @doc.layer product
 * @doc.pattern ValueObject, Configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FeatureTransformSpec {

    private static final Logger log = LoggerFactory.getLogger(FeatureTransformSpec.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules();

    // Jackson requires mutable setters or public fields for deserialization.
    // These are exposed as immutable views via getters.
    private List<String> eventTypes = new ArrayList<>();
    private List<String> includeFields = new ArrayList<>();
    private List<String> excludeFields = new ArrayList<>();
    private boolean derivedTimeFeatures = true;

    /** No-arg constructor for Jackson deserialization. */
    public FeatureTransformSpec() {}

    private FeatureTransformSpec(Builder b) {
        this.eventTypes = b.eventTypes;
        this.includeFields = b.includeFields;
        this.excludeFields = b.excludeFields;
        this.derivedTimeFeatures = b.derivedTimeFeatures;
    }

    /**
     * Loads a {@code FeatureTransformSpec} from a YAML file path.
     *
     * @param path absolute or relative path to the YAML config file
     * @return parsed spec
     * @throws IOException if the file cannot be read or parsed
     */
    public static FeatureTransformSpec fromYamlFile(String path) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return fromYamlStream(in);
        }
    }

    /**
     * Loads a {@code FeatureTransformSpec} from an {@link InputStream}.
     *
     * @param in YAML input stream
     * @return parsed spec
     * @throws IOException if the stream cannot be parsed
     */
    public static FeatureTransformSpec fromYamlStream(InputStream in) throws IOException {
        return YAML_MAPPER.readValue(in, FeatureTransformSpec.class);
    }

    /**
     * Returns a pass-through spec that accepts all events and all fields.
     * Used as the default when no YAML config is provided.
     */
    public static FeatureTransformSpec passThrough() {
        return new FeatureTransformSpec();
    }

    /**
     * Returns {@code true} if the given event type should be processed.
     *
     * @param eventType event type string from {@code EventEntry.eventType()}
     * @return true when the event passes the filter
     */
    public boolean acceptsEventType(String eventType) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return true; // no filter — accept all
        }
        return eventTypes.contains(eventType);
    }

    /**
     * Returns {@code true} if the given field name should be materialized as a feature.
     *
     * <p>Evaluation order: if {@code includeFields} is non-empty the field must be listed.
     * If {@code excludeFields} is non-empty the field must NOT be listed.
     *
     * @param fieldName raw field name from the event payload
     * @return true when the field should be materialized
     */
    public boolean acceptsField(String fieldName) {
        if (excludeFields != null && !excludeFields.isEmpty() && excludeFields.contains(fieldName)) {
            return false;
        }
        if (includeFields != null && !includeFields.isEmpty()) {
            return includeFields.contains(fieldName);
        }
        return true;
    }

    /** Whether to append derived time features (hour_of_day, day_of_week). */
    public boolean isDerivedTimeFeatures() {
        return derivedTimeFeatures;
    }

    /** Event types that should be ingested; empty means accept all. */
    public List<String> getEventTypes() {
        return List.copyOf(eventTypes == null ? List.of() : eventTypes);
    }

    /** Explicit allow-list of payload fields; empty means allow all. */
    public List<String> getIncludeFields() {
        return List.copyOf(includeFields == null ? List.of() : includeFields);
    }

    /** Explicit deny-list of payload fields. */
    public List<String> getExcludeFields() {
        return List.copyOf(excludeFields == null ? List.of() : excludeFields);
    }

    // Jackson setters
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }
    public void setIncludeFields(List<String> includeFields) { this.includeFields = includeFields; }
    public void setExcludeFields(List<String> excludeFields) { this.excludeFields = excludeFields; }
    public void setDerivedTimeFeatures(boolean derivedTimeFeatures) { this.derivedTimeFeatures = derivedTimeFeatures; }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for programmatic construction (e.g. in tests). */
    public static final class Builder {
        private List<String> eventTypes = new ArrayList<>();
        private List<String> includeFields = new ArrayList<>();
        private List<String> excludeFields = new ArrayList<>();
        private boolean derivedTimeFeatures = true;

        public Builder eventTypes(List<String> types) { this.eventTypes = new ArrayList<>(types); return this; }
        public Builder eventType(String type) { this.eventTypes.add(type); return this; }
        public Builder includeFields(List<String> fields) { this.includeFields = new ArrayList<>(fields); return this; }
        public Builder includeField(String field) { this.includeFields.add(field); return this; }
        public Builder excludeFields(List<String> fields) { this.excludeFields = new ArrayList<>(fields); return this; }
        public Builder excludeField(String field) { this.excludeFields.add(field); return this; }
        public Builder derivedTimeFeatures(boolean enabled) { this.derivedTimeFeatures = enabled; return this; }

        public FeatureTransformSpec build() {
            return new FeatureTransformSpec(this);
        }
    }
}
