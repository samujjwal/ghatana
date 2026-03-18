/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.DELETE;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.approval.ApprovalController;
import com.ghatana.yappc.api.architecture.ArchitectureController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for Architecture and Approval APIs.
 *
 * <ul>
 *   <li>/api/architecture/* - impact analysis, dependencies, tech debt, patterns</li>
 *   <li>/api/approvals/* - approval workflow management</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Register architecture analysis and approval workflow routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class ArchitectureApprovalRoutes {

  private ArchitectureApprovalRoutes() {}

  /**
   * Registers all architecture and approval API routes on the given builder.
   *
   * @param builder   the routing servlet builder
   * @param archCtrl  architecture controller
   * @param apprCtrl  approval controller
   */
  public static void register(
      RoutingServlet.Builder builder,
      ArchitectureController archCtrl,
      ApprovalController apprCtrl) {

    builder
        // Architecture
        .with(POST, "/api/architecture/impact",       archCtrl::analyzeImpact)
        .with(GET,  "/api/architecture/dependencies", archCtrl::getDependencies)
        .with(GET,  "/api/architecture/tech-debt",    archCtrl::getTechDebt)
        .with(GET,  "/api/architecture/patterns",     archCtrl::getPatternWarnings)
        .with(POST, "/api/architecture/simulate",     archCtrl::simulateChange)

        // Approvals
        .with(POST,   "/api/approvals",                  apprCtrl::createWorkflow)
        .with(GET,    "/api/approvals/pending",           apprCtrl::getPendingApprovals)
        .with(GET,    "/api/approvals/:id",
            request -> {
              String id = request.getPathParameter("id");
              return apprCtrl.getWorkflow(request, id);
            })
        .with(POST,   "/api/approvals/:id/decision",
            request -> {
              String id = request.getPathParameter("id");
              return apprCtrl.submitDecision(request, id);
            })
        .with(DELETE, "/api/approvals/:id",
            request -> {
              String id = request.getPathParameter("id");
              return apprCtrl.cancelWorkflow(request, id);
            })
        .with(GET,    "/api/approvals/:id/history",
            request -> {
              String id = request.getPathParameter("id");
              return apprCtrl.getHistory(request, id);
            });
  }
}
