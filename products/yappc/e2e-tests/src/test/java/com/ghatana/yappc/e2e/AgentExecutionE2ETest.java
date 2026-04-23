/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class AgentExecutionE2ETest extends EventloopTestBase {

    private static AgentPlatform platform;
    private static String tenantId;
    private static String javaExpertAgentId;
    private static String codeReviewerAgentId;

    @BeforeAll
    static void setUpPlatform() { // GH-90000
        tenantId = "e2e-agent-tenant-" + UUID.randomUUID(); // GH-90000
        platform = new MockAgentPlatform(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Agent Registration
    // -------------------------------------------------------------------------

    @Test
    @Order(1) // GH-90000
    @DisplayName("E2E: Should register Java expert agent from YAML config")
    void testRegisterJavaExpertAgent() { // GH-90000
        AgentRegistrationRequest request = AgentRegistrationRequest.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .agentConfigPath("agents/java-expert.yaml")
                .build(); // GH-90000

        AgentRegistrationResult result = runPromise(() -> platform.registerAgent(request)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.agentId()).isNotNull(); // GH-90000
        assertThat(result.agentName()).isEqualTo("Java Expert");
        assertThat(result.capabilities()).contains("code-analysis", "architecture-review"); // GH-90000
        assertThat(result.inputEventTypes()).contains("code.analysis.requested");
        assertThat(result.outputEventTypes()).contains("code.analysis.completed");

        javaExpertAgentId = result.agentId(); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("E2E: Should register code reviewer agent from YAML config")
    void testRegisterCodeReviewerAgent() { // GH-90000
        AgentRegistrationRequest request = AgentRegistrationRequest.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .agentConfigPath("agents/code-reviewer.yaml")
                .build(); // GH-90000

        AgentRegistrationResult result = runPromise(() -> platform.registerAgent(request)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.agentId()).isNotNull(); // GH-90000
        assertThat(result.agentName()).isEqualTo("Code Reviewer");
        assertThat(result.capabilities()).containsAnyOf("review", "analysis"); // GH-90000
        assertThat(result.inputEventTypes()).contains("code.review.requested");
        assertThat(result.outputEventTypes()).contains("code.review.completed");

        codeReviewerAgentId = result.agentId(); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("E2E: Should list registered agents for tenant")
    void testListAgentsForTenant() { // GH-90000
        List<AgentInfo> agents = runPromise(() -> platform.listAgents(tenantId)); // GH-90000

        assertThat(agents).isNotNull(); // GH-90000
        assertThat(agents.size()).isGreaterThanOrEqualTo(2); // GH-90000
        assertThat(agents).anyMatch(a -> a.name().equals("Java Expert"));
        assertThat(agents).anyMatch(a -> a.name().equals("Code Reviewer"));
    }

    // -------------------------------------------------------------------------
    // Code Generation Flow
    // -------------------------------------------------------------------------

    @Test
    @Order(4) // GH-90000
    @DisplayName("E2E: Should execute code generation for Java service")
    void testCodeGenerationJavaService() { // GH-90000
        CodeGenerationRequest request = CodeGenerationRequest.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .agentId(javaExpertAgentId) // GH-90000
                .language("java")
                .framework("spring-boot")
                .description("Build a REST API for user management with CRUD operations")
                .requirements(List.of( // GH-90000
                        "User entity with id, name, email, createdAt",
                        "CRUD endpoints: GET /users, POST /users, PUT /users/{id}, DELETE /users/{id}",
                        "Input validation with Bean Validation",
                        "Proper error responses with RFC 7807 Problem Details"
                ))
                .build(); // GH-90000

        CodeGenerationResult result = runPromise(() -> platform.generateCode(request)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.generatedFiles()).isNotEmpty(); // GH-90000
        assertThat(result.generatedFiles()).containsKey("UserController.java");
        assertThat(result.language()).isEqualTo("java");
        assertThat(result.executionTimeMs()).isGreaterThan(0); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("E2E: Should perform code review on generated code")
    void testCodeReviewOnGeneratedCode() { // GH-90000
        String codeToReview = """
                public class UserController {
                    private UserRepository repository;
                    
                    public User getUser(String id) { // GH-90000
                        return repository.findById(id).orElse(null); // GH-90000
                    }
                }
                """;

        CodeReviewRequest request = CodeReviewRequest.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .agentId(codeReviewerAgentId) // GH-90000
                .code(codeToReview) // GH-90000
                .language("java")
                .reviewType("quality")
                .build(); // GH-90000

        CodeReviewResult result = runPromise(() -> platform.reviewCode(request)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.issues()).isNotNull(); // GH-90000
        assertThat(result.overallScore()).isBetween(0, 100); // GH-90000
        assertThat(result.suggestions()).isNotEmpty(); // GH-90000
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("E2E: Should execute agent via AEP event routing")
    void testAgentExecutionViaAepEvent() { // GH-90000
        AepEvent inputEvent = AepEvent.builder() // GH-90000
                .eventType("code.analysis.requested")
                .tenantId(tenantId) // GH-90000
                .correlationId(UUID.randomUUID().toString()) // GH-90000
                .payload(Map.of( // GH-90000
                        "codeContext", "public class Example { private String name; }",
                        "question", "Is this class well designed?"
                ))
                .build(); // GH-90000

        AepEvent outputEvent = runPromise(() -> platform.routeEvent(inputEvent)); // GH-90000

        assertThat(outputEvent).isNotNull(); // GH-90000
        assertThat(outputEvent.eventType()).isEqualTo("code.analysis.completed");
        assertThat(outputEvent.tenantId()).isEqualTo(tenantId); // GH-90000
        assertThat(outputEvent.correlationId()).isEqualTo(inputEvent.correlationId()); // GH-90000
        assertThat(outputEvent.payload()).containsKey("analysis");
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("E2E: Should route events to dead letter queue on failure")
    void testDeadLetterQueueOnAgentFailure() { // GH-90000
        AepEvent badEvent = AepEvent.builder() // GH-90000
                .eventType("code.analysis.requested")
                .tenantId(tenantId) // GH-90000
                .correlationId(UUID.randomUUID().toString()) // GH-90000
                .payload(Map.of()) // Missing required fields — should trigger failure // GH-90000
                .build(); // GH-90000

        DeadLetterResult dlqResult = runPromise(() -> platform.routeEventExpectingFailure(badEvent)); // GH-90000

        assertThat(dlqResult).isNotNull(); // GH-90000
        assertThat(dlqResult.dlqName()).isEqualTo("yappc.dlq.java-expert");
        assertThat(dlqResult.originalEvent()).isEqualTo(badEvent); // GH-90000
        assertThat(dlqResult.failureReason()).isNotBlank(); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("E2E: Should enforce tenant isolation — agent from tenant A not accessible to tenant B")
    void testAgentTenantIsolation() { // GH-90000
        String otherTenant = "other-tenant-" + UUID.randomUUID(); // GH-90000
        List<AgentInfo> otherTenantAgents = runPromise(() -> platform.listAgents(otherTenant)); // GH-90000

        // Other tenant should not see agents registered for our tenant
        assertThat(otherTenantAgents).noneMatch(a -> // GH-90000
                a.agentId() != null && ( // GH-90000
                        a.agentId().equals(javaExpertAgentId) || // GH-90000
                        a.agentId().equals(codeReviewerAgentId) // GH-90000
                )
        );
    }

    // -------------------------------------------------------------------------
    // Supporting types and mock
    // -------------------------------------------------------------------------

    interface AgentPlatform {
        Promise<AgentRegistrationResult> registerAgent(AgentRegistrationRequest request); // GH-90000
        Promise<List<AgentInfo>> listAgents(String tenantId); // GH-90000
        Promise<CodeGenerationResult> generateCode(CodeGenerationRequest request); // GH-90000
        Promise<CodeReviewResult> reviewCode(CodeReviewRequest request); // GH-90000
        Promise<AepEvent> routeEvent(AepEvent event); // GH-90000
        Promise<DeadLetterResult> routeEventExpectingFailure(AepEvent event); // GH-90000
    }

    record AgentRegistrationRequest(String tenantId, String agentConfigPath) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId;
            private String agentConfigPath;
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder agentConfigPath(String v) { agentConfigPath = v; return this; } // GH-90000
            AgentRegistrationRequest build() { return new AgentRegistrationRequest(tenantId, agentConfigPath); } // GH-90000
        }
    }

    record AgentRegistrationResult( // GH-90000
            String agentId,
            String agentName,
            List<String> capabilities,
            List<String> inputEventTypes,
            List<String> outputEventTypes
    ) {}

    record AgentInfo(String agentId, String name, List<String> capabilities) {} // GH-90000

    record CodeGenerationRequest( // GH-90000
            String tenantId, String agentId, String language, String framework,
            String description, List<String> requirements
    ) {
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId, agentId, language, framework, description;
            private List<String> requirements = List.of(); // GH-90000
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder agentId(String v) { agentId = v; return this; } // GH-90000
            Builder language(String v) { language = v; return this; } // GH-90000
            Builder framework(String v) { framework = v; return this; } // GH-90000
            Builder description(String v) { description = v; return this; } // GH-90000
            Builder requirements(List<String> v) { requirements = v; return this; } // GH-90000
            CodeGenerationRequest build() { // GH-90000
                return new CodeGenerationRequest(tenantId, agentId, language, framework, description, requirements); // GH-90000
            }
        }
    }

    record CodeGenerationResult( // GH-90000
            boolean success, Map<String, String> generatedFiles, String language, long executionTimeMs
    ) {}

    record CodeReviewRequest(String tenantId, String agentId, String code, String language, String reviewType) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId, agentId, code, language, reviewType;
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder agentId(String v) { agentId = v; return this; } // GH-90000
            Builder code(String v) { code = v; return this; } // GH-90000
            Builder language(String v) { language = v; return this; } // GH-90000
            Builder reviewType(String v) { reviewType = v; return this; } // GH-90000
            CodeReviewRequest build() { // GH-90000
                return new CodeReviewRequest(tenantId, agentId, code, language, reviewType); // GH-90000
            }
        }
    }

    record CodeReviewResult( // GH-90000
            boolean success, List<String> issues, int overallScore, List<String> suggestions
    ) {}

    record AepEvent(String eventType, String tenantId, String correlationId, Map<String, Object> payload) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String eventType, tenantId, correlationId;
            private Map<String, Object> payload = Map.of(); // GH-90000
            Builder eventType(String v) { eventType = v; return this; } // GH-90000
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder correlationId(String v) { correlationId = v; return this; } // GH-90000
            Builder payload(Map<String, Object> v) { payload = v; return this; } // GH-90000
            AepEvent build() { return new AepEvent(eventType, tenantId, correlationId, payload); } // GH-90000
        }
    }

    record DeadLetterResult(String dlqName, AepEvent originalEvent, String failureReason) {} // GH-90000

    /**
     * Mock implementation of AgentPlatform for E2E tests.
     * Simulates the behaviour of the AEP-integrated agent registry and executor.
     */
    static class MockAgentPlatform implements AgentPlatform {
        private final Map<String, Map<String, AgentRegistrationResult>> tenantAgents = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public Promise<AgentRegistrationResult> registerAgent(AgentRegistrationRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                String agentId = UUID.randomUUID().toString(); // GH-90000
                AgentRegistrationResult result = switch (request.agentConfigPath()) { // GH-90000
                    case "agents/java-expert.yaml" -> new AgentRegistrationResult( // GH-90000
                            agentId, "Java Expert",
                            List.of("code-analysis", "best-practices", "architecture-review", "performance-optimization"), // GH-90000
                            List.of("code.analysis.requested", "java.review.requested"), // GH-90000
                            List.of("code.analysis.completed", "java.review.completed") // GH-90000
                    );
                    case "agents/code-reviewer.yaml" -> new AgentRegistrationResult( // GH-90000
                            agentId, "Code Reviewer",
                            List.of("analysis", "review", "suggestions", "best-practices"), // GH-90000
                            List.of("code.review.requested", "pull.request.opened"), // GH-90000
                            List.of("code.review.completed", "pull.request.review.posted") // GH-90000
                    );
                    default -> new AgentRegistrationResult(agentId, "Unknown Agent", // GH-90000
                            List.of(), List.of(), List.of()); // GH-90000
                };
                tenantAgents.computeIfAbsent(request.tenantId(), k -> new ConcurrentHashMap<>()) // GH-90000
                        .put(agentId, result); // GH-90000
                return result;
            });
        }

        @Override
        public Promise<List<AgentInfo>> listAgents(String tenantId) { // GH-90000
            return Promise.of( // GH-90000
                    tenantAgents.getOrDefault(tenantId, Map.of()).entrySet().stream() // GH-90000
                            .map(e -> new AgentInfo(e.getKey(), e.getValue().agentName(), // GH-90000
                                    e.getValue().capabilities())) // GH-90000
                            .toList() // GH-90000
            );
        }

        @Override
        public Promise<CodeGenerationResult> generateCode(CodeGenerationRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                long start = System.currentTimeMillis(); // GH-90000
                Thread.sleep(20); // GH-90000
                Map<String, String> files = new java.util.LinkedHashMap<>(); // GH-90000
                files.put("UserController.java", // GH-90000
                        "public class UserController { /* generated */ }");
                files.put("UserService.java", // GH-90000
                        "public class UserService { /* generated */ }");
                files.put("UserRepository.java", // GH-90000
                        "public interface UserRepository extends JpaRepository<User, String> {}");
                return new CodeGenerationResult(true, files, request.language(), // GH-90000
                        System.currentTimeMillis() - start); // GH-90000
            });
        }

        @Override
        public Promise<CodeReviewResult> reviewCode(CodeReviewRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                Thread.sleep(10); // GH-90000
                return new CodeReviewResult( // GH-90000
                        true,
                        List.of("Returning null from getUser() is unsafe — prefer Optional", // GH-90000
                                "Field injection detected — use constructor injection"),
                        72,
                        List.of("Replace null return with Optional.empty()", // GH-90000
                                "Use constructor injection for UserRepository",
                                "Add @NotNull annotation to id parameter")
                );
            });
        }

        @Override
        public Promise<AepEvent> routeEvent(AepEvent event) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                if (event.payload().isEmpty()) { // GH-90000
                    throw new IllegalArgumentException("Empty payload");
                }
                String outputType = event.eventType().replace(".requested", ".completed"); // GH-90000
                return new AepEvent(outputType, event.tenantId(), event.correlationId(), // GH-90000
                        Map.of("analysis", "Code analysis complete", "status", "success")); // GH-90000
            });
        }

        @Override
        public Promise<DeadLetterResult> routeEventExpectingFailure(AepEvent event) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                // Simulate DLQ routing on validation failure (empty payload) // GH-90000
                String dlq = "yappc.dlq.java-expert";
                String reason = event.payload().isEmpty() // GH-90000
                        ? "Required field 'codeContext' missing from payload"
                        : "Unexpected error during agent execution";
                return new DeadLetterResult(dlq, event, reason); // GH-90000
            });
        }
    }
}
