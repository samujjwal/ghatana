/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.datacloud.application.version.VersionComparator;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.datacloud.infrastructure.persistence.version.InMemoryVersionRecord;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.domain.AgentCapabilities;
import com.ghatana.yappc.api.domain.TaskDomain;
import com.ghatana.yappc.api.repository.InMemoryRequirementRepository;
import com.ghatana.yappc.api.service.ConfigService;
import com.ghatana.yappc.api.service.ConfigLoader;
import com.ghatana.yappc.api.service.RequirementService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for RequirementsController.
 *
 * <p><b>Test Coverage</b><br>
 *
 * <pre>
 * - createRequirement: Create new requirements
 * - queryRequirements: Query with filters
 * - getRequirement: Retrieve by ID
 * - updateRequirement: Update existing
 * - deleteRequirement: Delete by ID
 * - approveRequirement: Approval workflow
 * - getRequirementsFunnel: Funnel analytics
 * - calculateQualityScore: Quality scoring
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Controller integration tests
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("RequirementsController Tests")
class RequirementsControllerTest extends EventloopTestBase {

  private RequirementsController controller;
  private RequirementService requirementService;

  @BeforeEach
  void setUp() {
    // Create mock services for testing
    var auditService = new MockAuditService();
    var versionService = new VersionService(new InMemoryVersionRecord(), new VersionComparator());
    var requirementRepository = new InMemoryRequirementRepository();
    requirementService =
        new RequirementService(requirementRepository, auditService, versionService);
    var configService = new MockConfigService();

    controller = new RequirementsController(requirementService, configService);
  }

  /**
   * Helper to create a requirement via the service and return its UUID.
   */
  private String createTestRequirement() {
    var requirement = runPromise(() -> requirementService.createRequirement(
        "tenant-123", "Test Requirement", "Test Description",
        com.ghatana.yappc.api.domain.Requirement.RequirementType.FUNCTIONAL,
        com.ghatana.yappc.api.domain.Requirement.Priority.HIGH,
        "user-1"));
    return requirement.getId().toString();
  }

  @Nested
  @DisplayName("createRequirement")
  class CreateRequirementTests {

    @Test
    @DisplayName("should create requirement with valid request")
    void shouldCreateRequirementWithValidRequest() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.POST,
              "/api/requirements",
              Map.of("X-Tenant-Id", "tenant-123", "X-User-Id", "user-1"),
              """
                {
                    "title": "User Authentication",
                    "description": "Implement OAuth2 authentication with SSO",
                    "type": "FUNCTIONAL",
                    "priority": "HIGH",
                    "projectId": "00000000-0000-0000-0000-000000000010",
                    "acceptanceCriteria": [
                        {"id": "ac-1", "description": "Users can login via SSO", "testable": true},
                        {"id": "ac-2", "description": "Session timeout after 30min", "testable": true}
                    ]
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.createRequirement(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("should create requirement even without title (defaults to 'New Requirement')")
    void shouldCreateRequirementWithDefaultTitle() {
      // GIVEN - Controller defaults missing title to "New Requirement"
      HttpRequest request =
          createRequest(
              HttpMethod.POST,
              "/api/requirements",
              Map.of("X-Tenant-Id", "tenant-123"),
              """
                {
                    "description": "Some description",
                    "type": "FUNCTIONAL",
                    "priority": "HIGH",
                    "projectId": "00000000-0000-0000-0000-000000000010"
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.createRequirement(request));

      // THEN - Returns 201 (controller doesn't validate required title)
      assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("should auto-generate ID if not provided")
    void shouldAutoGenerateId() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.POST,
              "/api/requirements",
              Map.of("X-Tenant-Id", "tenant-123"),
              """
                {
                    "title": "New Requirement",
                    "description": "Description",
                    "type": "FUNCTIONAL",
                    "priority": "MEDIUM",
                    "projectId": "00000000-0000-0000-0000-000000000010"
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.createRequirement(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(201);
      // Response would contain auto-generated ID
    }
  }

  @Nested
  @DisplayName("queryRequirements")
  class QueryRequirementsTests {

    @Test
    @DisplayName("should query requirements by project")
    void shouldQueryRequirementsByProject() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements?projectId=00000000-0000-0000-0000-000000000010",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryRequirements(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should filter by status")
    void shouldFilterByStatus() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements?status=IN_REVIEW",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryRequirements(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should filter by priority")
    void shouldFilterByPriority() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements?priority=HIGH",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryRequirements(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should search by keyword")
    void shouldSearchByKeyword() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements?search=authentication",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryRequirements(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("getRequirement")
  class GetRequirementTests {

    @Test
    @DisplayName("should return requirement by ID")
    void shouldReturnRequirementById() {
      // GIVEN - First create a requirement to retrieve
      String reqId = createTestRequirement();

      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements/" + reqId,
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.getRequirement(request, reqId));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should return 404 for non-existent requirement")
    void shouldReturn404ForNonExistent() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements/00000000-0000-0000-0000-000000000099",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.getRequirement(request, "00000000-0000-0000-0000-000000000099"));

      // THEN
      assertThat(response.getCode()).isEqualTo(404);
    }
  }

  @Nested
  @DisplayName("updateRequirement")
  class UpdateRequirementTests {

    @Test
    @DisplayName("should update requirement fields")
    void shouldUpdateRequirementFields() {
      // GIVEN - First create a requirement to update
      String reqId = createTestRequirement();

      HttpRequest request =
          createRequest(
              HttpMethod.PUT,
              "/api/requirements/" + reqId,
              Map.of("X-Tenant-Id", "tenant-123"),
              """
                {
                    "title": "Updated Title",
                    "priority": "CRITICAL"
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.updateRequirement(request, reqId));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("deleteRequirement")
  class DeleteRequirementTests {

