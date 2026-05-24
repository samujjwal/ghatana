package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListing;
import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListingStatus;
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

@DisplayName("DmMarketplaceListingServiceImpl")
class DmMarketplaceListingServiceImplTest extends EventloopTestBase {

    private EphemeralListingRepository repository;
    private DmMarketplaceListingServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralListingRepository();
        service = new DmMarketplaceListingServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmMarketplaceListingService.CreateMarketplaceListingCommand cmd() {
        return new DmMarketplaceListingService.CreateMarketplaceListingCommand(
            "playbook", "item-1", "My Listing", "Great content", 9990000L, "USD"
        );
    }

    @Test
    @DisplayName("create produces DRAFT listing")
    void createSuccess() {
        DmMarketplaceListing listing = runPromise(() -> service.create(ctx, cmd()));

        assertThat(listing.getTenantId()).isEqualTo("tenant-1");
        assertThat(listing.getTitle()).isEqualTo("My Listing");
        assertThat(listing.getStatus()).isEqualTo(DmMarketplaceListingStatus.DRAFT);
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmMarketplaceListingServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx, cmd())));
    }

    @Test
    @DisplayName("publish transitions to PUBLISHED")
    void publishSuccess() {
        DmMarketplaceListing created = runPromise(() -> service.create(ctx, cmd()));
        DmMarketplaceListing published = runPromise(() -> service.publish(ctx, created.getId()));

        assertThat(published.getStatus()).isEqualTo(DmMarketplaceListingStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("unpublish transitions PUBLISHED to UNLISTED")
    void unpublishSuccess() {
        DmMarketplaceListing created = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.publish(ctx, created.getId()));
        DmMarketplaceListing unlisted = runPromise(() -> service.unpublish(ctx, created.getId()));

        assertThat(unlisted.getStatus()).isEqualTo(DmMarketplaceListingStatus.UNLISTED);
    }

    @Test
    @DisplayName("listByTenant returns tenant-scoped listings")
    void listByTenantSuccess() {
        runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.create(ctx, cmd()));

        List<DmMarketplaceListing> list = runPromise(() -> service.listByTenant(ctx));
        assertThat(list).hasSize(2);
        assertThat(list).allMatch(l -> l.getTenantId().equals("tenant-1"));
    }

    @Test
    @DisplayName("listPublished returns only published listings")
    void listPublishedSuccess() {
        DmMarketplaceListing l1 = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.publish(ctx, l1.getId()));

        List<DmMarketplaceListing> published = runPromise(() -> service.listPublished(ctx));
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getStatus()).isEqualTo(DmMarketplaceListingStatus.PUBLISHED);
    }

    @Test
    @DisplayName("CreateMarketplaceListingCommand rejects blank title")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMarketplaceListingService.CreateMarketplaceListingCommand(
                "playbook", "item-1", "", "desc", 0L, "USD"));
    }

    @Test
    @DisplayName("CreateMarketplaceListingCommand rejects blank itemType")
    void commandValidationBlankItemType() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMarketplaceListingService.CreateMarketplaceListingCommand(
                "", "item-1", "Title", "desc", 0L, "USD"));
    }

    @Test
    @DisplayName("CreateMarketplaceListingCommand rejects blank itemId")
    void commandValidationBlankItemId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMarketplaceListingService.CreateMarketplaceListingCommand(
                "playbook", "", "Title", "desc", 0L, "USD"));
    }

    @Test
    @DisplayName("CreateMarketplaceListingCommand rejects null currency")
    void commandValidationNullCurrency() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMarketplaceListingService.CreateMarketplaceListingCommand(
                "playbook", "item-1", "Title", "desc", 0L, null));
    }

    @Test
    @DisplayName("CreateMarketplaceListingCommand rejects negative priceMicros")
    void commandValidationNegativePrice() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMarketplaceListingService.CreateMarketplaceListingCommand(
                "playbook", "item-1", "Title", "desc", -1L, "USD"));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class EphemeralListingRepository implements DmMarketplaceListingRepository {
        private final Map<String, DmMarketplaceListing> store = new ConcurrentHashMap<>();

        @Override public Promise<DmMarketplaceListing> save(DmMarketplaceListing l) { store.put(l.getId(), l); return Promise.of(l); }
        @Override public Promise<DmMarketplaceListing> update(DmMarketplaceListing l) { store.put(l.getId(), l); return Promise.of(l); }
        @Override public Promise<Optional<DmMarketplaceListing>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmMarketplaceListing>> listByTenant(String tenantId) {
            List<DmMarketplaceListing> r = new ArrayList<>();
            for (DmMarketplaceListing l : store.values()) if (l.getTenantId().equals(tenantId)) r.add(l);
            return Promise.of(r);
        }
        @Override public Promise<List<DmMarketplaceListing>> listByStatus(DmMarketplaceListingStatus status) {
            List<DmMarketplaceListing> r = new ArrayList<>();
            for (DmMarketplaceListing l : store.values()) if (l.getStatus() == status) r.add(l);
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
