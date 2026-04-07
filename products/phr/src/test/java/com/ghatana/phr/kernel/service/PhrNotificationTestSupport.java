package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;
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
}