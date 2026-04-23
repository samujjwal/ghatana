package com.ghatana.platform.domain.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class AuditTrailTest {

    private AuditEntry createTestEntry(String action, String resourceId) { // GH-90000
        return AuditEntry.builder() // GH-90000
            .action(action) // GH-90000
            .resourceId(resourceId) // GH-90000
            .resourceType("TEST")
            .performedBy("test")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .build(); // GH-90000
    }

    @Test
    void shouldCreateEmptyAuditTrail() { // GH-90000
        // When
        AuditTrail trail = new AuditTrail(List.of()); // GH-90000

        // Then
        assertThat(trail.isEmpty()).isTrue(); // GH-90000
        assertThat(trail.size()).isZero(); // GH-90000
        assertThat(trail.getEntries()).isEmpty(); // GH-90000
        assertThat(trail.getLatestEntry()).isEmpty(); // GH-90000
    }

    @Test
    void shouldCreateAuditTrailWithEntries() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditEntry entry2 = createTestEntry("UPDATE", "res1"); // GH-90000

        // When
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2)); // GH-90000

        // Then
        assertThat(trail.isEmpty()).isFalse(); // GH-90000
        assertThat(trail.size()).isEqualTo(2); // GH-90000
        assertThat(trail.getEntries()).containsExactly(entry1, entry2); // GH-90000
        assertThat(trail.getLatestEntry()).contains(entry2); // GH-90000
    }

    @Test
    void shouldAddEntryToAuditTrail() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditEntry entry2 = createTestEntry("UPDATE", "res1"); // GH-90000
        AuditTrail trail = new AuditTrail(List.of(entry1)); // GH-90000

        // When
        AuditTrail updatedTrail = trail.withEntry(entry2); // GH-90000

        // Then
        assertThat(updatedTrail.size()).isEqualTo(2); // GH-90000
        assertThat(updatedTrail.getEntries()).containsExactly(entry1, entry2); // GH-90000
        assertThat(updatedTrail.getLatestEntry()).contains(entry2); // GH-90000

        // Original trail should remain unchanged
        assertThat(trail.size()).isEqualTo(1); // GH-90000
    }

    @Test
    void shouldNotAllowNullEntriesInConstructor() { // GH-90000
        // When/Then
        assertThatThrownBy(() -> new AuditTrail(null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessage("Entries list cannot be null");
    }

    @Test
    void shouldNotAllowNullEntryInWithEntry() { // GH-90000
        // Given
        AuditTrail trail = new AuditTrail(List.of()); // GH-90000

        // When/Then
        assertThatThrownBy(() -> trail.withEntry(null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessage("Audit entry cannot be null");
    }

    @Test
    void shouldFilterAuditTrail() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditEntry entry2 = createTestEntry("UPDATE", "res1"); // GH-90000
        AuditEntry entry3 = createTestEntry("CREATE", "res2"); // GH-90000
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2, entry3)); // GH-90000

        // When
        AuditTrail filteredTrail = trail.filter(entry -> "res1".equals(entry.getResourceId())); // GH-90000

        // Then
        assertThat(filteredTrail.size()).isEqualTo(2); // GH-90000
        assertThat(filteredTrail.getEntries()) // GH-90000
            .extracting(AuditEntry::getResourceId) // GH-90000
            .containsOnly("res1");
    }

    @Test
    void shouldReturnEmptyTrailWhenFilteringOutAllEntries() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditEntry entry2 = createTestEntry("UPDATE", "res1"); // GH-90000
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2)); // GH-90000

        // When
        AuditTrail filteredTrail = trail.filter(entry -> false); // GH-90000

        // Then
        assertThat(filteredTrail.isEmpty()).isTrue(); // GH-90000
    }

    @Test
    void shouldReturnLatestEntry() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditEntry entry2 = createTestEntry("UPDATE", "res1"); // GH-90000
        AuditTrail trail = new AuditTrail(List.of(entry1, entry2)); // GH-90000

        // When/Then
        assertThat(trail.getLatestEntry()) // GH-90000
            .isPresent() // GH-90000
            .contains(entry2); // GH-90000
    }

    @Test
    void shouldReturnEmptyForLatestEntryWhenTrailIsEmpty() { // GH-90000
        // Given
        AuditTrail trail = new AuditTrail(List.of()); // GH-90000

        // When/Then
        assertThat(trail.getLatestEntry()).isEmpty(); // GH-90000
    }

    @Test
    void shouldImplementEqualsAndHashCode() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditEntry entry2 = createTestEntry("UPDATE", "res1"); // GH-90000

        AuditTrail trail1 = new AuditTrail(List.of(entry1, entry2)); // GH-90000
        AuditTrail trail2 = new AuditTrail(List.of(entry1, entry2)); // GH-90000
        AuditTrail trail3 = new AuditTrail(List.of(entry1)); // GH-90000

        // Then
        assertThat(trail1).isEqualTo(trail2); // GH-90000
        assertThat(trail1.hashCode()).isEqualTo(trail2.hashCode()); // GH-90000

        assertThat(trail1).isNotEqualTo(trail3); // GH-90000
        assertThat(trail1).isNotEqualTo(null); // GH-90000
        assertThat(trail1).isNotEqualTo("not an audit trail");
    }

    @Test
    void shouldHaveMeaningfulToString() { // GH-90000
        // Given
        AuditEntry entry1 = createTestEntry("CREATE", "res1"); // GH-90000
        AuditTrail trail = new AuditTrail(List.of(entry1)); // GH-90000

        // When
        String str = trail.toString(); // GH-90000

        // Then
        assertThat(str).contains("AuditTrail");
        assertThat(str).contains("entries=1");
    }

    @Test
    void shouldHandleLargeNumberOfEntries() { // GH-90000
        // Given
        List<AuditEntry> entries = new java.util.ArrayList<>(); // GH-90000
        for (int i = 0; i < 1000; i++) { // GH-90000
            entries.add(createTestEntry("ACTION_" + i, "res" + i)); // GH-90000
        }

        // When
        AuditTrail trail = new AuditTrail(entries); // GH-90000

        // Then
        assertThat(trail.size()).isEqualTo(1000); // GH-90000
        assertThat(trail.getLatestEntry()) // GH-90000
            .isPresent() // GH-90000
            .hasValueSatisfying(e -> // GH-90000
                assertThat(e.getAction()).isEqualTo("ACTION_999"));
    }

    @Test
    void shouldHandleConcurrentAccess() { // GH-90000
        // Given
        List<AuditEntry> entries = new CopyOnWriteArrayList<>(); // GH-90000
        int numThreads = 100;
        int entriesPerThread = 100;

        // When
        List<Thread> threads = new java.util.ArrayList<>(); // GH-90000
        for (int i = 0; i < numThreads; i++) { // GH-90000
            final int threadId = i;
            Thread t = new Thread(() -> { // GH-90000
                for (int j = 0; j < entriesPerThread; j++) { // GH-90000
                    entries.add(createTestEntry( // GH-90000
                        "ACTION_" + threadId + "_" + j,
                        "RESOURCE_" + threadId + "_" + j
                    ));
                }
            });
            t.setName("TestThread-" + threadId); // GH-90000
            threads.add(t); // GH-90000
            t.start(); // GH-90000
        }

        // Wait for all threads to complete
        for (Thread t : threads) { // GH-90000
            try {
                t.join(); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
                fail("Thread was interrupted", e); // GH-90000
            }
        }

        // Build the final AuditTrail instance
        AuditTrail auditTrail = new AuditTrail(entries); // GH-90000

        // Then
        assertEquals(numThreads * entriesPerThread, auditTrail.getEntries().size()); // GH-90000
    }
}
