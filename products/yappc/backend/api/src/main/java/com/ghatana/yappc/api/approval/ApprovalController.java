/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval;

import com.ghatana.yappc.api.approval.dto.CreateWorkflowRequest;
import com.ghatana.yappc.api.approval.dto.SubmitDecisionRequest;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API Controller for Approval Workflow operations.
 *
 * <p>Delegates all business logic to {@link ApprovalService}.
 *
 * @doc.type class
 * @doc.purpose Approval workflow REST API
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ApprovalController {

  private static final Logger logger = LoggerFactory.getLogger(ApprovalController.class);

  private final ApprovalService approvalService;

  public ApprovalController(ApprovalService approvalService) {
    this.approvalService = approvalService;
  }

  /** POST /api/approvals — create a new approval workflow. */
  public Promise<HttpResponse> createWorkflow(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(ctx ->
            JsonUtils.parseBody(request, CreateWorkflowRequest.class)
                .map(req -> {
                  logger.info("Creating approval workflow for tenant={} user={}",
                      ctx.tenantId(), ctx.userId());
                  return ApiResponse.created(approvalService.create(ctx.tenantId(), ctx.userId(), req));
                }))
        .then(Promise::of, e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** GET /api/approvals/pending — get workflows pending the current user's decision. */
  public Promise<HttpResponse> getPendingApprovals(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .map(ctx -> {
          logger.debug("Fetching pending approvals for user={} tenant={}",
              ctx.userId(), ctx.tenantId());
          return ApiResponse.ok(approvalService.getPending(ctx.tenantId(), ctx.userId()));
        })
        .then(Promise::of, e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** GET /api/approvals/:id — get workflow by ID. */
  public Promise<HttpResponse> getWorkflow(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .map(ctx -> {
          logger.debug("Fetching approval workflow id={} tenant={}", id, ctx.tenantId());
          return ApiResponse.ok(approvalService.get(ctx.tenantId(), id));
        })
        .then(Promise::of, e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** POST /api/approvals/:id/decision — submit an approve/reject decision. */
  public Promise<HttpResponse> submitDecision(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(ctx ->
            JsonUtils.parseBody(request, SubmitDecisionRequest.class)
                .map(req -> {
                  logger.info("Decision submitted for workflow={} by user={} decision={}",
                      id, ctx.userId(), req.decision());
                  return ApiResponse.ok(
                      approvalService.submitDecision(ctx.tenantId(), ctx.userId(), id, req));
                }))
        .then(Promise::of, e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** DELETE /api/approvals/:id — cancel a workflow (initiator only). */
  public Promise<HttpResponse> cancelWorkflow(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .map(ctx -> {
          logger.info("Cancelling approval workflow id={} by user={}", id, ctx.userId());
          return ApiResponse.ok(approvalService.cancel(ctx.tenantId(), ctx.userId(), id));
        })
        .then(Promise::of, e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** GET /api/approvals/:id/history — get full decision history for a workflow. */
  public Promise<HttpResponse> getHistory(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .map(ctx -> {
          logger.debug("Fetching history for workflow={} tenant={}", id, ctx.tenantId());
          return ApiResponse.ok(approvalService.getHistory(ctx.tenantId(), id));
        })
        .then(Promise::of, e -> Promise.of(ApiResponse.fromException(e)));
  }
}
