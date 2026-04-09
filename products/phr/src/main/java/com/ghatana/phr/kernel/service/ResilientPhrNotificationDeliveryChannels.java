package com.ghatana.phr.kernel.service;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adds retry and circuit-breaker protection to durable PHR notification channel delivery
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ResilientPhrNotificationDeliveryChannels implements PhrNotificationDeliveryChannels {

    private final Eventloop eventloop;
    private final PhrNotificationDeliveryChannels delegate;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker emailCircuitBreaker;
    private final CircuitBreaker smsCircuitBreaker;
    private final CircuitBreaker pushCircuitBreaker;

    public ResilientPhrNotificationDeliveryChannels(Eventloop eventloop, PhrNotificationDeliveryChannels delegate) {
        this(
            eventloop,
            delegate,
            PhrNotificationDispatchResilienceUtils.createRetryPolicy(),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-notification-delivery-email"),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-notification-delivery-sms"),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-notification-delivery-push")
        );
    }

    ResilientPhrNotificationDeliveryChannels(
        Eventloop eventloop,
        PhrNotificationDeliveryChannels delegate,
        RetryPolicy retryPolicy,
        CircuitBreaker emailCircuitBreaker,
        CircuitBreaker smsCircuitBreaker,
        CircuitBreaker pushCircuitBreaker
    ) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.emailCircuitBreaker = Objects.requireNonNull(emailCircuitBreaker, "emailCircuitBreaker must not be null");
        this.smsCircuitBreaker = Objects.requireNonNull(smsCircuitBreaker, "smsCircuitBreaker must not be null");
        this.pushCircuitBreaker = Objects.requireNonNull(pushCircuitBreaker, "pushCircuitBreaker must not be null");
    }

    @Override
    public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
        return emailCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.sendEmail(notification))
        );
    }

    @Override
    public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
        return smsCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.sendSms(notification))
        );
    }

    @Override
    public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
        return pushCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.sendPush(notification))
        );
    }
}
