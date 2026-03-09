package com.ghatana.platform.core.types.identity;

import com.ghatana.platform.types.identity.AgentId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentId.
 */
class AgentIdTest {

    @Test
    void testOf() {
        AgentId id = AgentId.of("test-agent-id");
        
        assertEquals("test-agent-id", id.value());
        assertEquals("test-agent-id", id.raw());
    }

    @Test
    void testRandom() {
        AgentId id1 = AgentId.random();
        AgentId id2 = AgentId.random();
        
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void testNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AgentId(null)
        );
    }

    @Test
    void testBlankValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AgentId("  ")
        );
    }

    @Test
    void testEmptyValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AgentId("")
        );
    }

    @Test
    void testEquals() {
        AgentId id1 = AgentId.of("same-id");
        AgentId id2 = AgentId.of("same-id");
        AgentId id3 = AgentId.of("different-id");
        
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
    }

    @Test
    void testHashCode() {
        AgentId id1 = AgentId.of("same-id");
        AgentId id2 = AgentId.of("same-id");
        
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testToString() {
        AgentId id = AgentId.of("test-id");
        
        assertEquals("test-id", id.toString());
    }
}
