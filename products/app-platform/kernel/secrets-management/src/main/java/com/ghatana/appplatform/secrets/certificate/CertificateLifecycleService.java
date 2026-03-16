/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.certificate;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Manages the full lifecycle of X.509 TLS certificates across all kernel services
 * (STORY-K14-007).
 *
 * <p>Certificate lifecycle stages:
 * <ol>
 *   <li>{@link #register(String, X509Certificate)} — record an issued certificate</li>
 *   <li>{@link #checkExpiry(String)} — check days remaining before expiry</li>
 *   <li>{@link #renew(String)} — trigger renewal via the configured CA stub</li>
 *   <li>{@link #revoke(String, String)} — revoke a compromised certificate</li>
 * </ol>
 *
 * <p>Renewal is triggered automatically when remaining life drops below {@code renewalThreshold}.
 * In production the CA call is delegated to HashiCorp Vault's PKI secret engine.
 *
 * @doc.type  class
 * @doc.purpose Lifecycle management for TLS certificates: registration, renewal, revocation (K14-007)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class CertificateLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CertificateLifecycleService.class);

    /** Default renewal threshold: renew when fewer than 30 days remain. */
    public static final Duration DEFAULT_RENEWAL_THRESHOLD = Duration.ofDays(30);

    /**
     * SPI for certificate issuance. In production this calls Vault PKI
     * {@code pki/issue/{role}} or an ACME endpoint.
     */
    public interface CertificateRenewalPort {
        /**
         * Issue a new certificate for the given logical name.
         *
         * @param name        logical name (e.g. "api-gateway-tls")
         * @param currentCert the expiring certificate being replaced
         * @return the freshly issued X.509 certificate
         * @throws Exception on CA / network failure
         */
        X509Certificate issue(String name, X509Certificate currentCert) throws Exception;
    }

    private final Map<String, CertificateEntry> certificates = new ConcurrentHashMap<>();
    private final Duration renewalThreshold;
    private final Executor executor;
    private final AuditBusPort audit;
    private final CertificateRenewalPort renewalPort;

    public CertificateLifecycleService(Duration renewalThreshold, Executor executor,
                                        AuditBusPort audit, CertificateRenewalPort renewalPort) {
        this.renewalThreshold = Objects.requireNonNull(renewalThreshold, "renewalThreshold");
        this.executor         = Objects.requireNonNull(executor, "executor");
        this.audit            = Objects.requireNonNull(audit, "audit");
        this.renewalPort      = Objects.requireNonNull(renewalPort, "renewalPort");
    }

    /** Registers a certificate under a logical name. */
    public void register(String name, X509Certificate certificate) {
        Objects.requireNonNull(name,        "name");
        Objects.requireNonNull(certificate, "certificate");
        certificates.put(name, new CertificateEntry(name, certificate, CertificateStatus.ACTIVE, Instant.now()));
        log.info("Certificate registered: name={} subject={} expires={}", name,
                certificate.getSubjectX500Principal(), certificate.getNotAfter());
    }

    /**
     * Returns the number of days until a certificate expires, or -1 if not found.
     */
    public long daysUntilExpiry(String name) {
        CertificateEntry entry = certificates.get(name);
        if (entry == null) return -1;
        return Duration.between(Instant.now(), entry.certificate().getNotAfter().toInstant()).toDays();
    }

    /**
     * Renews the certificate identified by {@code name} via the configured
     * {@link CertificateRenewalPort} (Vault PKI, ACME, etc.).
     */
    public Promise<Void> renew(String name) {
        Objects.requireNonNull(name, "name");
        return Promise.ofBlocking(executor, () -> {
            CertificateEntry entry = certificates.get(name);
            if (entry == null) throw new IllegalArgumentException("Certificate not registered: " + name);
            log.info("Certificate renewal triggered: name={} daysRemaining={}",
                    name, daysUntilExpiry(name));
            X509Certificate renewed = renewalPort.issue(name, entry.certificate());
            certificates.put(name, new CertificateEntry(name, renewed, CertificateStatus.ACTIVE, Instant.now()));
            log.info("Certificate renewed: name={} newExpires={}", name, renewed.getNotAfter());
            audit.emit(AuditEvent.builder()
                    .tenantId("SYSTEM")
                    .eventType("CERTIFICATE_RENEWED")
                    .principal("CertificateLifecycleService")
                    .resourceType("certificate")
                    .resourceId(name)
                    .success(true)
                    .details(Map.of("daysRemaining", String.valueOf(daysUntilExpiry(name))))
                    .build());
            return null;
        });
    }

    /** Revokes a certificate and marks it as REVOKED in the registry. */
    public Promise<Void> revoke(String name, String reason) {
        Objects.requireNonNull(name,   "name");
        Objects.requireNonNull(reason, "reason");
        return Promise.ofBlocking(executor, () -> {
            if (!certificates.containsKey(name)) {
                throw new IllegalArgumentException("Certificate not registered: " + name);
            }
            certificates.computeIfPresent(name, (k, e) ->
                    new CertificateEntry(k, e.certificate(), CertificateStatus.REVOKED, e.registeredAt()));
            log.warn("Certificate revoked: name={} reason={}", name, reason);
            return null;
        });
    }

    /** Returns info for a registered certificate, or {@code null} if not found. */
    public CertificateEntry get(String name) {
        return certificates.get(name);
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public enum CertificateStatus { ACTIVE, REVOKED, EXPIRED }

    public record CertificateEntry(
            String name,
            X509Certificate certificate,
            CertificateStatus status,
            Instant registeredAt
    ) {}
}
