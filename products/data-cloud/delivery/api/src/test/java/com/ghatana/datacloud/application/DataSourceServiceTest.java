/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataSourceService (Pass 7 connector production workflow).
 *
 * @doc.type class
 * @doc.purpose Validate data source service behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Data Source Service Tests")
class DataSourceServiceTest {

    private DataSourceService dataSourceService;

    @BeforeEach
    void setUp() {
        // Initialize data source service with test dependencies
        dataSourceService = new DataSourceService();
    }

    @Test
    @DisplayName("Should register data source successfully")
    void shouldRegisterDataSource() {
        String name = "Test DataSource";
        String type = "POSTGRESQL";
        String connectionString = "jdbc:postgresql://localhost:5432/test";
        String targetCollectionId = "collection-123";

        var result = dataSourceService.register(name, type, connectionString, targetCollectionId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.dataSourceId()).isNotNull();
    }

    @Test
    @DisplayName("Should not register data source with invalid type")
    void shouldNotRegisterDataSourceWithInvalidType() {
        String name = "Test DataSource";
        String type = "INVALID_TYPE";
        String connectionString = "jdbc:postgresql://localhost:5432/test";
        String targetCollectionId = "collection-123";

        var result = dataSourceService.register(name, type, connectionString, targetCollectionId);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should activate data source successfully")
    void shouldActivateDataSource() {
        String dataSourceId = "datasource-123";

        var result = dataSourceService.activate(dataSourceId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should deactivate data source successfully")
    void shouldDeactivateDataSource() {
        String dataSourceId = "datasource-123";

        var result = dataSourceService.deactivate(dataSourceId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should get data source status")
    void shouldGetDataSourceStatus() {
        String dataSourceId = "datasource-123";

        var status = dataSourceService.getStatus(dataSourceId);

        assertThat(status).isNotNull();
        assertThat(status.dataSourceId()).isEqualTo(dataSourceId);
    }

    @Test
    @DisplayName("Should test data source connection")
    void shouldTestDataSourceConnection() {
        String dataSourceId = "datasource-123";

        var result = dataSourceService.testConnection(dataSourceId);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should sync data source")
    void shouldSyncDataSource() {
        String dataSourceId = "datasource-123";

        var result = dataSourceService.sync(dataSourceId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should get sync status")
    void shouldGetSyncStatus() {
        String dataSourceId = "datasource-123";

        var status = dataSourceService.getSyncStatus(dataSourceId);

        assertThat(status).isNotNull();
    }

    @Test
    @DisplayName("Should validate data source schema")
    void shouldValidateDataSourceSchema() {
        String dataSourceId = "datasource-123";

        var result = dataSourceService.validateSchema(dataSourceId);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should rotate data source credentials")
    void shouldRotateDataSourceCredentials() {
        String dataSourceId = "datasource-123";

        var result = dataSourceService.rotateCredentials(dataSourceId);

        assertThat(result.isSuccess()).isTrue();
    }
}
