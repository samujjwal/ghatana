package com.ghatana.yappc.services.lifecycle;

import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.OpenAICompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.validate.ValidationService;
import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * YAPPC Lifecycle Service — SDLC phase management and workflow orchestration.
 *
 * <p>Manages the complete software development lifecycle:
 * Intent → Shape → Generate → Run → Observe → Evolve → Learn → Validate</p>
 *
 * <p>Components wired via {@link LifecycleServiceModule}:
 * <ul>
 *   <li>{@link IntentService} — AI-assisted intent capture</li>
 *   <li>{@link ShapeService} — Architecture and domain modeling</li>
 *   <li>{@link GenerationService} — Code and artifact generation</li>
 *   <li>{@link RunService} — Build execution and orchestration</li>
 *   <li>{@link ObserveService} — Runtime monitoring</li>
 *   <li>{@link EvolutionService} — Progressive evolution planning</li>
 *   <li>{@link LearningService} — Pattern extraction and learning</li>
 *   <li>{@link ValidationService} — Security and policy compliance</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose SDLC lifecycle management service entry point
 * @doc.layer product
 * @doc.pattern Launcher
 */
public class YappcLifecycleService extends UnifiedApplicationLauncher {

    private static final Logger logger = LoggerFactory.getLogger(YappcLifecycleService.class);
    private static final int DEFAULT_PORT = 8082;

    @Override
    protected String getServiceName() {
        return "yappc-lifecycle";
    }

    @Override
    protected String getServiceVersion() {
        return "2.0.0";
    }

    @Override
    protected void setupService(ModuleBuilder builder) {
        // Eventloop (NioReactor) for HTTP server and routing
        builder.bind(io.activej.eventloop.Eventloop.class)
                .toInstance(io.activej.eventloop.Eventloop.create());

        // Platform dependencies required by LifecycleServiceModule
        MetricsCollector metrics = new NoopMetricsCollector();
        builder.bind(MetricsCollector.class).toInstance(metrics);
        builder.bind(AuditLogger.class).toInstance(AuditLogger.noop());

        // CompletionService — backed by LLMGateway or stub for dev mode
        String openAiKey = System.getenv("OPENAI_API_KEY");
        LLMConfiguration llmConfig = LLMConfiguration.builder()
                .apiKey(openAiKey != null ? openAiKey : "stub")
                .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
                .temperature(0.7)
                .maxTokens(2000)
                .timeoutSeconds(30)
                .maxRetries(3)
                .build();
        CompletionService completionService = new OpenAICompletionService(llmConfig, null, metrics);
        builder.bind(CompletionService.class).toInstance(completionService);

        // PolicyEngine — permissive stub for development; replace with real impl for production
        builder.bind(PolicyEngine.class).toInstance(new PolicyEngine() {
            @Override
            public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) {
                return Promise.of(true);
            }

            @Override
            public Promise<Boolean> policyExists(String policyName) {
                return Promise.of(false);
            }
        });

        // Install the lifecycle DI module (all 8 phase services)
        builder.install(new LifecycleServiceModule());

        logger.info("YAPPC Lifecycle service bindings configured via LifecycleServiceModule");
    }

    @Override
    protected HttpServer createHttpServer(Injector injector) {
        int port = Integer.parseInt(System.getProperty("yappc.lifecycle.port",
                String.valueOf(DEFAULT_PORT)));

        io.activej.eventloop.Eventloop eventloop = injector.getInstance(io.activej.eventloop.Eventloop.class);

        var router = io.activej.http.RoutingServlet.builder(eventloop)
                .with(GET, "/health", request ->
                        io.activej.http.HttpResponse.ok200().withPlainText("OK").toPromise())
                .with(GET, "/api/v1/lifecycle/phases", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"phases\":[\"intent\",\"shape\",\"generate\",\"run\",\"observe\",\"evolve\",\"learn\",\"validate\"]}")
                                .toPromise())
                .with(POST, "/api/v1/lifecycle/advance", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"status\":\"not_implemented\"}")
                                .toPromise())
                .build();

        logger.info("Creating YAPPC Lifecycle HTTP server on port {}", port);

        return HttpServer.builder(eventloop, router)
                .withListenPort(port)
                .build();
    }

    @Override
    protected void onApplicationStarted() {
        logger.info("=== YAPPC Lifecycle Service v{} started on port {} ===",
                getServiceVersion(),
                System.getProperty("yappc.lifecycle.port", String.valueOf(DEFAULT_PORT)));
    }

    public static void main(String[] args) throws Exception {
        new YappcLifecycleService().launch(args);
    }
}
