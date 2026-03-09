package com.ghatana.requirements.api.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.requirements.ai.RequirementEmbeddingService;
import com.ghatana.requirements.ai.persona.Persona;
import com.ghatana.requirements.ai.suggestions.AISuggestion;
import com.ghatana.requirements.ai.suggestions.SuggestionStatus;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.requirements.api.rest.dto.CreateRequirementRequest;
import com.ghatana.requirements.api.rest.dto.RecordFeedbackRequest;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for RequirementController REST endpoints.
 *
 * <p><b>Purpose:</b> Tests validate:
 * - Request validation
 * - Service call delegation
 * - Error handling
 * - Response transformation
 *
 * <p><b>Architecture Role:</b> Unit tests for REST controller layer.
 * Ensures controller properly handles HTTP requests and delegates
 * to appropriate service methods.
 *
 * <p><b>Thread Safety:</b> Tests are thread-safe and isolated.
 * Each test runs independently with fresh mocks.
 *
 * @see com.ghatana.requirements.api.rest.RequirementController
 * @see com.ghatana.requirements.ai.RequirementEmbeddingService
 * @doc.type class
 * @doc.purpose Unit tests for RequirementController REST endpoints
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("RequirementController Tests")
class RequirementControllerTest extends EventloopTestBase {

  @Mock private RequirementEmbeddingService embeddingService;

  private RequirementController controller;
  private ObjectMapper objectMapper;
  private static final String BASE_URL = "http://localhost:8080";

  private static String url(String path) {
    return BASE_URL + path;
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    objectMapper = new ObjectMapper();
    controller = new RequirementController(embeddingService);
  }

  @Test
  @DisplayName("Should create requirement controller with valid service")
  void shouldCreateController() {
    assertThat(controller).isNotNull();
  }

  @Test
  @DisplayName("Should throw NullPointerException when service is null")
  void shouldThrowWhenServiceNull() {
    assertThatThrownBy(() -> new RequirementController(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Should create requirement when valid request")
  void shouldCreateRequirementWhenValidRequest() throws Exception {
    // Given
    CreateRequirementRequest request = new CreateRequirementRequest("Test requirement", "HIGH");
    UUID projectId = UUID.randomUUID();
    
    // Mock the embedAndStore method to return a completed promise
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString()))
        .thenReturn(Promise.complete());

    // Create a proper HTTP request with the project ID in the path
    HttpRequest httpRequest = HttpRequest.post(url("/api/v1/projects/" + projectId + "/requirements"))
        .withBody(objectMapper.writeValueAsBytes(request))
        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();

    // When
    HttpResponse response = runPromise(() -> controller.createRequirement(httpRequest));

    // Then
    assertThat(response.getCode()).isEqualTo(201);
    assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    
    // Parse the response body (use runPromise to avoid .getResult() which is forbidden)
    ByteBuf bodyBuf = runPromise(() -> response.loadBody());
    String responseBody = bodyBuf.asString(StandardCharsets.UTF_8);
    JsonNode jsonResponse = objectMapper.readTree(responseBody);
    
    // Verify the response structure
    assertThat(jsonResponse.has("id")).isTrue();
    assertThat(jsonResponse.has("text")).isTrue();
    assertThat(jsonResponse.get("text").asText()).isEqualTo("Test requirement");
    assertThat(jsonResponse.has("status")).isTrue();
    assertThat(jsonResponse.get("status").asText()).isEqualTo("DRAFT");
    assertThat(jsonResponse.has("suggestions")).isTrue();
    assertThat(jsonResponse.get("suggestions").isArray()).isTrue();
    
    // Verify service interaction
    verify(embeddingService)
        .embedAndStore(anyString(), eq("Test requirement"), eq(projectId.toString()));
  }

  @Test
  @DisplayName("Should return detailed requirement with suggestions and similar items")
  void shouldGetRequirementWithSuggestionsAndSimilar() throws Exception {
    UUID projectId = UUID.randomUUID();
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString()))
        .thenReturn(Promise.of(null));

