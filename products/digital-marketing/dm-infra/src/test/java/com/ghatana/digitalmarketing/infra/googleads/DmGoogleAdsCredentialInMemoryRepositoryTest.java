package com.ghatana.digitalmarketing.infra.googleads;

import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmGoogleAdsCredentialInMemoryRepository")
class DmGoogleAdsCredentialInMemoryRepositoryTest extends EventloopTestBase {

    private DmGoogleAdsCredentialInMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DmGoogleAdsCredentialInMemoryRepository();
    }

    @Test
    @DisplayName("save/findById/findByConnectorId/update/delete lifecycle")
    void lifecycle() {
        DmGoogleAdsCredential first = credential("cred-1", "conn-1", "token-1");
        DmGoogleAdsCredential second = credential("cred-2", "conn-2", "token-2");

        runPromise(() -> repository.save(first));
        runPromise(() -> repository.save(second));

        Optional<DmGoogleAdsCredential> byId = runPromise(() -> repository.findById("cred-1"));
        assertThat(byId).isPresent();
        assertThat(byId.get().getConnectorId()).isEqualTo("conn-1");

        Optional<DmGoogleAdsCredential> byConnector = runPromise(() -> repository.findByConnectorId("conn-2"));
        assertThat(byConnector).isPresent();
        assertThat(byConnector.get().getId()).isEqualTo("cred-2");

        DmGoogleAdsCredential updated = credential("cred-1", "conn-1", "token-updated");
        runPromise(() -> repository.update(updated));
        Optional<DmGoogleAdsCredential> afterUpdate = runPromise(() -> repository.findById("cred-1"));
        assertThat(afterUpdate).isPresent();
        assertThat(afterUpdate.get().getAccessToken()).isEqualTo("token-updated");

        runPromise(() -> repository.delete("cred-1"));
        Optional<DmGoogleAdsCredential> afterDelete = runPromise(() -> repository.findById("cred-1"));
        assertThat(afterDelete).isEmpty();
    }

    private static DmGoogleAdsCredential credential(String id, String connectorId, String accessToken) {
        Instant now = Instant.now();
        return DmGoogleAdsCredential.builder()
            .id(id)
            .tenantId("tenant-a")
            .connectorId(connectorId)
            .accessToken(accessToken)
            .refreshToken("refresh-1")
            .expiresAt(now.plusSeconds(3600))
            .scopes(List.of("scope-1"))
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
