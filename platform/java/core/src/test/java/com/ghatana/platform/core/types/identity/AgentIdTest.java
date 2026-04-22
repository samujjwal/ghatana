package com.ghatana.platform.core.types.identity;

import com.ghatana.platform.types.identity.AgentId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentId.
 */
class AgentIdTest {

    @Test
    void testOf() { // GH-90000
        AgentId id = AgentId.of("test-agent-id [GH-90000]");

        assertEquals("test-agent-id", id.value()); // GH-90000
        assertEquals("test-agent-id", id.raw()); // GH-90000
    }

    @Test
    void testRandom() { // GH-90000
        AgentId id1 = AgentId.random(); // GH-90000
        AgentId id2 = AgentId.random(); // GH-90000

        assertNotNull(id1.value()); // GH-90000
        assertNotNull(id2.value()); // GH-90000
        assertNotEquals(id1.value(), id2.value()); // GH-90000
    }

    @Test
    void testNullValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new AgentId(null) // GH-90000
        );
    }

    @Test
    void testBlankValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new AgentId("   [GH-90000]")
        );
    }

    @Test
    void testEmptyValueThrowsException() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new AgentId(" [GH-90000]")
        );
    }

    @Test
    void testEquals() { // GH-90000
        AgentId id1 = AgentId.of("same-id [GH-90000]");
        AgentId id2 = AgentId.of("same-id [GH-90000]");
        AgentId id3 = AgentId.of("different-id [GH-90000]");

        assertEquals(id1, id2); // GH-90000
        assertNotEquals(id1, id3); // GH-90000
    }

    @Test
    void testHashCode() { // GH-90000
        AgentId id1 = AgentId.of("same-id [GH-90000]");
        AgentId id2 = AgentId.of("same-id [GH-90000]");

        assertEquals(id1.hashCode(), id2.hashCode()); // GH-90000
    }

    @Test
    void testToString() { // GH-90000
        AgentId id = AgentId.of("test-id [GH-90000]");

        assertEquals("test-id", id.toString()); // GH-90000
    }
}
