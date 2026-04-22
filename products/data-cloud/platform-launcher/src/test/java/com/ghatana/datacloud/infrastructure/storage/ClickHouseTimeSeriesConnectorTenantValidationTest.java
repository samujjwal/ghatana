/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for tenant boundary validation in {@link ClickHouseTimeSeriesConnector}.
 *
 * @doc.type class
 * @doc.purpose Verify tenant identifier validation blocks injection payloads before SQL construction
 * @doc.layer product
 * @doc.pattern Test, Unit
 */
@DisplayName("ClickHouseTimeSeriesConnector Tenant Validation Tests [GH-90000]")
class ClickHouseTimeSeriesConnectorTenantValidationTest {

    @Test
    @DisplayName("requireValidTenantId accepts Data Cloud safe tenant identifiers [GH-90000]")
    void requireValidTenantIdAcceptsSafeIdentifiers() { // GH-90000
        String tenantId = ClickHouseTimeSeriesConnector.requireValidTenantId("tenant.alpha-01:_prod [GH-90000]");

        assertThat(tenantId).isEqualTo("tenant.alpha-01:_prod [GH-90000]");
    }

    @Test
    @DisplayName("requireValidTenantId rejects SQL injection payloads [GH-90000]")
    void requireValidTenantIdRejectsSqlInjectionPayloads() { // GH-90000
        assertThatThrownBy(() -> ClickHouseTimeSeriesConnector.requireValidTenantId("tenant' OR 1=1 -- [GH-90000]"))
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("tenantId contains illegal characters [GH-90000]");
    }
}