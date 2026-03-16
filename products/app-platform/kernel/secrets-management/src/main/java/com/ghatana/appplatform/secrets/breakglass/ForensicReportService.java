/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.breakglass;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Generates forensic reports for all break-glass secret access events (STORY-K14-010).
 *
 * <p>After an incident is resolved, the security team requests a forensic report for each
 * break-glass grant that was exercised. The report includes:
 * <ul>
 *   <li>Grant metadata (issuer, reason, duration, secret paths)</li>
 *   <li>Per-access log: timestamp, path, accessor identity</li>
 *   <li>Integrity hash of the access log (SHA-256 for non-repudiation)</li>
 * </ul>
 *
 * <p>Reports should be exported as PDF via {@code AuditEvidencePdfGenerator} and
 * submitted to the compliance team.
 *
 * @doc.type  class
 * @doc.purpose Generates forensic reports for break-glass secret access events (K14-010)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class ForensicReportService {

    private static final Logger log = LoggerFactory.getLogger(ForensicReportService.class);

    private final Executor executor;

    public ForensicReportService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Generates a forensic report for a completed break-glass grant.
     *
     * @param grant        the grant to report on
     * @param accessEvents ordered list of recorded access events during the grant window
     * @return promise resolving to a {@link ForensicReport}
     */
    public Promise<ForensicReport> generate(BreakGlassSecretAccessService.BreakGlassGrant grant,
                                             List<AccessEvent> accessEvents) {
        Objects.requireNonNull(grant,        "grant");
        Objects.requireNonNull(accessEvents, "accessEvents");

        return Promise.ofBlocking(executor, () -> {
            String integrityHash = computeIntegrityHash(grant, accessEvents);
            ForensicReport report = new ForensicReport(
                    grant.grantId(),
                    grant.requestorId(),
                    grant.reason(),
                    grant.issuedAt(),
                    grant.expiresAt(),
                    grant.secretPaths(),
                    List.copyOf(accessEvents),
                    integrityHash,
                    Instant.now()
            );
            log.info("Forensic report generated: grantId={} accessEvents={} hash={}",
                    grant.grantId(), accessEvents.size(), integrityHash.substring(0, 8) + "...");
            return report;
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String computeIntegrityHash(BreakGlassSecretAccessService.BreakGlassGrant grant,
                                         List<AccessEvent> events) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(grant.grantId().getBytes());
            digest.update(grant.requestorId().getBytes());
            for (AccessEvent e : events) {
                digest.update(e.secretPath().getBytes());
                digest.update(e.accessedAt().toString().getBytes());
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * A single secret access event within a break-glass grant window.
     */
    public record AccessEvent(
            String grantId,
            String accessorId,
            String secretPath,
            Instant accessedAt
    ) {}

    /**
     * Complete forensic report for a break-glass grant.
     */
    public record ForensicReport(
            String grantId,
            String requestorId,
            String reason,
            Instant grantIssuedAt,
            Instant grantExpiredAt,
            java.util.Set<String> coveredSecretPaths,
            List<AccessEvent> accessEvents,
            String accessLogIntegrityHash,
            Instant reportGeneratedAt
    ) {}
}
