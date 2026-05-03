package com.ghatana.digitalmarketing.application.landingpage;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.landingpage.DmLandingPage;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;

/**
 * Service contract for landing page publishing runtime operations.
 *
 * @doc.type class
 * @doc.purpose Executes publish/unpublish landing page runtime lifecycle with guardrails (DMOS-F2-010)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface LandingPagePublishingService {

    Promise<DmLandingPage> publish(DmOperationContext ctx, PublishLandingPageCommand command);

    Promise<DmLandingPage> unpublish(DmOperationContext ctx, String landingPageId);

    Promise<Optional<DmLandingPage>> findById(DmOperationContext ctx, String landingPageId);

    /**
     * Command to publish an existing landing page.
     */
    record PublishLandingPageCommand(String landingPageId) {
        public PublishLandingPageCommand {
            Objects.requireNonNull(landingPageId, "landingPageId must not be null");
            if (landingPageId.isBlank()) {
                throw new IllegalArgumentException("landingPageId must not be blank");
            }
        }
    }
}
