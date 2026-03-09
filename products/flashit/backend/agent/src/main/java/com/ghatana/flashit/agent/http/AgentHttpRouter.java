package com.ghatana.flashit.agent.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.flashit.agent.config.JsonConfig;
import com.ghatana.flashit.agent.dto.*;
import com.ghatana.flashit.agent.service.*;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import static io.activej.http.HttpMethod.*;

/**
 * HTTP route definitions for the FlashIt Java Agent Service.
 *
 * <p>Exposes all 17 endpoints expected by the Node.js Gateway's Java Agent Client:
 * <ul>
 *   <li>Health &amp; readiness: /health, /ready</li>
 *   <li>Agent discovery: /api/v1/agents, /api/v1/agents/:id/status</li>
 *   <li>Classification: classify, suggest-spheres</li>
 *   <li>Embedding: generate, batch, search</li>
 *   <li>Reflection: insights, patterns, connections</li>
 *   <li>Transcription: transcribe, status/:jobId</li>
 *   <li>NLP: extract-entities, analyze-sentiment, detect-mood</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Defines all HTTP routes for the FlashIt Agent service
 * @doc.layer product
 * @doc.pattern Router
 */
public class AgentHttpRouter {
    private static final Logger log = LoggerFactory.getLogger(AgentHttpRouter.class);
    private static final ObjectMapper mapper = JsonConfig.objectMapper();

    private final ClassificationService classificationService;
    private final EmbeddingService embeddingService;
    private final ReflectionService reflectionService;
    private final TranscriptionService transcriptionService;
    private final NLPService nlpService;
    private final RecommendationService recommendationService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final IntelligenceAccumulationService intelligenceAccumulationService;
    private final Executor blockingExecutor;
    private final boolean openAiConfigured;
    private final Eventloop eventloop;

    public AgentHttpRouter(
            ClassificationService classificationService,
            EmbeddingService embeddingService,
            ReflectionService reflectionService,
            TranscriptionService transcriptionService,
            NLPService nlpService,
            RecommendationService recommendationService,
            KnowledgeGraphService knowledgeGraphService,
            IntelligenceAccumulationService intelligenceAccumulationService,
            Executor blockingExecutor,
            boolean openAiConfigured,
            Eventloop eventloop) {
        this.classificationService = classificationService;
        this.embeddingService = embeddingService;
        this.reflectionService = reflectionService;
        this.transcriptionService = transcriptionService;
        this.nlpService = nlpService;
        this.recommendationService = recommendationService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.intelligenceAccumulationService = intelligenceAccumulationService;
        this.blockingExecutor = blockingExecutor;
        this.openAiConfigured = openAiConfigured;
        this.eventloop = eventloop;
    }

    /**
     * Build the routing servlet with all 17 endpoints.
     *
     * @return configured RoutingServlet
     */
    public RoutingServlet createRoutes() {
        return RoutingServlet.builder(eventloop)

                // =====================================================================
                // Health & Readiness
                // =====================================================================
                .with(GET, "/health", this::handleHealth)
                .with(GET, "/ready", this::handleReady)

                // =====================================================================
                // Agent Discovery
                // =====================================================================
                .with(GET, "/api/v1/agents", this::handleListAgents)
                .with(GET, "/api/v1/agents/:id/status", this::handleAgentStatus)

                // =====================================================================
                // Classification Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/classification/classify", this::handleClassify)
                .with(POST, "/api/v1/agents/classification/suggest-spheres", this::handleSuggestSpheres)

                // =====================================================================
                // Embedding Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/embedding/generate", this::handleGenerateEmbedding)
                .with(POST, "/api/v1/agents/embedding/batch", this::handleBatchEmbeddings)
                .with(POST, "/api/v1/agents/embedding/search", this::handleSemanticSearch)

                // =====================================================================
                // Reflection Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/reflection/insights", this::handleInsights)
                .with(POST, "/api/v1/agents/reflection/patterns", this::handlePatterns)
                .with(POST, "/api/v1/agents/reflection/connections", this::handleConnections)

                // =====================================================================
                // Transcription Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/transcription/transcribe", this::handleTranscribe)
                .with(GET, "/api/v1/agents/transcription/status/:jobId", this::handleTranscriptionStatus)

                // =====================================================================
                // NLP Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/nlp/extract-entities", this::handleExtractEntities)
                .with(POST, "/api/v1/agents/nlp/analyze-sentiment", this::handleAnalyzeSentiment)
                .with(POST, "/api/v1/agents/nlp/detect-mood", this::handleDetectMood)

                // =====================================================================
                // Recommendation Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/recommendation/generate", this::handleGenerateRecommendations)

                // =====================================================================
                // Knowledge Graph Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/knowledge-graph/extract", this::handleExtractGraph)
                .with(POST, "/api/v1/agents/knowledge-graph/query", this::handleQueryGraph)
                .with(POST, "/api/v1/agents/knowledge-graph/expand", this::handleExpandGraph)

                // =====================================================================
                // Intelligence Accumulation Agent
                // =====================================================================
                .with(POST, "/api/v1/agents/intelligence/compute-profile", this::handleComputeProfile)

                .build();
    }

