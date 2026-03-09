package com.ghatana.platform.core.types.identity;

import com.ghatana.platform.types.identity.OperatorId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OperatorId.
 */
class OperatorIdTest {

    @Test
    void testOf() {
        OperatorId id = OperatorId.of("test-operator-id");
        
        assertEquals("test-operator-id", id.value());
        assertEquals("test-operator-id", id.raw());
    }

    @Test
    void testRandom() {
        OperatorId id1 = OperatorId.random();
        OperatorId id2 = OperatorId.random();
        
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void testNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new OperatorId(null)
        );
    }

    @Test
    void testBlankValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new OperatorId("  ")
        );
    }

    @Test
    void testEquals() {
        OperatorId id1 = OperatorId.of("same-id");
        OperatorId id2 = OperatorId.of("same-id");
        OperatorId id3 = OperatorId.of("different-id");
        
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
    }
}
