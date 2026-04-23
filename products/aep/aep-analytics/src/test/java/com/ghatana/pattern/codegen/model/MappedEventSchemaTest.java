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
    void mappedSchemaCombinesFieldGroupsAndExposesQualifiedClassName() { // GH-90000
        FieldDefinition header = new FieldDefinition("tenant_id", "tenantId", String.class, true, FieldCategory.HEADER, "tenant"); // GH-90000
        FieldDefinition payload = new FieldDefinition("amount", "amount", Double.class, true, FieldCategory.PAYLOAD, "amount"); // GH-90000
        FieldDefinition derived = new FieldDefinition("risk", "risk", Integer.class, false, FieldCategory.DERIVED, "risk"); // GH-90000

        MappedEventSchema schema = new MappedEventSchema( // GH-90000
                new GeneratedTypeKey("tenant", "Order.Created", "1.0", "pattern-1", "hash-1"), // GH-90000
                "com.ghatana.gen",
                "OrderCreated",
                "{json}",
                List.of(header), // GH-90000
                List.of(payload), // GH-90000
                List.of(derived) // GH-90000
        );

        assertEquals("com.ghatana.gen.OrderCreated", schema.qualifiedClassName()); // GH-90000
        assertEquals(List.of(header), schema.headerFields()); // GH-90000
        assertEquals(List.of(payload), schema.payloadFields()); // GH-90000
        assertEquals(List.of(derived), schema.derivedFields()); // GH-90000
        assertEquals(List.of(header, payload, derived), schema.allFields()); // GH-90000
    }

    @Test
    @DisplayName("mapped schema tolerates null field lists but not null key metadata")
    void mappedSchemaToleratesNullFieldListsButNotNullKeyMetadata() { // GH-90000
        MappedEventSchema schema = new MappedEventSchema( // GH-90000
                new GeneratedTypeKey("tenant", "Order.Created", "1.0", "pattern-1", "hash-1"), // GH-90000
                "com.ghatana.gen",
                "OrderCreated",
                "{}",
                null,
                null,
                null
        );

        assertEquals(List.of(), schema.allFields()); // GH-90000

        assertThrows(NullPointerException.class, // GH-90000
                () -> new MappedEventSchema(null, "pkg", "Name", "{}", List.of(), List.of(), List.of())); // GH-90000
    }
}
