package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;
import java.time.Instant;

enum NoOpPhrNotificationDeliveryChannels implements PhrNotificationDeliveryChannels {
    INSTANCE;

    @Override
    public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
        return Promise.of(new DeliveryReceipt("noop-email-" + notification.notificationId(), Instant.now()));
    }

    @Override
    public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
        return Promise.of(new DeliveryReceipt("noop-sms-" + notification.notificationId(), Instant.now()));
    }

    @Override
    public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
        return Promise.of(new DeliveryReceipt("noop-push-" + notification.notificationId(), Instant.now()));
    }
}
