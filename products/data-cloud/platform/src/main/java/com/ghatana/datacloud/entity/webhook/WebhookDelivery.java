package com.ghatana.datacloud.entity.webhook;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for webhook HTTP delivery attempt.
 *
 * <p><b>Purpose</b><br>
 * Tracks individual webhook delivery attempts including request/response data,
 * timestamps, HTTP status, and retry information. Provides forensics for
 * webhook operations and failure analysis.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WebhookDelivery delivery = new WebhookDelivery(
 *     UUID.randomUUID(),
 *     "tenant-123",
 *     webhookId,
 *     eventId,
 *     "https://example.com/webhook",
 *     payload,
 *     1
 * );
 *
 * // Mark as delivered
 * delivery = delivery.withResponse(200, "{\"success\":true}");
 * }</pre>
 *
 * <p><b>Lifecycle</b><br>
 * 1. Created with request URL and payload
 * 2. Delivery attempted (HTTP request sent)
 * 3. Response received (status code, body)
 * 4. Success/failure determined based on status code
 * 5. Retry scheduled if transient failure
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in hexagonal architecture
 * - Immutable value object pattern
 * - Supports multi-tenancy (tenantId field)
 * - Enables audit trail of webhook operations
 *
 * <p><b>Thread Safety</b><br>
 * Immutable (all fields final). Safe to share across threads.
 *
 * @see Webhook
 * @see WebhookEvent
 * @doc.type class
 * @doc.purpose Webhook delivery attempt tracking
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public class WebhookDelivery {

    private final UUID id;
    private final String tenantId;
    private final UUID webhookId;
    private final UUID eventId;
    private final String url;
    private final String requestPayload;
    private final int attemptNumber;
    private final Instant createdAt;
    private final Integer responseStatus;
    private final String responseBody;
    private final Instant deliveredAt;

    /**
     * Create webhook delivery record.
     *
     * @param id delivery ID (unique)
     * @param tenantId tenant identifier
     * @param webhookId webhook ID
     * @param eventId event ID
     * @param url delivery URL
     * @param requestPayload JSON request payload
     * @param attemptNumber attempt number (1-based)
     */
    public WebhookDelivery(
            UUID id,
            String tenantId,
            UUID webhookId,
            UUID eventId,
            String url,
            String requestPayload,
            int attemptNumber) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.webhookId = Objects.requireNonNull(webhookId, "webhookId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.url = Objects.requireNonNull(url, "url cannot be null");
        this.requestPayload = Objects.requireNonNull(requestPayload, "requestPayload cannot be null");

        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (url.isBlank()) {
            throw new IllegalArgumentException("url cannot be blank");
        }

        this.attemptNumber = attemptNumber;
        this.createdAt = Instant.now();
        this.responseStatus = null;
        this.responseBody = null;
        this.deliveredAt = null;
    }

    /**
     * Create webhook delivery with response.
     *
     * @param id delivery ID (unique)
     * @param tenantId tenant identifier
     * @param webhookId webhook ID
     * @param eventId event ID
     * @param url delivery URL
     * @param requestPayload JSON request payload
     * @param attemptNumber attempt number
     * @param responseStatus HTTP response status code
     * @param responseBody response body
     * @param deliveredAt delivery timestamp
     * @param createdAt creation timestamp
     */
    private WebhookDelivery(
            UUID id,
            String tenantId,
            UUID webhookId,
            UUID eventId,
            String url,
            String requestPayload,
            int attemptNumber,
            Integer responseStatus,
            String responseBody,
            Instant deliveredAt,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.webhookId = webhookId;
        this.eventId = eventId;
        this.url = url;
        this.requestPayload = requestPayload;
        this.attemptNumber = attemptNumber;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.deliveredAt = deliveredAt;
        this.createdAt = createdAt;
    }

    /**
     * Create new delivery with response data.
     *
     * @param responseStatus HTTP status code
     * @param responseBody response body
     * @return new WebhookDelivery with response data
     */
    public WebhookDelivery withResponse(int responseStatus, String responseBody) {
        Objects.requireNonNull(responseBody, "responseBody cannot be null");
        return new WebhookDelivery(
                this.id,
                this.tenantId,
                this.webhookId,
                this.eventId,
                this.url,
                this.requestPayload,
                this.attemptNumber,
                responseStatus,
                responseBody,
                Instant.now(),
                this.createdAt);
    }

    /**
     * Check if delivery was successful (2xx status code).
     *
     * @return true if responseStatus is 2xx
     */
    public boolean isSuccess() {
        return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
    }

    /**
     * Check if delivery should be retried (5xx or timeout).
     *
     * @return true if error is transient and should be retried
     */
    public boolean isRetryable() {
        if (responseStatus == null) {
            return true; // Network failure, should retry
        }
        return responseStatus >= 500 || responseStatus == 408 || responseStatus == 429;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getWebhookId() {
        return webhookId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getUrl() {
        return url;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    @Override
    public String toString() {
        return "WebhookDelivery{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", webhookId=" + webhookId +
                ", eventId=" + eventId +
                ", url='" + url + '\'' +
                ", attemptNumber=" + attemptNumber +
                ", responseStatus=" + responseStatus +
                ", isSuccess=" + isSuccess() +
                '}';
    }
}
