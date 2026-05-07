/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.store;

import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Migrates tenant-scoped pipeline definitions from fallback in-memory storage into durable storage
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PipelineMigrationUtil {

    private PipelineMigrationUtil() {
    }

    public static Promise<Void> migrateTenant(PipelineRepository source,
                                              PipelineRepository target,
                                              String tenantId) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return source.findByTenantId(tenantId)
            .then(pipelines -> {
                if (pipelines.isEmpty()) {
                    return Promise.complete();
                }

                List<Promise<Void>> writes = new ArrayList<>();
                for (PipelineRegistration pipeline : pipelines) {
                    writes.add(target.save(pipeline).map(ignored -> (Void) null));
                    writes.add(
                        source.findVersionHistory(pipeline.getId(), tenantId)
                            .then(snapshots -> persistSnapshots(target, pipeline.getId(), snapshots))
                    );
                }
                return Promises.all(writes).map(ignored -> null);
            });
    }

    private static Promise<Void> persistSnapshots(PipelineRepository target,
                                                  String pipelineId,
                                                  List<PipelineRegistration> snapshots) {
        if (snapshots.isEmpty()) {
            return Promise.complete();
        }
        List<Promise<Void>> writes = snapshots.stream()
            .map(snapshot -> target.saveVersionSnapshot(pipelineId, snapshot))
            .toList();
        return Promises.all(writes).map(ignored -> null);
    }
}