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
  void setUp() { 
    MockitoAnnotations.openMocks(this); 
    service = new RequirementService( 
        embeddingService,
        new AIEnricher(embeddingService), 
        new DuplicateDetector(embeddingService), 
        new QualityValidator(), 
        traceLinkBuilder);

    when(embeddingService.generateSuggestions(anyString(), anyString(), anyString())) 
        .thenReturn(Promise.of(List.of(sampleSuggestion()))); 
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString())) 
        .thenReturn(Promise.of((Void) null)); 
    when(embeddingService.updateEmbedding(anyString(), anyString())) 
        .thenReturn(Promise.of((Void) null)); 
    when(embeddingService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat())) 
        .thenReturn(Promise.of(List.of())); 
    when(traceLinkBuilder.linkRequirement(any(), anyString())) 
        .thenReturn(Promise.of(new TraceLinkBuilder.TraceLinkSummary("req", List.of(), List.of()))); 
  }

  @Test
  @DisplayName("createRequirement returns enrichment, quality score, and duplicate warnings")
  void createRequirementReturnsEnrichmentQualityAndDuplicates() { 
    UUID projectId = UUID.randomUUID(); 
    when(embeddingService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat())) 
        .thenReturn(Promise.of(List.of( 
            new VectorSearchResult( 
                UUID.randomUUID().toString(), 
                "Users must authenticate with SSO before viewing billing details.",
                new float[0],
                0.94,
                1))));

    RequirementService.StoredRequirement result = runPromise(() -> 
        service.createRequirement( 
            projectId,
            "Users must authenticate with SSO before viewing billing details.",
            "HIGH",
            "tester"));

    assertThat(result.enrichmentSuggestions()).hasSize(1); 
    assertThat(result.duplicateWarnings()).hasSize(1); 
    assertThat(result.qualityResult().getOverallScore()).isGreaterThan(0.7); 
    assertThat(result.requirement().getProjectId()).isEqualTo(projectId.toString()); 

    verify(embeddingService).embedAndStore( 
        result.requirement().getRequirementId(), 
        "Users must authenticate with SSO before viewing billing details.",
        projectId.toString()); 
    verify(traceLinkBuilder, never()).linkRequirement(any(), anyString()); 
  }

  @Test
  @DisplayName("updateRequirement re-runs enrichment and quality evaluation")
  void updateRequirementRerunsEnrichment() { 
    UUID projectId = UUID.randomUUID(); 

    RequirementService.StoredRequirement created = runPromise(() -> 
        service.createRequirement(projectId, "User can log in.", "LOW", "tester")); 

    RequirementService.StoredRequirement updated = runPromise(() -> 
        service.updateRequirement( 
            UUID.fromString(created.requirement().getRequirementId()), 
            "User must log in with MFA when accessing billing settings.",
            "HIGH",
            "tester"));

    assertThat(updated.requirement().getDescription()) 
        .isEqualTo("User must log in with MFA when accessing billing settings.");
    assertThat(updated.qualityResult().getOverallScore()) 
        .isGreaterThan(created.qualityResult().getOverallScore()); 

    verify(embeddingService).updateEmbedding( 
        created.requirement().getRequirementId(), 
        "User must log in with MFA when accessing billing settings.");
  }

    @Test
    @DisplayName("createRequirement triggers traceability linking when tenant context is available")
    void createRequirementTriggersTraceabilityLinking() { 
        UUID projectId = UUID.randomUUID(); 

        RequirementService.StoredRequirement result = runPromise(() -> 
                service.createRequirement( 
                        projectId,
                        "Users must authenticate with SSO before viewing billing details.",
                        "HIGH",
                        "tester",
                        "tenant-1"));

        verify(traceLinkBuilder).linkRequirement(result.requirement(), "tenant-1"); 
    }

  private static AISuggestion sampleSuggestion() { 
    return new AISuggestion( 
        UUID.randomUUID().toString(), 
        "Add explicit MFA acceptance criteria.",
        Persona.DEVELOPER,
        0.86f,
        0.72f,
        SuggestionStatus.PENDING,
        "user-1",
        null);
  }
}
