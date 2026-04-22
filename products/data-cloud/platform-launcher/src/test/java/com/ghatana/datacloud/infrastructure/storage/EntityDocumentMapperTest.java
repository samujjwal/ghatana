/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("EntityDocumentMapper [GH-90000]")
class EntityDocumentMapperTest {

    private static final String TENANT     = "acme-corp";
    private static final String COLLECTION = "orders";

    // ── toDocument ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toDocument() [GH-90000]")
    class ToDocument {

        @Test
        @DisplayName("injects all three metadata fields [GH-90000]")
        void injectsMetadataFields() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity entity = entity(id, Map.of("amount", 42)); // GH-90000

            Map<String, Object> doc = toDocument(entity); // GH-90000

            assertThat(doc.get(FIELD_TENANT_ID)).isEqualTo(TENANT); // GH-90000
            assertThat(doc.get(FIELD_COLLECTION_NAME)).isEqualTo(COLLECTION); // GH-90000
            assertThat(doc.get(FIELD_ENTITY_ID)).isEqualTo(id.toString()); // GH-90000
        }

        @Test
        @DisplayName("preserves all user data fields [GH-90000]")
        void preservesUserDataFields() { // GH-90000
            Entity entity = entity(UUID.randomUUID(), Map.of("status", "active", "total", 99.5)); // GH-90000

            Map<String, Object> doc = toDocument(entity); // GH-90000

            assertThat(doc.get("status [GH-90000]")).isEqualTo("active [GH-90000]");
            assertThat(doc.get("total [GH-90000]")).isEqualTo(99.5);
        }

        @Test
        @DisplayName("handles entity with null data gracefully [GH-90000]")
        void handlesNullData() { // GH-90000
            Entity entity = Entity.builder() // GH-90000
                    .id(UUID.randomUUID()) // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .data(null) // GH-90000
                    .build(); // GH-90000

            Map<String, Object> doc = toDocument(entity); // GH-90000

            assertThat(doc).containsKey(FIELD_TENANT_ID); // GH-90000
            assertThat(doc).containsKey(FIELD_COLLECTION_NAME); // GH-90000
            assertThat(doc).containsKey(FIELD_ENTITY_ID); // GH-90000
        }

