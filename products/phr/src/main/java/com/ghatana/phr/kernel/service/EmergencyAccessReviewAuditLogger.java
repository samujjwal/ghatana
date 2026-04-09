package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Records emergency access review workflow milestones for audit and compliance analysis
 * @doc.layer product
 * @doc.pattern Port
 */
public interface EmergencyAccessReviewAuditLogger {

    Promise<Void> logReviewQueued(
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    );

    Promise<Void> logReviewCompleted(
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    );
}
