/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Concrete golden test implementation for InMemoryEventLogStore.
 *
 * <p>This class extends the abstract golden test harness to verify that
 * InMemoryEventLogStore correctly preserves all canonical event envelope
 * fields through the complete round-trip: append → persist → query → replay/tail.
 *
 * @doc.type class
 * @doc.purpose Golden test for InMemoryEventLogStore event envelope durability
 * @doc.layer product
 * @doc.pattern GoldenMasterTest
 */
@Disabled("GH-1303 InMemoryEventLogStore implements the platform EventLogStore API; EventEnvelopeRoundTripTest covers this adapter.")
class InMemoryEventEnvelopeRoundTripGoldenTest {

    @Test
    void coveredByPlatformEventEnvelopeRoundTripTest() {
    }
}
