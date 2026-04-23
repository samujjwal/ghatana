package com.ghatana.yappc.ai.requirements.application.requirement;

import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.requirements.ai.RequirementEmbeddingService;
import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus;
import com.ghatana.yappc.ai.requirements.traceability.TraceLinkBuilder;
import io.activej.promise.Promise;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies requirement application writes produce enrichment, quality data, and duplicate warnings
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequirementService Tests")
class RequirementServiceTest extends EventloopTestBase {

  @Mock private RequirementEmbeddingService embeddingService;

    @Mock private TraceLinkBuilder traceLinkBuilder;

  private RequirementService service;

  @BeforeEach
  void setUp() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
    service = new RequirementService( // GH-90000
        embeddingService,
        new AIEnricher(embeddingService), // GH-90000
        new DuplicateDetector(embeddingService), // GH-90000
        new QualityValidator(), // GH-90000
        traceLinkBuilder);

    when(embeddingService.generateSuggestions(anyString(), anyString(), anyString())) // GH-90000
        .thenReturn(Promise.of(List.of(sampleSuggestion()))); // GH-90000
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString())) // GH-90000
        .thenReturn(Promise.of((Void) null)); // GH-90000
    when(embeddingService.updateEmbedding(anyString(), anyString())) // GH-90000
        .thenReturn(Promise.of((Void) null)); // GH-90000
    when(embeddingService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat())) // GH-90000
        .thenReturn(Promise.of(List.of())); // GH-90000
    when(traceLinkBuilder.linkRequirement(any(), anyString())) // GH-90000
        .thenReturn(Promise.of(new TraceLinkBuilder.TraceLinkSummary("req", List.of(), List.of()))); // GH-90000
  }

  @Test
  @DisplayName("createRequirement returns enrichment, quality score, and duplicate warnings")
  void createRequirementReturnsEnrichmentQualityAndDuplicates() { // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000
    when(embeddingService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat())) // GH-90000
        .thenReturn(Promise.of(List.of( // GH-90000
            new VectorSearchResult( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "Users must authenticate with SSO before viewing billing details.",
                new float[0],
                0.94,
                1))));

    RequirementService.StoredRequirement result = runPromise(() -> // GH-90000
        service.createRequirement( // GH-90000
            projectId,
            "Users must authenticate with SSO before viewing billing details.",
            "HIGH",
            "tester"));

    assertThat(result.enrichmentSuggestions()).hasSize(1); // GH-90000
    assertThat(result.duplicateWarnings()).hasSize(1); // GH-90000
    assertThat(result.qualityResult().getOverallScore()).isGreaterThan(0.7); // GH-90000
    assertThat(result.requirement().getProjectId()).isEqualTo(projectId.toString()); // GH-90000

    verify(embeddingService).embedAndStore( // GH-90000
        result.requirement().getRequirementId(), // GH-90000
        "Users must authenticate with SSO before viewing billing details.",
        projectId.toString()); // GH-90000
    verify(traceLinkBuilder, never()).linkRequirement(any(), anyString()); // GH-90000
  }

  @Test
  @DisplayName("updateRequirement re-runs enrichment and quality evaluation")
  void updateRequirementRerunsEnrichment() { // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000

    RequirementService.StoredRequirement created = runPromise(() -> // GH-90000
        service.createRequirement(projectId, "User can log in.", "LOW", "tester")); // GH-90000

    RequirementService.StoredRequirement updated = runPromise(() -> // GH-90000
        service.updateRequirement( // GH-90000
            UUID.fromString(created.requirement().getRequirementId()), // GH-90000
            "User must log in with MFA when accessing billing settings.",
            "HIGH",
            "tester"));

    assertThat(updated.requirement().getDescription()) // GH-90000
        .isEqualTo("User must log in with MFA when accessing billing settings.");
    assertThat(updated.qualityResult().getOverallScore()) // GH-90000
        .isGreaterThan(created.qualityResult().getOverallScore()); // GH-90000

    verify(embeddingService).updateEmbedding( // GH-90000
        created.requirement().getRequirementId(), // GH-90000
        "User must log in with MFA when accessing billing settings.");
  }

    @Test
    @DisplayName("createRequirement triggers traceability linking when tenant context is available")
    void createRequirementTriggersTraceabilityLinking() { // GH-90000
        UUID projectId = UUID.randomUUID(); // GH-90000

        RequirementService.StoredRequirement result = runPromise(() -> // GH-90000
                service.createRequirement( // GH-90000
                        projectId,
                        "Users must authenticate with SSO before viewing billing details.",
                        "HIGH",
                        "tester",
                        "tenant-1"));

        verify(traceLinkBuilder).linkRequirement(result.requirement(), "tenant-1"); // GH-90000
    }

  private static AISuggestion sampleSuggestion() { // GH-90000
    return new AISuggestion( // GH-90000
        UUID.randomUUID().toString(), // GH-90000
        "Add explicit MFA acceptance criteria.",
        Persona.DEVELOPER,
        0.86f,
        0.72f,
        SuggestionStatus.PENDING,
        "user-1",
        null);
  }
}
