package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ContentAssetVersion")
class ContentAssetVersionTest {

    @Test
    @DisplayName("builds immutable content version")
    void shouldBuildVersion() {
        Instant now = Instant.now();
        ContentAssetVersion version = ContentAssetVersion.builder()
            .versionId("ver-1")
            .assetId("asset-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .versionNumber(1)
            .contentBody("hello")
            .changeSummary("initial")
            .createdAt(now)
            .createdBy("user-1")
            .build();

        assertThat(version.getVersionId()).isEqualTo("ver-1");
        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThat(version.getAssetId()).isEqualTo("asset-1");
        assertThat(version.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(version.getContentBody()).isEqualTo("hello");
        assertThat(version.getChangeSummary()).isEqualTo("initial");
        assertThat(version.getCreatedAt()).isEqualTo(now);
        assertThat(version.getCreatedBy()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("rejects non-positive version number")
    void shouldRejectNonPositiveVersionNumber() {
        assertThatIllegalArgumentException().isThrownBy(() -> ContentAssetVersion.builder()
            .versionId("ver-1")
            .assetId("asset-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .versionNumber(0)
            .contentBody("hello")
            .createdAt(Instant.now())
            .createdBy("user-1")
            .build());

        assertThatIllegalArgumentException().isThrownBy(() -> ContentAssetVersion.builder()
            .versionId(" ")
            .assetId("asset-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .versionNumber(1)
            .contentBody("hello")
            .createdAt(Instant.now())
            .createdBy("user-1")
            .build());

        assertThatIllegalArgumentException().isThrownBy(() -> ContentAssetVersion.builder()
            .versionId("ver-1")
            .assetId(" ")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .versionNumber(1)
            .contentBody("hello")
            .createdAt(Instant.now())
            .createdBy("user-1")
            .build());
    }
}
