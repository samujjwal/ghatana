/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
import com.ghatana.pipeline.registry.repository.InMemoryPipelineRepository;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineMigrationUtil")
class PipelineMigrationUtilTest {

    private static final String TENANT_ID = "tenant-migrate";

    @Mock
    private DataCloudClient client;

    private InMemoryPipelineRepository source;
    private DataCloudPipelineStore target;

    @BeforeEach
    void setUp() {
        source = new InMemoryPipelineRepository();
        target = new DataCloudPipelineStore(client);
        when(client.save(eq(TENANT_ID), eq(DataCloudPipelineStore.COLLECTION), anyMap()))
            .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                String.valueOf(invocation.getArgument(2, Map.class).get("id")),
                DataCloudPipelineStore.COLLECTION,
                invocation.getArgument(2)
            )));
        when(client.save(eq(TENANT_ID), eq(DataCloudPipelineStore.VERSION_COLLECTION), anyMap()))
            .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                String.valueOf(invocation.getArgument(2, Map.class).get("id")),
                DataCloudPipelineStore.VERSION_COLLECTION,
                invocation.getArgument(2)
            )));
    }

    @Test
    @DisplayName("migrateTenant copies tenant pipelines and version snapshots into durable storage")
    void migrateTenantCopiesPipelinesAndSnapshots() {
        Pipeline current = pipeline("pipe-1", 2, "Orders sync");
        Pipeline snapshotV1 = pipeline("pipe-1", 1, "Orders sync");
        Pipeline snapshotV2 = pipeline("pipe-1", 2, "Orders sync");

        source.save(current).getResult();
        source.saveVersionSnapshot("pipe-1", snapshotV1).getResult();
        source.saveVersionSnapshot("pipe-1", snapshotV2).getResult();

        PipelineMigrationUtil.migrateTenant(source, target, TENANT_ID).getResult();

        verify(client, times(1)).save(eq(TENANT_ID), eq(DataCloudPipelineStore.COLLECTION), anyMap());
        verify(client, times(2)).save(eq(TENANT_ID), eq(DataCloudPipelineStore.VERSION_COLLECTION), anyMap());
    }

    private static Pipeline pipeline(String id, int version, String name) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(id);
        pipeline.setTenantId(TenantId.of(TENANT_ID));
        pipeline.setName(name);
        pipeline.setDescription("Migrated pipeline");
        pipeline.setVersion(version);
        pipeline.setActive(true);
        pipeline.setConfig("{\"mode\":\"sync\"}");
        pipeline.setCreatedAt(Instant.parse("2026-04-17T12:00:00Z"));
        pipeline.setUpdatedAt(Instant.parse("2026-04-17T12:05:00Z"));
        pipeline.setCreatedBy("migration-test");
        pipeline.setUpdatedBy("migration-test");
        pipeline.setVersionLabel("v" + version);
        pipeline.setVersionStatus(PipelineVersionStatus.PUBLISHED);
        return pipeline;
    }
}