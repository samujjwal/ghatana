/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.security;

import com.ghatana.appplatform.plugin.domain.PluginCapability;
import com.ghatana.appplatform.plugin.domain.PluginManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects and blocks attempts by plugins to exfiltrate data beyond their declared
 * egress-whitelist (STORY-K04-015).
 *
 * <p>A T3 plugin that declares {@code EXECUTE_NETWORK} must include an endpoint
 * pattern (URL prefix) in the capability parameter. This guard intercepts outbound
 * connection requests and:
 * <ol>
 *   <li>Verifies the destination URL matches at least one declared whitelist pattern</li>
 *   <li>Checks for obvious exfiltration patterns (Base64-encoded payloads in query
 *       strings, DNS TXT record patterns, suspicious port numbers)</li>
 *   <li>Emits a {@code WARN} log and throws {@link ExfiltrationAttemptException} on violation</li>
 * </ol>
 *
 * <p>T1 and T2 plugins are unconditionally blocked from making outbound connections.
 *
 * @doc.type  class
 * @doc.purpose DLP egress guard preventing data exfiltration via plugin outbound calls (K04-015)
 * @doc.layer kernel
 * @doc.pattern Guard
 */
public final class PluginExfiltrationGuard {

    private static final Logger log = LoggerFactory.getLogger(PluginExfiltrationGuard.class);

    /** Ports blacklisted for outbound T3 connections (DNS exfiltration, SMTP, etc.). */
    private static final Set<Integer> BLOCKED_PORTS = Set.of(25, 53, 465, 587, 993, 995, 8443);

    /** Pattern that flags base64-encoded blobs in URL query strings (DLP heuristic). */
    private static final Pattern BASE64_QUERY_EXFIL = Pattern.compile("[?&][^=]+=([A-Za-z0-9+/]{40,}={0,2})");

    /**
     * Asserts that a plugin is allowed to connect to the given destination URL.
     *
     * @param manifest       the plugin manifest
     * @param destinationUrl the outbound URL the plugin is attempting to reach
     * @throws ExfiltrationAttemptException if the connection is disallowed
     */
    public void check(PluginManifest manifest, String destinationUrl) {
        Objects.requireNonNull(manifest,        "manifest");
        Objects.requireNonNull(destinationUrl,  "destinationUrl");

        switch (manifest.tier()) {
            case T1, T2 -> throw new ExfiltrationAttemptException(
                    manifest.tier() + " plugin attempted outbound connection: plugin=" + manifest.name()
                            + " url=" + destinationUrl);
            case T3 -> checkT3(manifest, destinationUrl);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void checkT3(PluginManifest manifest, String url) {
        // Collect declared egress whitelist patterns from EXECUTE_NETWORK capabilities
        List<String> whitelistPatterns = manifest.capabilities().stream()
                .filter(c -> PluginCapability.EXECUTE_NETWORK.equals(c.name()))
                .map(PluginCapability::parameter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (whitelistPatterns.isEmpty()) {
            throw new ExfiltrationAttemptException(
                    "T3 plugin="  + manifest.name() + " attempted network call but declared no EXECUTE_NETWORK endpoint whitelist");
        }

        boolean whitelisted = whitelistPatterns.stream().anyMatch(url::startsWith);
        if (!whitelisted) {
            log.warn("EXFIL_ATTEMPT plugin={} url={} whitelist={}", manifest.name(), url, whitelistPatterns);
            throw new ExfiltrationAttemptException(
                    "Plugin=" + manifest.name() + " attempted connection to non-whitelisted URL: " + url
                            + " | Allowed prefixes: " + whitelistPatterns);
        }

        // DLP heuristic: block suspicious base64 query params
        if (BASE64_QUERY_EXFIL.matcher(url).find()) {
            log.warn("EXFIL_POTENTIAL_BASE64 plugin={} url={}", manifest.name(), url);
            throw new ExfiltrationAttemptException(
                    "Plugin=" + manifest.name() + " URL contains suspicious base64 query param: " + url);
        }

        // Block known exfiltration ports
        int port = extractPort(url);
        if (port > 0 && BLOCKED_PORTS.contains(port)) {
            throw new ExfiltrationAttemptException(
                    "Plugin=" + manifest.name() + " attempted connection on blocked port " + port + ": " + url);
        }
    }

    private int extractPort(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getPort();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Thrown when a plugin attempts to make an unauthorised outbound connection. */
    public static final class ExfiltrationAttemptException extends RuntimeException {
        public ExfiltrationAttemptException(String message) { super(message); }
    }
}
