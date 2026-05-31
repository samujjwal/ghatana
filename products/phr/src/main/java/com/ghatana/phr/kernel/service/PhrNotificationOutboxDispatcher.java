package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Dispatches pending durable PHR notification outbox entries to concrete email, SMS, and push providers
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PhrNotificationOutboxDispatcher extends PhrServiceBase {

    private final PhrNotificationDeliveryChannels deliveryChannels;

    public PhrNotificationOutboxDispatcher(KernelContext context) {
        this(context, PhrNotificationDeliveryChannelsFactory.fromContext(context));
    }

    public PhrNotificationOutboxDispatcher(KernelContext context, PhrNotificationDeliveryChannels deliveryChannels) {
        super(context);
        this.deliveryChannels = Objects.requireNonNull(deliveryChannels, "deliveryChannels must not be null");
    }

    @Override
    public String getName() {
        return "phr-notification-outbox-dispatcher";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return Promise.complete();
    }

    public Promise<DispatchResult> dispatchPendingNotifications(int limit) {
        ensureRunning();

        int boundedLimit = Math.max(1, limit);
        return queryRecords(
            DurablePhrNotificationSender.OUTBOX_DATASET,
            "status = :status",
            Map.of("status", DurablePhrNotificationSender.NotificationStatus.PENDING.name()),
            boundedLimit,
            0,
            DurablePhrNotificationSender.NotificationOutboxEntry.class
        ).map(entries -> entries.stream()
            .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
            .toList())
            .then(entries -> Promises.toList(entries.stream().map(this::dispatchNotification).toList())
                .map(outcomes -> summarize(entries.size(), outcomes)));
    }

    public Promise<DurablePhrNotificationSender.NotificationOutboxEntry> getNotification(String notificationId) {
        ensureRunning();
        String sanitizedNotificationId = PhrInputSanitizationUtils.requireSafeIdentifier(notificationId, "notificationId");
        return readRecord(
            DurablePhrNotificationSender.OUTBOX_DATASET,
            sanitizedNotificationId,
            DurablePhrNotificationSender.NotificationOutboxEntry.class
        ).map(opt -> opt.orElseThrow(() -> new IllegalStateException("Notification not found: " + sanitizedNotificationId)));
    }

    private Promise<DispatchOutcome> dispatchNotification(DurablePhrNotificationSender.NotificationOutboxEntry entry) {
        Instant attemptedAt = Instant.now();
        PhrNotificationDeliveryChannels.NotificationEnvelope envelope = new PhrNotificationDeliveryChannels.NotificationEnvelope(
            entry.id(),
            entry.patientId(),
            entry.recipientId(),
            entry.providerId(),
            entry.referenceId(),
            entry.referenceType(),
            entry.notificationType(),
            entry.channel(),
            entry.scheduledFor(),
            entry.createdAt(),
            entry.correlationId(),
            entry.traceOperation(),
            entry.safeReasonCode(),
            entry.deepLinkId()
        );

        return dispatchByChannel(entry.channel(), envelope)
            .then((receipt, exception) -> {
                if (exception == null) {
                    DurablePhrNotificationSender.NotificationOutboxEntry delivered = entry.markDelivered(
                        receipt.providerMessageId(),
                        receipt.deliveredAt()
                    );
                    return updateRecord(
                        DurablePhrNotificationSender.OUTBOX_DATASET,
                        entry.id(),
                        delivered,
                        mutationMetadata(DurablePhrNotificationSender.metadataFor(delivered), delivered.recipientId()),
                        "PhrNotificationOutboxEntry",
                        1
                    ).map(updated -> new DispatchOutcome(updated.id(), updated.status(), updated.providerMessageId(), updated.failureReason()));
                }
                String failureReason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                DurablePhrNotificationSender.NotificationOutboxEntry failed = entry.markFailed(failureReason, attemptedAt);
                return updateRecord(
                    DurablePhrNotificationSender.OUTBOX_DATASET,
                    entry.id(),
                    failed,
                    mutationMetadata(DurablePhrNotificationSender.metadataFor(failed), failed.recipientId()),
                    "PhrNotificationOutboxEntry",
                    1
                ).map(updated -> new DispatchOutcome(updated.id(), updated.status(), updated.providerMessageId(), updated.failureReason()));
            });
    }

    private Promise<PhrNotificationDeliveryChannels.DeliveryReceipt> dispatchByChannel(
        PhrNotificationSender.NotificationChannel channel,
        PhrNotificationDeliveryChannels.NotificationEnvelope envelope
    ) {
        return switch (channel) {
            case EMAIL -> deliveryChannels.sendEmail(envelope);
            case SMS -> deliveryChannels.sendSms(envelope);
            case PUSH -> deliveryChannels.sendPush(envelope);
        };
    }

    private static DispatchResult summarize(int processed, List<DispatchOutcome> outcomes) {
        long delivered = outcomes.stream()
            .filter(outcome -> outcome.status() == DurablePhrNotificationSender.NotificationStatus.DELIVERED)
            .count();
        long failed = outcomes.stream()
            .filter(outcome -> outcome.status() == DurablePhrNotificationSender.NotificationStatus.FAILED)
            .count();
        return new DispatchResult(processed, delivered, failed, outcomes);
    }

    public record DispatchOutcome(
        String notificationId,
        DurablePhrNotificationSender.NotificationStatus status,
        String providerMessageId,
        String failureReason
    ) {}

    public record DispatchResult(
        int processedCount,
        long deliveredCount,
        long failedCount,
        List<DispatchOutcome> outcomes
    ) {}
}