    // =========================================================================
    // Health & Readiness Handlers
    // =========================================================================

    private Promise<HttpResponse> handleHealth(HttpRequest req) {
        return jsonResponse(new HealthResponse("healthy", Instant.now().toString(), "flashit-agent"));
    }

    private Promise<HttpResponse> handleReady(HttpRequest req) {
        List<String> agents = List.of("classification", "embedding", "reflection", "transcription",
                "nlp", "recommendation", "knowledge-graph", "intelligence");
        return jsonResponse(new ReadinessResponse(openAiConfigured, agents, openAiConfigured));
    }

    // =========================================================================
    // Agent Discovery Handlers
    // =========================================================================

    private Promise<HttpResponse> handleListAgents(HttpRequest req) {
        List<AgentInfo> agents = List.of(
                new AgentInfo("classification", "Classification Agent",
                        "Classifies moments into spheres", "active",
                        List.of("classify", "suggest-spheres")),
                new AgentInfo("embedding", "Embedding Agent",
                        "Generates and searches text embeddings", "active",
                        List.of("generate", "batch", "search")),
                new AgentInfo("reflection", "Reflection Agent",
                        "Generates insights, patterns, and connections", "active",
                        List.of("insights", "patterns", "connections")),
                new AgentInfo("transcription", "Transcription Agent",
                        "Transcribes audio and video content", "active",
                        List.of("transcribe", "status")),
                new AgentInfo("nlp", "NLP Agent",
                        "Extracts entities, analyzes sentiment, detects mood", "active",
                        List.of("extract-entities", "analyze-sentiment", "detect-mood")),
                new AgentInfo("recommendation", "Recommendation Agent",
                        "Generates personalized recommendations", "active",
                        List.of("generate")),
                new AgentInfo("knowledge-graph", "Knowledge Graph Agent",
                        "Extracts and queries knowledge graphs", "active",
                        List.of("extract", "query", "expand")),
                new AgentInfo("intelligence", "Intelligence Agent",
                        "Computes long-term knowledge profiles", "active",
                        List.of("compute-profile"))
        );
        return jsonResponse(java.util.Map.of("agents", agents));
    }

