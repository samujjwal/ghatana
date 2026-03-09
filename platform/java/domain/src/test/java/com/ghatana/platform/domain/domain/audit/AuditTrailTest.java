package com.ghatana.platform.domain.domain.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class AuditTrailTest {
    
    private AuditEntry createTestEntry(String action, String resourceId) {
        return AuditEntry.builder()
            .action(action)
            .resourceId(resourceId)
            .resourceType("TEST")
            .performedBy("test")
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    @Test
    void shouldCreateEmptyAuditTrail() {
        // When
        AuditTrail trail = new AuditTrail(List.of());
        
        // Then
        assertThat(trail.isEmpty()).isTrue();
        assertThat(trail.size()).isZero();
        assertThat(trail.getEntries()).isEmpty();
        assertThat(trail.getLatestEntry()).isEmpty();
    }
    
    @Test
    void shouldCreateAuditTrailWithEntries() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditEntry entry2 = createTestEntry("UPDATE", "res1");
        
        // When
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2));
        
        // Then
        assertThat(trail.isEmpty()).isFalse();
        assertThat(trail.size()).isEqualTo(2);
        assertThat(trail.getEntries()).containsExactly(entry1, entry2);
        assertThat(trail.getLatestEntry()).contains(entry2);
    }
    
    @Test
    void shouldAddEntryToAuditTrail() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditEntry entry2 = createTestEntry("UPDATE", "res1");
        AuditTrail trail = new AuditTrail(List.of(entry1));
        
        // When
        AuditTrail updatedTrail = trail.withEntry(entry2);
        
        // Then
        assertThat(updatedTrail.size()).isEqualTo(2);
        assertThat(updatedTrail.getEntries()).containsExactly(entry1, entry2);
        assertThat(updatedTrail.getLatestEntry()).contains(entry2);
        
        // Original trail should remain unchanged
        assertThat(trail.size()).isEqualTo(1);
    }
    
    @Test
    void shouldNotAllowNullEntriesInConstructor() {
        // When/Then
        assertThatThrownBy(() -> new AuditTrail(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Entries list cannot be null");
    }
    
    @Test
    void shouldNotAllowNullEntryInWithEntry() {
        // Given
        AuditTrail trail = new AuditTrail(List.of());
        
        // When/Then
        assertThatThrownBy(() -> trail.withEntry(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Audit entry cannot be null");
    }
    
    @Test
    void shouldFilterAuditTrail() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditEntry entry2 = createTestEntry("UPDATE", "res1");
        AuditEntry entry3 = createTestEntry("CREATE", "res2");
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2, entry3));
        
        // When
        AuditTrail filteredTrail = trail.filter(entry -> "res1".equals(entry.getResourceId()));
        
        // Then
        assertThat(filteredTrail.size()).isEqualTo(2);
        assertThat(filteredTrail.getEntries())
            .extracting(AuditEntry::getResourceId)
            .containsOnly("res1");
    }
    
    @Test
    void shouldReturnEmptyTrailWhenFilteringOutAllEntries() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditEntry entry2 = createTestEntry("UPDATE", "res1");
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2));
        
        // When
        AuditTrail filteredTrail = trail.filter(entry -> false);
        
        // Then
        assertThat(filteredTrail.isEmpty()).isTrue();
    }
    
    @Test
    void shouldReturnLatestEntry() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditEntry entry2 = createTestEntry("UPDATE", "res1");
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2));
        
        // When/Then
        assertThat(trail.getLatestEntry())
            .isPresent()
            .contains(entry2);
    }
    
    @Test
    void shouldReturnEmptyForLatestEntryWhenTrailIsEmpty() {
        // Given
        AuditTrail trail = new AuditTrail(List.of());
        
        // When/Then
        assertThat(trail.getLatestEntry()).isEmpty();
    }
    
    @Test
    void shouldImplementEqualsAndHashCode() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditEntry entry2 = createTestEntry("UPDATE", "res1");
        
        AuditTrail trail1 = new AuditTrail(List.of(entry1, entry2));
        AuditTrail trail2 = new AuditTrail(List.of(entry1, entry2));
        AuditTrail trail3 = new AuditTrail(List.of(entry1));
        
        // Then
        assertThat(trail1).isEqualTo(trail2);
        assertThat(trail1.hashCode()).isEqualTo(trail2.hashCode());
        
        assertThat(trail1).isNotEqualTo(trail3);
        assertThat(trail1).isNotEqualTo(null);
        assertThat(trail1).isNotEqualTo("not an audit trail");
    }
    
    @Test
    void shouldHaveMeaningfulToString() {
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1");
        AuditTrail trail = new AuditTrail(List.of(entry1));
        
        // When
        String str = trail.toString();
        
        // Then
        assertThat(str).contains("AuditTrail");
        assertThat(str).contains("entries=1");
    }
    
    @Test
    void shouldHandleLargeNumberOfEntries() {
        // Given
        List<AuditEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            entries.add(createTestEntry("ACTION_" + i, "res" + i));
        }
        
        // When
        AuditTrail trail = new AuditTrail(entries);
        
        // Then
        assertThat(trail.size()).isEqualTo(1000);
        assertThat(trail.getLatestEntry())
            .isPresent()
            .hasValueSatisfying(e -> 
                assertThat(e.getAction()).isEqualTo("ACTION_999"));
    }
    
    @Test
    void shouldHandleConcurrentAccess() {
        // Given
        List<AuditEntry> entries = new CopyOnWriteArrayList<>();
        int numThreads = 100;
        int entriesPerThread = 100;
        
        // When
        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Thread t = new Thread(() -> {
                for (int j = 0; j < entriesPerThread; j++) {
                    entries.add(createTestEntry(
                        "ACTION_" + threadId + "_" + j,
                        "RESOURCE_" + threadId + "_" + j
                    ));
                }
            });
            t.setName("TestThread-" + threadId);
            threads.add(t);
            t.start();
        }
        
        // Wait for all threads to complete
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread was interrupted", e);
            }
        }
        
        // Build the final AuditTrail instance
        AuditTrail auditTrail = new AuditTrail(entries);
        
        // Then
        assertEquals(numThreads * entriesPerThread, auditTrail.getEntries().size());
    }
}
