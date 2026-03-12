package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.Domain;
import com.ghatana.tutorputor.explorer.model.EvidenceGenerator;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import com.ghatana.tutorputor.explorer.model.LearningEvidence;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * @doc.type test
 * @doc.purpose Unit tests for EvidenceGenerator
 * @doc.layer test
 */
class EvidenceGeneratorTest {

    private EvidenceGenerator evidenceGenerator;

    @BeforeEach
    void setUp() {
        evidenceGenerator = new EvidenceGenerator();
    }

    @Test
    @DisplayName("Should generate evidence for each claim")
    void shouldGenerateEvidenceForEachClaim() throws Exception {
        // Given
        List<LearningClaim> claims = List.of(
            LearningClaim.builder().id("c1").text("Claim 1").domain("PHYSICS").gradeLevel("HS").build(),
            LearningClaim.builder().id("c2").text("Claim 2").domain("PHYSICS").gradeLevel("HS").build()
        );
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Physics")
            .domain(Domain.PHYSICS)
            .tenantId("tenant-1")
            .build();

        // When
        Promise<List<LearningEvidence>> promise = evidenceGenerator.generateEvidence(claims, request);
        List<LearningEvidence> evidence = promise.get();

        // Then
        assertThat(evidence).hasSize(2);
        assertThat(evidence.get(0).getClaimId()).isEqualTo("c1");
        assertThat(evidence.get(1).getClaimId()).isEqualTo("c2");
    }

    @Test
    @DisplayName("Should create evidence with correct structure")
    void shouldCreateEvidenceWithCorrectStructure() throws Exception {
        // Given
        List<LearningClaim> claims = List.of(
            LearningClaim.builder().id("c1").text("Newton's First Law").build()
        );
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Newton's Laws")
            .domain(Domain.PHYSICS)
            .tenantId("tenant-1")
            .build();

        // When
        List<LearningEvidence> evidence = evidenceGenerator.generateEvidence(claims, request).get();

        // Then
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).getType()).isEqualTo("EXAMPLE");
        assertThat(evidence.get(0).getContent()).contains("Newton's First Law");
    }
}
