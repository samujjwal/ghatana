/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStoreContractTest;
import com.ghatana.platform.domain.eventstore.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Conformance test for {@link H2SovereignEventLogStore}.
 *
 * <p>Runs the full {@link EventLogStoreContractTest} harness against the H2-backed sovereign
 * event log store. Each test receives a fresh store rooted in a {@link TempDir},
 * ensuring full isolation.
 *
 * @doc.type class
 * @doc.purpose Verifies H2SovereignEventLogStore satisfies all EventLogStore SPI contracts
 * @doc.layer product
 * @doc.pattern ConformanceTest
 */
@DisplayName("H2SovereignEventLogStore — contract conformance")
class H2SovereignEventLogStoreContractTest extends EventLogStoreContractTest {

    @TempDir
    Path tempDir;

    private final List<H2SovereignEventLogStore> openStores = new ArrayList<>();

    @Override
    protected EventLogStore createStore() {
        H2SovereignEventLogStore store = new H2SovereignEventLogStore(
                tempDir.resolve("store-" + openStores.size()));
        openStores.add(store);
        return store;
    }

    @Override
    protected TenantContext createTenant(String tenantId) {
        return TenantContext.of(tenantId);
    }

    @AfterEach
    void closeStores() throws Exception {
        for (H2SovereignEventLogStore store : openStores) {
            store.close();
        }
        openStores.clear();
    }
}
