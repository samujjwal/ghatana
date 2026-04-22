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
            .action("UPDATE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
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
                .resourceType("USER [GH-90000]")
                .resourceId("123 [GH-90000]")
                .performedBy("test-user [GH-90000]")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Action should be required"
        );

        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .action("CREATE [GH-90000]")
                .resourceId("123 [GH-90000]")
                .performedBy("test-user [GH-90000]")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Resource type should be required"
        );

        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .action("CREATE [GH-90000]")
                .resourceType("USER [GH-90000]")
                .performedBy("test-user [GH-90000]")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Resource ID should be required"
        );

        assertThrows(NullPointerException.class, () -> // GH-90000
            AuditEntry.builder() // GH-90000
                .action("CREATE [GH-90000]")
                .resourceType("USER [GH-90000]")
                .resourceId("123 [GH-90000]")
                .timestamp(System.currentTimeMillis()) // GH-90000
                .build(), // GH-90000
            "Performed by should be required"
        );
    }

    @Test
    void shouldHandleNullNotes() { // GH-90000
        // When
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("DELETE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
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
            .action("VIEW [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes(" [GH-90000]")
            .build(); // GH-90000

        // Then
        assertThat(entry.getNotes()).isEmpty(); // GH-90000
    }

    @Test
    void shouldBeImmutable() { // GH-90000
        // Given
        AuditEntry entry = AuditEntry.builder() // GH-90000
            .action("UPDATE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes("Original notes [GH-90000]")
            .build(); // GH-90000

        // When/Then - Verify that the class is immutable by checking for mutator methods
        assertThat(entry.getClass().getMethods()) // GH-90000
            .filteredOn(method -> method.getName().startsWith("set [GH-90000]"))
            .describedAs("AuditEntry should not have any setter methods [GH-90000]")
            .isEmpty(); // GH-90000
    }

    @Test
    void shouldImplementComparable() { // GH-90000
        // Given
        long now = System.currentTimeMillis(); // GH-90000
        AuditEntry earlier = AuditEntry.builder() // GH-90000
            .action("CREATE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
            .timestamp(now - 1000) // GH-90000
            .build(); // GH-90000

        AuditEntry later = AuditEntry.builder() // GH-90000
            .action("UPDATE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
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
            .action("CREATE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
            .timestamp(1234567890L) // GH-90000
            .notes("User created [GH-90000]")
            .build(); // GH-90000

        // When
        String toString = entry.toString(); // GH-90000

        // Then
        assertThat(toString) // GH-90000
            .contains("action=CREATE [GH-90000]")
            .contains("resourceType=USER [GH-90000]")
            .contains("resourceId=123 [GH-90000]")
            .contains("performedBy=test-user [GH-90000]")
            .contains("timestamp=1234567890 [GH-90000]")
            .contains("notes=User created [GH-90000]");
    }

    @Test
    void shouldCreateCopyWithNewNotes() { // GH-90000
        // Given
        AuditEntry original = AuditEntry.builder() // GH-90000
            .action("CREATE [GH-90000]")
            .resourceType("USER [GH-90000]")
            .resourceId("123 [GH-90000]")
            .performedBy("test-user [GH-90000]")
            .timestamp(System.currentTimeMillis()) // GH-90000
            .notes("Original notes [GH-90000]")
            .build(); // GH-90000

        // When
        AuditEntry updated = original.withNotes("Updated notes [GH-90000]");

        // Then
        assertThat(original.getNotes()).isEqualTo("Original notes [GH-90000]");
        assertThat(updated.getNotes()).isEqualTo("Updated notes [GH-90000]");

        // Verify other fields remain the same
        assertThat(updated.getAction()).isEqualTo(original.getAction()); // GH-90000
        assertThat(updated.getResourceType()).isEqualTo(original.getResourceType()); // GH-90000
        assertThat(updated.getResourceId()).isEqualTo(original.getResourceId()); // GH-90000
        assertThat(updated.getPerformedBy()).isEqualTo(original.getPerformedBy()); // GH-90000
        assertThat(updated.getTimestamp()).isEqualTo(original.getTimestamp()); // GH-90000
    }
}
