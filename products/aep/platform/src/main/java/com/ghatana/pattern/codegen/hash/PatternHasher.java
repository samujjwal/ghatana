package com.ghatana.pattern.codegen.hash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.platform.domain.domain.event.EventType;
import com.ghatana.pattern.api.model.PatternSpecification;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Produces stable hashes for (EventType, PatternSpecification) pairs.
 */
public final class PatternHasher {
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper mapper used for deterministic JSON serialization.
     */
    public PatternHasher(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Generates a SHA-256 hash derived from the event meta-data and pattern specification.
     */
    public String hash(EventType eventType, PatternSpecification specification) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(specification, "specification");
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("tenantId", eventType.getTenantId());
            root.put("eventType", eventType.getName());
            root.put("version", eventType.getVersion());
            root.set("pattern", objectMapper.valueToTree(specification));
            byte[] data = objectMapper.writeValueAsBytes(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash pattern specification", e);
        }
    }
}
