package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.Domain;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * @doc.type test
 * @doc.purpose Unit tests for ClaimGenerator
 * @doc.layer test
 */
class ClaimGeneratorTest {

    private ClaimGenerator claimGenerator;

    @BeforeEach
    void setUp() {
        claimGenerator = new ClaimGenerator();
    }

    @Test
    @DisplayName("Should generate claims for valid request")
    void shouldGenerateClaimsForValidRequest() throws Exception {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Newton's Laws")
            .gradeLevel("HIGH_SCHOOL")
            .domain(Domain.PHYSICS)
            .tenantId("tenant-1")
            .build();

        // When
        Promise<List<LearningClaim>> promise = claimGenerator.generateClaims(request);
        List<LearningClaim> claims = promise.get();

        // Then
        assertThat(claims).isNotEmpty();
        assertThat(claims.get(0).getText()).contains("Newton's Laws");
        assertThat(claims.get(0).getDomain()).isEqualTo("PHYSICS");
        assertThat(claims.get(0).getGradeLevel()).isEqualTo("HIGH_SCHOOL");
    }

    @Test
    @DisplayName("Should generate unique claim IDs")
    void shouldGenerateUniqueClaimIds() throws Exception {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Test Topic")
            .gradeLevel("MIDDLE_SCHOOL")
            .domain(Domain.BIOLOGY)
            .tenantId("tenant-1")
            .build();

        // When
        List<LearningClaim> claims1 = claimGenerator.generateClaims(request).get();
        List<LearningClaim> claims2 = claimGenerator.generateClaims(request).get();

        // Then
        assertThat(claims1.get(0).getId()).isNotEqualTo(claims2.get(0).getId());
    }
}
