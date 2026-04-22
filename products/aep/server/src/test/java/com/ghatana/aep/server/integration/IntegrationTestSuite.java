/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.integration;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Suite entry point for the Phase-1 AEP integration tests.
 *
 * @doc.type class
 * @doc.purpose Group PostgreSQL, Redis, Kafka, and Data-Cloud integration tests under one suite
 * @doc.layer product
 * @doc.pattern TestSuite
 */
@Suite
@SelectClasses({ // GH-90000
    PostgresIntegrationTest.class,
    RedisIntegrationTest.class,
    KafkaIntegrationTest.class,
    DataCloudMockIntegrationTest.class
})
class IntegrationTestSuite {
}
