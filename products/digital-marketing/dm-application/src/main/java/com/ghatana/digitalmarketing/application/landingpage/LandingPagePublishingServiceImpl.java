package com.ghatana.digitalmarketing.application.landingpage;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.landingpage.DmLandingPage;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Production implementation of {@link LandingPagePublishingService}.
 *
 * @doc.type class
 * @doc.purpose Publishes and unpublishes landing pages with tenant-safe authorization and audit recording (DMOS-F2-010)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class LandingPagePublishingServiceImpl implements LandingPagePublishingService {

    private final DmLandingPageRepository landingPageRepository;
    private final DmLandingPagePublisher landingPagePublisher;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public LandingPagePublishingServiceImpl(
            DmLandingPageRepository landingPageRepository,
            DmLandingPagePublisher landingPagePublisher,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.landingPageRepository = Objects.requireNonNull(landingPageRepository, "landingPageRepository must not be null");
        this.landingPagePublisher = Objects.requireNonNull(landingPagePublisher, "landingPagePublisher must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmLandingPage> publish(DmOperationContext ctx, PublishLandingPageCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content", "publish")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to publish landing pages"));
                }
                return requireOwnedLandingPage(ctx, command.landingPageId())
                    .then(page -> publishRuntime(page)
                        .then(publishedUrl -> landingPageRepository.update(page.publish(publishedUrl))
                            .then(published -> kernelAdapter.recordAudit(
                                ctx,
                                published.getId(),
                                "landing-page-published",
                                Map.of(
                                    "slug", published.getSlug(),
                                    "publishedUrl", published.getPublishedUrl()
                                )
                            ).map(__ -> published))
                        )
                        .then(result -> Promise.of(result), e ->
                            landingPageRepository.update(page.markFailed(e.getMessage()))
                                .then(__ -> Promise.ofException(e))
                        )
                    );
            });
    }

    @Override
    public Promise<DmLandingPage> unpublish(DmOperationContext ctx, String landingPageId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (landingPageId == null || landingPageId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("landingPageId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "content", "publish")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to unpublish landing pages"));
                }
                return requireOwnedLandingPage(ctx, landingPageId)
                    .then(page -> landingPagePublisher.unpublish(new DmLandingPagePublisher.UnpublishLandingPageRequest(
                            page.getId(),
                            page.getTenantId(),
                            page.getSlug(),
                            page.getPublishedUrl()
                        ))
                        .then(__ -> landingPageRepository.update(page.unpublish())
                            .then(unpublished -> kernelAdapter.recordAudit(
                                ctx,
                                unpublished.getId(),
                                "landing-page-unpublished",
                                Map.of("slug", unpublished.getSlug())
                            ).map(___ -> unpublished))
                        )
                    );
            });
    }

    @Override
    public Promise<Optional<DmLandingPage>> findById(DmOperationContext ctx, String landingPageId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (landingPageId == null || landingPageId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("landingPageId must not be blank"));
        }

        return landingPageRepository.findById(landingPageId)
            .map(opt -> opt.filter(page -> page.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    private Promise<DmLandingPage> requireOwnedLandingPage(DmOperationContext ctx, String landingPageId) {
        return landingPageRepository.findById(landingPageId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new NoSuchElementException("Landing page not found: " + landingPageId));
                }
                return Promise.of(opt.get());
            });
    }

    private Promise<String> publishRuntime(DmLandingPage page) {
        return landingPagePublisher.publish(new DmLandingPagePublisher.PublishLandingPageRequest(
            page.getId(),
            page.getTenantId(),
            page.getSlug(),
            page.getTitle(),
            page.getContentHtml()
        ));
    }
}