    private Promise<HttpResponse> handleAgentStatus(HttpRequest req) {
        String agentId = req.getPathParameter("id");
        AgentInfo info = switch (agentId) {
            case "classification" -> new AgentInfo(agentId, "Classification Agent",
                    "Classifies moments into spheres", "active",
                    List.of("classify", "suggest-spheres"));
            case "embedding" -> new AgentInfo(agentId, "Embedding Agent",
                    "Generates and searches text embeddings", "active",
                    List.of("generate", "batch", "search"));
            case "reflection" -> new AgentInfo(agentId, "Reflection Agent",
                    "Generates insights, patterns, and connections", "active",
                    List.of("insights", "patterns", "connections"));
            case "transcription" -> new AgentInfo(agentId, "Transcription Agent",
                    "Transcribes audio and video content", "active",
                    List.of("transcribe", "status"));
            case "nlp" -> new AgentInfo(agentId, "NLP Agent",
                    "Extracts entities, analyzes sentiment, detects mood", "active",
                    List.of("extract-entities", "analyze-sentiment", "detect-mood"));
            case "recommendation" -> new AgentInfo(agentId, "Recommendation Agent",
                    "Generates personalized recommendations", "active",
                    List.of("generate"));
            case "knowledge-graph" -> new AgentInfo(agentId, "Knowledge Graph Agent",
                    "Extracts and queries knowledge graphs", "active",
                    List.of("extract", "query", "expand"));
            case "intelligence" -> new AgentInfo(agentId, "Intelligence Agent",
                    "Computes long-term knowledge profiles", "active",
                    List.of("compute-profile"));
            default -> null;
        };

        if (info == null) {
            return errorResponse(404, "Not Found", "Agent not found: " + agentId);
        }
        return jsonResponse(info);
    }

    // =========================================================================
    // Classification Handlers
    // =========================================================================

    private Promise<HttpResponse> handleClassify(HttpRequest req) {
        return parseAndExecute(req, ClassificationRequest.class,
                classificationService::classify);
    }

    private Promise<HttpResponse> handleSuggestSpheres(HttpRequest req) {
        return parseAndExecute(req, ClassificationRequest.class,
                classificationService::suggestSpheres);
    }

    // =========================================================================
    // Embedding Handlers
    // =========================================================================

    private Promise<HttpResponse> handleGenerateEmbedding(HttpRequest req) {
        return parseAndExecute(req, EmbeddingRequest.class,
                embeddingService::generateEmbedding);
    }

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleBatchEmbeddings(HttpRequest req) {
        return req.loadBody()
                .then($ -> Promise.ofBlocking(blockingExecutor, () -> {
                    try {
                        List<EmbeddingRequest> requests = mapper.readValue(
                                req.getBody().asArray(),
                                mapper.getTypeFactory().constructCollectionType(List.class, EmbeddingRequest.class));
                        Object result = embeddingService.generateBatchEmbeddings(requests);
                        return HttpResponse.ok200()
                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody(mapper.writeValueAsBytes(result))
                                .build();
                    } catch (Exception e) {
                        log.error("Batch embedding failed", e);
                        return HttpResponse.ofCode(500)
                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody(mapper.writeValueAsBytes(
                                        new ErrorResponse("Internal Error", e.getMessage())))
                                .build();
                    }
                }));
    }

    private Promise<HttpResponse> handleSemanticSearch(HttpRequest req) {
        return parseAndExecute(req, SemanticSearchRequest.class,
                embeddingService::semanticSearch);
    }

    // =========================================================================
    // Reflection Handlers
    // =========================================================================

    private Promise<HttpResponse> handleInsights(HttpRequest req) {
        return parseAndExecute(req, ReflectionRequest.class,
                reflectionService::generateInsights);
    }

    private Promise<HttpResponse> handlePatterns(HttpRequest req) {
        return parseAndExecute(req, ReflectionRequest.class,
                reflectionService::detectPatterns);
    }

    private Promise<HttpResponse> handleConnections(HttpRequest req) {
        return parseAndExecute(req, ReflectionRequest.class,
                reflectionService::findConnections);
    }

    // =========================================================================
    // Transcription Handlers
    // =========================================================================

    private Promise<HttpResponse> handleTranscribe(HttpRequest req) {
        return parseAndExecute(req, TranscriptionRequest.class,
                transcriptionService::transcribe);
    }

    private Promise<HttpResponse> handleTranscriptionStatus(HttpRequest req) {
        String jobId = req.getPathParameter("jobId");
        return Promise.ofBlocking(blockingExecutor, () -> {
            TranscriptionResponse result = transcriptionService.getStatus(jobId);
            if (result == null) {
                return HttpResponse.ofCode(404)
                        .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(mapper.writeValueAsBytes(
                                new ErrorResponse("Not Found", "Job not found: " + jobId)))
                        .build();
            }
            return HttpResponse.ok200()
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(mapper.writeValueAsBytes(result))
                    .build();
        });
    }

