package com.ghatana.digitalmarketing.infra.connector;

import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmConnectorEphemeralRepository")
class DmConnectorEphemeralRepositoryTest extends EventloopTestBase {

    private DmConnectorEphemeralRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DmConnectorEphemeralRepository();
    }

    @Test
    @DisplayName("save/findById/update round-trip")
    void saveAndFindAndUpdate() {
        DmConnectorConfig saved = runPromise(() -> repository.save(connector("c-1", "tenant-a", DmConnectorType.GOOGLE_ADS, DmConnectorStatus.PENDING)));
        assertThat(saved.getId()).isEqualTo("c-1");

        Optional<DmConnectorConfig> found = runPromise(() -> repository.findById("c-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo("tenant-a");

        DmConnectorConfig updated = connector("c-1", "tenant-a", DmConnectorType.GOOGLE_ADS, DmConnectorStatus.ACTIVE);
        DmConnectorConfig result = runPromise(() -> repository.update(updated));
        assertThat(result.getStatus()).isEqualTo(DmConnectorStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByType, findByStatus and countByStatus are tenant-scoped")
    void tenantScopedQueries() {
        runPromise(() -> repository.save(connector("c-1", "tenant-a", DmConnectorType.GOOGLE_ADS, DmConnectorStatus.ACTIVE)));
        runPromise(() -> repository.save(connector("c-2", "tenant-a", DmConnectorType.META_ADS, DmConnectorStatus.SUSPENDED)));
        runPromise(() -> repository.save(connector("c-3", "tenant-b", DmConnectorType.GOOGLE_ADS, DmConnectorStatus.ACTIVE)));

        List<DmConnectorConfig> byType = runPromise(() -> repository.findByType("tenant-a", DmConnectorType.GOOGLE_ADS, 10));
        assertThat(byType).hasSize(1);
        assertThat(byType.get(0).getId()).isEqualTo("c-1");

        List<DmConnectorConfig> byStatus = runPromise(() -> repository.findByStatus("tenant-a", DmConnectorStatus.ACTIVE, 10));
        assertThat(byStatus).hasSize(1);
        assertThat(byStatus.get(0).getId()).isEqualTo("c-1");

        Long activeCount = runPromise(() -> repository.countByStatus("tenant-a", DmConnectorStatus.ACTIVE));
        assertThat(activeCount).isEqualTo(1L);
    }

    private static DmConnectorConfig connector(String id, String tenant, DmConnectorType type, DmConnectorStatus status) {
        Instant now = Instant.now();
        return DmConnectorConfig.builder()
            .id(id)
            .tenantId(tenant)
            .workspaceId("ws-1")
            .name("Connector " + id)
            .connectorType(type)
            .status(status)
            .settings(java.util.Map.of("k", "v"))
            .externalAccountId("acct-1")
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
