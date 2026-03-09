package com.ghatana.virtualorg.example;

import com.ghatana.virtualorg.agent.VirtualOrgAgent;
import com.ghatana.virtualorg.agent.roles.SeniorEngineerAgent;
import com.ghatana.virtualorg.factory.VirtualOrgAgentFactory;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.impl.OpenAILLMClient;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.memory.impl.InMemoryAgentMemory;
import com.ghatana.virtualorg.observability.VirtualOrgAgentMetrics;
import com.ghatana.virtualorg.observability.VirtualOrgObservabilityConfig;
import com.ghatana.virtualorg.orchestration.TaskDispatcher;
import com.ghatana.virtualorg.v1.AgentRoleProto;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskRequestProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import com.ghatana.virtualorg.v1.TaskTypeProto;
import com.ghatana.virtualorg.v1.TaskPriorityProto;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.impl.DefaultToolExecutor;
import com.ghatana.virtualorg.tool.impl.SimpleToolRegistry;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghatana.virtualorg.orchestration.TaskQueue;
import com.ghatana.virtualorg.v1.ExecutionOptionsProto;
import java.util.Collections;
import java.time.Instant;
import java.util.UUID;

/**
 * Example launcher demonstrating the Virtual Organization Agent system setup and execution.
 *
 * <p><b>Purpose</b><br>
 * Provides end-to-end example of bootstrapping the virtual organization
 * multi-agent system with all components properly configured. Serves as
 * reference implementation for production deployments and integration testing.
 *
 * <p><b>Architecture Role</b><br>
 * Example/demo application demonstrating:
 * - Application configuration loading (Typesafe Config)
 * - Observability stack initialization (OpenTelemetry + Micrometer)
 * - LLM client setup (OpenAI GPT-4)
 * - Agent factory and lifecycle management
 * - Multi-agent task dispatch and coordination
 * - Graceful shutdown handling
 *
 * <p><b>Components Initialized</b><br>
 * The launcher bootstraps:
 * 1. <b>Configuration</b>: Loads from application.conf with environment overrides
 * 2. <b>Observability</b>: OpenTelemetry tracer + Micrometer registry → OTLP collector
 * 3. <b>ActiveJ Eventloop</b>: Async execution runtime for Promise-based operations
 * 4. <b>LLM Client</b>: OpenAI API client with retry/timeout configuration
 * 5. <b>Agent Memory</b>: In-memory (demo) or PgVector (production)
 * 6. <b>Tool Registry</b>: Git, HTTP, file operations, etc.
 * 7. <b>Agent Factory</b>: Creates agents by role with authority configuration
 * 8. <b>Task Dispatcher</b>: Routes tasks to agents based on role/availability
 *
 * <p><b>Example Workflow Demonstrated</b><br>
 * The launcher executes a sample workflow:
 * 1. Create senior engineer agent with code review authority
 * 2. Submit code review task to dispatcher
 * 3. Agent processes task with LLM reasoning
 * 4. Agent executes tools (git, static analysis)
 * 5. Agent returns decision with reasoning
 * 6. Metrics and traces exported to observability backend
 *
 * <p><b>Environment Variables</b><br>
 * Required configuration (set before running):
 * - <b>LLM_API_KEY</b>: OpenAI API key for GPT-4 access
 * - <b>DATABASE_URL</b>: PostgreSQL connection (optional, defaults to in-memory)
 * - <b>REDIS_HOST</b>: Redis host for distributed caching (optional)
 * - <b>OTLP_ENDPOINT</b>: OpenTelemetry collector URL (default: http://localhost:4317)
 * - <b>LOG_LEVEL</b>: Logging level (default: INFO)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * # Set environment variables
 * export LLM_API_KEY="sk-..."
 * export DATABASE_URL="postgresql://localhost:5432/virtualorg"
 * export OTLP_ENDPOINT="http://localhost:4317"
 * 
 * # Run with Gradle
 * ./gradlew :products:virtual-org:apps:virtual-org-service:run
 * 
 * # Or run JAR directly
 * java -jar build/libs/virtual-org-service-1.0.0.jar
 * 
 * # Expected output:
 * # - "Starting Virtual Organization Agent System..."
 * # - "Initialized observability: tracing=enabled, metrics=enabled"
 * # - "Created agent: role=SENIOR_ENGINEER, id=agent-xxx"
 * # - "Task submitted: task-yyy"
 * # - "Task completed: decision=APPROVED, reasoning=..."
 * # - "System shutdown complete"
 * }</pre>
 *
 * <p><b>Production Deployment</b><br>
 * For production, modify this launcher:
 * - Use externalized configuration (Kubernetes ConfigMap, Vault)
 * - Enable PgVector memory instead of in-memory
 * - Configure Redis for distributed state
 * - Set up health checks and readiness probes
 * - Enable structured JSON logging
 * - Configure autoscaling based on task queue depth
 *
 * <p><b>Thread Safety</b><br>
 * Main thread initializes components sequentially. ActiveJ Eventloop
 * handles async operations after initialization.
 *
 * @see VirtualOrgAgentFactory
 * @see TaskDispatcher
 * @see VirtualOrgObservabilityConfig
 * @see OpenAILLMClient
 * @doc.type class
 * @doc.purpose Example launcher demonstrating system bootstrap
 * @doc.layer product
 * @doc.pattern Main
 */
