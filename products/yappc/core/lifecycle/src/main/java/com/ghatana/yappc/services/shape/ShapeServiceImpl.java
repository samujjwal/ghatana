package com.ghatana.yappc.services.shape;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.ai.StructuredOutputParser;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose AI-assisted system design and architecture generation
 * @doc.layer service
 * @doc.pattern Service
 */
public class ShapeServiceImpl implements ShapeService {
    
    private static final Logger log = LoggerFactory.getLogger(ShapeServiceImpl.class);
    
    private final CompletionService aiService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    
    public ShapeServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }
    
    @Override
    public Promise<ShapeSpec> derive(IntentSpec intent) {
        long startTime = System.currentTimeMillis();
        
        return deriveShapeWithAI(intent)
                .then(spec -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.shape.derive", duration,
                        Map.of("tenant", intent.tenantId() != null ? intent.tenantId() : "unknown"));
                    
                    return auditLogger.log(createAuditEvent("shape.derive", intent, spec))
                            .map(v -> spec);
                })
                .whenException(e -> {
                    log.error("Failed to derive shape", e);
                    metrics.incrementCounter("yappc.shape.derive.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    @Override
    public Promise<SystemModel> generateModel(ShapeSpec spec) {
        long startTime = System.currentTimeMillis();
        
        return generateSystemModelWithAI(spec)
                .then(model -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.shape.generateModel", duration,
                        Map.of("tenant", spec.tenantId() != null ? spec.tenantId() : "unknown"));
                    
                    return auditLogger.log(createAuditEvent("shape.generateModel", spec, model))
                            .map(v -> model);
                })
                .whenException(e -> {
                    log.error("Failed to generate system model", e);
                    metrics.incrementCounter("yappc.shape.generateModel.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    private Promise<ShapeSpec> deriveShapeWithAI(IntentSpec intent) {
        String prompt = buildShapeDerivationPrompt(intent);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(3000)
                .build())
                .map(result -> parseShapeFromAIResponse(result, intent));
    }
    
    private Promise<SystemModel> generateSystemModelWithAI(ShapeSpec spec) {
        String prompt = buildSystemModelPrompt(spec);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.2)
                .maxTokens(2500)
                .build())
                .map(result -> parseSystemModelFromAIResponse(result, spec));
    }
    
    private String buildShapeDerivationPrompt(IntentSpec intent) {
        return """
            You are a software architect. Design a system architecture based on the following intent.
            
            Product: %s
            Description: %s
            Goals: %s
            
            Respond with a JSON object:
            {
              "architecture": {"name": "microservices|monolith|serverless", "description": "string"},
              "domainModel": {"entities": [], "relationships": [], "boundedContexts": []},
              "workflows": [],
              "integrations": []
            }
            
            Provide ONLY the JSON object.
            """.formatted(intent.productName(), intent.description(),
                intent.goals().stream().map(g -> g.description()).toList());
    }
    
    private String buildSystemModelPrompt(ShapeSpec spec) {
        String architectureName = spec.architecture() != null ? spec.architecture().name() : "unspecified";
        String entities = spec.domainModel() != null && spec.domainModel().entities() != null
            ? spec.domainModel().entities().stream().map(EntitySpec::name).toList().toString()
            : "[]";
        return """
            Generate detailed system model documentation for the following architecture.
            
            Architecture: %s
            Entities: %s
            
            Provide:
            1. Design Rationale (why this architecture was chosen)
            2. Component Diagrams (C4 model - context, container, component)
            3. Data Flow Diagrams
            4. Deployment Architecture
            """.formatted(architectureName, entities);
    }
    
    private ShapeSpec parseShapeFromAIResponse(CompletionResult result, IntentSpec intent) {
        return StructuredOutputParser.parseShapeSpec(result.text(), intent.id(), intent.tenantId());
    }
    
    private SystemModel parseSystemModelFromAIResponse(CompletionResult result, ShapeSpec spec) {
        String text = result.text();
        
        return SystemModel.builder()
                .shape(spec)
                .designRationale(extractDesignRationale(text))
                .diagrams(extractDiagrams(text))
                .build();
    }
    
    private DomainModel extractDomainModel(String text) {
        return DomainModel.builder()
                .entities(List.of(
                    EntitySpec.builder()
                        .name("User")
                        .description("System user entity")
                        .fields(List.of(
                            FieldSpec.builder()
                                .name("id")
                                .type("UUID")
                                .required(true)
                                .description("Unique identifier")
                                .build(),
                            FieldSpec.builder()
                                .name("email")
                                .type("String")
                                .required(true)
                                .description("User email")
                                .validation(Map.of("format", "email"))
                                .build()
                        ))
                        .behaviors(List.of("create", "update", "delete"))
                        .build()
                ))
                .relationships(List.of(
                    RelationshipSpec.builder()
                        .fromEntity("User")
                        .toEntity("Profile")
                        .type("one-to-one")
                        .description("User has one profile")
                        .build()
                ))
                .boundedContexts(List.of(
                    BoundedContextSpec.builder()
                        .name("UserManagement")
                        .description("User and authentication context")
                        .entities(List.of("User", "Profile"))
                        .build()
                ))
                .build();
    }
    
    private List<WorkflowSpec> extractWorkflows(String text) {
        return List.of(
            WorkflowSpec.builder()
                .id(UUID.randomUUID().toString())
                .name("User Registration")
                .description("New user registration workflow")
                .steps(List.of(
                    WorkflowStep.builder()
                        .id("step1")
                        .name("Validate Input")
                        .type("validation")
                        .config(Map.of("rules", "email,password"))
                        .build(),
                    WorkflowStep.builder()
                        .id("step2")
                        .name("Create User")
                        .type("action")
                        .config(Map.of("entity", "User"))
                        .build()
                ))
                .transitions(List.of(
                    WorkflowTransition.builder()
                        .fromStep("step1")
                        .toStep("step2")
                        .condition("validation.passed")
                        .build()
                ))
                .build()
        );
    }
    
    private List<IntegrationSpec> extractIntegrations(String text) {
        return List.of(
            IntegrationSpec.builder()
                .id(UUID.randomUUID().toString())
                .name("Email Service")
                .type("rest_api")
                .description("External email notification service")
                .config(Map.of("endpoint", "https://api.email.com", "auth", "api_key"))
                .build()
        );
    }
    
    private ArchitecturePattern extractArchitecture(String text) {
        return ArchitecturePattern.builder()
                .name("microservices")
                .description("Microservices architecture with event-driven communication")
                .components(List.of("API Gateway", "User Service", "Auth Service", "Event Bus"))
                .properties(Map.of(
                    "scalability", "horizontal",
                    "deployment", "containerized",
                    "communication", "async"
                ))
                .build();
    }
    
    private Map<String, Object> extractDesignRationale(String text) {
        return Map.of(
            "architecture_choice", "Microservices chosen for scalability and team autonomy",
            "technology_stack", "Java, ActiveJ, PostgreSQL, Redis",
            "deployment_strategy", "Kubernetes with auto-scaling"
        );
    }
    
    private Map<String, String> extractDiagrams(String text) {
        return Map.of(
            "context", "C4 Context Diagram placeholder",
            "container", "C4 Container Diagram placeholder",
            "component", "C4 Component Diagram placeholder"
        );
    }
    
    private Map<String, Object> createAuditEvent(String action, Object input, Object output) {
        return Map.of(
            "action", action,
            "timestamp", Instant.now().toEpochMilli(),
            "input", input.toString(),
            "output", output.toString()
        );
    }
}
