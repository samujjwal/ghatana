/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 *
 * Integration test for agent orchestration.
 * Tests the full flow from task submission through agent resolution to result handling.
 */
package com.ghatana.yappc.api.service;

import com.ghatana.agent.framework.AgentResult;
import com.ghatana.agent.framework.TypedAgent;
import com.ghatana.agent.framework.catalog.AgentDefinition;
import com.ghatana.agent.framework.registry.AgentRegistry;
import com.ghatana.yappc.api.config.TestConfig;
import com.ghatana.yappc.core.agents.JavaExpertAgent;
import com.ghatana.yappc.core.agents.CodeReviewerAgent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for agent orchestration.
 *
 * <p><b>Purpose</b><br>
 * Tests the end-to-end agent workflow from task submission through
 * agent resolution, execution, and result handling.
 *
 * @doc.type class
 * @doc.purpose Integration tests for agent orchestration
 * @doc.layer test
 */
@ExtendWith(TestConfig.class)
/**
 * @doc.type class
 * @doc.purpose Handles agent orchestration integration test operations
 * @doc.layer product
 * @doc.pattern Test
 */
public class AgentOrchestrationIntegrationTest extends EventloopTestBase {

    @Inject
    private AgentRegistry registry;

    @Inject
    private JavaExpertAgent javaExpert;

    @Inject
    private CodeReviewerAgent codeReviewer;

    @BeforeEach
    void setUp() {
        // Clear any previous test state
        registry.clearCache();
    }

    @Test
    void testAgentResolutionByCapability() {
        // Given a code-generation capability request
        String capability = "code-generation";
        Map<String, Object> context = Map.of(
            "language", "java",
            "framework", "spring"
        );

        // When resolving agents for the capability
        // Then should return a matching agent
        Optional<AgentDefinition> agent = runPromise(() -> registry.resolveByCapability(capability, context));
        assertTrue(agent.isPresent(), "Should resolve an agent for code-generation");
        assertEquals("agent.yappc.java-expert", agent.get().getId());
    }

    @Test
    void testAgentExecutionFlow() {
        // Given a task for the Java expert agent
        String task = "Generate a REST controller for user management";
        Map<String, Object> inputs = Map.of(
            "entity", "User",
            "operations", new String[]{"create", "read", "update", "delete"}
        );

        // When executing the task
        // Then should return a successful result
        AgentResult agentResult = runPromise(() -> javaExpert.execute(task, inputs));
        assertNotNull(agentResult, "Should return a result");
        assertTrue(agentResult.isSuccess(), "Execution should succeed");
        assertNotNull(agentResult.getOutput(), "Should have output");
    }

    @Test
    void testMultiAgentOrchestration() {
        // Given a workflow requiring multiple agents
        // 1. Java expert generates code
        // 2. Code reviewer reviews it

        // When executing the workflow
        AgentResult generated = runPromise(() -> javaExpert.execute(
            "Generate service class",
            Map.of("name", "UserService")
        ));
        assertTrue(generated.isSuccess());

        // Then the reviewer should be able to review the output
        AgentResult review = runPromise(() -> codeReviewer.execute(
            "Review the generated code",
            Map.of(
                "code", generated.getOutput(),
                "criteria", new String[]{"security", "performance", "readability"}
            )
        ));
        assertNotNull(review);
        assertTrue(review.isSuccess());
    }

    @Test
    void testAgentFailureAndRecovery() {
        // Given an invalid task that will fail
        String invalidTask = "";
        Map<String, Object> emptyInputs = Map.of();

        // When executing
        // Then should handle failure gracefully
        AgentResult agentResult = runPromise(() -> javaExpert.execute(invalidTask, emptyInputs));
        assertNotNull(agentResult);
        // Should have error information without throwing
        assertNotNull(agentResult.getExplanation());
    }

    @Test
    void testAgentHierarchicalRouting() {
        // Given a task that should be routed to a worker agent
        // Through the systems-architect -> java-expert -> java-class-writer chain

        Map<String, Object> task = Map.of(
            "type", "generate-class",
            "specification", "User entity with JPA annotations"
        );

        // When routing through hierarchy
        // Then should find appropriate worker
        Optional<TypedAgent> worker = runPromise(() -> registry.routeToWorker("code-generation", task));
        assertTrue(worker.isPresent(), "Should route to a worker agent");
    }

    @Test
    void testAgentCatalogIntegration() {
        // Given the catalog should be loaded
        // Then catalog should be available
        assertTrue(runPromise(() -> registry.isCatalogLoaded()), "Agent catalog should be loaded");
    }

    @Test
    void testAgentCapabilitiesTaxonomy() {
        // Given a request for cloud-security capability
        String capability = "cloud-resource-discovery";

        // When looking up the capability
        // Then should exist in taxonomy
        assertTrue(runPromise(() -> registry.hasCapability(capability)), "Should have cloud-security capability");
    }
}
