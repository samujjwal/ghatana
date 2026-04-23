package com.ghatana.yappc.ai.requirements.traceability;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.requirements.domain.requirement.Requirement;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementMetadata;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementPriority;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementStatus;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementType;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies requirement traceability links are built for code and test candidates
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TraceLinkBuilder Tests")
class TraceLinkBuilderTest extends EventloopTestBase {

  @Mock
  private RequirementTraceabilityGraphPort graphPort;

  private TraceLinkBuilder builder;

  @BeforeEach
  void setUp() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
    builder = new TraceLinkBuilder(graphPort); // GH-90000
    when(graphPort.upsertRequirementNode(anyStringRequirement(), anyString())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(graphPort.createRelationship(anyString(), anyString(), anyString(), anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of((Void) null)); // GH-90000
  }

  @Test
  @DisplayName("linkRequirement creates IMPLEMENTS and TESTS links from semantic candidates")
  void linkRequirementCreatesExpectedLinks() { // GH-90000
    Requirement requirement = requirement(); // GH-90000
    when(graphPort.semanticSearch(anyString(), eq("tenant-1"), anyInt(), anyDouble()))
        .thenReturn(Promise.of(List.of( // GH-90000
            new RequirementTraceabilityGraphPort.TraceabilityCandidate( // GH-90000
                "svc-1", "SERVICE", "BillingService", 0.93, Map.of()), // GH-90000
            new RequirementTraceabilityGraphPort.TraceabilityCandidate( // GH-90000
                "test-1", "TEST", "BillingServiceTest", 0.88, Map.of())))); // GH-90000

    TraceLinkBuilder.TraceLinkSummary result = runPromise(() -> builder.linkRequirement(requirement, "tenant-1")); // GH-90000

    assertThat(result.implementationNodeIds()).containsExactly("svc-1");
    assertThat(result.testNodeIds()).containsExactly("test-1");

    verify(graphPort).upsertRequirementNode(requirement, "tenant-1"); // GH-90000
    verify(graphPort).createRelationship( // GH-90000
        requirement.getRequirementId(), "svc-1", "IMPLEMENTS", "tenant-1", Map.of( // GH-90000
            "linkedBy", "TraceLinkBuilder",
            "similarity", 0.93,
            "candidateType", "SERVICE"));
    verify(graphPort).createRelationship( // GH-90000
        requirement.getRequirementId(), "test-1", "TESTS", "tenant-1", Map.of( // GH-90000
            "linkedBy", "TraceLinkBuilder",
            "similarity", 0.88,
            "candidateType", "TEST"));
    verify(graphPort, times(2)).createRelationship(anyString(), anyString(), anyString(), anyString(), anyMap()); // GH-90000
  }

  private Requirement requirement() { // GH-90000
    return Requirement.builder() // GH-90000
        .requirementId("req-1")
        .projectId("proj-1")
        .title("Secure billing access")
        .description("Users must authenticate with MFA before opening billing settings.")
        .type(RequirementType.FUNCTIONAL) // GH-90000
        .priority(RequirementPriority.MUST_HAVE) // GH-90000
        .status(RequirementStatus.DRAFT) // GH-90000
        .createdBy("tester")
        .metadata(RequirementMetadata.empty()) // GH-90000
        .createdAt(Instant.parse("2026-04-06T00:00:00Z"))
        .updatedAt(Instant.parse("2026-04-06T00:00:00Z"))
        .build(); // GH-90000
  }

  private Requirement anyStringRequirement() { // GH-90000
    return org.mockito.ArgumentMatchers.any(); // GH-90000
  }
}