        @Test
        @DisplayName("handles entity with null ID [GH-90000]")
        void handlesNullId() { // GH-90000
            Entity entity = Entity.builder() // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .data(Map.of("x", 1)) // GH-90000
                    .build(); // GH-90000

            Map<String, Object> doc = toDocument(entity); // GH-90000

            assertThat(doc.get(FIELD_ENTITY_ID)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("returned map is independently mutable [GH-90000]")
        void returnedMapIsMutable() { // GH-90000
            Entity entity = entity(UUID.randomUUID(), Map.of("a", 1)); // GH-90000

            Map<String, Object> doc = toDocument(entity); // GH-90000
            doc.put("extra", "field"); // GH-90000

            assertThat(doc).containsKey("extra [GH-90000]");
        }
    }

    // ── fromDocument ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromDocument() [GH-90000]")
    class FromDocument {

        @Test
        @DisplayName("reconstructs tenantId, collectionName, and id from metadata fields [GH-90000]")
        void reconstructsEntityFromDocument() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Map<String, Object> doc = new HashMap<>(Map.of( // GH-90000
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       id.toString(), // GH-90000
                    "payload",             "data"
            ));

            Entity entity = fromDocument(doc); // GH-90000

            assertThat(entity.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(entity.getCollectionName()).isEqualTo(COLLECTION); // GH-90000
            assertThat(entity.getId()).isEqualTo(id); // GH-90000
        }

        @Test
        @DisplayName("strips metadata fields from entity data payload [GH-90000]")
        void stripsMetadataFromData() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Map<String, Object> doc = new HashMap<>(Map.of( // GH-90000
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       id.toString(), // GH-90000
                    "userField",           "userValue"
            ));

            Entity entity = fromDocument(doc); // GH-90000

            assertThat(entity.getData()).doesNotContainKey(FIELD_TENANT_ID); // GH-90000
            assertThat(entity.getData()).doesNotContainKey(FIELD_COLLECTION_NAME); // GH-90000
            assertThat(entity.getData()).doesNotContainKey(FIELD_ENTITY_ID); // GH-90000
            assertThat(entity.getData()).containsEntry("userField", "userValue"); // GH-90000
        }

        @Test
        @DisplayName("generates random UUID when _dc_entity_id is absent [GH-90000]")
        void generatesUuidWhenEntityIdAbsent() { // GH-90000
            Map<String, Object> doc = new HashMap<>(Map.of( // GH-90000
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION
            ));

            Entity entity = fromDocument(doc); // GH-90000

            assertThat(entity.getId()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("generates random UUID when _dc_entity_id is malformed [GH-90000]")
        void generatesUuidWhenEntityIdMalformed() { // GH-90000
            Map<String, Object> doc = new HashMap<>(Map.of( // GH-90000
                    FIELD_TENANT_ID,       TENANT,
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       "not-a-uuid"
            ));

            Entity entity = fromDocument(doc); // GH-90000

            assertThat(entity.getId()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("uses empty string for tenantId when _dc_tenant_id is absent [GH-90000]")
        void fallsBackToEmptyTenantId() { // GH-90000
            Map<String, Object> doc = new HashMap<>(Map.of( // GH-90000
                    FIELD_COLLECTION_NAME, COLLECTION,
                    FIELD_ENTITY_ID,       UUID.randomUUID().toString() // GH-90000
            ));

            Entity entity = fromDocument(doc); // GH-90000

            assertThat(entity.getTenantId()).isEqualTo(" [GH-90000]");
        }
    }

    // ── toJson ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toJson() [GH-90000]")
    class ToJson {

        @Test
        @DisplayName("serialises a simple map to a JSON string [GH-90000]")
        void serialisesSimpleMap() { // GH-90000
            Map<String, Object> data = Map.of("key", "value", "num", 1); // GH-90000

            String json = toJson(data); // GH-90000

            assertThat(json).contains("\"key\"").contains("\"value\""); // GH-90000
            assertThat(json).contains("\"num\"").contains("1 [GH-90000]");
        }

        @Test
        @DisplayName("returns '{}' for null input [GH-90000]")
        void returnsEmptyObjectForNull() { // GH-90000
            assertThat(toJson(null)).isEqualTo("{} [GH-90000]");
        }

        @Test
        @DisplayName("returns '{}' for empty map [GH-90000]")
        void returnsEmptyObjectForEmptyMap() { // GH-90000
            assertThat(toJson(Collections.emptyMap())).isEqualTo("{} [GH-90000]");
        }
    }

    // ── fromJson ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromJson() [GH-90000]")
    class FromJson {

        @Test
        @DisplayName("parses a valid JSON object to a map [GH-90000]")
        void parsesValidJson() { // GH-90000
            String json = "{\"status\":\"ok\",\"count\":3}";

            Map<String, Object> result = fromJson(json); // GH-90000

            assertThat(result).containsEntry("status", "ok"); // GH-90000
            assertThat(result.get("count [GH-90000]")).isInstanceOf(Number.class);
        }

        @Test
        @DisplayName("returns empty map for null input [GH-90000]")
        void returnsEmptyMapForNull() { // GH-90000
            assertThat(fromJson(null)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty map for blank input [GH-90000]")
        void returnsEmptyMapForBlank() { // GH-90000
            assertThat(fromJson("    [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("returns empty map for malformed JSON [GH-90000]")
        void returnsEmptyMapForMalformedJson() { // GH-90000
            assertThat(fromJson("{not valid json} [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("round-trips with toJson [GH-90000]")
        void roundTripsWithToJson() { // GH-90000
            Map<String, Object> original = new HashMap<>(); // GH-90000
            original.put("name", "Alice"); // GH-90000
            original.put("score", 95.5); // GH-90000

            Map<String, Object> roundTripped = fromJson(toJson(original)); // GH-90000

            assertThat(roundTripped).containsEntry("name", "Alice"); // GH-90000
            assertThat(roundTripped.get("score [GH-90000]")).isInstanceOf(Number.class);
        }
    }

    // ── escapeValue ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("escapeValue() [GH-90000]")
    class EscapeValue {

        @Test
        @DisplayName("returns empty string for null input [GH-90000]")
        void returnsEmptyForNull() { // GH-90000
            assertThat(escapeValue(null)).isEqualTo(" [GH-90000]");
        }

        @Test
        @DisplayName("escapes single quotes [GH-90000]")
        void escapesSingleQuotes() { // GH-90000
            assertThat(escapeValue("O'Brien [GH-90000]")).isEqualTo("O\\'Brien [GH-90000]");
        }

        @Test
        @DisplayName("escapes backslashes before single quotes [GH-90000]")
        void escapesBackslashes() { // GH-90000
            assertThat(escapeValue("path\\to [GH-90000]")).isEqualTo("path\\\\to [GH-90000]");
        }

        @Test
        @DisplayName("strips null bytes (DC3-H1) [GH-90000]")
        void stripsNullBytes() { // GH-90000
            assertThat(escapeValue("abc\0def [GH-90000]")).isEqualTo("abcdef [GH-90000]");
        }

        @Test
        @DisplayName("passes through clean strings unchanged [GH-90000]")
        void passesCleanStrings() { // GH-90000
            assertThat(escapeValue("hello-world_123 [GH-90000]")).isEqualTo("hello-world_123 [GH-90000]");
        }
    }

    // ── escapeIdentifier ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("escapeIdentifier() [GH-90000]")
    class EscapeIdentifier {

        @Test
        @DisplayName("returns empty string for null input [GH-90000]")
        void returnsEmptyForNull() { // GH-90000
            assertThat(escapeIdentifier(null)).isEqualTo(" [GH-90000]");
        }

        @Test
        @DisplayName("applies same escaping as escapeValue [GH-90000]")
        void appliesSameEscapingAsValue() { // GH-90000
            // Both methods have identical escaping logic; confirm they agree
            String raw = "tenant's\\data\0col";
            assertThat(escapeIdentifier(raw)).isEqualTo(escapeValue(raw)); // GH-90000
        }

        @Test
        @DisplayName("escapes single quotes in identifier [GH-90000]")
        void escapesSingleQuotesInIdentifier() { // GH-90000
            assertThat(escapeIdentifier("col'name [GH-90000]")).isEqualTo("col\\'name [GH-90000]");
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) { // GH-90000
        return Entity.builder() // GH-90000
                .id(id) // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(data) // GH-90000
                .build(); // GH-90000
    }
}
