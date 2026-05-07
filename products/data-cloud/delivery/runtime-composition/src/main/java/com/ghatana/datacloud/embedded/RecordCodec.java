/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.6 — JSON-based Record serialization/deserialization for persistent storage backends.
 */
package com.ghatana.datacloud.embedded;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ghatana.datacloud.record.Record;

import java.io.IOException;
import java.util.*;

/**
 * Data record entity
 *
 * @doc.type record
 * @doc.purpose Data record entity
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class RecordCodec {

    /** Plain ObjectMapper — no BigInteger/BigDecimal promotion so primitives survive round-trip. */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private RecordCodec() {}

    /**
     * Serializes a Record to a UTF-8 JSON byte array.
     */
    public static byte[] serialize(Record record) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", record.id().toString());
            map.put("tenantId", record.tenantId());
            map.put("collectionName", record.collectionName());
            map.put("recordType", record.recordType().name());
            map.put("data", record.data());
            return MAPPER.writeValueAsBytes(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize record: " + record.id(), e);
        }
    }

    /**
     * Deserializes a UTF-8 JSON byte array back to a Record.
     */
    @SuppressWarnings("unchecked")
    public static Record deserialize(byte[] bytes) {
        try {
            Map<String, Object> map = MAPPER.readValue(bytes, Map.class);
            UUID id = UUID.fromString((String) map.get("id"));
            String tenantId = (String) map.get("tenantId");
            String collectionName = (String) map.get("collectionName");
            Record.RecordType recordType = Record.RecordType.valueOf((String) map.get("recordType"));
            Map<String, Object> data = map.containsKey("data") && map.get("data") instanceof Map
                    ? (Map<String, Object>) map.get("data")
                    : Map.of();
            return new StoredRecord(id, tenantId, collectionName, recordType, data);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize record", e);
        }
    }

    /**
     * Immutable Record implementation used for deserialization from persistent stores.
     */
    public record StoredRecord(
            UUID id,
            String tenantId,
            String collectionName,
            Record.RecordType recordType,
            Map<String, Object> data
    ) implements Record {

        public StoredRecord {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(tenantId, "tenantId required");
            Objects.requireNonNull(collectionName, "collectionName required");
            Objects.requireNonNull(recordType, "recordType required");
            data = data != null ? Map.copyOf(data) : Map.of();
        }
    }
}
