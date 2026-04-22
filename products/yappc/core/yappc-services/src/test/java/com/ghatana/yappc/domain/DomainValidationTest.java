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
    void shouldRejectBlankIntentIdentityAndCoreFields() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> IntentSpec.builder() // GH-90000
            .id("  [GH-90000]")
            .productName("Valid Product [GH-90000]")
            .description("Valid description [GH-90000]")
            .build()); // GH-90000

        assertThrows(IllegalArgumentException.class, () -> IntentSpec.builder() // GH-90000
            .id("intent-1 [GH-90000]")
            .productName("  [GH-90000]")
            .description("Valid description [GH-90000]")
            .build()); // GH-90000

        assertThrows(IllegalArgumentException.class, () -> IntentSpec.builder() // GH-90000
            .id("intent-1 [GH-90000]")
            .productName("Valid Product [GH-90000]")
            .description("  [GH-90000]")
            .build()); // GH-90000
    }

    @Test
    void shouldInitializeNullCollectionsToSafeDefaults() { // GH-90000
        IntentSpec intentSpec = assertDoesNotThrow(() -> IntentSpec.builder() // GH-90000
            .id("intent-1 [GH-90000]")
            .productName("Product [GH-90000]")
            .description("Description [GH-90000]")
            .goals(null) // GH-90000
            .personas(null) // GH-90000
            .constraints(null) // GH-90000
            .metadata(null) // GH-90000
            .build()); // GH-90000

        assertEquals(0, intentSpec.goals().size()); // GH-90000
        assertEquals(0, intentSpec.personas().size()); // GH-90000
        assertEquals(0, intentSpec.constraints().size()); // GH-90000
        assertEquals(0, intentSpec.metadata().size()); // GH-90000
    }

    @Test
    void shouldRejectBlankShapeIdentity() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> ShapeSpec.builder() // GH-90000
            .id("\t [GH-90000]")
            .build()); // GH-90000
    }
}
