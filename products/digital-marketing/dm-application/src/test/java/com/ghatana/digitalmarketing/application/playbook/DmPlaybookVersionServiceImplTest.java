package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersion;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersionStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmPlaybookVersionServiceImpl")
class DmPlaybookVersionServiceImplTest extends EventloopTestBase {

    private InMemoryPlaybookVersionRepository repository;
    private DmPlaybookVersionServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPlaybookVersionRepository();
        service = new DmPlaybookVersionServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("create playbook version with DRAFT status")
    void createSuccess() {
        DmPlaybookVersion v = runPromise(() -> service.create(ctx, cmd()));

        assertThat(v.getStatus()).isEqualTo(DmPlaybookVersionStatus.DRAFT);
        assertThat(v.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmPlaybookVersionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx, cmd())));
    }

    @Test
    @DisplayName("promote transitions to ACTIVE")
    void promoteSuccess() {
        DmPlaybookVersion created = runPromise(() -> service.create(ctx, cmd()));
        DmPlaybookVersion promoted = runPromise(() -> service.promote(ctx, created.getId()));

        assertThat(promoted.getStatus()).isEqualTo(DmPlaybookVersionStatus.ACTIVE);
    }

    @Test
    @DisplayName("archive transitions to ARCHIVED")
    void archiveSuccess() {
        DmPlaybookVersion created = runPromise(() -> service.create(ctx, cmd()));
        DmPlaybookVersion promoted = runPromise(() -> service.promote(ctx, created.getId()));
        DmPlaybookVersion archived = runPromise(() -> service.archive(ctx, promoted.getId()));

        assertThat(archived.getStatus()).isEqualTo(DmPlaybookVersionStatus.ARCHIVED);
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmPlaybookVersion created = runPromise(() -> service.create(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        assertThat(runPromise(() -> service.findById(other, created.getId()))).isEmpty();
    }

    @Test
    @DisplayName("listByPlaybook returns matching versions")
    void listByPlaybookSuccess() {
        runPromise(() -> service.create(ctx, cmd()));
        List<DmPlaybookVersion> results = runPromise(() -> service.listByPlaybook(ctx, "pb-1"));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("command validates versionNumber < 1")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPlaybookVersionService.CreatePlaybookVersionCommand("pb-1", 0, "{}"));
    }

    @Test
    @DisplayName("promote rejects unauthorized actor")
    void promoteUnauthorized() {
        DmPlaybookVersion created = runPromise(() -> service.create(ctx, cmd()));
        DmPlaybookVersionServiceImpl deniedService =
            new DmPlaybookVersionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.promote(ctx, created.getId())));
    }

    @Test
    @DisplayName("archive rejects unauthorized actor")
    void archiveUnauthorized() {
        DmPlaybookVersion created = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.promote(ctx, created.getId()));
        DmPlaybookVersionServiceImpl deniedService =
            new DmPlaybookVersionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.archive(ctx, created.getId())));
    }

    @Test
    @DisplayName("listByPlaybook rejects unauthorized actor")
    void listByPlaybookUnauthorized() {
        service = new DmPlaybookVersionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByPlaybook(ctx, "pb-1")));
    }

    @Test
    @DisplayName("promote throws NoSuchElementException for unknown id")
    void promoteUnknownId() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.promote(ctx, "nonexistent")));
    }

    private DmPlaybookVersionService.CreatePlaybookVersionCommand cmd() {
        return new DmPlaybookVersionService.CreatePlaybookVersionCommand("pb-1", 1, "{\"steps\":[]}");
    }

    static final class InMemoryPlaybookVersionRepository implements DmPlaybookVersionRepository {
        private final Map<String, DmPlaybookVersion> store = new ConcurrentHashMap<>();

        @Override public Promise<DmPlaybookVersion> save(DmPlaybookVersion v) { store.put(v.getId(), v); return Promise.of(v); }
        @Override public Promise<DmPlaybookVersion> update(DmPlaybookVersion v) { store.put(v.getId(), v); return Promise.of(v); }
        @Override public Promise<Optional<DmPlaybookVersion>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmPlaybookVersion>> listByPlaybook(String tenantId, String playbookId) {
            return Promise.of(store.values().stream()
                .filter(v -> v.getTenantId().equals(tenantId) && v.getPlaybookId().equals(playbookId)).toList());
        }
        @Override public Promise<List<DmPlaybookVersion>> listByStatus(String tenantId, DmPlaybookVersionStatus status) {
            return Promise.of(store.values().stream()
                .filter(v -> v.getTenantId().equals(tenantId) && v.getStatus() == status).toList());
        }
    }

    static final class StubKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;
        StubKernelAdapter(boolean allowed) { this.allowed = allowed; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) { return Promise.of(allowed); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext ctx, String type, String subjectId, String desc) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action, Map<String, Object> meta) { return Promise.of("audit-1"); }
    }
}