public class VirtualOrgLauncher {
    private static final Logger logger = LoggerFactory.getLogger(VirtualOrgLauncher.class);

    public static void main(String[] args) {
        logger.info("Starting Virtual Organization Agent System...");

        // Load configuration
        Config config = ConfigFactory.load();
        logger.info("Configuration loaded successfully");

        // Create eventloop
        Eventloop eventloop = Eventloop.builder()
            .withThreadName("virtual-org-main")
            .build();

        // Setup observability
        VirtualOrgObservabilityConfig observabilityConfig = new VirtualOrgObservabilityConfig(
            config.getString("virtual-org.service.name"),
            config.getString("virtual-org.service.version"),
            config.getString("virtual-org.observability.tracing.otlp-endpoint"),
            Collections.emptyMap()
        );
        observabilityConfig.initialize();
        logger.info("Observability configured: tracing and metrics enabled");

        try {
            // Run the example in the eventloop
            eventloop.submit(() -> runExample(config, eventloop, observabilityConfig))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Example failed", error);
                        System.exit(1);
                    } else {
                        logger.info("Example completed successfully");
                        observabilityConfig.shutdown();
                        System.exit(0);
                    }
                });

            // Start eventloop
            eventloop.run();

        } catch (Exception e) {
            logger.error("Fatal error in Virtual Organization Agent System", e);
            observabilityConfig.shutdown();
            System.exit(1);
        }
    }

    @NotNull
    private static Promise<Void> runExample(
        @NotNull Config config,
        @NotNull Eventloop eventloop,
        @NotNull VirtualOrgObservabilityConfig observabilityConfig
    ) {
        logger.info("=".repeat(80));
        logger.info("Virtual Organization Agent - Example Scenario");
        logger.info("=".repeat(80));

        // Example 1: Single Agent Processing
        return runSingleAgentExample(config, eventloop, observabilityConfig)
            .then(() -> {
                logger.info("\n" + "=".repeat(80));
                return Promise.complete();
            })
            // Example 2: Multi-Agent Coordination
            .then(() -> runMultiAgentExample(config, eventloop, observabilityConfig))
            .then(() -> {
                logger.info("\n" + "=".repeat(80));
                logger.info("All examples completed successfully!");
                logger.info("=".repeat(80));
                return Promise.complete();
            });
    }

    /**
     * Example 1: Single agent processing a task
     */
    @NotNull
    private static Promise<Void> runSingleAgentExample(
        @NotNull Config config,
        @NotNull Eventloop eventloop,
        @NotNull VirtualOrgObservabilityConfig observabilityConfig
    ) {
        logger.info("\n>>> Example 1: Single Senior Engineer Agent");
        logger.info("Creating agent and processing a code review task...\n");

        // Initialize LLM client
        LLMClient llmClient = new OpenAILLMClient(
            config.getString("virtual-org.llm.api-key"),
            config.getString("virtual-org.llm.model"),
            (float) config.getDouble("virtual-org.llm.temperature"),
            config.getInt("virtual-org.llm.max-tokens"),
            60,
            eventloop
        );

        // Initialize memory (in-memory for demo)
        AgentMemory memory = new InMemoryAgentMemory(
            eventloop,
            1000, // max long-term entries
            100   // short-term size
        );

        // Initialize tool registry
        SimpleToolRegistry toolRegistry = new SimpleToolRegistry();

        // Initialize tool executor
        ToolExecutor toolExecutor = new DefaultToolExecutor(toolRegistry, eventloop);

        // Initialize metrics
        MetricsCollector metricsCollector = MetricsCollectorFactory.create(observabilityConfig.getMeterRegistry());
        VirtualOrgAgentMetrics metrics = new VirtualOrgAgentMetrics(
            metricsCollector,
            "senior-engineer-1",
            "senior-engineer");

        // Create agent
        String agentId = "senior-engineer-1";
        VirtualOrgAgent agent = new SeniorEngineerAgent(
            agentId,
            com.ghatana.virtualorg.v1.DecisionAuthorityProto.getDefaultInstance(),
            eventloop,
            llmClient,
            memory,
            toolRegistry,
            toolExecutor,
            observabilityConfig.getMeterRegistry(),
            observabilityConfig.getTracer(),
            com.ghatana.virtualorg.v1.LLMConfigProto.getDefaultInstance(),
            com.ghatana.virtualorg.v1.MemoryConfigProto.getDefaultInstance()
        );

        logger.info("Agent created: {}", agentId);

        // Start agent
        return agent.start()
            .then(() -> {
                logger.info("Agent started successfully\n");

                // Create a sample task
                TaskProto task = TaskProto.newBuilder()
                    .setTaskId(UUID.randomUUID().toString())
                    .setTitle("Review authentication module")
                    .setDescription("Review the authentication module implementation for security best practices. " +
                        "Check for:\n" +
                        "1. Input validation\n" +
                        "2. SQL injection prevention\n" +
                        "3. Password hashing\n" +
                        "4. Session management")
                    .setType(TaskTypeProto.TASK_TYPE_CODE_REVIEW)
                    .setPriority(TaskPriorityProto.TASK_PRIORITY_HIGH)
                    .setCreatedAt(com.google.protobuf.Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
                    .setCreatedBy("user-1")
                    .build();

                TaskRequestProto request = TaskRequestProto.newBuilder()
                    .setTask(task)
                    .setOptions(ExecutionOptionsProto.newBuilder().setAsync(false).build())
                    .build();

                logger.info("Submitting task: {}", task.getTitle());
                logger.info("Task type: {}", task.getType());
                logger.info("Priority: {}\n", task.getPriority());

                // Process task
                return agent.processTask(request);
            })
            .then(response -> {
                logger.info("Task completed!");
                logger.info("Result: {}", response.getResult());
                logger.info("Tokens used: {}", response.getMetrics().getTokensUsed());
                logger.info("Success: {}, Duration: {}ms", response.getSuccess(), response.getMetrics().getProcessingTimeMs());

                // Stop agent
                return agent.stop();
            })
            .then(() -> {
                logger.info("Agent stopped\n");
                return Promise.complete();
            });
    }

    /**
     * Example 2: Multi-agent coordination with TaskDispatcher
     */
    @NotNull
    private static Promise<Void> runMultiAgentExample(
        @NotNull Config config,
        @NotNull Eventloop eventloop,
        @NotNull VirtualOrgObservabilityConfig observabilityConfig
    ) {
        logger.info("\n>>> Example 2: Multi-Agent System with Task Dispatcher");
        logger.info("Creating multiple agents and coordinating tasks...\n");

        // Initialize dependencies
        LLMClient llmClient = new OpenAILLMClient(
            config.getString("virtual-org.llm.api-key"),
            config.getString("virtual-org.llm.model"),
            (float) config.getDouble("virtual-org.llm.temperature"),
            config.getInt("virtual-org.llm.max-tokens"),
            60,
            eventloop
        );
        AgentMemory memory = new InMemoryAgentMemory(eventloop, 1000, 100);
        SimpleToolRegistry toolRegistry = new SimpleToolRegistry();
        ToolExecutor toolExecutor = new DefaultToolExecutor(toolRegistry, eventloop);

        // Create agent factory
        VirtualOrgAgentFactory factory = new VirtualOrgAgentFactory(
            eventloop,
            llmClient,
            memory,
            toolRegistry,
            toolExecutor,
            observabilityConfig.getMeterRegistry(),
            observabilityConfig.getTracer()
        );

        // Create task dispatcher
        TaskQueue taskQueue = new TaskQueue(100);
        TaskDispatcher dispatcher = new TaskDispatcher(taskQueue);

        // Create agents
        VirtualOrgAgent agent1 = factory.createAgent(AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER);
        VirtualOrgAgent agent2 = factory.createAgent(AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER);

        dispatcher.registerAgent(agent1);
        dispatcher.registerAgent(agent2);

        logger.info("Registered agents");

        // Start agents
        return Promises.all(agent1.start(), agent2.start())
            .then(() -> {
                logger.info("All agents started\n");

                // Create sample tasks
                TaskProto task1 = createTask(
                    "Implement user registration API",
                    "Create REST API endpoint for user registration with validation",
                    TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION
                );

                TaskProto task2 = createTask(
                    "Fix password reset bug",
                    "Password reset emails are not being sent. Investigate and fix.",
                    TaskTypeProto.TASK_TYPE_BUG_FIX
                );

                logger.info("Dispatching tasks...\n");

                // Dispatch tasks
                Promise<TaskResponseProto> response1 = dispatcher.dispatch(task1);
                Promise<TaskResponseProto> response2 = dispatcher.dispatch(task2);

                return Promises.toList(response1, response2);
            })
            .then(responses -> {
                logger.info("\nAll tasks completed!\n");

                for (int i = 0; i < responses.size(); i++) {
                    TaskResponseProto response = responses.get(i);
                    logger.info("Task {}: {}", i + 1, response.getSuccess());
                    logger.info("  Agent: {}", response.getAgentId());
                    logger.info("  Duration: {}ms", response.getMetrics().getProcessingTimeMs());
                    logger.info("  Tokens: {}\n", response.getMetrics().getTokensUsed());
                }

                // Stop agents
                return Promises.all(agent1.stop(), agent2.stop());
            })
            .then(() -> {
                logger.info("All agents stopped");
                return Promise.complete();
            });
    }

    @NotNull
    private static TaskProto createTask(
        @NotNull String title,
        @NotNull String description,
        @NotNull TaskTypeProto type
    ) {
        return TaskProto.newBuilder()
            .setTaskId(UUID.randomUUID().toString())
            .setTitle(title)
            .setDescription(description)
            .setType(type)
            .setPriority(TaskPriorityProto.TASK_PRIORITY_MEDIUM)
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
            .setCreatedBy("system")
            .build();
    }
}
