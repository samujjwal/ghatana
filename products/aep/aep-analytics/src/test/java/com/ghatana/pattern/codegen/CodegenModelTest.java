package com.ghatana.pattern.codegen;

import com.ghatana.pattern.codegen.model.FieldCategory;
import com.ghatana.pattern.codegen.model.FieldDefinition;
import com.ghatana.pattern.codegen.naming.SanitizedFieldNamingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Codegen Model Tests [GH-90000]")
class CodegenModelTest {

    private final SanitizedFieldNamingStrategy strategy = new SanitizedFieldNamingStrategy(); // GH-90000

    @Test
    @DisplayName("sanitized naming strategy normalizes mixed input into java-safe field names [GH-90000]")
    void sanitizedNamingStrategyNormalizesMixedInputIntoJavaSafeFieldNames() { // GH-90000
        assertEquals("customerId", strategy.toFieldName("Customer ID [GH-90000]"));
                assertEquals("f123Status", strategy.toFieldName("123-status [GH-90000]"));
        assertEquals("classValue", strategy.toFieldName("class [GH-90000]"));
        assertEquals("field", strategy.toFieldName(null)); // GH-90000
        assertEquals("field", strategy.toFieldName("    [GH-90000]"));
        assertEquals("field", strategy.toFieldName("--- [GH-90000]"));
    }

    @Test
    @DisplayName("field definition preserves values and derives getter name [GH-90000]")
    void fieldDefinitionPreservesValuesAndDerivesGetterName() { // GH-90000
        FieldDefinition definition = new FieldDefinition( // GH-90000
                "event_id",
                "eventId",
                String.class,
                true,
                FieldCategory.HEADER,
                null
        );
        FieldDefinition singleLetterDefinition = new FieldDefinition( // GH-90000
                "x",
                "x",
                Integer.class,
                false,
                FieldCategory.DERIVED,
                "Count"
        );

        assertEquals("event_id", definition.originalName()); // GH-90000
        assertEquals("eventId", definition.javaName()); // GH-90000
        assertEquals(String.class, definition.javaType()); // GH-90000
        assertEquals(true, definition.required()); // GH-90000
        assertEquals(FieldCategory.HEADER, definition.category()); // GH-90000
        assertEquals("", definition.description()); // GH-90000
        assertEquals("getEventId", definition.getterName()); // GH-90000
        assertEquals("getX", singleLetterDefinition.getterName()); // GH-90000
        assertEquals("Count", singleLetterDefinition.description()); // GH-90000
    }

    @Test
    @DisplayName("field definition rejects required null constructor arguments [GH-90000]")
    void fieldDefinitionRejectsRequiredNullConstructorArguments() { // GH-90000
        assertThrows(NullPointerException.class, // GH-90000
                () -> new FieldDefinition(null, "field", String.class, true, FieldCategory.PAYLOAD, "desc")); // GH-90000
        assertThrows(NullPointerException.class, // GH-90000
                () -> new FieldDefinition("orig", null, String.class, true, FieldCategory.PAYLOAD, "desc")); // GH-90000
        assertThrows(NullPointerException.class, // GH-90000
                () -> new FieldDefinition("orig", "field", null, true, FieldCategory.PAYLOAD, "desc")); // GH-90000
        assertThrows(NullPointerException.class, // GH-90000
                () -> new FieldDefinition("orig", "field", String.class, true, null, "desc")); // GH-90000
    }
}
