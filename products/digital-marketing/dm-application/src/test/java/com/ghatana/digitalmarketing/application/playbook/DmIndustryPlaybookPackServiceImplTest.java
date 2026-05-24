package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.playbook.DmIndustryPlaybookPack;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmIndustryPlaybookPackServiceImpl")
class DmIndustryPlaybookPackServiceImplTest extends EventloopTestBase {

    private EphemeralPackRepository repository;
    private DmIndustryPlaybookPackServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralPackRepository();
        service = new DmIndustryPlaybookPackServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand cmd() {
        return new DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand(
            "SaaS Growth Pack", "SaaS", "Playbook for SaaS companies", "1.0.0", List.of("pb-1", "pb-2")
        );
    }

    @Test
    @DisplayName("create produces unpublished pack")
    void createSuccess() {
        DmIndustryPlaybookPack pack = runPromise(() -> service.create(ctx, cmd()));

        assertThat(pack.getName()).isEqualTo("SaaS Growth Pack");
        assertThat(pack.getIndustry()).isEqualTo("SaaS");
        assertThat(pack.isPublished()).isFalse();
        assertThat(pack.getPlaybookIds()).containsExactly("pb-1", "pb-2");
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmIndustryPlaybookPackServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx, cmd())));
    }

    @Test
    @DisplayName("publish marks pack as published with publishedAt")
    void publishSuccess() {
        DmIndustryPlaybookPack created = runPromise(() -> service.create(ctx, cmd()));
        DmIndustryPlaybookPack published = runPromise(() -> service.publish(ctx, created.getId()));

        assertThat(published.isPublished()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById returns pack by id")
    void findByIdSuccess() {
        DmIndustryPlaybookPack created = runPromise(() -> service.create(ctx, cmd()));

        Optional<DmIndustryPlaybookPack> found = runPromise(() -> service.findById(ctx, created.getId()));
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("listPublished returns only published packs")
    void listPublishedSuccess() {
        DmIndustryPlaybookPack p1 = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.publish(ctx, p1.getId()));

        List<DmIndustryPlaybookPack> published = runPromise(() -> service.listPublished(ctx));
        assertThat(published).hasSize(1);
        assertThat(published.get(0).isPublished()).isTrue();
    }

    @Test
    @DisplayName("listByIndustry filters by industry")
    void listByIndustrySuccess() {
        runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.create(ctx,
            new DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand(
                "Retail Pack", "Retail", "Playbook for retail", "1.0.0", List.of())));

        List<DmIndustryPlaybookPack> saas = runPromise(() -> service.listByIndustry(ctx, "SaaS"));
        assertThat(saas).allMatch(p -> p.getIndustry().equals("SaaS"));
    }

    @Test
    @DisplayName("CreateIndustryPlaybookPackCommand rejects blank name")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand(
                "", "SaaS", "desc", "1.0", List.of()));
    }

    @Test
    @DisplayName("CreateIndustryPlaybookPackCommand rejects null industry")
    void commandValidationNullIndustry() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand(
                "Pack", null, "desc", "1.0", List.of()));
    }

    @Test
    @DisplayName("CreateIndustryPlaybookPackCommand rejects blank version")
    void commandValidationBlankVersion() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand(
                "Pack", "SaaS", "desc", "", List.of()));
    }

    @Test
    @DisplayName("CreateIndustryPlaybookPackCommand rejects null playbookIds")
    void commandValidationNullPlaybookIds() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmIndustryPlaybookPackService.CreateIndustryPlaybookPackCommand(
                "Pack", "SaaS", "desc", "1.0", null));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class EphemeralPackRepository implements DmIndustryPlaybookPackRepository {
        private final Map<String, DmIndustryPlaybookPack> store = new ConcurrentHashMap<>();

        @Override public Promise<DmIndustryPlaybookPack> save(DmIndustryPlaybookPack p) { store.put(p.getId(), p); return Promise.of(p); }
        @Override public Promise<DmIndustryPlaybookPack> update(DmIndustryPlaybookPack p) { store.put(p.getId(), p); return Promise.of(p); }
        @Override public Promise<Optional<DmIndustryPlaybookPack>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmIndustryPlaybookPack>> listAll() { return Promise.of(List.copyOf(store.values())); }
        @Override public Promise<List<DmIndustryPlaybookPack>> listByIndustry(String industry) {
            List<DmIndustryPlaybookPack> r = new ArrayList<>();
            for (DmIndustryPlaybookPack p : store.values()) if (p.getIndustry().equals(industry)) r.add(p);
            return Promise.of(r);
        }
        @Override public Promise<List<DmIndustryPlaybookPack>> listPublished() {
            List<DmIndustryPlaybookPack> r = new ArrayList<>();
            for (DmIndustryPlaybookPack p : store.values()) if (p.isPublished()) r.add(p);
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
