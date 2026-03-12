package com.ghatana.tutorputor.explorer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * @doc.type test
 * @doc.purpose Unit tests for ContentExample model
 * @doc.layer test
 */
class ContentExampleTest {

    @Test
    @DisplayName("Should create ContentExample with all fields")
    void shouldCreateContentExampleWithAllFields() {
        // Given
        String id = "ex-123";
        String claimId = "claim-456";
        String title = "Pendulum Motion Example";
        String description = "A detailed example of pendulum motion";
        List<String> steps = List.of("Step 1: Setup", "Step 2: Execute", "Step 3: Analyze");
        String visualAidDescription = "Diagram showing pendulum with angle theta";
        String gradeLevel = "HIGH_SCHOOL";
        String domain = "PHYSICS";
        long createdAt = System.currentTimeMillis();

        // When
        ContentExample example = ContentExample.builder()
            .id(id)
            .claimId(claimId)
            .title(title)
            .description(description)
            .steps(steps)
            .visualAidDescription(visualAidDescription)
            .gradeLevel(gradeLevel)
            .domain(domain)
            .createdAt(createdAt)
            .build();

        // Then
        assertThat(example.getId()).isEqualTo(id);
        assertThat(example.getClaimId()).isEqualTo(claimId);
        assertThat(example.getTitle()).isEqualTo(title);
        assertThat(example.getDescription()).isEqualTo(description);
        assertThat(example.getSteps()).isEqualTo(steps);
        assertThat(example.getVisualAidDescription()).isEqualTo(visualAidDescription);
        assertThat(example.getGradeLevel()).isEqualTo(gradeLevel);
        assertThat(example.getDomain()).isEqualTo(domain);
        assertThat(example.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Should create ContentExample with minimal fields")
    void shouldCreateContentExampleWithMinimalFields() {
        // When
        ContentExample example = ContentExample.builder()
            .id("ex-minimal")
            .title("Minimal Example")
            .build();

        // Then
        assertThat(example.getId()).isEqualTo("ex-minimal");
        assertThat(example.getTitle()).isEqualTo("Minimal Example");
        assertThat(example.getSteps()).isNull();
    }
}
