package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

enum NoOpPhrNotificationSender implements PhrNotificationSender {
    INSTANCE;

    @Override
    public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
        return Promise.complete();
    }

    @Override
    public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
        return Promise.complete();
    }

    @Override
    public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
        return Promise.complete();
    }

    @Override
    public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
        return Promise.complete();
    }
}
