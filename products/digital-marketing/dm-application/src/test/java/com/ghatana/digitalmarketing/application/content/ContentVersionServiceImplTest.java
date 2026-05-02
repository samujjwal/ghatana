package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentAssetVersion;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContentVersionServiceImpl")
class ContentVersionServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryVersionRepository repository;
    private ContentVersionServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new InMemoryVersionRepository();
        service = new ContentVersionServiceImpl(kernelAdapter, repository);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("creates initial and next versions")
    void shouldCreateVersions() {
        ContentAssetVersion first = runPromise(() -> service.createInitialVersion(ctx, "asset-1", "v1", "initial"));
        ContentAssetVersion second = runPromise(() -> service.createNextVersion(ctx, "asset-1", "v2", "update"));
        List<ContentAssetVersion> versions = runPromise(() -> service.listVersions(ctx, "asset-1"));

        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(versions).hasSize(2);
    }

    @Test
    @DisplayName("fails next version when initial missing")
    void shouldFailNextVersionWithoutInitial() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.createNextVersion(ctx, "asset-1", "v2", "update")));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new ContentVersionServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContentVersionServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects blank asset id and content body")
    void shouldRejectBlankAssetOrContent() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.createInitialVersion(ctx, " ", "body", "initial")));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.createInitialVersion(ctx, "asset-1", " ", "initial")));
    }

    @Test
    @DisplayName("denies unauthorized version writes and reads")
    void shouldDenyUnauthorizedOperations() {
        ContentVersionServiceImpl deniedService = new ContentVersionServiceImpl(new DenyKernelAdapter(), repository);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.createInitialVersion(ctx, "asset-1", "v1", "initial")));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.listVersions(ctx, "asset-1")));
    }

    private static final class InMemoryVersionRepository implements ContentVersionRepository {
        private final ConcurrentHashMap<String, List<ContentAssetVersion>> store = new ConcurrentHashMap<>();

        @Override
        public Promise<ContentAssetVersion> save(ContentAssetVersion version) {
            String key = version.getWorkspaceId().getValue() + ":" + version.getAssetId();
            store.computeIfAbsent(key, ignored -> new ArrayList<>()).add(version);
            return Promise.of(version);
        }

        @Override
        public Promise<Optional<ContentAssetVersion>> findLatestVersion(DmWorkspaceId workspaceId, String assetId) {
            String key = workspaceId.getValue() + ":" + assetId;
            return Promise.of(store.getOrDefault(key, List.of()).stream()
                .max(Comparator.comparingInt(ContentAssetVersion::getVersionNumber)));
        }

        @Override
        public Promise<List<ContentAssetVersion>> listVersions(DmWorkspaceId workspaceId, String assetId) {
            String key = workspaceId.getValue() + ":" + assetId;
            return Promise.of(List.copyOf(store.getOrDefault(key, List.of())));
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        @Override public void start() { }
        @Override public void stop() { }
        @Override public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) { return Promise.of(true); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) { return Promise.of("audit-1"); }
    }

    private static final class DenyKernelAdapter implements DigitalMarketingKernelAdapter {
        @Override public void start() { }
        @Override public void stop() { }
        @Override public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) { return Promise.of(false); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) { return Promise.of("audit-1"); }
    }
}
