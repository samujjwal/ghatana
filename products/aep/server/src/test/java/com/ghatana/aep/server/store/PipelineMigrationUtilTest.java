/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PipelineMigrationUtil")
class PipelineMigrationUtilTest {

    private static final String TENANT_ID = "tenant-migrate";

    @Mock
    private DataCloudClient client;

    private InMemoryPipelineRepository source;
    private DataCloudPipelineStore target;

    @BeforeEach
    void setUp() { // GH-90000
        source = new InMemoryPipelineRepository(); // GH-90000
        target = new DataCloudPipelineStore(client); // GH-90000
        when(client.save(eq(TENANT_ID), eq(DataCloudPipelineStore.COLLECTION), anyMap())) // GH-90000
            .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of( // GH-90000
                String.valueOf(invocation.getArgument(2, Map.class).get("id")),
                DataCloudPipelineStore.COLLECTION,
                invocation.getArgument(2) // GH-90000
            )));
        when(client.save(eq(TENANT_ID), eq(DataCloudPipelineStore.VERSION_COLLECTION), anyMap())) // GH-90000
            .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of( // GH-90000
                String.valueOf(invocation.getArgument(2, Map.class).get("id")),
                DataCloudPipelineStore.VERSION_COLLECTION,
                invocation.getArgument(2) // GH-90000
            )));
    }

    @Test
    @DisplayName("migrateTenant copies tenant pipelines and version snapshots into durable storage")
    void migrateTenantCopiesPipelinesAndSnapshots() { // GH-90000
        Pipeline current = pipeline("pipe-1", 2, "Orders sync"); // GH-90000
        Pipeline snapshotV1 = pipeline("pipe-1", 1, "Orders sync"); // GH-90000
        Pipeline snapshotV2 = pipeline("pipe-1", 2, "Orders sync"); // GH-90000

        source.save(current).getResult(); // GH-90000
        source.saveVersionSnapshot("pipe-1", snapshotV1).getResult(); // GH-90000
        source.saveVersionSnapshot("pipe-1", snapshotV2).getResult(); // GH-90000

        PipelineMigrationUtil.migrateTenant(source, target, TENANT_ID).getResult(); // GH-90000

        verify(client, times(1)).save(eq(TENANT_ID), eq(DataCloudPipelineStore.COLLECTION), anyMap()); // GH-90000
        verify(client, times(2)).save(eq(TENANT_ID), eq(DataCloudPipelineStore.VERSION_COLLECTION), anyMap()); // GH-90000
    }

    private static Pipeline pipeline(String id, int version, String name) { // GH-90000
        Pipeline pipeline = new Pipeline(); // GH-90000
        pipeline.setId(id); // GH-90000
        pipeline.setTenantId(TenantId.of(TENANT_ID)); // GH-90000
        pipeline.setName(name); // GH-90000
        pipeline.setDescription("Migrated pipeline");
        pipeline.setVersion(version); // GH-90000
        pipeline.setActive(true); // GH-90000
        pipeline.setConfig("{\"mode\":\"sync\"}"); // GH-90000
        pipeline.setCreatedAt(Instant.parse("2026-04-17T12:00:00Z"));
        pipeline.setUpdatedAt(Instant.parse("2026-04-17T12:05:00Z"));
        pipeline.setCreatedBy("migration-test");
        pipeline.setUpdatedBy("migration-test");
        pipeline.setVersionLabel("v" + version); // GH-90000
        pipeline.setVersionStatus(PipelineVersionStatus.PUBLISHED); // GH-90000
        return pipeline;
    }
}