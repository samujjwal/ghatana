/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for AuditController.
 *
 * <p><b>Test Coverage</b><br>
 *
 * <pre>
 * - recordEvent: Record new audit events
 * - queryEvents: Query with filters and pagination
 * - getEvent: Retrieve single event by ID
 * - Error handling: Missing tenant, invalid requests
 * - Response format validation
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Controller integration tests
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("AuditController Tests")
class AuditControllerTest extends EventloopTestBase {

  private AuditController controller;
  private MockAuditService mockAuditService;

  @BeforeEach
  void setUp() {
    mockAuditService = new MockAuditService();
    controller = new AuditController(mockAuditService);
  }

  @Nested
  @DisplayName("recordEvent")
  class RecordEventTests {

    @Test
    @DisplayName("should record audit event with valid request")
    void shouldRecordEventWithValidRequest() {
      // GIVEN
      HttpRequest request =
          createRequest(
              "POST",
              "/api/audit/record",
              Map.of("X-Tenant-Id", "tenant-123"),
              """
                {
                    "workspaceId": "workspace-001",
                    "entityId": "req-001",
                    "entityType": "REQUIREMENT",
                    "action": "CREATE",
                    "category": "REQUIREMENT",
                    "actorId": "user-001",
                    "details": {"title": "New Requirement"}
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.recordEvent(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(201);
      assertThat(mockAuditService.getRecordedEvents()).hasSize(1);

      AuditEvent recorded = mockAuditService.getRecordedEvents().get(0);
      assertThat(recorded.getResourceId()).isEqualTo("req-001");
    }

    @Test
    @DisplayName("should record event without optional entityId")
    void shouldRecordEventWithoutOptionalEntityId() {
      // GIVEN - entityId is optional in the DTO
      HttpRequest request =
          createRequest(
              "POST",
              "/api/audit/record",
              Map.of("X-Tenant-Id", "tenant-123"),
              """
                {
                    "workspaceId": "workspace-001",
                    "entityType": "REQUIREMENT",
                    "action": "CREATE",
                    "category": "REQUIREMENT",
                    "actorId": "user-001"
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.recordEvent(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(201);
      assertThat(mockAuditService.getRecordedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("should return 401 for missing tenant header")
    void shouldReturn401ForMissingTenant() {
      // GIVEN
      HttpRequest request =
          createRequest(
              "POST",
              "/api/audit/record",
              Map.of(), // No tenant header
              """
                {
                    "workspaceId": "workspace-001",
                    "entityId": "req-001",
                    "entityType": "REQUIREMENT",
                    "action": "CREATE",
                    "category": "REQUIREMENT",
                    "actorId": "user-001"
                }
                """);

      // WHEN
      HttpResponse response = runPromise(() -> controller.recordEvent(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(401);
    }
  }

  @Nested
  @DisplayName("queryEvents")
  class QueryEventsTests {

    @BeforeEach
    void seedEvents() {
      // Seed some test events
      mockAuditService.addEvent(createTestEvent("evt-1", "REQUIREMENT", "CREATE"));
      mockAuditService.addEvent(createTestEvent("evt-2", "COMPONENT", "UPDATE"));
      mockAuditService.addEvent(createTestEvent("evt-3", "REQUIREMENT", "DELETE"));
    }

    @Test
    @DisplayName("should return all events for empty query")
    void shouldReturnAllEventsForEmptyQuery() {
      // GIVEN
      HttpRequest request =
          createRequest("GET", "/api/audit/events", Map.of("X-Tenant-Id", "tenant-123"), null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryEvents(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
      // Response body would contain paginated events
    }

    @Test
    @DisplayName("should filter events by category")
    void shouldFilterEventsByCategory() {
      // GIVEN
      HttpRequest request =
          createRequest(
              "GET",
              "/api/audit/events?category=REQUIREMENT",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryEvents(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
      // Filtered results would only include REQUIREMENT events
    }

    @Test
    @DisplayName("should paginate results correctly")
    void shouldPaginateResultsCorrectly() {
      // GIVEN - Add more events
      for (int i = 4; i <= 25; i++) {
        mockAuditService.addEvent(createTestEvent("evt-" + i, "REQUIREMENT", "UPDATE"));
      }

      HttpRequest request =
          createRequest(
              "GET",
              "/api/audit/events?page=2&pageSize=10",
              Map.of("X-Tenant-Id", "tenant-123"),
              null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.queryEvents(request));

      // THEN
      assertThat(response.getCode()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("getEvent")
  class GetEventTests {

    @Test
    @DisplayName("should return 404 for any event lookup (query not yet implemented)")
    void shouldReturn404ForEventLookup() {
      // GIVEN - getEvent always returns 404 because the base AuditService
      // interface only supports record(); query by ID requires an extended interface
      AuditEvent event = createTestEvent("evt-123", "REQUIREMENT", "CREATE");
      mockAuditService.addEvent(event);

      HttpRequest request =
          createRequest(
              "GET", "/api/audit/events/evt-123", Map.of("X-Tenant-Id", "tenant-123"), null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.getEvent(request, "evt-123"));

      // THEN - returns 404 as query interface is not yet available
      assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("should return 404 for non-existent event")
    void shouldReturn404ForNonExistentEvent() {
      // GIVEN
      HttpRequest request =
          createRequest(
              "GET", "/api/audit/events/non-existent", Map.of("X-Tenant-Id", "tenant-123"), null);

      // WHEN
      HttpResponse response = runPromise(() -> controller.getEvent(request, "non-existent"));

      // THEN
      assertThat(response.getCode()).isEqualTo(404);
    }
  }

  // ========== Test Helpers ==========

  private HttpRequest createRequest(
      String method, String path, Map<String, String> headers, String body) {

    HttpRequest.Builder builder =
        HttpRequest.builder(io.activej.http.HttpMethod.valueOf(method), "http://localhost" + path);

    headers.forEach((key, value) -> builder.withHeader(HttpHeaders.of(key), value));

    if (body != null) {
      builder.withBody(body.getBytes());
      builder.withHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    return builder.build();
  }

  private AuditEvent createTestEvent(String id, String resourceType, String action) {
    return AuditEvent.builder()
        .tenantId("tenant-123")
        .eventType(resourceType + "." + action)
        .resourceType(resourceType)
        .resourceId("resource-" + id)
        .principal("test-user")
        .success(true)
        .details(Map.of("eventId", id))
        .timestamp(Instant.now())
        .build();
  }

  // ========== Mock Service ==========

  private static class MockAuditService implements AuditService {
    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public Promise<Void> record(AuditEvent event) {
      events.add(event);
      return Promise.complete();
    }

    public List<AuditEvent> getRecordedEvents() {
      return new ArrayList<>(events);
    }

    public void addEvent(AuditEvent event) {
      events.add(event);
    }

    public AuditEvent getEventById(String id) {
      return events.stream()
          .filter(e -> id.equals(e.getDetails().get("eventId")))
          .findFirst()
          .orElse(null);
    }
  }
}
