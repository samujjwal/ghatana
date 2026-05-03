package com.ghatana.digitalmarketing.application.api;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKey;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKeyStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmPublicApiKeyServiceImpl")
class DmPublicApiKeyServiceImplTest extends EventloopTestBase {

    private InMemoryApiKeyRepository repository;
    private DmPublicApiKeyServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryApiKeyRepository();
        service = new DmPublicApiKeyServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmPublicApiKeyService.IssuePublicApiKeyCommand cmd() {
        return new DmPublicApiKeyService.IssuePublicApiKeyCommand(
            "Analytics Key", "sha256-hash-abc", List.of("analytics:read", "reports:read"), null
        );
    }

    @Test
    @DisplayName("issue creates ACTIVE key")
    void issueSuccess() {
        DmPublicApiKey key = runPromise(() -> service.issue(ctx, cmd()));

        assertThat(key.getTenantId()).isEqualTo("tenant-1");
        assertThat(key.getDisplayName()).isEqualTo("Analytics Key");
        assertThat(key.getStatus()).isEqualTo(DmPublicApiKeyStatus.ACTIVE);
        assertThat(key.getScopes()).containsExactlyInAnyOrder("analytics:read", "reports:read");
    }

    @Test
    @DisplayName("issue rejects unauthorized actor")
    void issueUnauthorized() {
        service = new DmPublicApiKeyServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.issue(ctx, cmd())));
    }

    @Test
    @DisplayName("revoke transitions key to REVOKED")
    void revokeSuccess() {
        DmPublicApiKey issued = runPromise(() -> service.issue(ctx, cmd()));
        DmPublicApiKey revoked = runPromise(() -> service.revoke(ctx, issued.getId()));

        assertThat(revoked.getStatus()).isEqualTo(DmPublicApiKeyStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("revoke rejects key from different tenant")
    void revokeTenantIsolation() {
        DmPublicApiKey issued = runPromise(() -> service.issue(ctx, cmd()));
        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.revoke(otherCtx, issued.getId())));
    }

    @Test
    @DisplayName("recordUsage updates lastUsedAt")
    void recordUsageSuccess() {
        DmPublicApiKey issued = runPromise(() -> service.issue(ctx, cmd()));
        DmPublicApiKey after = runPromise(() -> service.recordUsage(ctx, issued.getId()));

        assertThat(after.getLastUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("listActiveByTenant returns only ACTIVE keys")
    void listActiveByTenantSuccess() {
        DmPublicApiKey k1 = runPromise(() -> service.issue(ctx, cmd()));
        runPromise(() -> service.issue(ctx, cmd()));
        runPromise(() -> service.revoke(ctx, k1.getId()));

        List<DmPublicApiKey> active = runPromise(() -> service.listActiveByTenant(ctx));
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getStatus()).isEqualTo(DmPublicApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("IssuePublicApiKeyCommand rejects blank displayName")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPublicApiKeyService.IssuePublicApiKeyCommand(
                "", "hash", List.of(), null));
    }

    @Test
    @DisplayName("IssuePublicApiKeyCommand rejects null displayName")
    void commandValidationNullDisplayName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPublicApiKeyService.IssuePublicApiKeyCommand(
                null, "hash", List.of(), null));
    }

    @Test
    @DisplayName("IssuePublicApiKeyCommand rejects null keyHash")
    void commandValidationNullHash() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPublicApiKeyService.IssuePublicApiKeyCommand(
                "Key Name", null, List.of(), null));
    }

    @Test
    @DisplayName("IssuePublicApiKeyCommand rejects null scopes")
    void commandValidationNullScopes() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPublicApiKeyService.IssuePublicApiKeyCommand(
                "Key Name", "hash", null, null));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class InMemoryApiKeyRepository implements DmPublicApiKeyRepository {
        private final Map<String, DmPublicApiKey> store = new ConcurrentHashMap<>();

        @Override public Promise<DmPublicApiKey> save(DmPublicApiKey k) { store.put(k.getId(), k); return Promise.of(k); }
        @Override public Promise<DmPublicApiKey> update(DmPublicApiKey k) { store.put(k.getId(), k); return Promise.of(k); }
        @Override public Promise<Optional<DmPublicApiKey>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmPublicApiKey>> listByTenant(String tenantId) {
            List<DmPublicApiKey> r = new ArrayList<>();
            for (DmPublicApiKey k : store.values()) if (k.getTenantId().equals(tenantId)) r.add(k);
            return Promise.of(r);
        }
        @Override public Promise<List<DmPublicApiKey>> listByTenantAndStatus(String tenantId, DmPublicApiKeyStatus status) {
            List<DmPublicApiKey> r = new ArrayList<>();
            for (DmPublicApiKey k : store.values()) if (k.getTenantId().equals(tenantId) && k.getStatus() == status) r.add(k);
            return Promise.of(r);
        }
    }

    // ── Stub kernel adapter ───────────────────────────────────────────────────

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
