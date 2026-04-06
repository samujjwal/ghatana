package com.ghatana.yappc.ai.requirements.application.requirement;

import com.ghatana.yappc.ai.requirements.ai.RequirementEmbeddingService;
import com.ghatana.yappc.ai.requirements.ai.RequirementQualityResult;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.domain.requirement.Requirement;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementMetadata;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementPriority;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementStatus;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementType;
import com.ghatana.yappc.ai.requirements.traceability.TraceLinkBuilder;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @doc.type class
 * @doc.purpose Owns requirement create and update workflows including enrichment, quality scoring, duplicate detection, and vector indexing
 * @doc.layer product
 * @doc.pattern Application Service
 */
public final class RequirementService {
  private final RequirementEmbeddingService embeddingService;
  private final AIEnricher aiEnricher;
  private final DuplicateDetector duplicateDetector;
  private final QualityValidator qualityValidator;
  private final TraceLinkBuilder traceLinkBuilder;
  private final ConcurrentMap<UUID, StoredRequirement> requirements = new ConcurrentHashMap<>();

  public RequirementService(RequirementEmbeddingService embeddingService) {
    this(
        embeddingService,
        new AIEnricher(embeddingService),
        new DuplicateDetector(embeddingService),
        new QualityValidator(),
        null);
  }

  public RequirementService(
      RequirementEmbeddingService embeddingService,
      AIEnricher aiEnricher,
      DuplicateDetector duplicateDetector,
      QualityValidator qualityValidator,
      TraceLinkBuilder traceLinkBuilder) {
    this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
    this.aiEnricher = Objects.requireNonNull(aiEnricher, "aiEnricher");
    this.duplicateDetector = Objects.requireNonNull(duplicateDetector, "duplicateDetector");
    this.qualityValidator = Objects.requireNonNull(qualityValidator, "qualityValidator");
    this.traceLinkBuilder = traceLinkBuilder;
  }

  public Promise<StoredRequirement> createRequirement(
      UUID projectId,
      String requirementText,
      String priority,
      String createdBy) {
    return createRequirement(projectId, requirementText, priority, createdBy, null);
  }

  public Promise<StoredRequirement> createRequirement(
      UUID projectId,
      String requirementText,
      String priority,
      String createdBy,
      String tenantId) {
    String normalizedText = requireText(requirementText);
    String normalizedCreator = normalizeUser(createdBy);
    UUID requirementId = UUID.randomUUID();

    return aiEnricher.enrich(requirementId.toString(), normalizedText, normalizedCreator)
        .then(suggestions -> duplicateDetector.analyze(requirementId.toString(), normalizedText, projectId.toString())
            .then(duplicates -> qualityValidator.validate(normalizedText)
                .then(quality -> persistCreatedRequirement(
                    requirementId,
                    projectId,
                    normalizedText,
                    priority,
                    normalizedCreator,
                    suggestions,
                    duplicates,
                    quality,
                    tenantId))));
  }

  public Promise<StoredRequirement> updateRequirement(
      UUID requirementId,
      String requirementText,
      String priority,
      String updatedBy) {
    return updateRequirement(requirementId, requirementText, priority, updatedBy, null);
  }

  public Promise<StoredRequirement> updateRequirement(
      UUID requirementId,
      String requirementText,
      String priority,
      String updatedBy,
      String tenantId) {
    String normalizedText = requireText(requirementText);
    String normalizedUpdater = normalizeUser(updatedBy);
    StoredRequirement existing = requirements.get(requirementId);
    if (existing == null) {
      return Promise.ofException(new IllegalArgumentException("Requirement not found: " + requirementId));
    }

    Requirement requirement = existing.requirement();
    requirement.setDescription(normalizedText);
    requirement.setTitle(deriveTitle(normalizedText));
    requirement.setPriority(mapPriority(priority));
    requirement.createVersion(normalizedUpdater, "Requirement updated");

    return aiEnricher.enrich(requirementId.toString(), normalizedText, normalizedUpdater)
        .then(suggestions -> duplicateDetector.analyze(requirementId.toString(), normalizedText, requirement.getProjectId())
            .then(duplicates -> qualityValidator.validate(normalizedText)
                .then(quality -> embeddingService.updateEmbedding(requirementId.toString(), normalizedText)
                    .then(ignored -> {
                      StoredRequirement stored = new StoredRequirement(requirement, suggestions, duplicates, quality);
                      requirements.put(requirementId, stored);
                      return maybeBuildTraceLinks(stored, tenantId);
                    }))));
  }

