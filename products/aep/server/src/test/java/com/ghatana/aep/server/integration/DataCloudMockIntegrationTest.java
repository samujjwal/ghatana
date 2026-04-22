/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudMockIntegrationTest [GH-90000]")
class DataCloudMockIntegrationTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("pattern store reads persisted pattern state from a mocked Data-Cloud client [GH-90000]")
    void patternStoreReadsPersistedPatternStateFromMockClient() { // GH-90000
        DataCloudPatternStore store = new DataCloudPatternStore(dataCloudClient); // GH-90000
        UUID patternId = UUID.fromString("11111111-1111-1111-1111-111111111111 [GH-90000]");
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
            patternId.toString(), // GH-90000
            "aep_patterns",
            Map.of( // GH-90000
                "id", patternId.toString(), // GH-90000
                "name", "High CPU",
                "tenantId", "tenant-a",
                "status", "ACTIVE",
                "description", "mocked pattern"));

        when(dataCloudClient.findById(eq("tenant-a [GH-90000]"), eq("aep_patterns [GH-90000]"), eq(patternId.toString())))
            .thenReturn(Promise.of(java.util.Optional.of(entity))); // GH-90000

        var found = runPromise(() -> store.findByTenantAndId("tenant-a", patternId)); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(patternId); // GH-90000
        assertThat(found.get().getName()).isEqualTo("High CPU [GH-90000]");
    }
}
