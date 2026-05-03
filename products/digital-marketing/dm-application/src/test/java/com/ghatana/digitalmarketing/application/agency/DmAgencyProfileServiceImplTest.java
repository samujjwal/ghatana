package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.agency.DmAgencyProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmAgencyProfileServiceImpl")
class DmAgencyProfileServiceImplTest extends EventloopTestBase {

    private InMemoryAgencyRepository repository;
    private DmAgencyProfileServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAgencyRepository();
        service = new DmAgencyProfileServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("agency-tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("create produces agency profile with empty managed tenants")
    void createSuccess() {
        DmAgencyProfile profile = runPromise(() -> service.create(ctx,
            new DmAgencyProfileService.CreateAgencyProfileCommand("My Agency")));

        assertThat(profile.getAgencyTenantId()).isEqualTo("agency-tenant-1");
        assertThat(profile.getDisplayName()).isEqualTo("My Agency");
        assertThat(profile.getManagedTenantIds()).isEmpty();
        assertThat(profile.isActive()).isTrue();
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmAgencyProfileServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx,
                new DmAgencyProfileService.CreateAgencyProfileCommand("Agency"))));
    }

    @Test
    @DisplayName("addManagedTenant adds tenant to the list")
    void addManagedTenantSuccess() {
        DmAgencyProfile created = runPromise(() -> service.create(ctx,
            new DmAgencyProfileService.CreateAgencyProfileCommand("Agency")));
        DmAgencyProfile updated = runPromise(() -> service.addManagedTenant(ctx, created.getId(), "client-tenant-1"));

        assertThat(updated.getManagedTenantIds()).containsExactly("client-tenant-1");
        assertThat(updated.manages("client-tenant-1")).isTrue();
    }

    @Test
    @DisplayName("removeManagedTenant removes tenant from list")
    void removeManagedTenantSuccess() {
        DmAgencyProfile created = runPromise(() -> service.create(ctx,
            new DmAgencyProfileService.CreateAgencyProfileCommand("Agency")));
        runPromise(() -> service.addManagedTenant(ctx, created.getId(), "client-tenant-1"));
        DmAgencyProfile updated = runPromise(() -> service.removeManagedTenant(ctx, created.getId(), "client-tenant-1"));

        assertThat(updated.getManagedTenantIds()).isEmpty();
    }

    @Test
    @DisplayName("findById returns profile by id")
    void findByIdSuccess() {
        DmAgencyProfile created = runPromise(() -> service.create(ctx,
            new DmAgencyProfileService.CreateAgencyProfileCommand("Agency")));

        Optional<DmAgencyProfile> found = runPromise(() -> service.findById(ctx, created.getId()));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("listAll returns all agency profiles")
    void listAllSuccess() {
        runPromise(() -> service.create(ctx, new DmAgencyProfileService.CreateAgencyProfileCommand("Agency A")));
        runPromise(() -> service.create(ctx, new DmAgencyProfileService.CreateAgencyProfileCommand("Agency B")));

        List<DmAgencyProfile> list = runPromise(() -> service.listAll(ctx));
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("CreateAgencyProfileCommand rejects blank displayName")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmAgencyProfileService.CreateAgencyProfileCommand(""));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class InMemoryAgencyRepository implements DmAgencyProfileRepository {
        private final Map<String, DmAgencyProfile> store = new ConcurrentHashMap<>();

        @Override public Promise<DmAgencyProfile> save(DmAgencyProfile p) { store.put(p.getId(), p); return Promise.of(p); }
        @Override public Promise<DmAgencyProfile> update(DmAgencyProfile p) { store.put(p.getId(), p); return Promise.of(p); }
        @Override public Promise<Optional<DmAgencyProfile>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<Optional<DmAgencyProfile>> findByAgencyTenantId(String agencyTenantId) {
            return Promise.of(store.values().stream().filter(p -> p.getAgencyTenantId().equals(agencyTenantId)).findFirst());
        }
        @Override public Promise<List<DmAgencyProfile>> listAll() { return Promise.of(List.copyOf(store.values())); }
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