    CreateRequirementRequest create = new CreateRequirementRequest("Initial requirement", "HIGH");
    HttpResponse createResponse =
        runPromise(
            () ->
                controller.createRequirement(
                    HttpRequest.post(url("/api/projects/" + projectId + "/requirements"))
                        .withBody(objectMapper.writeValueAsBytes(create))
                        .build()));
    JsonNode created =
        objectMapper.readTree(createResponse.getBody().asString(StandardCharsets.UTF_8));
    String requirementId = created.get("id").asText();

    when(embeddingService.generateSuggestions(anyString(), anyString(), any()))
        .thenReturn(
            Promise.of(
                List.of(
                    sampleSuggestion(
                        requirementId, Persona.DEVELOPER, "Add logging for mobile flows"))));

    doReturn(Promise.of(
                List.of(
                    new VectorSearchResult(
                        UUID.randomUUID().toString(), "Preview requirement text", new float[0], 0.91, 0))))
        .when(embeddingService).findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat());

    HttpRequest getRequest =
        HttpRequest.get(
            url(
                "/api/requirements/"
                    + requirementId
                    + "?includeSuggestions=true&includeSimilar=true"))
            .build();

    HttpResponse getResponse = runPromise(() -> controller.getRequirement(getRequest));

    assertThat(getResponse.getCode()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(getResponse.getBody().asString(StandardCharsets.UTF_8));
    assertThat(body.get("suggestions")).hasSize(1);
    assertThat(body.get("similarRequirements")).hasSize(1);
  }

  @Test
  @DisplayName("Should return 400 when creating requirement with invalid data")
  void shouldReturn400WhenCreatingRequirementWithInvalidData() throws Exception {
    UUID projectId = UUID.randomUUID();
    HttpRequest httpRequest =
        HttpRequest.post(url("/api/projects/" + projectId + "/requirements"))
            .withBody(new byte[0])
            .build();

    HttpResponse response = runPromise(() -> controller.createRequirement(httpRequest));

    assertThat(response.getCode()).isEqualTo(400);
    verify(embeddingService, never()).embedAndStore(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("Should find similar requirements when valid request")
  void shouldFindSimilarRequirementsWhenValidRequest() throws Exception {
    UUID projectId = UUID.randomUUID();
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString()))
        .thenReturn(Promise.of(null));

    HttpResponse createResponse =
        runPromise(
            () ->
                controller.createRequirement(
                    HttpRequest.post(url("/api/projects/" + projectId + "/requirements"))
                        .withBody(
                            objectMapper.writeValueAsBytes(
                                new CreateRequirementRequest("Req text", "HIGH")))
                        .build()));
    String requirementId =
        objectMapper.readTree(createResponse.getBody().asString(StandardCharsets.UTF_8))
            .get("id")
            .asText();

    doReturn(Promise.of(
                List.of(
                    new VectorSearchResult(
                        UUID.randomUUID().toString(), "Potentially similar requirement", new float[0], 0.88, 0))))
        .when(embeddingService).findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat());

    HttpRequest httpRequest =
        HttpRequest.get(
            url("/api/requirements/" + requirementId + "/similar?limit=2&minSimilarity=0.8"))
            .build();

    HttpResponse response = runPromise(() -> controller.findSimilar(httpRequest));
    assertThat(response.getCode()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(response.getBody().asString(StandardCharsets.UTF_8));
    assertThat(body.isArray()).isTrue();
    assertThat(body).hasSize(1);
  }

  @Test
  @DisplayName("Should generate suggestions and allow recording feedback")
  void shouldGenerateSuggestionsAndRecordFeedback() throws Exception {
    UUID projectId = UUID.randomUUID();
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString()))
        .thenReturn(Promise.of(null));

    HttpResponse createResponse =
        runPromise(
            () ->
                controller.createRequirement(
                    HttpRequest.post(url("/api/projects/" + projectId + "/requirements"))
                        .withBody(
                            objectMapper.writeValueAsBytes(
                                new CreateRequirementRequest("Req text", "HIGH")))
                        .build()));
    String requirementId =
        objectMapper.readTree(createResponse.getBody().asString(StandardCharsets.UTF_8))
            .get("id")
            .asText();

    AISuggestion suggestion =
        sampleSuggestion(requirementId, Persona.PRODUCT_MANAGER, "Align roadmap with API");
    when(embeddingService.generateSuggestions(anyString(), anyString(), any()))
        .thenReturn(Promise.of(List.of(suggestion)));
    when(embeddingService.recordFeedback(any(), any()))
        .thenReturn(Promise.of(suggestion.withStatus(SuggestionStatus.APPROVED)));

    HttpRequest generateRequest =
        HttpRequest.post(url("/api/requirements/" + requirementId + "/suggestions"))
            .withBody(
                """
                {
                  "featureDescription": "Custom feature",
                  "personas": ["PRODUCT_MANAGER"],
                  "limit": 1
                }
                """.getBytes(StandardCharsets.UTF_8))
            .build();

    HttpResponse suggestionsResponse =
        runPromise(() -> controller.generateSuggestions(generateRequest));
    assertThat(suggestionsResponse.getCode()).isEqualTo(200);
    JsonNode suggestions =
        objectMapper.readTree(
            suggestionsResponse.getBody().asString(StandardCharsets.UTF_8));
    String suggestionId = suggestions.get(0).get("id").asText();

    RecordFeedbackRequest feedback = new RecordFeedbackRequest("HELPFUL", 5, "Great insight");
    HttpResponse feedbackResponse =
        runPromise(
            () ->
                controller.recordFeedback(
                    HttpRequest.post(url("/api/suggestions/" + suggestionId + "/feedback"))
                        .withBody(objectMapper.writeValueAsBytes(feedback))
                        .build()));

    assertThat(feedbackResponse.getCode()).isEqualTo(204);
    verify(embeddingService).recordFeedback(any(), any());
  }

  @Test
  @DisplayName("Should reject feedback with invalid rating")
  void shouldRejectInvalidRating() {
    assertThatThrownBy(() -> new RecordFeedbackRequest("HELPFUL", 6, "Text"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rating");
  }

  @Test
  @DisplayName("Should accept feedback with valid rating 1-5")
  void shouldAcceptValidRatings() {
    for (int rating = 1; rating <= 5; rating++) {
      RecordFeedbackRequest feedback = new RecordFeedbackRequest("HELPFUL", rating, "Text");
      assertThat(feedback.getRating()).isEqualTo(rating);
    }
  }

  @Test
  @DisplayName("Should handle feedback with null rating")
  void shouldHandleNullRating() {
    RecordFeedbackRequest feedback = new RecordFeedbackRequest("HELPFUL", null, "Text");
    assertThat(feedback.hasRating()).isFalse();
  }

  @Test
  @DisplayName("Should create valid CreateRequirementRequest")
  void shouldCreateValidRequest() {
    CreateRequirementRequest req = new CreateRequirementRequest("Add OAuth2", "HIGH");

    assertThat(req.text()).isEqualTo("Add OAuth2");
    assertThat(req.priority()).isEqualTo("HIGH");
  }

  @Test
  @DisplayName("Should set default priority in CreateRequirementRequest")
  void shouldSetDefaultPriority() {
    CreateRequirementRequest req = new CreateRequirementRequest("Add OAuth2", null);

    assertThat(req.priority()).isEqualTo("MEDIUM");
  }

  @Test
  @DisplayName("Should reject empty requirement text")
  void shouldRejectEmptyText() {
    assertThatThrownBy(() -> new CreateRequirementRequest("   ", "HIGH"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be empty");
  }

  private AISuggestion sampleSuggestion(
      String requirementId, Persona persona, String suggestionText) {
    return new AISuggestion(
        requirementId,
        suggestionText,
        persona,
        0.9f,
        0.8f,
        SuggestionStatus.PENDING,
        "user-1",
        null);
  }
}
