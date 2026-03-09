package com.ghatana.platform.core.types.identity;

import com.ghatana.platform.types.identity.EventTypeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventTypeId.
 */
class EventTypeIdTest {

    @Test
    void testOf() {
        EventTypeId id = EventTypeId.of("test-event-type");
        
        assertEquals("test-event-type", id.value());
        assertEquals("test-event-type", id.raw());
    }

    @Test
    void testRandom() {
        EventTypeId id1 = EventTypeId.random();
        EventTypeId id2 = EventTypeId.random();
        
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void testNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EventTypeId(null)
        );
    }

    @Test
    void testBlankValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EventTypeId("  ")
        );
    }

    @Test
    void testEquals() {
        EventTypeId id1 = EventTypeId.of("same-id");
        EventTypeId id2 = EventTypeId.of("same-id");
        EventTypeId id3 = EventTypeId.of("different-id");
        
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
    }
}
