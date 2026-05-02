package com.ghatana.digitalmarketing.application.brand;

import com.ghatana.digitalmarketing.application.offer.ProductOfferRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.brand.BrandProfile;
import com.ghatana.digitalmarketing.domain.offer.ProductOffer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BrandCatalogServiceImpl")
class BrandCatalogServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryBrandProfileRepository brandRepository;
    private InMemoryProductOfferRepository offerRepository;
    private BrandCatalogServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        brandRepository = new InMemoryBrandProfileRepository();
        offerRepository = new InMemoryProductOfferRepository();
        service = new BrandCatalogServiceImpl(kernelAdapter, brandRepository, offerRepository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("upsertBrandProfile creates and then updates profile")
    void shouldUpsertBrandProfile() {
        BrandCatalogService.UpsertBrandProfileCommand createCommand = new BrandCatalogService.UpsertBrandProfileCommand(
            "Acme",
            "Direct",
            List.of("#111111"),
            List.of("US")
        );

        BrandProfile created = runPromise(() -> service.upsertBrandProfile(ctx, createCommand));
        BrandProfile updated = runPromise(() -> service.upsertBrandProfile(
            ctx,
            new BrandCatalogService.UpsertBrandProfileCommand("Acme 2", "Friendly", List.of("#222222"), List.of("US", "CA"))
        ));

        assertThat(created.getDisplayName()).isEqualTo("Acme");
        assertThat(updated.getDisplayName()).isEqualTo("Acme 2");
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(kernelAdapter.auditActions).contains("upsert");
    }

    @Test
    @DisplayName("create and update offer works with list filtering")
    void shouldCreateUpdateAndListOffers() {
        ProductOffer created = runPromise(() -> service.createOffer(ctx,
            new BrandCatalogService.CreateOfferCommand("Offer A", "Desc", new BigDecimal("99.00"), "USD")));

        ProductOffer updated = runPromise(() -> service.updateOffer(ctx, created.getId(),
            new BrandCatalogService.UpdateOfferCommand("Offer A+", "Desc2", new BigDecimal("149.00"), "USD", false)));

        List<ProductOffer> activeOnly = runPromise(() -> service.listOffers(ctx, false));
        List<ProductOffer> includeInactive = runPromise(() -> service.listOffers(ctx, true));

        assertThat(updated.isActive()).isFalse();
        assertThat(activeOnly).isEmpty();
        assertThat(includeInactive).hasSize(1);
        assertThat(kernelAdapter.auditActions).contains("create", "update");
    }

    @Test
    @DisplayName("denies unauthorized brand write")
    void shouldDenyUnauthorizedBrandWrite() {
        kernelAdapter.defaultAllowed = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.upsertBrandProfile(
                ctx,
                new BrandCatalogService.UpsertBrandProfileCommand("Acme", "Tone", List.of(), List.of())
            )));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new BrandCatalogServiceImpl(null, brandRepository, offerRepository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BrandCatalogServiceImpl(kernelAdapter, null, offerRepository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BrandCatalogServiceImpl(kernelAdapter, brandRepository, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getBrandProfile throws when profile missing")
    void shouldThrowWhenBrandProfileMissing() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getBrandProfile(ctx)));
    }

    @Test
    @DisplayName("denies unauthorized reads and listings")
    void shouldDenyUnauthorizedReadsAndLists() {
        kernelAdapter.defaultAllowed = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getBrandProfile(ctx)));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listOffers(ctx, true)));
    }

    @Test
    @DisplayName("updateOffer fails when offer missing")
    void shouldFailWhenOfferMissing() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.updateOffer(
                ctx,
                "missing-offer",
                new BrandCatalogService.UpdateOfferCommand("Offer", "desc", new BigDecimal("10.00"), "USD", true)
            )));
    }

    private static final class InMemoryBrandProfileRepository implements BrandProfileRepository {
        private final ConcurrentHashMap<String, BrandProfile> store = new ConcurrentHashMap<>();

        @Override
        public Promise<BrandProfile> save(BrandProfile profile) {
            store.put(profile.getWorkspaceId().getValue(), profile);
            return Promise.of(profile);
        }

        @Override
        public Promise<Optional<BrandProfile>> findByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue())));
        }
    }

    private static final class InMemoryProductOfferRepository implements ProductOfferRepository {
        private final ConcurrentHashMap<String, ProductOffer> store = new ConcurrentHashMap<>();

        @Override
        public Promise<ProductOffer> save(ProductOffer offer) {
            store.put(offer.getWorkspaceId().getValue() + ":" + offer.getId(), offer);
            return Promise.of(offer);
        }

        @Override
        public Promise<Optional<ProductOffer>> findById(DmWorkspaceId workspaceId, String offerId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue() + ":" + offerId)));
        }

        @Override
        public Promise<List<ProductOffer>> listByWorkspace(DmWorkspaceId workspaceId, boolean includeInactive) {
            List<ProductOffer> result = store.values().stream()
                .filter(offer -> offer.getWorkspaceId().equals(workspaceId))
                .filter(offer -> includeInactive || offer.isActive())
                .toList();
            return Promise.of(result);
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private boolean defaultAllowed = true;
        private final List<String> auditActions = new ArrayList<>();

        @Override public void start() { }
        @Override public void stop() { }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(defaultAllowed);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) {
            auditActions.add(action);
            return Promise.of("audit-1");
        }
    }
}
