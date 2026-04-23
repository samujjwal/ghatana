package com.ghatana.platform.domain.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditEntryTest {

    @Test
    void shouldCreateAuditEntryWithRequiredFields() { // GH-90000
        // Given
        String action = "CREATE";
        String resourceType = "USER";
        String resourceId = "123";
        String performedBy = "test-user";
        long timestamp = System.currentTimeMillis(); // GH-90000
        String notes = "Test notes";

        // When
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action(action) // GH-90000
            .resourceType(resourceType) // GH-90000
            .resourceId(resourceId) // GH-90000
            .performedBy(performedBy) // GH-90000
            .timestamp(timestamp) // GH-90000
            .notes(notes) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(entry.getAction()).isEqualTo(action); // GH-90000
        assertThat(entry.getResourceType()).isEqualTo(resourceType); // GH-90000
        assertThat(entry.getResourceId()).isEqualTo(resourceId); // GH-90000
        assertThat(entry.getPerformedBy()).isEqualTo(performedBy); // GH-90000
        assertThat(entry.getTimestamp()).isEqualTo(timestamp); // GH-90000
        assertThat(entry.getNotes()).isEqualTo(notes); // GH-90000
    }

    @Test
    void shouldCreateAuditEntryWithNotes() { // GH-90000
        // Given
        String notes = "User was created with admin privileges";

        // When
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("UPDATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes(notes) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(entry.getNotes()).isEqualTo(notes); // GH-90000
    }

    @Test
    void shouldThrowExceptionWhenRequiredFieldsAreMissing() { // GH-90000
        // When/Then
        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .resourceType("USER")
                .resourceId("123")
                .performedBy("test-user")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Action should be required"
        );

        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .action("CREATE")
                .resourceId("123")
                .performedBy("test-user")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Resource type should be required"
        );

        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .action("CREATE")
                .resourceType("USER")
                .performedBy("test-user")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Resource ID should be required"
        );

        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .action("CREATE")
                .resourceType("USER")
                .resourceId("123")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Performed by should be required"
        );
    }

    @Test
    void shouldHandleNullNotes() { // GH-90000
        // When
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("DELETE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes(null) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(entry.getNotes()).isNull(); // GH-90000
    }

    @Test
    void shouldHandleEmptyNotes() { // GH-90000
        // When
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("VIEW")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes("")
            .build(); // GH-90000

        // Then
        assertThat(entry.getNotes()).isEmpty(); // GH-90000
    }

    @Test
    void shouldBeImmutable() { // GH-90000
        // Given
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("UPDATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes("Original notes")
            .build(); // GH-90000

        // When/Then - Verify that the class is immutable by checking for mutator methods
        assertThat(entry.getClass().getMethods()) // GH-90000
            .filteredOn(method -> method.getName().startsWith("set"))
            .describedAs("AuditEntry should not have any setter methods")
            .isEmpty(); // GH-90000
    }

    @Test
    void shouldImplementComparable() { // GH-90000
        // Given
        long now = System.currentTimeMillis(); // GH-90000
        AuditEntry earlier = AuditEntry.builder() // GH-90000
            .action("CREATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(now - 1000) // GH-90000
            .build(); // GH-90000

        AuditEntry later = AuditEntry.builder() // GH-90000
            .action("UPDATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(now) // GH-90000
            .build(); // GH-90000

        // When/Then
        assertThat(earlier.compareTo(later)).isNegative(); // GH-90000
        assertThat(later.compareTo(earlier)).isPositive(); // GH-90000
        assertThat(earlier.compareTo(earlier)).isZero(); // GH-90000
    }

    @Test
    void shouldHaveUsefulToString() { // GH-90000
        // Given
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("CREATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(1234567890L) // GH-90000
            .notes("User created")
            .build(); // GH-90000

        // When
        String toString = entry.toString(); // GH-90000

        // Then
        assertThat(toString) // GH-90000
            .contains("action=CREATE")
            .contains("resourceType=USER")
            .contains("resourceId=123")
            .contains("performedBy=test-user")
            .contains("timestamp=1234567890")
            .contains("notes=User created");
    }

    @Test
    void shouldCreateCopyWithNewNotes() { // GH-90000
        // Given
        AuditEntry original = AuditEntry.builder() // GH-90000
            .action("CREATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes("Original notes")
            .build(); // GH-90000

        // When
        AuditEntry updated = original.withNotes("Updated notes");

        // Then
        assertThat(original.getNotes()).isEqualTo("Original notes");
        assertThat(updated.getNotes()).isEqualTo("Updated notes");

        // Verify other fields remain the same
        assertThat(updated.getAction()).isEqualTo(original.getAction()); // GH-90000
        assertThat(updated.getResourceType()).isEqualTo(original.getResourceType()); // GH-90000
        assertThat(updated.getResourceId()).isEqualTo(original.getResourceId()); // GH-90000
        assertThat(updated.getPerformedBy()).isEqualTo(original.getPerformedBy()); // GH-90000
        assertThat(updated.getTimestamp()).isEqualTo(original.getTimestamp()); // GH-90000
    }
}