  public Optional<StoredRequirement> getRequirement(UUID requirementId) {
    return Optional.ofNullable(requirements.get(requirementId));
  }

  private Promise<StoredRequirement> persistCreatedRequirement(
      UUID requirementId,
      UUID projectId,
      String requirementText,
      String priority,
      String createdBy,
      List<AISuggestion> suggestions,
      List<DuplicateWarning> duplicates,
      RequirementQualityResult quality,
      String tenantId) {
    Instant now = Instant.now();
    Requirement requirement = Requirement.builder()
        .requirementId(requirementId.toString())
        .projectId(projectId.toString())
        .title(deriveTitle(requirementText))
        .description(requirementText)
        .type(RequirementType.FUNCTIONAL)
        .priority(mapPriority(priority))
        .status(RequirementStatus.DRAFT)
        .createdBy(createdBy)
        .metadata(new RequirementMetadata(
            List.of(),
            List.of(),
            "",
            Map.of("qualityLevel", quality.getQualityLevel().name()),
            List.of()))
        .createdAt(now)
        .updatedAt(now)
        .build();
    requirement.createVersion(createdBy, "Initial creation");

    return embeddingService.embedAndStore(requirementId.toString(), requirementText, projectId.toString())
        .then(ignored -> {
          StoredRequirement stored = new StoredRequirement(requirement, suggestions, duplicates, quality);
          requirements.put(requirementId, stored);
          return maybeBuildTraceLinks(stored, tenantId);
        });
  }

  private Promise<StoredRequirement> maybeBuildTraceLinks(StoredRequirement stored, String tenantId) {
    if (traceLinkBuilder == null || tenantId == null || tenantId.isBlank()) {
      return Promise.of(stored);
    }
    return traceLinkBuilder.linkRequirement(stored.requirement(), tenantId).map(ignored -> stored);
  }

  private String requireText(String requirementText) {
    Objects.requireNonNull(requirementText, "requirementText");
    String normalized = requirementText.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("requirementText cannot be empty");
    }
    return normalized;
  }

  private String normalizeUser(String userId) {
    if (userId == null || userId.isBlank()) {
      return "system";
    }
    return userId;
  }

  private String deriveTitle(String requirementText) {
    int breakIndex = requirementText.indexOf('.');
    String candidate = breakIndex > 0 ? requirementText.substring(0, breakIndex) : requirementText;
    return candidate.length() <= 80 ? candidate : candidate.substring(0, 80);
  }

  private RequirementPriority mapPriority(String priority) {
    if (priority == null) {
      return RequirementPriority.SHOULD_HAVE;
    }
    return switch (priority.trim().toUpperCase(Locale.ROOT)) {
      case "HIGH", "CRITICAL", "MUST_HAVE", "MUST HAVE" -> RequirementPriority.MUST_HAVE;
      case "LOW", "COULD_HAVE", "COULD HAVE" -> RequirementPriority.COULD_HAVE;
      case "WONT_HAVE", "WON'T HAVE", "WONT HAVE" -> RequirementPriority.WONT_HAVE;
      default -> RequirementPriority.SHOULD_HAVE;
    };
  }

  public record DuplicateWarning(String requirementId, String text, float similarityScore) {}

  public record StoredRequirement(
      Requirement requirement,
      List<AISuggestion> enrichmentSuggestions,
      List<DuplicateWarning> duplicateWarnings,
      RequirementQualityResult qualityResult) {}
}