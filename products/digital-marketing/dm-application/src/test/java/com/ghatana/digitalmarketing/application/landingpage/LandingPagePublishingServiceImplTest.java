package com.ghatana.digitalmarketing.application.landingpage;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.landingpage.DmLandingPage;
import com.ghatana.digitalmarketing.domain.landingpage.DmLandingPageStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("LandingPagePublishingServiceImpl")
class LandingPagePublishingServiceImplTest extends EventloopTestBase {

    private InMemoryLandingPageRepository repository;
    private FakeLandingPagePublisher publisher;
    private LandingPagePublishingServiceImpl service;
    private DmOperationContext ctx;
    private DmLandingPage draftPage;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLandingPageRepository();
        publisher = new FakeLandingPagePublisher();
        service = new LandingPagePublishingServiceImpl(
            repository,
            publisher,
            new AllowingKernelAdapter(true));

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        draftPage = DmLandingPage.builder()
            .id("lp-1")
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .slug("home-cleaning")
            .title("Home Cleaning Services")
            .contentHtml("<html>body</html>")
            .status(DmLandingPageStatus.DRAFT)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> repository.save(draftPage));
    }

    @Test
    @DisplayName("publish transitions draft page to PUBLISHED")
    void publishSuccess() {
        DmLandingPage published = runPromise(() -> service.publish(
            ctx,
            new LandingPagePublishingService.PublishLandingPageCommand(draftPage.getId())
        ));

        assertThat(published.getStatus()).isEqualTo(DmLandingPageStatus.PUBLISHED);
        assertThat(published.getPublishedUrl()).isEqualTo("https://cdn.example/tenant-1/home-cleaning");
    }

    @Test
    @DisplayName("publish rejects unauthorized actor")
    void publishUnauthorized() {
        service = new LandingPagePublishingServiceImpl(
            repository,
            publisher,
            new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.publish(
                ctx,
                new LandingPagePublishingService.PublishLandingPageCommand(draftPage.getId())
            )));
    }

    @Test
    @DisplayName("publish fails when landing page not found")
    void publishMissingPage() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.publish(
                ctx,
                new LandingPagePublishingService.PublishLandingPageCommand("missing")
            )));
    }

    @Test
    @DisplayName("publish marks landing page as FAILED when publisher fails")
    void publishRuntimeFailureMarksFailed() {
        publisher.failPublish = true;

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.publish(
                ctx,
                new LandingPagePublishingService.PublishLandingPageCommand(draftPage.getId())
            )));

        Optional<DmLandingPage> updated = runPromise(() -> repository.findById(draftPage.getId()));
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(DmLandingPageStatus.FAILED);
        assertThat(updated.get().getFailureReason()).contains("publish failed");
    }

    @Test
    @DisplayName("unpublish transitions published page to UNPUBLISHED")
    void unpublishSuccess() {
        DmLandingPage published = runPromise(() -> service.publish(
            ctx,
            new LandingPagePublishingService.PublishLandingPageCommand(draftPage.getId())
        ));

        DmLandingPage unpublished = runPromise(() -> service.unpublish(ctx, published.getId()));

        assertThat(unpublished.getStatus()).isEqualTo(DmLandingPageStatus.UNPUBLISHED);
    }

    @Test
    @DisplayName("unpublish rejects unauthorized actor")
    void unpublishUnauthorized() {
        service = new LandingPagePublishingServiceImpl(
            repository,
            publisher,
            new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.unpublish(ctx, draftPage.getId())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmOperationContext otherTenantCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmLandingPage> visible = runPromise(() -> service.findById(otherTenantCtx, draftPage.getId()));
        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("findById rejects blank id")
    void findByIdRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findById(ctx, "  ")));
    }

    @Test
    @DisplayName("publish rejects tenant-mismatched page")
    void publishTenantMismatch() {
        runPromise(() -> repository.update(
            draftPage.toBuilder().tenantId("tenant-2").build()
        ));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.publish(
                ctx,
                new LandingPagePublishingService.PublishLandingPageCommand(draftPage.getId())
            )));
    }

    static final class InMemoryLandingPageRepository implements DmLandingPageRepository {
        private final Map<String, DmLandingPage> byId = new ConcurrentHashMap<>();

        @Override
        public Promise<DmLandingPage> save(DmLandingPage landingPage) {
            byId.put(landingPage.getId(), landingPage);
            return Promise.of(landingPage);
        }

        @Override
        public Promise<Optional<DmLandingPage>> findById(String landingPageId) {
            return Promise.of(Optional.ofNullable(byId.get(landingPageId)));
        }

        @Override
        public Promise<DmLandingPage> update(DmLandingPage landingPage) {
            byId.put(landingPage.getId(), landingPage);
            return Promise.of(landingPage);
        }
    }

    static final class FakeLandingPagePublisher implements DmLandingPagePublisher {
        private boolean failPublish;

        @Override
        public Promise<String> publish(PublishLandingPageRequest request) {
            if (failPublish) {
                return Promise.ofException(new IllegalStateException("publish failed"));
            }
            return Promise.of("https://cdn.example/" + request.tenantId() + "/" + request.slug());
        }

        @Override
        public Promise<Void> unpublish(UnpublishLandingPageRequest request) {
            return Promise.complete();
        }
    }

    static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;

        AllowingKernelAdapter(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(allowed);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}
