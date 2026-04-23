package com.ghatana.platform.core.types.identity;

import com.ghatana.platform.types.identity.EventTypeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventTypeId.
 */
class EventTypeIdTest {

    @Test
    void testOf() { // GH-90000
        EventTypeId id = EventTypeId.of("test-event-type");

        assertEquals("test-event-type", id.value()); // GH-90000
        assertEquals("test-event-type", id.raw()); // GH-90000
    }

    @Test
    void testRandom() { // GH-90000
        EventTypeId id1 = EventTypeId.random(); // GH-90000
        EventTypeId id2 = EventTypeId.random(); // GH-90000

        assertNotNull(id1.value()); // GH-90000
        assertNotNull(id2.value()); // GH-90000
        assertNotEquals(id1.value(), id2.value()); // GH-90000
    }

    @Test
    void testNullValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new EventTypeId(null) // GH-90000
        );
    }

    @Test
    void testBlankValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new EventTypeId("  ")
        );
    }

    @Test
    void testEquals() { // GH-90000
        EventTypeId id1 = EventTypeId.of("same-id");
        EventTypeId id2 = EventTypeId.of("same-id");
        EventTypeId id3 = EventTypeId.of("different-id");

        assertEquals(id1, id2); // GH-90000
        assertNotEquals(id1, id3); // GH-90000
    }
}
