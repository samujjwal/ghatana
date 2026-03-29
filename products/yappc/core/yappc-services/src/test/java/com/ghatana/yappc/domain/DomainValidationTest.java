package com.ghatana.yappc.domain;

import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Regression tests for domain record validation invariants
 * @doc.layer test
 * @doc.pattern Unit Test
 */
class DomainValidationTest {

    @Test
    void shouldRejectBlankIntentIdentityAndCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> IntentSpec.builder()
            .id(" ")
            .productName("Valid Product")
            .description("Valid description")
            .build());

        assertThrows(IllegalArgumentException.class, () -> IntentSpec.builder()
            .id("intent-1")
            .productName(" ")
            .description("Valid description")
            .build());

        assertThrows(IllegalArgumentException.class, () -> IntentSpec.builder()
            .id("intent-1")
            .productName("Valid Product")
            .description(" ")
            .build());
    }

    @Test
    void shouldInitializeNullCollectionsToSafeDefaults() {
        IntentSpec intentSpec = assertDoesNotThrow(() -> IntentSpec.builder()
            .id("intent-1")
            .productName("Product")
            .description("Description")
            .goals(null)
            .personas(null)
            .constraints(null)
            .metadata(null)
            .build());

        assertEquals(0, intentSpec.goals().size());
        assertEquals(0, intentSpec.personas().size());
        assertEquals(0, intentSpec.constraints().size());
        assertEquals(0, intentSpec.metadata().size());
    }

    @Test
    void shouldRejectBlankShapeIdentity() {
        assertThrows(IllegalArgumentException.class, () -> ShapeSpec.builder()
            .id("\t")
            .build());
    }
}
