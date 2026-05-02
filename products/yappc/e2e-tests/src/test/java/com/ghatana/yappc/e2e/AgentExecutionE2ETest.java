/*
 * Copyright (c) 2026 Ghatana 
 */
package com.ghatana.yappc.e2e;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for agent execution flows: agent creation, code generation, and event routing.
 *
 * @doc.type class
 * @doc.purpose E2E test coverage for YAPPC agent execution and code generation workflows
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("Agent Execution E2E Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) 
class AgentExecutionE2ETest extends EventloopTestBase {

    private static AgentPlatform platform;
    private static String tenantId;
    private static String javaExpertAgentId;
    private static String codeReviewerAgentId;

    @BeforeAll
    static void setUpPlatform() { 
        tenantId = "e2e-agent-tenant-" + UUID.randomUUID(); 
        platform = new MockAgentPlatform(); 
    }

    // -------------------------------------------------------------------------
    // Agent Registration
    // -------------------------------------------------------------------------

    @Test
    @Order(1) 
    @DisplayName("E2E: Should register Java expert agent from YAML config")
    void testRegisterJavaExpertAgent() { 
        AgentRegistrationRequest request = AgentRegistrationRequest.builder() 
                .tenantId(tenantId) 
                .agentConfigPath("agents/java-expert.yaml")
                .build(); 

        AgentRegistrationResult result = runPromise(() -> platform.registerAgent(request)); 

        assertThat(result).isNotNull(); 
        assertThat(result.agentId()).isNotNull(); 
        assertThat(result.agentName()).isEqualTo("Java Expert");
        assertThat(result.capabilities()).contains("code-analysis", "architecture-review"); 
        assertThat(result.inputEventTypes()).contains("code.analysis.requested");
        assertThat(result.outputEventTypes()).contains("code.analysis.completed");

        javaExpertAgentId = result.agentId(); 
    }

    @Test
    @Order(2) 
    @DisplayName("E2E: Should register code reviewer agent from YAML config")
    void testRegisterCodeReviewerAgent() { 
        AgentRegistrationRequest request = AgentRegistrationRequest.builder() 
                .tenantId(tenantId) 
                .agentConfigPath("agents/code-reviewer.yaml")
                .build(); 

        AgentRegistrationResult result = runPromise(() -> platform.registerAgent(request)); 

        assertThat(result).isNotNull(); 
        assertThat(result.agentId()).isNotNull(); 
        assertThat(result.agentName()).isEqualTo("Code Reviewer");
        assertThat(result.capabilities()).containsAnyOf("review", "analysis"); 
        assertThat(result.inputEventTypes()).contains("code.review.requested");
        assertThat(result.outputEventTypes()).contains("code.review.completed");

        codeReviewerAgentId = result.agentId(); 
    }

    @Test
    @Order(3) 
    @DisplayName("E2E: Should list registered agents for tenant")
    void testListAgentsForTenant() { 
        List<AgentInfo> agents = runPromise(() -> platform.listAgents(tenantId)); 

        assertThat(agents).isNotNull(); 
        assertThat(agents.size()).isGreaterThanOrEqualTo(2); 
        assertThat(agents).anyMatch(a -> a.name().equals("Java Expert"));
        assertThat(agents).anyMatch(a -> a.name().equals("Code Reviewer"));
    }

    // -------------------------------------------------------------------------
    // Code Generation Flow
    // -------------------------------------------------------------------------

    @Test
    @Order(4) 
    @DisplayName("E2E: Should execute code generation for Java service")
    void testCodeGenerationJavaService() { 
        CodeGenerationRequest request = CodeGenerationRequest.builder() 
                .tenantId(tenantId) 
                .agentId(javaExpertAgentId) 
                .language("java")
                .framework("spring-boot")
                .description("Build a REST API for user management with CRUD operations")
                .requirements(List.of( 
                        "User entity with id, name, email, createdAt",
                        "CRUD endpoints: GET /users, POST /users, PUT /users/{id}, DELETE /users/{id}",
                        "Input validation with Bean Validation",
                        "Proper error responses with RFC 7807 Problem Details"
                ))
                .build(); 

        CodeGenerationResult result = runPromise(() -> platform.generateCode(request)); 

        assertThat(result).isNotNull(); 
        assertThat(result.success()).isTrue(); 
        assertThat(result.generatedFiles()).isNotEmpty(); 
        assertThat(result.generatedFiles()).containsKey("UserController.java");
        assertThat(result.language()).isEqualTo("java");
        assertThat(result.executionTimeMs()).isGreaterThan(0); 
    }

    @Test
    @Order(5) 
    @DisplayName("E2E: Should perform code review on generated code")
    void testCodeReviewOnGeneratedCode() { 
        String codeToReview = """
                public class UserController {
                    private UserRepository repository;
                    
                    public User getUser(String id) { 
                        return repository.findById(id).orElse(null); 
                    }
                }
                """;

        CodeReviewRequest request = CodeReviewRequest.builder() 
                .tenantId(tenantId) 
                .agentId(codeReviewerAgentId) 
                .code(codeToReview) 
                .language("java")
                .reviewType("quality")
                .build(); 

        CodeReviewResult result = runPromise(() -> platform.reviewCode(request)); 

        assertThat(result).isNotNull(); 
        assertThat(result.success()).isTrue(); 
        assertThat(result.issues()).isNotNull(); 
        assertThat(result.overallScore()).isBetween(0, 100); 
        assertThat(result.suggestions()).isNotEmpty(); 
    }

    @Test
    @Order(6) 
    @DisplayName("E2E: Should execute agent via AEP event routing")
    void testAgentExecutionViaAepEvent() { 
        AepEvent inputEvent = AepEvent.builder() 
                .eventType("code.analysis.requested")
                .tenantId(tenantId) 
                .correlationId(UUID.randomUUID().toString()) 
                .payload(Map.of( 
                        "codeContext", "public class Example { private String name; }",
                        "question", "Is this class well designed?"
                ))
                .build(); 

        AepEvent outputEvent = runPromise(() -> platform.routeEvent(inputEvent)); 

        assertThat(outputEvent).isNotNull(); 
        assertThat(outputEvent.eventType()).isEqualTo("code.analysis.completed");
        assertThat(outputEvent.tenantId()).isEqualTo(tenantId); 
        assertThat(outputEvent.correlationId()).isEqualTo(inputEvent.correlationId()); 
        assertThat(outputEvent.payload()).containsKey("analysis");
    }

    @Test
    @Order(7) 
    @DisplayName("E2E: Should route events to dead letter queue on failure")
    void testDeadLetterQueueOnAgentFailure() { 
        AepEvent badEvent = AepEvent.builder() 
                .eventType("code.analysis.requested")
                .tenantId(tenantId) 
                .correlationId(UUID.randomUUID().toString()) 
                .payload(Map.of()) // Missing required fields — should trigger failure 
                .build(); 

        DeadLetterResult dlqResult = runPromise(() -> platform.routeEventExpectingFailure(badEvent)); 

        assertThat(dlqResult).isNotNull(); 
        assertThat(dlqResult.dlqName()).isEqualTo("yappc.dlq.java-expert");
        assertThat(dlqResult.originalEvent()).isEqualTo(badEvent); 
        assertThat(dlqResult.failureReason()).isNotBlank(); 
    }

    @Test
    @Order(8) 
    @DisplayName("E2E: Should enforce tenant isolation — agent from tenant A not accessible to tenant B")
    void testAgentTenantIsolation() { 
        String otherTenant = "other-tenant-" + UUID.randomUUID(); 
        List<AgentInfo> otherTenantAgents = runPromise(() -> platform.listAgents(otherTenant)); 

        // Other tenant should not see agents registered for our tenant
        assertThat(otherTenantAgents).noneMatch(a -> 
                a.agentId() != null && ( 
                        a.agentId().equals(javaExpertAgentId) || 
                        a.agentId().equals(codeReviewerAgentId) 
                )
        );
    }

    // -------------------------------------------------------------------------
    // Supporting types and mock
    // -------------------------------------------------------------------------

    interface AgentPlatform {
        Promise<AgentRegistrationResult> registerAgent(AgentRegistrationRequest request); 
        Promise<List<AgentInfo>> listAgents(String tenantId); 
        Promise<CodeGenerationResult> generateCode(CodeGenerationRequest request); 
        Promise<CodeReviewResult> reviewCode(CodeReviewRequest request); 
        Promise<AepEvent> routeEvent(AepEvent event); 
        Promise<DeadLetterResult> routeEventExpectingFailure(AepEvent event); 
    }

    record AgentRegistrationRequest(String tenantId, String agentConfigPath) { 
        static Builder builder() { return new Builder(); } 
        static class Builder {
            private String tenantId;
            private String agentConfigPath;
            Builder tenantId(String v) { tenantId = v; return this; } 
            Builder agentConfigPath(String v) { agentConfigPath = v; return this; } 
            AgentRegistrationRequest build() { return new AgentRegistrationRequest(tenantId, agentConfigPath); } 
        }
    }

    record AgentRegistrationResult( 
            String agentId,
            String agentName,
            List<String> capabilities,
            List<String> inputEventTypes,
            List<String> outputEventTypes
    ) {}

    record AgentInfo(String agentId, String name, List<String> capabilities) {} 

    record CodeGenerationRequest( 
            String tenantId, String agentId, String language, String framework,
            String description, List<String> requirements
    ) {
        static Builder builder() { return new Builder(); } 
        static class Builder {
            private String tenantId, agentId, language, framework, description;
            private List<String> requirements = List.of(); 
            Builder tenantId(String v) { tenantId = v; return this; } 
            Builder agentId(String v) { agentId = v; return this; } 
            Builder language(String v) { language = v; return this; } 
            Builder framework(String v) { framework = v; return this; } 
            Builder description(String v) { description = v; return this; } 
            Builder requirements(List<String> v) { requirements = v; return this; } 
            CodeGenerationRequest build() { 
                return new CodeGenerationRequest(tenantId, agentId, language, framework, description, requirements); 
            }
        }
    }

    record CodeGenerationResult( 
            boolean success, Map<String, String> generatedFiles, String language, long executionTimeMs
    ) {}

    record CodeReviewRequest(String tenantId, String agentId, String code, String language, String reviewType) { 
        static Builder builder() { return new Builder(); } 
        static class Builder {
            private String tenantId, agentId, code, language, reviewType;
            Builder tenantId(String v) { tenantId = v; return this; } 
            Builder agentId(String v) { agentId = v; return this; } 
            Builder code(String v) { code = v; return this; } 
            Builder language(String v) { language = v; return this; } 
            Builder reviewType(String v) { reviewType = v; return this; } 
            CodeReviewRequest build() { 
                return new CodeReviewRequest(tenantId, agentId, code, language, reviewType); 
            }
        }
    }

    record CodeReviewResult( 
            boolean success, List<String> issues, int overallScore, List<String> suggestions
    ) {}

    record AepEvent(String eventType, String tenantId, String correlationId, Map<String, Object> payload) { 
        static Builder builder() { return new Builder(); } 
        static class Builder {
            private String eventType, tenantId, correlationId;
            private Map<String, Object> payload = Map.of(); 
            Builder eventType(String v) { eventType = v; return this; } 
            Builder tenantId(String v) { tenantId = v; return this; } 
            Builder correlationId(String v) { correlationId = v; return this; } 
            Builder payload(Map<String, Object> v) { payload = v; return this; } 
            AepEvent build() { return new AepEvent(eventType, tenantId, correlationId, payload); } 
        }
    }

    record DeadLetterResult(String dlqName, AepEvent originalEvent, String failureReason) {} 

    /**
     * Mock implementation of AgentPlatform for E2E tests.
     * Simulates the behaviour of the AEP-integrated agent registry and executor.
     */
    static class MockAgentPlatform implements AgentPlatform {
        private final Map<String, Map<String, AgentRegistrationResult>> tenantAgents = new ConcurrentHashMap<>(); 

        @Override
        public Promise<AgentRegistrationResult> registerAgent(AgentRegistrationRequest request) { 
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { 
                String agentId = UUID.randomUUID().toString(); 
                AgentRegistrationResult result = switch (request.agentConfigPath()) { 
                    case "agents/java-expert.yaml" -> new AgentRegistrationResult( 
                            agentId, "Java Expert",
                            List.of("code-analysis", "best-practices", "architecture-review", "performance-optimization"), 
                            List.of("code.analysis.requested", "java.review.requested"), 
                            List.of("code.analysis.completed", "java.review.completed") 
                    );
                    case "agents/code-reviewer.yaml" -> new AgentRegistrationResult( 
                            agentId, "Code Reviewer",
                            List.of("analysis", "review", "suggestions", "best-practices"), 
                            List.of("code.review.requested", "pull.request.opened"), 
                            List.of("code.review.completed", "pull.request.review.posted") 
                    );
                    default -> new AgentRegistrationResult(agentId, "Unknown Agent", 
                            List.of(), List.of(), List.of()); 
                };
                tenantAgents.computeIfAbsent(request.tenantId(), k -> new ConcurrentHashMap<>()) 
                        .put(agentId, result); 
                return result;
            });
        }

        @Override
        public Promise<List<AgentInfo>> listAgents(String tenantId) { 
            return Promise.of( 
                    tenantAgents.getOrDefault(tenantId, Map.of()).entrySet().stream() 
                            .map(e -> new AgentInfo(e.getKey(), e.getValue().agentName(), 
                                    e.getValue().capabilities())) 
                            .toList() 
            );
        }

        @Override
        public Promise<CodeGenerationResult> generateCode(CodeGenerationRequest request) { 
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { 
                long start = System.currentTimeMillis(); 
                Thread.sleep(20); 
                Map<String, String> files = new java.util.LinkedHashMap<>(); 
                files.put("UserController.java", 
                        "public class UserController { /* generated */ }");
                files.put("UserService.java", 
                        "public class UserService { /* generated */ }");
                files.put("UserRepository.java", 
                        "public interface UserRepository extends JpaRepository<User, String> {}");
                return new CodeGenerationResult(true, files, request.language(), 
                        System.currentTimeMillis() - start); 
            });
        }

        @Override
        public Promise<CodeReviewResult> reviewCode(CodeReviewRequest request) { 
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { 
                Thread.sleep(10); 
                return new CodeReviewResult( 
                        true,
                        List.of("Returning null from getUser() is unsafe — prefer Optional", 
                                "Field injection detected — use constructor injection"),
                        72,
                        List.of("Replace null return with Optional.empty()", 
                                "Use constructor injection for UserRepository",
                                "Add @NotNull annotation to id parameter")
                );
            });
        }

        @Override
        public Promise<AepEvent> routeEvent(AepEvent event) { 
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { 
                if (event.payload().isEmpty()) { 
                    throw new IllegalArgumentException("Empty payload");
                }
                String outputType = event.eventType().replace(".requested", ".completed"); 
                return new AepEvent(outputType, event.tenantId(), event.correlationId(), 
                        Map.of("analysis", "Code analysis complete", "status", "success")); 
            });
        }

        @Override
        public Promise<DeadLetterResult> routeEventExpectingFailure(AepEvent event) { 
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { 
                // Simulate DLQ routing on validation failure (empty payload) 
                String dlq = "yappc.dlq.java-expert";
                String reason = event.payload().isEmpty() 
                        ? "Required field 'codeContext' missing from payload"
                        : "Unexpected error during agent execution";
                return new DeadLetterResult(dlq, event, reason); 
            });
        }
    }
}
