/**
 * P1-038: Public intake abuse control service.
 *
 * Provides comprehensive abuse detection and prevention for public intake endpoints:
 * <ul>
 *   <li>Rate limiting (per-IP, per-email)</li>
 *   <li>Honeypot field validation</li>
 *   <li>Duplicate submission detection</li>
 *   <li>Suspicious pattern detection</li>
 *   <li>Request size limits</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Abuse control service for public intake endpoints
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
package com.ghatana.digitalmarketing.application.abuse;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.Map;

public interface IntakeAbuseControlService {

    /**
     * Checks if a request should be blocked due to abuse detection.
     *
     * @param ctx operation context
     * @param clientIp client IP address
     * @param email email address (optional, may be null)
     * @param formData form data for pattern analysis
     * @return abuse check result
     */
    Promise<AbuseCheckResult> checkAbuse(
        DmOperationContext ctx,
        String clientIp,
        String email,
        Map<String, String> formData
    );

    /**
     * Records a successful submission for rate limiting.
     *
     * @param ctx operation context
     * @param clientIp client IP address
     * @param email email address
     */
    Promise<Void> recordSubmission(DmOperationContext ctx, String clientIp, String email);

    /**
     * Validates honeypot field (should be empty if filled by bots).
     *
     * @param honeypotValue value of honeypot field
     * @return true if honeypot is valid (empty), false if bot detected
     */
    boolean validateHoneypot(String honeypotValue);

    /**
     * Checks for duplicate submissions within time window.
     *
     * @param ctx operation context
     * @param email email address
     * @param formHash hash of form data
     * @return true if duplicate detected
     */
    Promise<Boolean> isDuplicateSubmission(DmOperationContext ctx, String email, String formHash);

    /**
     * Result of abuse check.
     */
    record AbuseCheckResult(
        boolean blocked,
        String blockReason,
        String blockCode,
        long retryAfterSeconds
    ) {
        public static AbuseCheckResult allowed() {
            return new AbuseCheckResult(false, null, null, 0);
        }

        public static AbuseCheckResult blocked(String reason, String code, long retryAfter) {
            return new AbuseCheckResult(true, reason, code, retryAfter);
        }
    }

    /**
     * Configuration for abuse controls.
     */
    record AbuseControlConfig(
        int maxRequestsPerMinute,
        int maxRequestsPerHour,
        int maxRequestsPerDay,
        boolean enableHoneypot,
        boolean enableDuplicateDetection,
        int duplicateWindowMinutes,
        int maxRequestSizeBytes
    ) {
        public static AbuseControlConfig defaults() {
            return new AbuseControlConfig(
                10,  // max 10 requests per minute
                60,  // max 60 requests per hour
                500, // max 500 requests per day
                true, // enable honeypot
                true, // enable duplicate detection
                5,   // 5 minute duplicate window
                10240 // 10KB max request size
            );
        }
    }
}
