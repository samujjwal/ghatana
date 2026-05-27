package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Sends compliance and review notifications for break-glass emergency access events
 * @doc.layer product
 * @doc.pattern Port
 */
public interface EmergencyAccessNotificationSender {

    Promise<Void> notifyComplianceLead(
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    );

    Promise<Void> scheduleMandatoryReview(
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    );

    Promise<Void> notifyEscalation(
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    );

    Promise<Void> notifyPatient(
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    );
}