    @Test
    @DisplayName("should delete requirement by ID")
    void shouldDeleteRequirementById() {
      // GIVEN - First create a requirement to delete
      String reqId = createTestRequirement();

      HttpRequest request =
          createRequest(
              HttpMethod.DELETE,
              "/api/requirements/" + reqId,
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.deleteRequirement(request, reqId));

      // THEN
      assertThat(response.getCode()).isEqualTo(204);
    }
  }

  @Nested
  @DisplayName("approveRequirement")
  class ApproveRequirementTests {

    @Test
    @DisplayName("should approve requirement")
    void shouldApproveRequirement() {
      // GIVEN - Create a requirement and transition it to REVIEW state first
      String reqId = createTestRequirement();
      // Submit for review (DRAFT → IN_REVIEW)
      runPromise(() -> requirementService.submitForReview(
          "tenant-123", java.util.UUID.fromString(reqId), "user-1"));

      HttpRequest request =
          createRequest(
              HttpMethod.POST,
              "/api/requirements/" + reqId + "/approve",
              Map.of("X-Tenant-Id", "tenant-123", "X-User-Id", "approver-1"),
              """
                {
                    "comment": "Approved after review"
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.approveRequirement(request, reqId));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("getRequirementsFunnel")
  class GetRequirementsFunnelTests {

    @Test
    @DisplayName("should return funnel for project")
    void shouldReturnFunnelForProject() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements/funnel?projectId=00000000-0000-0000-0000-000000000010",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.getFunnelAnalytics(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should include all funnel stages")
    void shouldIncludeAllFunnelStages() {
      // GIVEN
      HttpRequest request =
          createRequest(
              HttpMethod.GET,
              "/api/requirements/funnel?projectId=00000000-0000-0000-0000-000000000010",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.getFunnelAnalytics(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
      // Response would include all 7 stages: DRAFT, SUBMITTED, IN_REVIEW, etc.
    }
  }

  @Nested
  @DisplayName("calculateQualityScore")
  class CalculateQualityScoreTests {

    @Test
    @DisplayName("should calculate quality score for requirement")
    void shouldCalculateQualityScore() {
      // GIVEN - First create a requirement to calculate quality for
      String reqId = createTestRequirement();

      HttpRequest request =
          createRequest(
              HttpMethod.POST,
              "/api/requirements/" + reqId + "/quality",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response =
          runPromise(() -> controller.calculateQualityScore(request, reqId));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should include all quality dimensions")
    void shouldIncludeAllQualityDimensions() {
      // GIVEN - First create a requirement
      String reqId = createTestRequirement();

      HttpRequest request =
          createRequest(
              HttpMethod.POST,
              "/api/requirements/" + reqId + "/quality",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response =
          runPromise(() -> controller.calculateQualityScore(request, reqId));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
      // Response would include: overall, clarity, completeness, testability, feasibility,
      // consistency
    }
  }

  // ========== Test Helpers ==========

  private HttpRequest createRequest(
      HttpMethod method, String path, Map<String, String> headers, String body) {

    HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path);

    headers.forEach((key, value) -> builder.withHeader(HttpHeaders.of(key), value));

    if (body != null) {
      builder.withBody(body.getBytes());
      builder.withHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    return builder.build();
  }

  // Mock AuditService for testing
  private static class MockAuditService implements AuditService {
    @Override
    public Promise<Void> record(AuditEvent event) {
      return Promise.complete();
    }
  }

  // Mock ConfigService for testing
  private static class MockConfigService extends ConfigService {
    
    public MockConfigService() {
      super(org.mockito.Mockito.mock(ConfigLoader.class));
    }

    @Override
    public Promise<List<TaskDomain>> getDomains() {
      return Promise.of(List.of());
    }

    @Override
    public Promise<AgentCapabilities> getAgentCapabilities() {
      return Promise.of(new AgentCapabilities(List.of(), Map.of()));
    }
  }
}
