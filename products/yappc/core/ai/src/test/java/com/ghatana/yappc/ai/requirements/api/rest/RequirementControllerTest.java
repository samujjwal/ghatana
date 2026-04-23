package com.ghatana.yappc.ai.requirements.api.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.requirements.application.requirement.RequirementService;
import com.ghatana.yappc.ai.requirements.ai.RequirementEmbeddingService;
import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.yappc.ai.requirements.api.rest.dto.CreateRequirementRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.RecordFeedbackRequest;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
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
    private RequirementService requirementService;
  private ObjectMapper objectMapper;
  private static final String BASE_URL = "http://localhost:8082";

  private static String url(String path) { // GH-90000
    return BASE_URL + path;
  }

  @BeforeEach
  void setUp() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
    objectMapper = new ObjectMapper(); // GH-90000
    when(embeddingService.generateSuggestions(anyString(), anyString(), anyString())) // GH-90000
        .thenReturn(Promise.of(List.of())); // GH-90000
    when(embeddingService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat())) // GH-90000
        .thenReturn(Promise.of(List.of())); // GH-90000
    when(embeddingService.embedAndStore(anyString(), anyString(), anyString())) // GH-90000
        .thenReturn(Promise.of((Void) null)); // GH-90000
    when(embeddingService.updateEmbedding(anyString(), anyString())) // GH-90000
        .thenReturn(Promise.of((Void) null)); // GH-90000

    requirementService = new RequirementService(embeddingService); // GH-90000
    controller = new RequirementController(requirementService, embeddingService); // GH-90000
  }

  @Test
  @DisplayName("Should create requirement controller with valid service")
  void shouldCreateController() { // GH-90000
    assertThat(controller).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("Should throw NullPointerException when service is null")
  void shouldThrowWhenServiceNull() { // GH-90000
    assertThatThrownBy(() -> new RequirementController(null)) // GH-90000
        .isInstanceOf(NullPointerException.class); // GH-90000
  }

  @Test
  @DisplayName("Should create requirement when valid request")
  void shouldCreateRequirementWhenValidRequest() throws Exception { // GH-90000
    CreateRequirementRequest request = new CreateRequirementRequest("Test requirement", "HIGH"); // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000

    HttpRequest httpRequest = HttpRequest.post(url("/api/v1/projects/" + projectId + "/requirements")) // GH-90000
        .withBody(objectMapper.writeValueAsBytes(request)) // GH-90000
        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
        .build(); // GH-90000

    HttpResponse response = runPromise(() -> controller.createRequirement(httpRequest)); // GH-90000

    assertThat(response.getCode()).isEqualTo(201); // GH-90000
    assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");

    ByteBuf bodyBuf = runPromise(() -> response.loadBody()); // GH-90000
    String responseBody = bodyBuf.asString(StandardCharsets.UTF_8); // GH-90000
    JsonNode jsonResponse = objectMapper.readTree(responseBody); // GH-90000

    assertThat(jsonResponse.has("id")).isTrue();
    assertThat(jsonResponse.has("text")).isTrue();
    assertThat(jsonResponse.get("text").asText()).isEqualTo("Test requirement");
    assertThat(jsonResponse.has("status")).isTrue();
    assertThat(jsonResponse.get("status").asText()).isEqualTo("DRAFT");
    assertThat(jsonResponse.has("qualityScore")).isTrue();
    assertThat(jsonResponse.get("qualityScore").asDouble()).isGreaterThan(0.0);
    assertThat(jsonResponse.has("duplicateWarnings")).isTrue();
    assertThat(jsonResponse.get("duplicateWarnings").isArray()).isTrue();
    assertThat(jsonResponse.has("suggestions")).isTrue();
    assertThat(jsonResponse.get("suggestions").isArray()).isTrue();

    verify(embeddingService) // GH-90000
        .embedAndStore(anyString(), eq("Test requirement"), eq(projectId.toString()));
  }

  @Test
  @DisplayName("Should return detailed requirement with suggestions and similar items")
  void shouldGetRequirementWithSuggestionsAndSimilar() throws Exception { // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000

    CreateRequirementRequest create = new CreateRequirementRequest("Initial requirement", "HIGH"); // GH-90000
    HttpResponse createResponse =
        runPromise( // GH-90000
            () -> // GH-90000
                controller.createRequirement( // GH-90000
                    HttpRequest.post(url("/api/v1/projects/" + projectId + "/requirements")) // GH-90000
                        .withBody(objectMapper.writeValueAsBytes(create)) // GH-90000
                        .build())); // GH-90000
    JsonNode created = objectMapper.readTree(runPromise(() -> createResponse.loadBody()).asString(StandardCharsets.UTF_8)); // GH-90000
    String requirementId = created.get("id").asText();

    when(embeddingService.generateSuggestions(anyString(), anyString(), any())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                List.of( // GH-90000
                    sampleSuggestion( // GH-90000
                        requirementId, Persona.DEVELOPER, "Add logging for mobile flows"))));

    doReturn(Promise.of( // GH-90000
                List.of( // GH-90000
                    new VectorSearchResult( // GH-90000
                        UUID.randomUUID().toString(), "Preview requirement text", new float[0], 0.91, 0)))) // GH-90000
        .when(embeddingService).findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat()); // GH-90000

    HttpRequest getRequest =
        HttpRequest.get( // GH-90000
            url( // GH-90000
                "/api/v1/requirements/"
                    + requirementId
                    + "?includeSuggestions=true&includeSimilar=true"))
            .build(); // GH-90000

    HttpResponse getResponse = runPromise(() -> controller.getRequirement(getRequest)); // GH-90000

    assertThat(getResponse.getCode()).isEqualTo(200); // GH-90000
    JsonNode body = objectMapper.readTree(runPromise(() -> getResponse.loadBody()).asString(StandardCharsets.UTF_8)); // GH-90000
    assertThat(body.get("suggestions")).hasSize(1);
    assertThat(body.get("similarRequirements")).hasSize(1);
    assertThat(body.get("qualityScore").asDouble()).isGreaterThan(0.0);
  }

  @Test
  @DisplayName("Should return 400 when creating requirement with invalid data")
  void shouldReturn400WhenCreatingRequirementWithInvalidData() throws Exception { // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000
    HttpRequest httpRequest =
                HttpRequest.post(url("/api/v1/projects/" + projectId + "/requirements")) // GH-90000
            .withBody(new byte[0]) // GH-90000
            .build(); // GH-90000

    HttpResponse response = runPromise(() -> controller.createRequirement(httpRequest)); // GH-90000

    assertThat(response.getCode()).isEqualTo(400); // GH-90000
    verify(embeddingService, never()).embedAndStore(anyString(), anyString(), anyString()); // GH-90000
  }

  @Test
  @DisplayName("Should find similar requirements when valid request")
  void shouldFindSimilarRequirementsWhenValidRequest() throws Exception { // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000
    HttpResponse createResponse =
        runPromise( // GH-90000
            () -> // GH-90000
                controller.createRequirement( // GH-90000
                    HttpRequest.post(url("/api/v1/projects/" + projectId + "/requirements")) // GH-90000
                        .withBody( // GH-90000
                            objectMapper.writeValueAsBytes( // GH-90000
                                new CreateRequirementRequest("Req text", "HIGH"))) // GH-90000
                        .build())); // GH-90000
    String requirementId =
        objectMapper.readTree(runPromise(() -> createResponse.loadBody()).asString(StandardCharsets.UTF_8)) // GH-90000
            .get("id")
            .asText(); // GH-90000

    doReturn(Promise.of( // GH-90000
                List.of( // GH-90000
                    new VectorSearchResult( // GH-90000
                        UUID.randomUUID().toString(), "Potentially similar requirement", new float[0], 0.88, 0)))) // GH-90000
        .when(embeddingService).findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat()); // GH-90000

    HttpRequest httpRequest =
        HttpRequest.get( // GH-90000
                        url("/api/v1/requirements/" + requirementId + "/similar?limit=2&minSimilarity=0.8")) // GH-90000
            .build(); // GH-90000

    HttpResponse response = runPromise(() -> controller.findSimilar(httpRequest)); // GH-90000
    assertThat(response.getCode()).isEqualTo(200); // GH-90000
        JsonNode body = objectMapper.readTree(runPromise(() -> response.loadBody()).asString(StandardCharsets.UTF_8)); // GH-90000
    assertThat(body.isArray()).isTrue(); // GH-90000
    assertThat(body).hasSize(1); // GH-90000
  }

  @Test
  @DisplayName("Should generate suggestions and allow recording feedback")
  void shouldGenerateSuggestionsAndRecordFeedback() throws Exception { // GH-90000
    UUID projectId = UUID.randomUUID(); // GH-90000
    HttpResponse createResponse =
        runPromise( // GH-90000
            () -> // GH-90000
                controller.createRequirement( // GH-90000
                    HttpRequest.post(url("/api/v1/projects/" + projectId + "/requirements")) // GH-90000
                        .withBody( // GH-90000
                            objectMapper.writeValueAsBytes( // GH-90000
                                new CreateRequirementRequest("Req text", "HIGH"))) // GH-90000
                        .build())); // GH-90000
    String requirementId =
        objectMapper.readTree(runPromise(() -> createResponse.loadBody()).asString(StandardCharsets.UTF_8)) // GH-90000
            .get("id")
            .asText(); // GH-90000

    AISuggestion suggestion =
        sampleSuggestion(requirementId, Persona.PRODUCT_MANAGER, "Align roadmap with API"); // GH-90000
    when(embeddingService.generateSuggestions(anyString(), anyString(), any())) // GH-90000
        .thenReturn(Promise.of(List.of(suggestion))); // GH-90000
    when(embeddingService.recordFeedback(any(), any())) // GH-90000
        .thenReturn(Promise.of(suggestion.withStatus(SuggestionStatus.APPROVED))); // GH-90000

    HttpRequest generateRequest =
        HttpRequest.post(url("/api/v1/requirements/" + requirementId + "/suggestions")) // GH-90000
            .withBody( // GH-90000
                """
                {
                  "featureDescription": "Custom feature",
                  "personas": ["PRODUCT_MANAGER"],
                  "limit": 1
                }
                """.getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000

    HttpResponse suggestionsResponse =
        runPromise(() -> controller.generateSuggestions(generateRequest)); // GH-90000
    assertThat(suggestionsResponse.getCode()).isEqualTo(200); // GH-90000
    JsonNode suggestions =
        objectMapper.readTree(runPromise(() -> suggestionsResponse.loadBody()).asString(StandardCharsets.UTF_8)); // GH-90000
    String suggestionId = suggestions.get(0).get("id").asText();

    RecordFeedbackRequest feedback = new RecordFeedbackRequest("HELPFUL", 5, "Great insight"); // GH-90000
    HttpResponse feedbackResponse =
        runPromise( // GH-90000
            () -> // GH-90000
                controller.recordFeedback( // GH-90000
                    HttpRequest.post(url("/api/v1/suggestions/" + suggestionId + "/feedback")) // GH-90000
                        .withBody(objectMapper.writeValueAsBytes(feedback)) // GH-90000
                        .build())); // GH-90000

    assertThat(feedbackResponse.getCode()).isEqualTo(204); // GH-90000
    verify(embeddingService).recordFeedback(any(), any()); // GH-90000
  }

  @Test
  @DisplayName("Should reject feedback with invalid rating")
  void shouldRejectInvalidRating() { // GH-90000
    assertThatThrownBy(() -> new RecordFeedbackRequest("HELPFUL", 6, "Text")) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("rating");
  }

  @Test
  @DisplayName("Should accept feedback with valid rating 1-5")
  void shouldAcceptValidRatings() { // GH-90000
    for (int rating = 1; rating <= 5; rating++) { // GH-90000
      RecordFeedbackRequest feedback = new RecordFeedbackRequest("HELPFUL", rating, "Text"); // GH-90000
      assertThat(feedback.getRating()).isEqualTo(rating); // GH-90000
    }
  }

  @Test
  @DisplayName("Should handle feedback with null rating")
  void shouldHandleNullRating() { // GH-90000
    RecordFeedbackRequest feedback = new RecordFeedbackRequest("HELPFUL", null, "Text"); // GH-90000
    assertThat(feedback.hasRating()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("Should create valid CreateRequirementRequest")
  void shouldCreateValidRequest() { // GH-90000
    CreateRequirementRequest req = new CreateRequirementRequest("Add OAuth2", "HIGH"); // GH-90000

    assertThat(req.text()).isEqualTo("Add OAuth2");
    assertThat(req.priority()).isEqualTo("HIGH");
  }

  @Test
  @DisplayName("Should set default priority in CreateRequirementRequest")
  void shouldSetDefaultPriority() { // GH-90000
    CreateRequirementRequest req = new CreateRequirementRequest("Add OAuth2", null); // GH-90000

    assertThat(req.priority()).isEqualTo("MEDIUM");
  }

  @Test
  @DisplayName("Should reject empty requirement text")
  void shouldRejectEmptyText() { // GH-90000
    assertThatThrownBy(() -> new CreateRequirementRequest("   ", "HIGH")) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("cannot be empty");
  }

  private AISuggestion sampleSuggestion( // GH-90000
      String requirementId, Persona persona, String suggestionText) {
    return new AISuggestion( // GH-90000
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
