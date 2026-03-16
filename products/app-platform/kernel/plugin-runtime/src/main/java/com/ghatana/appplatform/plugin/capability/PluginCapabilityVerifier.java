/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.capability;

import com.ghatana.appplatform.plugin.domain.PluginCapability;
import com.ghatana.appplatform.plugin.domain.PluginManifest;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Verifies that the capabilities a plugin declares at registration are consistent
 * with its tier and do not exceed what the platform allows (STORY-K04-007).
 *
 * <p>Called once at plugin registration, before the plugin enters
 * {@code PluginStatus#PENDING_APPROVAL} (for high-risk caps) or
 * {@code PluginStatus#ACTIVE} (for standard caps).
 *
 * <p>Verification rules:
 * <ol>
 *   <li>T1 plugins must declare no capabilities</li>
 *   <li>T2 plugins may only declare capabilities in the T2 allowed set</li>
 *   <li>T3 plugins may declare any capability but high-risk ones need external approval</li>
 *   <li>No duplicate capability names are permitted</li>
 * </ol>
 *
 * @doc.type  class
 * @doc.purpose Validates declared capabilities against tier policy at registration (K04-007)
 * @doc.layer kernel
 * @doc.pattern Guard
 */
public final class PluginCapabilityVerifier {

    private static final Logger log = LoggerFactory.getLogger(PluginCapabilityVerifier.class);

    private static final Set<String> T2_ALLOWED = Set.of(
            PluginCapability.READ_CONFIG,
            PluginCapability.READ_CALENDAR,
            PluginCapability.QUERY_REF_DATA,
            PluginCapability.EMIT_LOGS
    );

    private final Executor executor;

    public PluginCapabilityVerifier(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Verifies the capabilities declared in {@code manifest}.
     *
     * @return a {@link VerificationResult} indicating whether the plugin can be
     *         immediately activated or needs further approval
     */
    public Promise<VerificationResult> verify(PluginManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");

        return Promise.ofBlocking(executor, () -> {
            List<PluginCapability> caps = manifest.capabilities();

            // Check for duplicate capability names
            long distinct = caps.stream().map(PluginCapability::name).distinct().count();
            if (distinct != caps.size()) {
                throw new CapabilityVerificationException(
                        "Duplicate capabilities declared in manifest for plugin: " + manifest.name());
            }

            return switch (manifest.tier()) {
                case T1 -> {
                    if (!caps.isEmpty()) {
                        throw new CapabilityVerificationException(
                                "T1 plugin must not declare any capabilities. Plugin="
                                        + manifest.name() + " declared: " + caps);
                    }
                    yield VerificationResult.approved(List.of());
                }

                case T2 -> {
                    List<String> disallowed = caps.stream()
                            .map(PluginCapability::name)
                            .filter(n -> !T2_ALLOWED.contains(n))
                            .toList();
                    if (!disallowed.isEmpty()) {
                        throw new CapabilityVerificationException(
                                "T2 plugin declared capabilities not allowed for T2: " + disallowed
                                + ". Plugin=" + manifest.name());
                    }
                    yield VerificationResult.approved(List.of());
                }

                case T3 -> {
                    List<PluginCapability> highRisk = manifest.highRiskCapabilities();
                    if (highRisk.isEmpty()) {
                        yield VerificationResult.approved(List.of());
                    }
                    log.info("Plugin {} requests high-risk capabilities: {}", manifest.name(), highRisk);
                    yield VerificationResult.pendingApproval(highRisk);
                }
            };
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public record VerificationResult(
            boolean approved,
            List<PluginCapability> capabilitiesPendingApproval
    ) {
        static VerificationResult approved(List<PluginCapability> empty) {
            return new VerificationResult(true, List.of());
        }
        static VerificationResult pendingApproval(List<PluginCapability> highRisk) {
            return new VerificationResult(false, highRisk);
        }

        public boolean requiresApproval() {
            return !capabilitiesPendingApproval.isEmpty();
        }
    }

    public static final class CapabilityVerificationException extends RuntimeException {
        public CapabilityVerificationException(String message) { super(message); }
    }
}
