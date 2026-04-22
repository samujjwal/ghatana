package com.ghatana.platform.core.types.identity;

import com.ghatana.platform.types.identity.OperatorId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OperatorId.
 */
class OperatorIdTest {

    @Test
    void testOf() { // GH-90000
        OperatorId id = OperatorId.of("test-operator-id [GH-90000]");

        assertEquals("test-operator-id", id.value()); // GH-90000
        assertEquals("test-operator-id", id.raw()); // GH-90000
    }

    @Test
    void testRandom() { // GH-90000
        OperatorId id1 = OperatorId.random(); // GH-90000
        OperatorId id2 = OperatorId.random(); // GH-90000

        assertNotNull(id1.value()); // GH-90000
        assertNotNull(id2.value()); // GH-90000
        assertNotEquals(id1.value(), id2.value()); // GH-90000
    }

    @Test
    void testNullValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new OperatorId(null) // GH-90000
        );
    }

    @Test
    void testBlankValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new OperatorId("   [GH-90000]")
        );
    }

    @Test
    void testEquals() { // GH-90000
        OperatorId id1 = OperatorId.of("same-id [GH-90000]");
        OperatorId id2 = OperatorId.of("same-id [GH-90000]");
        OperatorId id3 = OperatorId.of("different-id [GH-90000]");

        assertEquals(id1, id2); // GH-90000
        assertNotEquals(id1, id3); // GH-90000
    }
}
