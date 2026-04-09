package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class PhrNotificationTestSupport {

    private PhrNotificationTestSupport() {}

    static final class RecordingNotificationSender implements PhrNotificationSender {

        private final List<AppointmentReminderNotification> scheduledAppointmentReminders = new ArrayList<>();
        private final List<AppointmentReminderNotification> cancelledAppointmentReminders = new ArrayList<>();
        private final List<ConsentChangeNotification> consentChangeNotifications = new ArrayList<>();
        private final List<TelemedicineSessionNotification> telemedicineNotifications = new ArrayList<>();

        @Override
        public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
            scheduledAppointmentReminders.add(notification);
            return Promise.complete();
        }

        @Override
        public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
            cancelledAppointmentReminders.add(notification);
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
            consentChangeNotifications.add(notification);
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
            telemedicineNotifications.add(notification);
            return Promise.complete();
        }

        List<AppointmentReminderNotification> scheduledAppointmentReminders() {
            return scheduledAppointmentReminders;
        }

        List<AppointmentReminderNotification> cancelledAppointmentReminders() {
            return cancelledAppointmentReminders;
        }

        List<ConsentChangeNotification> consentChangeNotifications() {
            return consentChangeNotifications;
        }

        List<TelemedicineSessionNotification> telemedicineNotifications() {
            return telemedicineNotifications;
        }
    }

    static final class RecordingDeliveryChannels implements PhrNotificationDeliveryChannels {

        private final List<NotificationEnvelope> emailNotifications = new ArrayList<>();
        private final List<NotificationEnvelope> smsNotifications = new ArrayList<>();
        private final List<NotificationEnvelope> pushNotifications = new ArrayList<>();

        @Override
        public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
            emailNotifications.add(notification);
            return Promise.of(new DeliveryReceipt("email-" + notification.notificationId(), Instant.now()));
        }

        @Override
        public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
            smsNotifications.add(notification);
            return Promise.of(new DeliveryReceipt("sms-" + notification.notificationId(), Instant.now()));
        }

        @Override
        public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
            pushNotifications.add(notification);
            return Promise.of(new DeliveryReceipt("push-" + notification.notificationId(), Instant.now()));
        }

        List<NotificationEnvelope> emailNotifications() {
            return emailNotifications;
        }

        List<NotificationEnvelope> smsNotifications() {
            return smsNotifications;
        }

        List<NotificationEnvelope> pushNotifications() {
            return pushNotifications;
        }
    }
}
