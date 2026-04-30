package com.ghatana.integration.crossservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("YAPPC Data Cloud repository contract")
class YappcDataCloudRepositoryContractTest extends EventloopTestBase {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("propagates tenant-scoped entity save contract")
    void propagatesTenantScopedEntitySaveContract() {
        DataCloudClient client = mock(DataCloudClient.class);
        SampleEntity entity = new SampleEntity(UUID.randomUUID(), "Project Phoenix", "ACTIVE");

        when(client.save(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = invocation.getArgument(2, Map.class);
                    return Promise.of(new DataCloudClient.Entity(
                            entity.id().toString(),
                            "projects",
                            payload,
                            Instant.parse("2026-04-16T10:15:30Z"),
                            Instant.parse("2026-04-16T10:16:30Z"),
                            1L
                    ));
                });

        YappcDataCloudRepository<SampleEntity> repository = new YappcDataCloudRepository<>(
                client,
                new YappcEntityMapper(new ObjectMapper()),
                "projects",
                SampleEntity.class
        );

        SampleEntity saved = runPromise(() -> {
            TenantContext.setCurrentTenantId("tenant-contract");
            return repository.save(entity);
        });

        assertThat(saved).isEqualTo(entity);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = mapCaptor();
        verify(client).save(eq("tenant-contract"), eq("projects"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("name", "Project Phoenix")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("uses TenantContext fallback tenant when no explicit tenant is active")
    void usesTenantContextFallbackTenantWhenNoExplicitTenantIsActive() {
        DataCloudClient client = mock(DataCloudClient.class);
        when(client.query(anyString(), anyString(), any())).thenReturn(Promise.of(List.of()));

        YappcDataCloudRepository<SampleEntity> repository = new YappcDataCloudRepository<>(
                client,
                new YappcEntityMapper(new ObjectMapper()),
                "projects",
                SampleEntity.class
        );

        List<SampleEntity> results = runPromise(() -> repository.findAll());

        assertThat(results).isEmpty();
        verify(client).query(eq("default-tenant"), eq("projects"), any());
    }

    record SampleEntity(UUID id, String name, String status) implements Identifiable<UUID> {
        @Override
        public UUID getId() {
            return id;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}