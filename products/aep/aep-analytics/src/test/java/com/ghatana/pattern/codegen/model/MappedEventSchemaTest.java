package com.ghatana.pattern.codegen.model;

import com.ghatana.pattern.api.codegen.GeneratedTypeKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("MappedEventSchema Tests")
class MappedEventSchemaTest {

    @Test
    @DisplayName("mapped schema combines field groups and exposes qualified class name")
    void mappedSchemaCombinesFieldGroupsAndExposesQualifiedClassName() {
        FieldDefinition header = new FieldDefinition("tenant_id", "tenantId", String.class, true, FieldCategory.HEADER, "tenant");
        FieldDefinition payload = new FieldDefinition("amount", "amount", Double.class, true, FieldCategory.PAYLOAD, "amount");
        FieldDefinition derived = new FieldDefinition("risk", "risk", Integer.class, false, FieldCategory.DERIVED, "risk");

        MappedEventSchema schema = new MappedEventSchema(
                new GeneratedTypeKey("tenant", "Order.Created", "1.0", "pattern-1", "hash-1"),
                "com.ghatana.gen",
                "OrderCreated",
                "{json}",
                List.of(header),
                List.of(payload),
                List.of(derived)
        );

        assertEquals("com.ghatana.gen.OrderCreated", schema.qualifiedClassName());
        assertEquals(List.of(header), schema.headerFields());
        assertEquals(List.of(payload), schema.payloadFields());
        assertEquals(List.of(derived), schema.derivedFields());
        assertEquals(List.of(header, payload, derived), schema.allFields());
    }

    @Test
    @DisplayName("mapped schema tolerates null field lists but not null key metadata")
    void mappedSchemaToleratesNullFieldListsButNotNullKeyMetadata() {
        MappedEventSchema schema = new MappedEventSchema(
                new GeneratedTypeKey("tenant", "Order.Created", "1.0", "pattern-1", "hash-1"),
                "com.ghatana.gen",
                "OrderCreated",
                "{}",
                null,
                null,
                null
        );

        assertEquals(List.of(), schema.allFields());

        assertThrows(NullPointerException.class,
                () -> new MappedEventSchema(null, "pkg", "Name", "{}", List.of(), List.of(), List.of()));
    }
}