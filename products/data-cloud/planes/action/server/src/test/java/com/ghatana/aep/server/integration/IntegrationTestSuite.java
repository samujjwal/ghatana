/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@SelectClasses({ 
    PostgresIntegrationTest.class,
    RedisIntegrationTest.class,
    KafkaIntegrationTest.class,
    DataCloudMockIntegrationTest.class
})
class IntegrationTestSuite {
}
