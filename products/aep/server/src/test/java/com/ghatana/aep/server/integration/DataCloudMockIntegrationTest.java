/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.integration;

import com.ghatana.aep.server.store.DataCloudPatternStore;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Mocked Data-Cloud integration test for the AEP Phase-1 integration suite.
 *
 * @doc.type class
 * @doc.purpose Verify AEP Data-Cloud integration surfaces can operate against a mock client
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudMockIntegrationTest")
class DataCloudMockIntegrationTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("pattern store reads persisted pattern state from a mocked Data-Cloud client")
    void patternStoreReadsPersistedPatternStateFromMockClient() {
        DataCloudPatternStore store = new DataCloudPatternStore(dataCloudClient);
        UUID patternId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        DataCloudClient.Entity entity = DataCloudClient.Entity.of(
            patternId.toString(),
            "aep_patterns",
            Map.of(
                "id", patternId.toString(),
                "name", "High CPU",
                "tenantId", "tenant-a",
                "status", "ACTIVE",
                "description", "mocked pattern"));

        when(dataCloudClient.findById(eq("tenant-a"), eq("aep_patterns"), eq(patternId.toString())))
            .thenReturn(Promise.of(java.util.Optional.of(entity)));

        var found = runPromise(() -> store.findByTenantAndId("tenant-a", patternId));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(patternId);
        assertThat(found.get().getName()).isEqualTo("High CPU");
    }
}
