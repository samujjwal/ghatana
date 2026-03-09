package com.ghatana.pattern.api.codegen;

import com.ghatana.platform.domain.domain.event.EventType;
import com.ghatana.pattern.api.model.PatternSpecification;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable identifier for a dynamically generated event class.
 *
 * <p>The key forms the basis for cache lookups, auditing, and telemetry.</p>
 */
public record GeneratedTypeKey(
        String tenantId,
        String eventTypeName,
        String eventTypeVersion,
        String patternId,
        String specificationHash) {

    public GeneratedTypeKey {
        tenantId = sanitize(tenantId, "tenantId");
        eventTypeName = sanitize(eventTypeName, "eventTypeName");
        eventTypeVersion = sanitize(eventTypeVersion, "eventTypeVersion");
        patternId = sanitize(patternId, "patternId");
        specificationHash = sanitize(specificationHash, "specificationHash");
    }

    /**
     * Builds a key from canonical domain objects.
     *
     * @param eventType event metadata
     * @param specification pattern definition
     * @param specHash stable hash/fingerprint for the specification
     * @return immutable key
     */
    public static GeneratedTypeKey from(EventType eventType,
                                        PatternSpecification specification,
                                        String specHash) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(specHash, "specHash");

        String tenant = Optional.ofNullable(eventType.getTenantId()).orElse("default");
        String pattern = Optional.ofNullable(specification.getId())
                .map(UUID::toString)
                .orElseGet(() -> Optional.ofNullable(specification.getName()).orElse("anonymous"));
        return new GeneratedTypeKey(
                tenant,
                eventType.getName(),
                eventType.getVersion(),
                pattern,
                specHash);
    }

    /**
     * Canonical cache key.
     */
    public String asCacheKey() {
        return String.join("|", tenantId, eventTypeName, eventTypeVersion, patternId, specificationHash);
    }

    /**
     * Token-safe class suffix used when building class names.
     */
    public String classNameToken() {
        return sanitizeIdentifier(eventTypeName) + "__v" + versionToken() + "__p" + sanitizeIdentifier(patternId);
    }

    /**
     * Version token with dots replaced by underscores.
     */
    public String versionToken() {
        return eventTypeVersion.replace('.', '_');
    }

    private static String sanitize(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return trimmed;
    }

    private static String sanitizeIdentifier(String raw) {
        String base = raw == null ? "" : raw.trim();
        if (base.isEmpty()) {
            return "anonymous";
        }
        String alphanumeric = base
                .replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_");
        if (Character.isDigit(alphanumeric.charAt(0))) {
            alphanumeric = "_" + alphanumeric;
        }
        return toUpperCamel(alphanumeric);
    }

    private static String toUpperCamel(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean capitalize = true;
        for (char c : value.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalize = true;
                continue;
            }
            if (capitalize) {
                builder.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    /**
     * Human-friendly representation used in audit logs.
     */
    public String describe() {
        return String.format(Locale.ROOT,
                "%s/%s:%s pattern=%s hash=%s",
                tenantId,
                eventTypeName,
                eventTypeVersion,
                patternId,
                specificationHash);
    }
}