    // =========================================================================
    // NLP Handlers
    // =========================================================================

    private Promise<HttpResponse> handleExtractEntities(HttpRequest req) {
        return parseAndExecute(req, NLPRequest.class, nlpService::extractEntities);
    }

    private Promise<HttpResponse> handleAnalyzeSentiment(HttpRequest req) {
        return parseAndExecute(req, NLPRequest.class, nlpService::analyzeSentiment);
    }

    private Promise<HttpResponse> handleDetectMood(HttpRequest req) {
        return parseAndExecute(req, NLPRequest.class, nlpService::detectMood);
    }

    // =========================================================================
    // Recommendation Handlers
    // =========================================================================

    private Promise<HttpResponse> handleGenerateRecommendations(HttpRequest req) {
        return parseAndExecute(req, RecommendationRequest.class,
                recommendationService::generateRecommendations);
    }

    // =========================================================================
    // Knowledge Graph Handlers
    // =========================================================================

    private Promise<HttpResponse> handleExtractGraph(HttpRequest req) {
        return parseAndExecute(req, KnowledgeGraphRequest.class,
                knowledgeGraphService::extractGraph);
    }

    private Promise<HttpResponse> handleQueryGraph(HttpRequest req) {
        return parseAndExecute(req, KnowledgeGraphRequest.class,
                knowledgeGraphService::queryGraph);
    }

    private Promise<HttpResponse> handleExpandGraph(HttpRequest req) {
        return parseAndExecute(req, KnowledgeGraphRequest.class,
                knowledgeGraphService::expandGraph);
    }

    // =========================================================================
    // Intelligence Accumulation Handlers
    // =========================================================================

    private Promise<HttpResponse> handleComputeProfile(HttpRequest req) {
        return parseAndExecute(req, IntelligenceAccumulationRequest.class,
                intelligenceAccumulationService::computeProfile);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Generic handler: parse JSON body, execute service method, return JSON response.
     * Service calls are offloaded to blocking executor to avoid blocking the eventloop.
     */
    private <T, R> Promise<HttpResponse> parseAndExecute(
            HttpRequest req, Class<T> requestType, java.util.function.Function<T, R> handler) {
        return req.loadBody()
                .then($ -> Promise.ofBlocking(blockingExecutor, () -> {
                    try {
                        T body = mapper.readValue(req.getBody().asArray(), requestType);
                        R result = handler.apply(body);
                        return HttpResponse.ok200()
                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody(mapper.writeValueAsBytes(result))
                                .build();
                    } catch (com.fasterxml.jackson.databind.JsonMappingException
                             | com.fasterxml.jackson.core.JsonParseException e) {
                        log.warn("Invalid request body: {}", e.getMessage());
                        return HttpResponse.ofCode(400)
                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody(mapper.writeValueAsBytes(
                                        new ErrorResponse("Bad Request", "Invalid JSON: " + e.getMessage())))
                                .build();
                    } catch (Exception e) {
                        log.error("Request handler failed", e);
                        return HttpResponse.ofCode(500)
                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody(mapper.writeValueAsBytes(
                                        new ErrorResponse("Internal Error", e.getMessage())))
                                .build();
                    }
                }));
    }

    private <T> Promise<HttpResponse> jsonResponse(T body) {
        try {
            byte[] json = mapper.writeValueAsBytes(body);
            return Promise.of(HttpResponse.ok200()
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json)
                    .build());
        } catch (Exception e) {
            log.error("JSON serialization failed", e);
            return Promise.of(HttpResponse.ofCode(500).build());
        }
    }

    private Promise<HttpResponse> errorResponse(int code, String error, String message) {
        try {
            byte[] json = mapper.writeValueAsBytes(new ErrorResponse(error, message));
            return Promise.of(HttpResponse.ofCode(code)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json)
                    .build());
        } catch (Exception e) {
            return Promise.of(HttpResponse.ofCode(code).build());
        }
    }
}
