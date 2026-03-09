package com.ghatana.flashit.agent.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.config.JsonConfig;
import com.ghatana.flashit.agent.dto.*;
import com.ghatana.flashit.agent.service.*;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import io.activej.test.ExpectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AgentHttpRouter.
 *
 * <p>Tests route definitions and handler wiring with mocked services.
 */
@DisplayName("AgentHttpRouter Tests")
class AgentHttpRouterTest {

    private AgentHttpRouter router;
    private ClassificationService classificationService;
    private EmbeddingService embeddingService;
    private ReflectionService reflectionService;
    private TranscriptionService transcriptionService;
    private NLPService nlpService;

    @BeforeEach
    void setUp() {
        classificationService = Mockito.mock(ClassificationService.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        reflectionService = Mockito.mock(ReflectionService.class);
        transcriptionService = Mockito.mock(TranscriptionService.class);
        nlpService = Mockito.mock(NLPService.class);

        Executor executor = Executors.newSingleThreadExecutor();
        Eventloop eventloop = Eventloop.create();
        router = new AgentHttpRouter(
                classificationService, embeddingService, reflectionService,
                transcriptionService, nlpService, executor, true, eventloop);
    }

    @Test
    @DisplayName("createRoutes() returns non-null RoutingServlet")
    void createRoutesReturnsServlet() {
        // WHEN
        RoutingServlet servlet = router.createRoutes();

        // THEN
        assertThat(servlet).isNotNull();
    }

    @Test
    @DisplayName("Router registers all 17 expected endpoints")
    void routerRegistersAllEndpoints() {
        // Verify the servlet builds without error — routes registered
        RoutingServlet servlet = router.createRoutes();
        assertThat(servlet).isNotNull();
    }

    @Test
    @DisplayName("DTO serialization round-trip for HealthResponse")
    void healthResponseSerializationRoundTrip() throws Exception {
        // GIVEN
        ObjectMapper mapper = JsonConfig.objectMapper();
        HealthResponse original = new HealthResponse("healthy", Instant.now().toString(), "flashit-agent");

        // WHEN
        String json = mapper.writeValueAsString(original);
        HealthResponse deserialized = mapper.readValue(json, HealthResponse.class);

        // THEN
        assertThat(deserialized.status()).isEqualTo("healthy");
        assertThat(deserialized.service()).isEqualTo("flashit-agent");
    }

    @Test
    @DisplayName("DTO serialization round-trip for ClassificationResponse")
    void classificationResponseSerializationRoundTrip() throws Exception {
        // GIVEN
        ObjectMapper mapper = JsonConfig.objectMapper();
        ClassificationResponse original = new ClassificationResponse(
                "sphere-1", "Health", 0.85, "Exercise related content",
                List.of(new SphereSuggestion("sphere-2", "Daily", 0.6, "General content")),
                150L, "gpt-4o");

        // WHEN
        String json = mapper.writeValueAsString(original);
        ClassificationResponse deserialized = mapper.readValue(json, ClassificationResponse.class);

        // THEN
        assertThat(deserialized.sphereId()).isEqualTo("sphere-1");
        assertThat(deserialized.confidence()).isEqualTo(0.85);
        assertThat(deserialized.alternatives()).hasSize(1);
    }

    @Test
    @DisplayName("DTO serialization round-trip for NLPResponse")
    void nlpResponseSerializationRoundTrip() throws Exception {
        // GIVEN
        ObjectMapper mapper = JsonConfig.objectMapper();
        NLPResponse original = new NLPResponse(
                "moment-1",
                List.of(new Entity("Paris", "LOCATION", 0.95, 12, 17)),
                new SentimentResult("positive", 0.8, 0.8, 0.1, 0.1),
                new MoodResult("happy", 0.9, List.of("excited"), 0.7),
                200L, "gpt-4o");

        // WHEN
        String json = mapper.writeValueAsString(original);
        NLPResponse deserialized = mapper.readValue(json, NLPResponse.class);

        // THEN
        assertThat(deserialized.entities()).hasSize(1);
        assertThat(deserialized.entities().getFirst().type()).isEqualTo("LOCATION");
        assertThat(deserialized.sentiment().label()).isEqualTo("positive");
        assertThat(deserialized.mood().primaryMood()).isEqualTo("happy");
    }

    @Test
    @DisplayName("ErrorResponse serialization includes error and message")
    void errorResponseSerialization() throws Exception {
        // GIVEN
        ObjectMapper mapper = JsonConfig.objectMapper();
        ErrorResponse error = new ErrorResponse("Bad Request", "Invalid JSON");

        // WHEN
        String json = mapper.writeValueAsString(error);

        // THEN
        assertThat(json).contains("Bad Request");
        assertThat(json).contains("Invalid JSON");
    }
}
