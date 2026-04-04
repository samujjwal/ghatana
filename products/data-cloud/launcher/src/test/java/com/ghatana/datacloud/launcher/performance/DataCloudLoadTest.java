package com.ghatana.datacloud.launcher.performance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

/**
 * Legacy launcher-local load test placeholder.
 *
 * <p>The original suite targeted the pre-split embedded client API and record
 * model. After the Data Cloud module extraction, the maintained performance
 * coverage lives in platform-launcher through the in-memory baseline and
 * concurrent tenant load suites.
 *
 * <p>Retained as a disabled placeholder so the launcher module keeps a stable
 * test surface without compiling against removed APIs.
 *
 * @doc.type class
 * @doc.purpose Disabled compatibility placeholder for legacy launcher-local load tests
 * @doc.layer product
 * @doc.pattern Test
 */
@Disabled("Superseded by platform-launcher performance tests after Data Cloud client/module split")
@DisplayName("Data-Cloud Load Testing Suite")
class DataCloudLoadTest {
}
