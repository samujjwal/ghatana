/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.DataSourceRepository;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.MetaDataSource;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for DataSourceService (Pass 7 connector production workflow).
 *
 * @doc.type class
 * @doc.purpose Validate data source service behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Data Source Service Tests")
class DataSourceServiceTest extends EventloopTestBase {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private MetricsCollector metrics;

    private DataSourceService dataSourceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dataSourceService = new DataSourceService(
            dataSourceRepository, collectionRepository, metrics);
    }

    @Test
    @DisplayName("Should register data source successfully")
    void shouldRegisterDataSource() {
        String name = "Test DataSource";
        String type = "RELATIONAL";
        String connectionString = "jdbc:postgresql://localhost:5432/test";
        String targetCollectionId = "collection-123";

        when(dataSourceRepository.existsByName(anyString(), anyString()))
            .thenReturn(Promise.of(false));
        when(collectionRepository.findByName(anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(mock(com.ghatana.datacloud.entity.MetaCollection.class))));
        when(dataSourceRepository.save(anyString(), any(MetaDataSource.class)))
            .thenAnswer(inv -> {
                MetaDataSource ds = inv.getArgument(1);
                // Return a new instance with ID set (simulating JPA behavior)
                MetaDataSource withId = MetaDataSource.builder()
                    .id(UUID.randomUUID())
                    .tenantId(ds.getTenantId())
                    .name(ds.getName())
                    .type(ds.getType())
                    .connectionConfig(ds.getConnectionConfig())
                    .targetCollection(ds.getTargetCollection())
                    .connectionStatus(ds.getConnectionStatus())
                    .build();
                return Promise.of(withId);
            });

        var result = runPromise(() -> dataSourceService.register(name, type, connectionString, targetCollectionId));

        assertThat(result).isNotNull();
        assertThat(result.get("id")).isNotNull();
        assertThat(result.get("name")).isEqualTo(name);
    }

    @Test
    @DisplayName("Should activate data source successfully")
    void shouldActivateDataSource() {
        String dataSourceName = "test-ds";
        MetaDataSource ds = MetaDataSource.builder()
            .tenantId("test-tenant")
            .name(dataSourceName)
            .type(MetaDataSource.DataSourceType.RELATIONAL)
            .build();
        when(dataSourceRepository.findByName(anyString(), eq(dataSourceName)))
            .thenReturn(Promise.of(Optional.of(ds)));
        when(dataSourceRepository.save(anyString(), any(MetaDataSource.class)))
            .thenAnswer(inv -> {
                MetaDataSource saved = inv.getArgument(1);
                return Promise.of(MetaDataSource.builder()
                    .id(UUID.randomUUID())
                    .tenantId(saved.getTenantId())
                    .name(saved.getName())
                    .type(saved.getType())
                    .connectionStatus(saved.getConnectionStatus())
                    .build());
            });

        var result = runPromise(() -> dataSourceService.activate(dataSourceName));

        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("Should deactivate data source successfully")
    void shouldDeactivateDataSource() {
        String dataSourceName = "test-ds";
        MetaDataSource ds = MetaDataSource.builder()
            .tenantId("test-tenant")
            .name(dataSourceName)
            .type(MetaDataSource.DataSourceType.RELATIONAL)
            .build();
        when(dataSourceRepository.findByName(anyString(), eq(dataSourceName)))
            .thenReturn(Promise.of(Optional.of(ds)));
        when(dataSourceRepository.save(anyString(), any(MetaDataSource.class)))
            .thenAnswer(inv -> {
                MetaDataSource saved = inv.getArgument(1);
                return Promise.of(MetaDataSource.builder()
                    .id(UUID.randomUUID())
                    .tenantId(saved.getTenantId())
                    .name(saved.getName())
                    .type(saved.getType())
                    .connectionStatus(saved.getConnectionStatus())
                    .build());
            });

        var result = runPromise(() -> dataSourceService.deactivate(dataSourceName));

        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("DISCONNECTED");
    }

    @Test
    @DisplayName("Should get data source status")
    void shouldGetDataSourceStatus() {
        String dataSourceName = "test-ds";
        MetaDataSource ds = MetaDataSource.builder()
            .id(UUID.randomUUID())
            .tenantId("test-tenant")
            .name(dataSourceName)
            .type(MetaDataSource.DataSourceType.RELATIONAL)
            .connectionStatus("CONNECTED")
            .build();
        when(dataSourceRepository.findByName(anyString(), eq(dataSourceName)))
            .thenReturn(Promise.of(Optional.of(ds)));

        var status = runPromise(() -> dataSourceService.getStatus(dataSourceName));

        assertThat(status).isNotNull();
        assertThat(status.get("name")).isEqualTo(dataSourceName);
        assertThat(status.get("status")).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("Should test data source connection")
    void shouldTestDataSourceConnection() {
        String dataSourceId = "datasource-123";

        var result = runPromise(() -> dataSourceService.testConnection(dataSourceId));

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should sync data source")
    void shouldSyncDataSource() {
        String dataSourceName = "test-ds";
        MetaDataSource ds = MetaDataSource.builder()
            .tenantId("test-tenant")
            .name(dataSourceName)
            .type(MetaDataSource.DataSourceType.RELATIONAL)
            .build();
        when(dataSourceRepository.findByName(anyString(), eq(dataSourceName)))
            .thenReturn(Promise.of(Optional.of(ds)));
        when(dataSourceRepository.save(anyString(), any(MetaDataSource.class)))
            .thenAnswer(inv -> {
                MetaDataSource saved = inv.getArgument(1);
                return Promise.of(MetaDataSource.builder()
                    .id(UUID.randomUUID())
                    .tenantId(saved.getTenantId())
                    .name(saved.getName())
                    .type(saved.getType())
                    .connectionStatus(saved.getConnectionStatus())
                    .syncStats(saved.getSyncStats())
                    .build());
            });

        var result = runPromise(() -> dataSourceService.sync(dataSourceName));

        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("SYNCING");
    }

    @Test
    @DisplayName("Should get sync status")
    void shouldGetSyncStatus() {
        String dataSourceName = "test-ds";
        MetaDataSource ds = MetaDataSource.builder()
            .id(UUID.randomUUID())
            .tenantId("test-tenant")
            .name(dataSourceName)
            .type(MetaDataSource.DataSourceType.RELATIONAL)
            .syncStats(java.util.Map.of("status", "COMPLETED"))
            .lastSyncedAt(java.time.Instant.now())
            .build();
        when(dataSourceRepository.findByName(anyString(), eq(dataSourceName)))
            .thenReturn(Promise.of(Optional.of(ds)));

        var status = runPromise(() -> dataSourceService.getSyncStatus(dataSourceName));

        assertThat(status).isNotNull();
        assertThat(status.get("syncStats")).isNotNull();
    }

    @Test
    @DisplayName("Should validate data source schema")
    void shouldValidateDataSourceSchema() {
        String dataSourceId = "datasource-123";

        var result = runPromise(() -> dataSourceService.validateSchema(dataSourceId));

        assertThat(result).isNotNull();
        assertThat(result.get("valid")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should rotate data source credentials")
    void shouldRotateDataSourceCredentials() {
        String dataSourceId = "datasource-123";

        var result = runPromise(() -> dataSourceService.rotateCredentials(dataSourceId));

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
    }
}
