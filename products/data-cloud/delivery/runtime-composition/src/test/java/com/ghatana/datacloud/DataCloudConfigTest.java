/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WS16: Test for DataCloudConfig to assert that allowInvalidLocalEventsForTests is preserved.
 *
 * <p>This test validates the WS5-1 fix that ensures the compact constructor
 * preserves the input value of allowInvalidLocalEventsForTests instead of
 * forcing it to false.
 *
 * @doc.type test
 * @doc.purpose Assert allowInvalidLocalEventsForTests is preserved in DataCloudConfig
 * @doc.layer test
 */
class DataCloudConfigTest {

    @Test
    void testForTestingFactoryPreservesAllowInvalidLocalEventsForTests() {
        // WS16: Assert that forTesting() factory method sets allowInvalidLocalEventsForTests to true
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.forTesting();
        
        assertTrue(config.allowInvalidLocalEventsForTests(),
            "forTesting() factory should set allowInvalidLocalEventsForTests to true");
    }

    @Test
    void testDefaultsFactorySetsAllowInvalidLocalEventsForTestsToFalse() {
        // WS16: Assert that defaults() factory method sets allowInvalidLocalEventsForTests to false
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.defaults();
        
        assertFalse(config.allowInvalidLocalEventsForTests(),
            "defaults() factory should set allowInvalidLocalEventsForTests to false");
    }

    @Test
    void testExplicitTrueValueIsPreserved() {
        // WS16: Assert that explicit true value is preserved through compact constructor
        DataCloud.DataCloudConfig config = new DataCloud.DataCloudConfig(
            "test-instance",
            1,
            false,
            false,
            DataCloud.DataCloudProfile.LOCAL,
            Map.of(),
            true // allowInvalidLocalEventsForTests = true
        );
        
        assertTrue(config.allowInvalidLocalEventsForTests(),
            "Explicit true value should be preserved through compact constructor");
    }

    @Test
    void testExplicitFalseValueIsPreserved() {
        // WS16: Assert that explicit false value is preserved through compact constructor
        DataCloud.DataCloudConfig config = new DataCloud.DataCloudConfig(
            "test-instance",
            1,
            false,
            false,
            DataCloud.DataCloudProfile.LOCAL,
            Map.of(),
            false // allowInvalidLocalEventsForTests = false
        );
        
        assertFalse(config.allowInvalidLocalEventsForTests(),
            "Explicit false value should be preserved through compact constructor");
    }

    @Test
    void testBuilderPreservesAllowInvalidLocalEventsForTests() {
        // WS16: Assert that builder preserves allowInvalidLocalEventsForTests
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.builder()
            .instanceId("test-instance")
            .maxConnectionsPerTenant(1)
            .enableCaching(false)
            .enableMetrics(false)
            .profile(DataCloud.DataCloudProfile.LOCAL)
            .customConfig(Map.of())
            .allowInvalidLocalEventsForTests(true)
            .build();
        
        assertTrue(config.allowInvalidLocalEventsForTests(),
            "Builder should preserve allowInvalidLocalEventsForTests when set to true");
    }
}
