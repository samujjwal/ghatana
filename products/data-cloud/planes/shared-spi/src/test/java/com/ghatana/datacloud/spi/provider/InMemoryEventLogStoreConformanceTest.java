/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.spi.provider;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStoreContractTest;
import com.ghatana.datacloud.spi.TenantContext;
import org.junit.jupiter.api.DisplayName;

/**
 * Runs the shared {@link EventLogStoreContractTest} conformance suite against
 * the {@link InMemoryEventLogStoreProvider} implementation.
 *
 * <p>DC-DRY-003: Any new EventLogStore provider (H2, Kafka, etc.) must have
 * an analogous subclass extending {@link EventLogStoreContractTest} to prove
 * conformance with the SPI contract.
 *
 * @doc.type class
 * @doc.purpose Provider conformance test for InMemoryEventLogStoreProvider
 * @doc.layer spi
 * @doc.pattern ContractTest
 */
@DisplayName("InMemoryEventLogStoreProvider — SPI conformance suite (DC-DRY-003)")
class InMemoryEventLogStoreConformanceTest extends EventLogStoreContractTest {

    @Override
    protected EventLogStore createStore() {
        return new InMemoryEventLogStoreProvider();
    }

    @Override
    protected TenantContext createTenant(String tenantId) {
        return TenantContext.of(tenantId);
    }
}
