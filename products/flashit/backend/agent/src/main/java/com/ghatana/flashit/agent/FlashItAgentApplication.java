package com.ghatana.flashit.agent;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.http.AgentHttpRouter;
import com.ghatana.flashit.agent.service.*;
import com.ghatana.flashit.agent.util.OpenAIClientFactory;
import com.ghatana.platform.governance.security.TenantIsolationHttpFilter;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpServer;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraphModule;
import com.openai.client.OpenAIClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * FlashIt Agent Service — Main application launcher.
 *
 * <p>This is the Java backend for FlashIt's AI capabilities. It runs on port 8090
 * (configurable via AGENT_PORT) and exposes 17 HTTP endpoints consumed by the
 * Node.js Fastify gateway.
 *
 * <p><b>Capabilities:</b>
 * <ul>
 *   <li>Moment classification into spheres (OpenAI GPT)</li>
 *   <li>Text embedding generation and semantic search (OpenAI Embeddings)</li>
 *   <li>AI-powered reflection: insights, patterns, connections (OpenAI GPT)</li>
 *   <li>Audio/video transcription (OpenAI Whisper)</li>
 *   <li>NLP: entity extraction, sentiment analysis, mood detection (OpenAI GPT)</li>
 * </ul>
 *
 * <p><b>Environment Variables:</b>
 * <ul>
 *   <li>{@code OPENAI_API_KEY} — OpenAI API key (required)</li>
 *   <li>{@code OPENAI_MODEL} — Chat model (default: gpt-4o)</li>
 *   <li>{@code OPENAI_EMBEDDING_MODEL} — Embedding model (default: text-embedding-3-small)</li>
 *   <li>{@code WHISPER_MODEL} — Transcription model (default: whisper-1)</li>
 *   <li>{@code AGENT_PORT} — HTTP server port (default: 8090)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Main entry point for the FlashIt Java Agent Service
 * @doc.layer product
 * @doc.pattern Launcher
 */
public final class FlashItAgentApplication extends Launcher {

    private static final Logger logger = LoggerFactory.getLogger(FlashItAgentApplication.class);

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     * @throws Exception if startup fails
     */
    public static void main(String[] args) throws Exception {
        new FlashItAgentApplication().launch(args);
    }

    @Override
    protected Module getModule() {
        logger.info("Setting up FlashIt Agent service modules");

        AgentConfig config = new AgentConfig();
        Eventloop eventloop = Eventloop.create();
        Executor blockingExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2);

        // M3 FIX: Single shared OpenAI client — reuses HTTP connection pool
        OpenAIClient openAIClient = config.isOpenAiConfigured()
                ? OpenAIClientFactory.create(config)
                : null;

        // Services — single instances shared between DI and router
        ClassificationService classificationService = new ClassificationService(openAIClient, config);
        EmbeddingService embeddingService = new EmbeddingService(openAIClient, config);
        ReflectionService reflectionService = new ReflectionService(openAIClient, config);
        TranscriptionService transcriptionService = new TranscriptionService(config);
        NLPService nlpService = new NLPService(openAIClient, config);
        RecommendationService recommendationService = new RecommendationService(openAIClient, config);
        KnowledgeGraphService knowledgeGraphService = new KnowledgeGraphService(openAIClient, config);
        IntelligenceAccumulationService intelligenceAccumulationService = new IntelligenceAccumulationService(openAIClient, config);

        AgentHttpRouter router = new AgentHttpRouter(
                classificationService,
                embeddingService,
                reflectionService,
                transcriptionService,
                nlpService,
                recommendationService,
                knowledgeGraphService,
                intelligenceAccumulationService,
                blockingExecutor,
                config.isOpenAiConfigured(),
                eventloop
        );

        AsyncServlet servlet = TenantIsolationHttpFilter.wrap(router.createRoutes());

        HttpServer httpServer = HttpServer.builder(eventloop, servlet)
                .withListenPort(config.getServerPort())
                .build();

        return ModuleBuilder.create()
                .bind(Eventloop.class).toInstance(eventloop)
                .bind(HttpServer.class).toInstance(httpServer)
                .bind(AgentConfig.class).toInstance(config)
                .bind(Executor.class).toInstance(blockingExecutor)
                .bind(ClassificationService.class).toInstance(classificationService)
                .bind(EmbeddingService.class).toInstance(embeddingService)
                .bind(ReflectionService.class).toInstance(reflectionService)
                .bind(TranscriptionService.class).toInstance(transcriptionService)
                .bind(NLPService.class).toInstance(nlpService)
                .bind(RecommendationService.class).toInstance(recommendationService)
                .bind(KnowledgeGraphService.class).toInstance(knowledgeGraphService)
                .bind(IntelligenceAccumulationService.class).toInstance(intelligenceAccumulationService)
                .bind(AgentHttpRouter.class).toInstance(router)
                .install(ServiceGraphModule.create())
                .build();
    }

    @Override
    protected void run() throws Exception {
        logger.info("FlashIt Agent Service started successfully");
        logger.info("  Service: flashit-agent");
        logger.info("  Version: 0.1.0-SNAPSHOT");
        logger.info("  Agents (8):");
        logger.info("    Classification:   POST /api/v1/agents/classification/*");
        logger.info("    Embedding:        POST /api/v1/agents/embedding/*");
        logger.info("    Reflection:       POST /api/v1/agents/reflection/*");
        logger.info("    Transcription:    POST /api/v1/agents/transcription/*");
        logger.info("    NLP:              POST /api/v1/agents/nlp/*");
        logger.info("    Recommendation:   POST /api/v1/agents/recommendation/*");
        logger.info("    Knowledge Graph:  POST /api/v1/agents/knowledge-graph/*");
        logger.info("    Intelligence:     POST /api/v1/agents/intelligence/*");
        logger.info("  Infra:");
        logger.info("    Health:           GET  /health");
        logger.info("    Ready:            GET  /ready");
        logger.info("    Agent Discovery:  GET  /api/v1/agents");
        awaitShutdown();
    }
}
