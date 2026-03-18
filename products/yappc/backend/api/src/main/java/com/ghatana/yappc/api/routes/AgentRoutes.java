/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.DELETE;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.controller.WorkflowAgentController;
import com.ghatana.yappc.api.codegen.CodeGenerationController;
import com.ghatana.yappc.api.history.AgentHistoryController;
import com.ghatana.yappc.api.policy.LearnedPolicyController;
import com.ghatana.yappc.api.testing.TestGenerationController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for Agent, Code Generation, Test Generation, History, and Policy APIs.
 *
 * <ul>
 *   <li>/api/agents/*        - workflow agent execution and catalog</li>
 *   <li>/api/v1/codegen/*    - code generation from OpenAPI/GraphQL/schema</li>
 *   <li>/api/v1/testing/*    - test generation and coverage analysis</li>
 *   <li>/api/v1/agents/{agentId}/history  - agent history and decision rationale</li>
 *   <li>/api/v1/agents/{agentId}/policies - learned policies</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Register agent execution, code/test generation, history, and policy routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class AgentRoutes {

  private AgentRoutes() {}

  /**
   * Registers all agent, codegen, testgen, history, and policy routes on the given builder.
   *
   * @param builder         the routing servlet builder
   * @param agentCtrl       workflow agent controller
   * @param codegenCtrl     code generation controller
   * @param testgenCtrl     test generation controller
   * @param historyCtrl     agent history controller
   * @param policyCtrl      learned policy controller
   */
  public static void register(
      RoutingServlet.Builder builder,
      WorkflowAgentController agentCtrl,
      CodeGenerationController codegenCtrl,
      TestGenerationController testgenCtrl,
      AgentHistoryController historyCtrl,
      LearnedPolicyController policyCtrl) {

    builder
        // Workflow Agent Execution
        .with(POST,   "/api/agents/execute",             agentCtrl::executeAgent)
        .with(POST,   "/api/agents/execute/batch",       agentCtrl::executeBatch)
        .with(DELETE, "/api/agents/execute/:id",         request -> agentCtrl.cancelExecution(request))
        .with(GET,    "/api/agents/execute/:id",         request -> agentCtrl.getExecutionStatus(request))
        .with(GET,    "/api/agents",                     agentCtrl::listAgents)
        .with(GET,    "/api/agents/role/:role",          request -> agentCtrl.getAgentsByRole(request))
        .with(GET,    "/api/agents/:id/health",          request -> agentCtrl.getAgentHealth(request))

        // Code Generation
        .with(POST, "/api/v1/codegen/from-openapi",  codegenCtrl::generateFromOpenAPI)
        .with(POST, "/api/v1/codegen/from-graphql",  codegenCtrl::generateFromGraphQL)
        .with(POST, "/api/v1/codegen/from-schema",   codegenCtrl::generateFromSchema)
        .with(POST, "/api/v1/codegen/preview",       codegenCtrl::previewCode)

        // Test Generation
        .with(POST, "/api/v1/testing/generate",  testgenCtrl::generateTests)
        .with(POST, "/api/v1/testing/coverage",  testgenCtrl::analyzeCoverage)
        .with(GET,  "/api/v1/testing/templates", testgenCtrl::listTemplates)

        // Agent History & Rationale (Observability 6.4)
        .with(GET, "/api/v1/agents/:agentId/history",
            request -> {
              String aid = request.getPathParameter("agentId");
              return historyCtrl.getHistory(request, aid);
            })
        .with(GET, "/api/v1/agents/:agentId/rationale/:turnId",
            request -> {
              String aid    = request.getPathParameter("agentId");
              String turnId = request.getPathParameter("turnId");
              return historyCtrl.getRationale(request, aid, turnId);
            })

        // Learned Policies (Plan 9.5.4)
        .with(GET, "/api/v1/agents/:agentId/policies",
            request -> {
              String aid = request.getPathParameter("agentId");
              return policyCtrl.getPolicies(request, aid);
            });
  }
}
