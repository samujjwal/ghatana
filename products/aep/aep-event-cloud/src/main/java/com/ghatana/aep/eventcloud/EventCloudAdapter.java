/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.spi.EventCloudConnector;

/**
 * Abstraction layer for AEP event-cloud integrations (AEP-013).
 *
 * <p>This interface decouples AEP's event-cloud plugin and engine-facing callers
 * from the concrete Data-Cloud-backed implementation. Alternative adapters can
 * provide the same facade/connector pair for tests or future transports.
 *
 * @doc.type interface
 * @doc.purpose Abstract event-cloud integrations behind a stable adapter contract
 * @doc.layer product
 * @doc.pattern Adapter
 */
public interface EventCloudAdapter {

    /**
     * @return the synchronous AEP event-cloud facade
     */
    EventCloud eventCloud();

    /**
     * @return the transport-level connector for async integration points
     */
    EventCloudConnector connector();

    /**
     * @return a stable human-readable adapter name for diagnostics
     */
    default String adapterName() {
        return getClass().getSimpleName();
    }
}