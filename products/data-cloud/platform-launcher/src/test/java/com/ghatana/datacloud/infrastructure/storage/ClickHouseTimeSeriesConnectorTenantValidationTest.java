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
@DisplayName("ClickHouseTimeSeriesConnector Tenant Validation Tests")
class ClickHouseTimeSeriesConnectorTenantValidationTest {

    @Test
    @DisplayName("requireValidTenantId accepts Data Cloud safe tenant identifiers")
    void requireValidTenantIdAcceptsSafeIdentifiers() { // GH-90000
        String tenantId = ClickHouseTimeSeriesConnector.requireValidTenantId("tenant.alpha-01:_prod");

        assertThat(tenantId).isEqualTo("tenant.alpha-01:_prod");
    }

    @Test
    @DisplayName("requireValidTenantId rejects SQL injection payloads")
    void requireValidTenantIdRejectsSqlInjectionPayloads() { // GH-90000
        assertThatThrownBy(() -> ClickHouseTimeSeriesConnector.requireValidTenantId("tenant' OR 1=1 --"))
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("tenantId contains illegal characters");
    }
}