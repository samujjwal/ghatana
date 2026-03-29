/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.facade;

import com.ghatana.datacloud.application.WorkflowService;
import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.application.SchemaDiffService;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.Workflow;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain facade aggregating workflow-related application services.
 *
 * <p>Handlers that previously needed to inject {@code WorkflowService},
 * {@code CollectionService}, and {@code SchemaDiffService} separately now
 * inject one {@code WorkflowDomainService} instead.
 *
 * @doc.type class
 * @doc.purpose Domain facade for workflow-related application services
 * @doc.layer application
 * @doc.pattern Facade, Service
 */
public final class WorkflowDomainService {

    private final WorkflowService workflowService;
    private final CollectionService collectionService;
    private final SchemaDiffService schemaDiffService;

    public WorkflowDomainService(
            WorkflowService workflowService,
            CollectionService collectionService,
            SchemaDiffService schemaDiffService) {
        this.workflowService   = Objects.requireNonNull(workflowService, "workflowService");
        this.collectionService = Objects.requireNonNull(collectionService, "collectionService");
        this.schemaDiffService = Objects.requireNonNull(schemaDiffService, "schemaDiffService");
    }

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    public Promise<Workflow> createWorkflow(String tenantId, Workflow workflow, String userId) {
        return workflowService.createWorkflow(tenantId, workflow, userId);
    }

    public Promise<Optional<Workflow>> getWorkflow(String tenantId, UUID workflowId) {
        return workflowService.getWorkflow(tenantId, workflowId);
    }

    public Promise<List<Workflow>> listWorkflows(String tenantId, int offset, int limit) {
        return workflowService.listWorkflows(tenantId, offset, limit);
    }

    public Promise<Workflow> updateWorkflow(String tenantId, UUID workflowId,
                                            Workflow update, String userId) {
        return workflowService.updateWorkflow(tenantId, workflowId, update, userId);
    }

    public Promise<Void> deleteWorkflow(String tenantId, UUID workflowId, String userId) {
        return workflowService.deleteWorkflow(tenantId, workflowId, userId);
    }

    // ── Collection management ─────────────────────────────────────────────────

    public Promise<MetaCollection> createCollection(String tenantId,
                                                     MetaCollection collection, String userId) {
        return collectionService.createCollection(tenantId, collection, userId);
    }

    public Promise<List<MetaCollection>> listCollections(String tenantId) {
        return collectionService.listCollections(tenantId);
    }

    // ── Escape hatches ────────────────────────────────────────────────────────

    public WorkflowService workflows() {
        return workflowService;
    }

    public CollectionService collections() {
        return collectionService;
    }

    public SchemaDiffService schemaDiff() {
        return schemaDiffService;
    }
}
