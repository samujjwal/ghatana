package com.ghatana.digitalmarketing.infra.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentAssetVersion;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("InMemoryContentVersionRepository")
class InMemoryContentVersionRepositoryTest extends EventloopTestBase {

    private InMemoryContentVersionRepository repository;

    private static final DmWorkspaceId WS_1  = DmWorkspaceId.of("ws-1");
    private static final DmWorkspaceId WS_2  = DmWorkspaceId.of("ws-2");
    private static final String        ASSET = "asset-abc";

    @BeforeEach
    void setUp() {
        repository = new InMemoryContentVersionRepository();
    }

    @Test
    @DisplayName("save returns the saved version")
    void shouldReturnSavedVersion() {
        ContentAssetVersion v = buildVersion(WS_1, ASSET, "v-1", 1);
        ContentAssetVersion saved = runPromise(() -> repository.save(v));
        assertThat(saved).isSameAs(v);
    }

    @Test
    @DisplayName("findLatestVersion returns the version with the highest version number")
    void shouldReturnLatestVersion() {
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-1", 1)));
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-2", 2)));
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-3", 3)));

        Optional<ContentAssetVersion> latest = runPromise(() -> repository.findLatestVersion(WS_1, ASSET));

        assertThat(latest).isPresent();
        assertThat(latest.get().getVersionNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("findLatestVersion returns empty when no versions exist")
    void shouldReturnEmptyWhenNoVersions() {
        Optional<ContentAssetVersion> latest = runPromise(() -> repository.findLatestVersion(WS_1, ASSET));
        assertThat(latest).isEmpty();
    }

    @Test
    @DisplayName("findLatestVersion is scoped to workspace")
    void shouldScopeLatestToWorkspace() {
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-1", 1)));

        Optional<ContentAssetVersion> found = runPromise(() -> repository.findLatestVersion(WS_2, ASSET));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listVersions returns all versions for the asset sorted ascending")
    void shouldListVersionsSortedAscending() {
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-3", 3)));
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-1", 1)));
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-2", 2)));

        List<ContentAssetVersion> versions = runPromise(() -> repository.listVersions(WS_1, ASSET));

        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(1);
        assertThat(versions.get(2).getVersionNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("listVersions returns empty for unknown asset")
    void shouldReturnEmptyForUnknownAsset() {
        List<ContentAssetVersion> versions = runPromise(() -> repository.listVersions(WS_1, "no-such-asset"));
        assertThat(versions).isEmpty();
    }

    @Test
    @DisplayName("listVersions excludes versions from other workspaces")
    void shouldScopeListToWorkspace() {
        runPromise(() -> repository.save(buildVersion(WS_1, ASSET, "v-1", 1)));
        runPromise(() -> repository.save(buildVersion(WS_2, ASSET, "v-2", 2)));

        List<ContentAssetVersion> versions = runPromise(() -> repository.listVersions(WS_1, ASSET));

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getWorkspaceId()).isEqualTo(WS_1);
    }

    @Test
    @DisplayName("save rejects null version")
    void shouldRejectNullVersion() {
        assertThatNullPointerException().isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("findLatestVersion rejects null arguments")
    void shouldRejectNullFindLatest() {
        assertThatNullPointerException().isThrownBy(() -> repository.findLatestVersion(null, ASSET));
        assertThatNullPointerException().isThrownBy(() -> repository.findLatestVersion(WS_1, null));
    }

    @Test
    @DisplayName("listVersions rejects null arguments")
    void shouldRejectNullListVersions() {
        assertThatNullPointerException().isThrownBy(() -> repository.listVersions(null, ASSET));
        assertThatNullPointerException().isThrownBy(() -> repository.listVersions(WS_1, null));
    }

    private static ContentAssetVersion buildVersion(
            DmWorkspaceId workspaceId, String assetId, String versionId, int versionNumber) {
        return ContentAssetVersion.builder()
            .versionId(versionId)
            .assetId(assetId)
            .workspaceId(workspaceId)
            .versionNumber(versionNumber)
            .contentBody("body-" + versionNumber)
            .createdAt(Instant.now())
            .createdBy("test-user")
            .build();
    }
}
