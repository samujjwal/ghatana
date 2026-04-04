/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.ghatana.datacloud.infrastructure.storage.EntityDocumentMapper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EntityDocumentMapper}.
 *
 * <p>Covers all four public operations: {@link EntityDocumentMapper#toDocument},
 * {@link EntityDocumentMapper#fromDocument}, {@link EntityDocumentMapper#toJson},
 * {@link EntityDocumentMapper#fromJson}, plus the SQL-safety helpers
 * {@link EntityDocumentMapper#escapeValue} and {@link EntityDocumentMapper#escapeIdentifier}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the shared entity ↔ document conversion utility
 * @doc.layer product
 * @doc.pattern Test, Unit
 */
@DisplayName("EntityDocumentMapper")
class EntityDocumentMapperTest {

    private static final String TENANT     = "acme-corp";
    private static final String COLLECTION = "orders";

    // ── toDocument ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toDocument()")
    class ToDocument {

        @Test
        @DisplayName("injects all three metadata fields")
        void injectsMetadataFields() {
            UUID id = UUID.randomUUID();
            Entity entity = entity(id, Map.of("amount", 42));

            Map<String, Object> doc = toDocument(entity);

            assertThat(doc.get(FIELD_TENANT_ID)).isEqualTo(TENANT);
            assertThat(doc.get(FIELD_COLLECTION_NAME)).isEqualTo(COLLECTION);
            assertThat(doc.get(FIELD_ENTITY_ID)).isEqualTo(id.toString());
        }

        @Test
        @DisplayName("preserves all user data fields")
        void preservesUserDataFields() {
            Entity entity = entity(UUID.randomUUID(), Map.of("status", "active", "total", 99.5));

            Map<String, Object> doc = toDocument(entity);

            assertThat(doc.get("status")).isEqualTo("active");
            assertThat(doc.get("total")).isEqualTo(99.5);
        }

        @Test
        @DisplayName("handles entity with null data gracefully")
        void handlesNullData() {
            Entity entity = Entity.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT)
                    .collectionName(COLLECTION)
                    .data(null)
                    .build();

            Map<String, Object> doc = toDocument(entity);

            assertThat(doc).containsKey(FIELD_TENANT_ID);
            assertThat(doc).containsKey(FIELD_COLLECTION_NAME);
            assertThat(doc).containsKey(FIELD_ENTITY_ID);
        }

        @Test
        @DisplayName("handles entity with null ID")
        void handlesNullId() {
            Entity entity = Entity.builder()
                    .tenantId(TENANT)
                    .collectionName(COLLECTION)
                    .data(Map.of("x", 1))
                    .build();

            Map<String, Object> doc = toDocument(entity);

            assertThat(doc.get(FIELD_ENTITY_ID)).isNull();
        }

        @Test
        @DisplayName("returned map is independently mutable")
        void returnedMapIsMutable() {
            Entity entity = entity(UUID.randomUUID(), Map.of("a", 1));

            Map<String, Object> doc = toDocument(entity);
            doc.put("extra", "field");

            assertThat(doc).containsKey("extra");
        }
    }

    // ── fromDocument ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromDocument()")
    class FromDocument {

        @Test
        @DisplayName("reconstructs tenantId, collectionName, and id from metadata fields")
        void reconstructsEntityFromDocument() {
            UUID id = UUID.randomUUID();
            Map<String, Object> doc = new HashMap<>(Map.of(
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       id.toString(),
                    "payload",             "data"
            ));

            Entity entity = fromDocument(doc);

            assertThat(entity.getTenantId()).isEqualTo(TENANT);
            assertThat(entity.getCollectionName()).isEqualTo(COLLECTION);
            assertThat(entity.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("strips metadata fields from entity data payload")
        void stripsMetadataFromData() {
            UUID id = UUID.randomUUID();
            Map<String, Object> doc = new HashMap<>(Map.of(
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       id.toString(),
                    "userField",           "userValue"
            ));

            Entity entity = fromDocument(doc);

            assertThat(entity.getData()).doesNotContainKey(FIELD_TENANT_ID);
            assertThat(entity.getData()).doesNotContainKey(FIELD_COLLECTION_NAME);
            assertThat(entity.getData()).doesNotContainKey(FIELD_ENTITY_ID);
            assertThat(entity.getData()).containsEntry("userField", "userValue");
        }

        @Test
        @DisplayName("generates random UUID when _dc_entity_id is absent")
        void generatesUuidWhenEntityIdAbsent() {
            Map<String, Object> doc = new HashMap<>(Map.of(
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION
            ));

            Entity entity = fromDocument(doc);

            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @DisplayName("generates random UUID when _dc_entity_id is malformed")
        void generatesUuidWhenEntityIdMalformed() {
            Map<String, Object> doc = new HashMap<>(Map.of(
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       "not-a-uuid"
            ));

            Entity entity = fromDocument(doc);

            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @DisplayName("uses empty string for tenantId when _dc_tenant_id is absent")
        void fallsBackToEmptyTenantId() {
            Map<String, Object> doc = new HashMap<>(Map.of(
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       UUID.randomUUID().toString()
            ));

            Entity entity = fromDocument(doc);

            assertThat(entity.getTenantId()).isEqualTo("");
        }
    }

    // ── toJson ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toJson()")
    class ToJson {

        @Test
        @DisplayName("serialises a simple map to a JSON string")
        void serialisesSimpleMap() {
            Map<String, Object> data = Map.of("key", "value", "num", 1);

            String json = toJson(data);

            assertThat(json).contains("\"key\"").contains("\"value\"");
            assertThat(json).contains("\"num\"").contains("1");
        }

        @Test
        @DisplayName("returns '{}' for null input")
        void returnsEmptyObjectForNull() {
            assertThat(toJson(null)).isEqualTo("{}");
        }

        @Test
        @DisplayName("returns '{}' for empty map")
        void returnsEmptyObjectForEmptyMap() {
            assertThat(toJson(Collections.emptyMap())).isEqualTo("{}");
        }
    }

    // ── fromJson ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromJson()")
    class FromJson {

        @Test
        @DisplayName("parses a valid JSON object to a map")
        void parsesValidJson() {
            String json = "{\"status\":\"ok\",\"count\":3}";

            Map<String, Object> result = fromJson(json);

            assertThat(result).containsEntry("status", "ok");
            assertThat(result.get("count")).isInstanceOf(Number.class);
        }

        @Test
        @DisplayName("returns empty map for null input")
        void returnsEmptyMapForNull() {
            assertThat(fromJson(null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty map for blank input")
        void returnsEmptyMapForBlank() {
            assertThat(fromJson("   ")).isEmpty();
        }

        @Test
        @DisplayName("returns empty map for malformed JSON")
        void returnsEmptyMapForMalformedJson() {
            assertThat(fromJson("{not valid json}")).isEmpty();
        }

        @Test
        @DisplayName("round-trips with toJson")
        void roundTripsWithToJson() {
            Map<String, Object> original = new HashMap<>();
            original.put("name", "Alice");
            original.put("score", 95.5);

            Map<String, Object> roundTripped = fromJson(toJson(original));

            assertThat(roundTripped).containsEntry("name", "Alice");
            assertThat(roundTripped.get("score")).isInstanceOf(Number.class);
        }
    }

    // ── escapeValue ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("escapeValue()")
    class EscapeValue {

        @Test
        @DisplayName("returns empty string for null input")
        void returnsEmptyForNull() {
            assertThat(escapeValue(null)).isEqualTo("");
        }

        @Test
        @DisplayName("escapes single quotes")
        void escapesSingleQuotes() {
            assertThat(escapeValue("O'Brien")).isEqualTo("O\\'Brien");
        }

        @Test
        @DisplayName("escapes backslashes before single quotes")
        void escapesBackslashes() {
            assertThat(escapeValue("path\\to")).isEqualTo("path\\\\to");
        }

        @Test
        @DisplayName("strips null bytes (DC3-H1)")
        void stripsNullBytes() {
            assertThat(escapeValue("abc\0def")).isEqualTo("abcdef");
        }

        @Test
        @DisplayName("passes through clean strings unchanged")
        void passesCleanStrings() {
            assertThat(escapeValue("hello-world_123")).isEqualTo("hello-world_123");
        }
    }

    // ── escapeIdentifier ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("escapeIdentifier()")
    class EscapeIdentifier {

        @Test
        @DisplayName("returns empty string for null input")
        void returnsEmptyForNull() {
            assertThat(escapeIdentifier(null)).isEqualTo("");
        }

        @Test
        @DisplayName("applies same escaping as escapeValue")
        void appliesSameEscapingAsValue() {
            // Both methods have identical escaping logic; confirm they agree
            String raw = "tenant's\\data\0col";
            assertThat(escapeIdentifier(raw)).isEqualTo(escapeValue(raw));
        }

        @Test
        @DisplayName("escapes single quotes in identifier")
        void escapesSingleQuotesInIdentifier() {
            assertThat(escapeIdentifier("col'name")).isEqualTo("col\\'name");
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) {
        return Entity.builder()
                .id(id)
                .tenantId(TENANT)
                .collectionName(COLLECTION)
                .data(data)
                .build();
    }
}
