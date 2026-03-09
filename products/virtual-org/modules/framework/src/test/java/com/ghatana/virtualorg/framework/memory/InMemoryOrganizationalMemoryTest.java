package com.ghatana.virtualorg.framework.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryOrganizationalMemory.
 *
 * @doc.type test
 * @doc.purpose InMemoryOrganizationalMemory validation
 */
@DisplayName("In-Memory Organizational Memory Tests")
class InMemoryOrganizationalMemoryTest {
    
    private OrganizationalMemory memory;
    
    @BeforeEach
    void setup() {
        memory = new InMemoryOrganizationalMemory();
    }
    
    @Test
    @DisplayName("Create memory instance")
    void testCreateMemory() {
        assertNotNull(memory);
        assertEquals(0, memory.getSize());
    }
    
    @Test
    @DisplayName("Store and retrieve entry")
    void testStoreAndRetrieve() {
        MemoryEntry entry = MemoryEntry.of("decisions", "Test Decision", "Content", "Engineer");
        memory.store(entry);
        
        Optional<MemoryEntry> retrieved = memory.retrieve(entry.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(entry.getId(), retrieved.get().getId());
    }
    
    @Test
    @DisplayName("Search by query")
    void testSearchByQuery() {
        memory.store(MemoryEntry.of("decisions", "Sprint Planning", "Plan sprint", "PM"));
        memory.store(MemoryEntry.of("decisions", "Feature Design", "Design feature", "Architect"));
        memory.store(MemoryEntry.of("bugs", "Bug Fix", "Fix bug", "Engineer"));
        
        List<MemoryEntry> results = memory.search("sprint", 10);
        assertEquals(1, results.size());
        assertEquals("Sprint Planning", results.get(0).getTitle());
    }
    
    @Test
    @DisplayName("Search by category")
    void testSearchByCategory() {
        memory.store(MemoryEntry.of("decisions", "Decision 1", "Content", "PM"));
        memory.store(MemoryEntry.of("decisions", "Decision 2", "Content", "Architect"));
        memory.store(MemoryEntry.of("bugs", "Bug 1", "Content", "Engineer"));
        
        List<MemoryEntry> results = memory.searchByCategory("decisions", 10);
        assertEquals(2, results.size());
    }
    
    @Test
    @DisplayName("Search by actor")
    void testSearchByActor() {
        memory.store(MemoryEntry.of("decisions", "Decision 1", "Content", "PM"));
        memory.store(MemoryEntry.of("decisions", "Decision 2", "Content", "PM"));
        memory.store(MemoryEntry.of("bugs", "Bug 1", "Content", "Engineer"));
        
        List<MemoryEntry> results = memory.searchByActor("PM", 10);
        assertEquals(2, results.size());
    }
    
    @Test
    @DisplayName("Get recent entries")
    void testGetRecent() {
        memory.store(MemoryEntry.of("decisions", "Decision 1", "Content", "PM"));
        memory.store(MemoryEntry.of("decisions", "Decision 2", "Content", "PM"));
        memory.store(MemoryEntry.of("decisions", "Decision 3", "Content", "PM"));
        
        List<MemoryEntry> results = memory.getRecent(2);
        assertEquals(2, results.size());
    }
    
    @Test
    @DisplayName("Clear memory")
    void testClear() {
        memory.store(MemoryEntry.of("decisions", "Decision 1", "Content", "PM"));
        memory.store(MemoryEntry.of("decisions", "Decision 2", "Content", "PM"));
        assertEquals(2, memory.getSize());
        
        memory.clear();
        assertEquals(0, memory.getSize());
    }
    
    @Test
    @DisplayName("Get size")
    void testGetSize() {
        assertEquals(0, memory.getSize());
        
        memory.store(MemoryEntry.of("decisions", "Decision 1", "Content", "PM"));
        assertEquals(1, memory.getSize());
        
        memory.store(MemoryEntry.of("decisions", "Decision 2", "Content", "PM"));
        assertEquals(2, memory.getSize());
    }
    
    @Test
    @DisplayName("Search returns recent first")
    void testSearchReturnsRecentFirst() throws InterruptedException {
        memory.store(MemoryEntry.of("decisions", "Old Decision", "Content", "PM"));
        Thread.sleep(10);
        memory.store(MemoryEntry.of("decisions", "New Decision", "Content", "PM"));
        
        List<MemoryEntry> results = memory.search("decision", 10);
        assertEquals("New Decision", results.get(0).getTitle());
    }
}
