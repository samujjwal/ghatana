package com.ghatana.platform.domain.domain.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditEntryTest {

    @Test
    void shouldCreateAuditEntryWithRequiredFields() {
        // Given
        String action = "CREATE";
        String resourceType = "USER";
        String resourceId = "123";
        String performedBy = "test-user";
        long timestamp = System.currentTimeMillis();
        String notes = "Test notes";

        // When
        AuditEntry entry = AuditEntry.builder()
            .action(action)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .performedBy(performedBy)
            .timestamp(timestamp)
            .notes(notes)
            .build();

        // Then
        assertThat(entry.getAction()).isEqualTo(action);
        assertThat(entry.getResourceType()).isEqualTo(resourceType);
        assertThat(entry.getResourceId()).isEqualTo(resourceId);
        assertThat(entry.getPerformedBy()).isEqualTo(performedBy);
        assertThat(entry.getTimestamp()).isEqualTo(timestamp);
        assertThat(entry.getNotes()).isEqualTo(notes);
    }

    @Test
    void shouldCreateAuditEntryWithNotes() {
        // Given
        String notes = "User was created with admin privileges";

        // When
        AuditEntry entry = AuditEntry.builder()
            .action("UPDATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis())
            .notes(notes)
            .build();

        // Then
        assertThat(entry.getNotes()).isEqualTo(notes);
    }

    @Test
    void shouldThrowExceptionWhenRequiredFieldsAreMissing() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            AuditEntry.builder()
                .resourceType("USER")
                .resourceId("123")
                .performedBy("test-user")
                .timestamp(System.currentTimeMillis())
                .build(),
            "Action should be required"
        );

        assertThrows(NullPointerException.class, () ->
            AuditEntry.builder()
                .action("CREATE")
                .resourceId("123")
                .performedBy("test-user")
                .timestamp(System.currentTimeMillis())
                .build(),
            "Resource type should be required"
        );

        assertThrows(NullPointerException.class, () ->
            AuditEntry.builder()
                .action("CREATE")
                .resourceType("USER")
                .performedBy("test-user")
                .timestamp(System.currentTimeMillis())
                .build(),
            "Resource ID should be required"
        );

        assertThrows(NullPointerException.class, () ->
            AuditEntry.builder()
                .action("CREATE")
                .resourceType("USER")
                .resourceId("123")
                .timestamp(System.currentTimeMillis())
                .build(),
            "Performed by should be required"
        );
    }

    @Test
    void shouldHandleNullNotes() {
        // When
        AuditEntry entry = AuditEntry.builder()
            .action("DELETE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis())
            .notes(null)
            .build();

        // Then
        assertThat(entry.getNotes()).isNull();
    }

    @Test
    void shouldHandleEmptyNotes() {
        // When
        AuditEntry entry = AuditEntry.builder()
            .action("VIEW")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis())
            .notes("")
            .build();

        // Then
        assertThat(entry.getNotes()).isEmpty();
    }

    @Test
    void shouldBeImmutable() {
        // Given
        AuditEntry entry = AuditEntry.builder()
            .action("UPDATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis())
            .notes("Original notes")
            .build();

        // When/Then - Verify that the class is immutable by checking for mutator methods
        assertThat(entry.getClass().getMethods())
            .filteredOn(method -> method.getName().startsWith("set"))
            .describedAs("AuditEntry should not have any setter methods")
            .isEmpty();
    }

    @Test
    void shouldImplementComparable() {
        // Given
        long now = System.currentTimeMillis();
        AuditEntry earlier = AuditEntry.builder()
            .action("CREATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(now - 1000)
            .build();

        AuditEntry later = AuditEntry.builder()
            .action("UPDATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(now)
            .build();

        // When/Then
        assertThat(earlier.compareTo(later)).isNegative();
        assertThat(later.compareTo(earlier)).isPositive();
        assertThat(earlier.compareTo(earlier)).isZero();
    }

    @Test
    void shouldHaveUsefulToString() {
        // Given
        AuditEntry entry = AuditEntry.builder()
            .action("CREATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(1234567890L)
            .notes("User created")
            .build();

        // When
        String toString = entry.toString();

        // Then
        assertThat(toString)
            .contains("action=CREATE")
            .contains("resourceType=USER")
            .contains("resourceId=123")
            .contains("performedBy=test-user")
            .contains("timestamp=1234567890")
            .contains("notes=User created");
    }

    @Test
    void shouldCreateCopyWithNewNotes() {
        // Given
        AuditEntry original = AuditEntry.builder()
            .action("CREATE")
            .resourceType("USER")
            .resourceId("123")
            .performedBy("test-user")
            .timestamp(System.currentTimeMillis())
            .notes("Original notes")
            .build();

        // When
        AuditEntry updated = original.withNotes("Updated notes");

        // Then
        assertThat(original.getNotes()).isEqualTo("Original notes");
        assertThat(updated.getNotes()).isEqualTo("Updated notes");
        
        // Verify other fields remain the same
        assertThat(updated.getAction()).isEqualTo(original.getAction());
        assertThat(updated.getResourceType()).isEqualTo(original.getResourceType());
        assertThat(updated.getResourceId()).isEqualTo(original.getResourceId());
        assertThat(updated.getPerformedBy()).isEqualTo(original.getPerformedBy());
        assertThat(updated.getTimestamp()).isEqualTo(original.getTimestamp());
    }
}
