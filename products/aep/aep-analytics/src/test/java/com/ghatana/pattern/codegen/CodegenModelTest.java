package com.ghatana.pattern.codegen;

import com.ghatana.pattern.codegen.model.FieldCategory;
import com.ghatana.pattern.codegen.model.FieldDefinition;
import com.ghatana.pattern.codegen.naming.SanitizedFieldNamingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Codegen Model Tests")
class CodegenModelTest {

    private final SanitizedFieldNamingStrategy strategy = new SanitizedFieldNamingStrategy(); 

    @Test
    @DisplayName("sanitized naming strategy normalizes mixed input into java-safe field names")
    void sanitizedNamingStrategyNormalizesMixedInputIntoJavaSafeFieldNames() { 
        assertEquals("customerId", strategy.toFieldName("Customer ID"));
                assertEquals("f123Status", strategy.toFieldName("123-status"));
        assertEquals("classValue", strategy.toFieldName("class"));
        assertEquals("field", strategy.toFieldName(null)); 
        assertEquals("field", strategy.toFieldName("   "));
        assertEquals("field", strategy.toFieldName("---"));
    }

    @Test
    @DisplayName("field definition preserves values and derives getter name")
    void fieldDefinitionPreservesValuesAndDerivesGetterName() { 
        FieldDefinition definition = new FieldDefinition( 
                "event_id",
                "eventId",
                String.class,
                true,
                FieldCategory.HEADER,
                null
        );
        FieldDefinition singleLetterDefinition = new FieldDefinition( 
                "x",
                "x",
                Integer.class,
                false,
                FieldCategory.DERIVED,
                "Count"
        );

        assertEquals("event_id", definition.originalName()); 
        assertEquals("eventId", definition.javaName()); 
        assertEquals(String.class, definition.javaType()); 
        assertEquals(true, definition.required()); 
        assertEquals(FieldCategory.HEADER, definition.category()); 
        assertEquals("", definition.description()); 
        assertEquals("getEventId", definition.getterName()); 
        assertEquals("getX", singleLetterDefinition.getterName()); 
        assertEquals("Count", singleLetterDefinition.description()); 
    }

    @Test
    @DisplayName("field definition rejects required null constructor arguments")
    void fieldDefinitionRejectsRequiredNullConstructorArguments() { 
        assertThrows(NullPointerException.class, 
                () -> new FieldDefinition(null, "field", String.class, true, FieldCategory.PAYLOAD, "desc")); 
        assertThrows(NullPointerException.class, 
                () -> new FieldDefinition("orig", null, String.class, true, FieldCategory.PAYLOAD, "desc")); 
        assertThrows(NullPointerException.class, 
                () -> new FieldDefinition("orig", "field", null, true, FieldCategory.PAYLOAD, "desc")); 
        assertThrows(NullPointerException.class, 
                () -> new FieldDefinition("orig", "field", String.class, true, null, "desc")); 
    }
}
