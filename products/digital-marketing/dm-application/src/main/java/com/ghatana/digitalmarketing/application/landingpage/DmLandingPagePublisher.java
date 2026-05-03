package com.ghatana.digitalmarketing.application.landingpage;

import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Runtime publisher port for landing page deployment/unpublish operations.
 *
 * @doc.type class
 * @doc.purpose Publishes and unpublishes landing pages on runtime hosting infrastructure (DMOS-F2-010)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DmLandingPagePublisher {

    Promise<String> publish(PublishLandingPageRequest request);

    Promise<Void> unpublish(UnpublishLandingPageRequest request);

    /**
     * Request payload to publish a landing page.
     */
    record PublishLandingPageRequest(
        String landingPageId,
        String tenantId,
        String slug,
        String title,
        String contentHtml
    ) {
        public PublishLandingPageRequest {
            Objects.requireNonNull(landingPageId, "landingPageId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(slug, "slug must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(contentHtml, "contentHtml must not be null");
            if (landingPageId.isBlank()) {
                throw new IllegalArgumentException("landingPageId must not be blank");
            }
            if (tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (slug.isBlank()) {
                throw new IllegalArgumentException("slug must not be blank");
            }
            if (title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            if (contentHtml.isBlank()) {
                throw new IllegalArgumentException("contentHtml must not be blank");
            }
        }
    }

    /**
     * Request payload to unpublish a landing page.
     */
    record UnpublishLandingPageRequest(
        String landingPageId,
        String tenantId,
        String slug,
        String publishedUrl
    ) {
        public UnpublishLandingPageRequest {
            Objects.requireNonNull(landingPageId, "landingPageId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(slug, "slug must not be null");
            Objects.requireNonNull(publishedUrl, "publishedUrl must not be null");
            if (landingPageId.isBlank()) {
                throw new IllegalArgumentException("landingPageId must not be blank");
            }
            if (tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (slug.isBlank()) {
                throw new IllegalArgumentException("slug must not be blank");
            }
            if (publishedUrl.isBlank()) {
                throw new IllegalArgumentException("publishedUrl must not be blank");
            }
        }
    }
}
