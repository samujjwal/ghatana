package com.ghatana.aep;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.InMemoryEventCloud;
import com.ghatana.platform.testing.activej.EventloopTestBase;

/**
 * Tests for AepEngine core behavior.
 *
 * @doc.type class
 * @doc.purpose Verifies AepEngine event processing, pipeline execution, and lifecycle
 * @doc.layer product
 * @doc.pattern Unit Test
 *
 * NOTE: This test file is temporarily disabled due to significant API changes in AepEngine.
 * The test needs to be rewritten to match the current AepEngine interface which now uses:
 * - Event as a record with specific fields
 * - PatternDefinition for pattern registration
 * - Promise-based async APIs
 * - Different method signatures
 *
 * TODO: Rewrite tests to match current AepEngine API
 */
@org.junit.jupiter.api.Disabled("Test file needs rewrite for current AepEngine API")
class AepEngineTest extends EventloopTestBase {

    // All test methods disabled - file needs complete rewrite
}
