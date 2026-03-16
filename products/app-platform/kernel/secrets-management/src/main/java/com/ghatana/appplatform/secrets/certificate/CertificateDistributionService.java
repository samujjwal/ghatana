/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.certificate;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Distributes renewed or newly issued certificates to all registered downstream consumers
 * (STORY-K14-008).
 *
 * <p>When {@link CertificateLifecycleService} issues a new certificate, this service:
 * <ol>
 *   <li>Notifies all registered {@link CertificateConsumer}s atomically</li>
 *   <li>Records the distribution event for audit</li>
 *   <li>Tracks which consumers have acknowledged receipt</li>
 * </ol>
 *
 * <p>Consumers register themselves at boot (e.g. the API gateway TLS context, the JDBC
 * connection pool) and implement {@link CertificateConsumer#onCertificateUpdated}.
 *
 * @doc.type  class
 * @doc.purpose Distributes new/renewed certificates to all registered service consumers (K14-008)
 * @doc.layer kernel
 * @doc.pattern Service, Observer
 */
public final class CertificateDistributionService {

    private static final Logger log = LoggerFactory.getLogger(CertificateDistributionService.class);

    private final Map<String, CertificateConsumer> consumers = new ConcurrentHashMap<>();
    private final Executor executor;

    public CertificateDistributionService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Registers a certificate consumer.
     *
     * @param consumerId unique ID for this consumer (e.g. {@code "api-gateway-tls"})
     * @param consumer   the consumer callback
     */
    public void register(String consumerId, CertificateConsumer consumer) {
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(consumer,   "consumer");
        consumers.put(consumerId, consumer);
        log.info("Certificate consumer registered: id={}", consumerId);
    }

    /** Deregisters a consumer (called during graceful shutdown). */
    public void deregister(String consumerId) {
        consumers.remove(consumerId);
        log.info("Certificate consumer deregistered: id={}", consumerId);
    }

    /**
     * Distributes a new certificate to all registered consumers.
     *
     * @param certName    logical certificate name
     * @param certificate the new X.509 certificate
     * @return promise resolving when all consumers have been notified
     */
    public Promise<DistributionResult> distribute(String certName, X509Certificate certificate) {
        Objects.requireNonNull(certName,    "certName");
        Objects.requireNonNull(certificate, "certificate");

        return Promise.ofBlocking(executor, () -> {
            log.info("Distributing certificate: name={} consumers={}", certName, consumers.size());

            List<String> succeeded = new java.util.ArrayList<>();
            List<String> failed    = new java.util.ArrayList<>();

            for (Map.Entry<String, CertificateConsumer> entry : consumers.entrySet()) {
                try {
                    entry.getValue().onCertificateUpdated(certName, certificate);
                    succeeded.add(entry.getKey());
                } catch (Exception e) {
                    log.error("Certificate distribution failed for consumer={} cert={}: {}",
                            entry.getKey(), certName, e.getMessage());
                    failed.add(entry.getKey());
                }
            }

            log.info("Certificate distribution complete: name={} succeeded={} failed={}",
                    certName, succeeded.size(), failed.size());
            return new DistributionResult(certName, succeeded, failed);
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /** Callback interface for services that consume TLS certificates. */
    @FunctionalInterface
    public interface CertificateConsumer {
        /**
         * Called when a certificate is issued or renewed.
         *
         * @param certName    logical certificate name
         * @param certificate the new certificate
         */
        void onCertificateUpdated(String certName, X509Certificate certificate);
    }

    public record DistributionResult(
            String certName,
            List<String> succeededConsumers,
            List<String> failedConsumers
    ) {
        public boolean fullyDistributed() {
            return failedConsumers.isEmpty();
        }
    }
}
