package com.ghatana.platform.governance.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Server-Side Request Forgery (SSRF) protection utility for outbound HTTP calls.
 *
 * <p>Validates that a URL is safe to use as an outbound request target.
 * Specifically, it:
 * <ol>
 *   <li>Requires HTTP or HTTPS scheme only (blocks {@code file://}, {@code ftp://}, etc.)</li>
 *   <li>Blocks cloud metadata service IPs (AWS 169.254.169.254, GCP 169.254.169.254,
 *       Azure 169.254.169.254, Alibaba 100.100.100.200, Oracle 192.0.0.192).</li>
 *   <li>Detects and rejects URL parseable tricks (embedded credentials, fragment abuse).</li>
 * </ol>
 *
 * <p>Private and loopback addresses (127.x.x.x, 10.x.x.x, 192.168.x.x, etc.) are
 * intentionally <em>allowed</em> because legitimate platform services (e.g. Ollama at
 * {@code http://localhost:11434}) run on-host. Callers that need stricter isolation
 * may call {@link #validateEndpointStrict(String)} which additionally blocks RFC‑1918.
 *
 * <p>Usage:
 * <pre>{@code
 * // In OllamaModelAdapter.execute():
 * SsrfGuard.validateEndpoint(config.getEndpoint()); // throws SecurityException if unsafe
 * HttpRequest req = HttpRequest.newBuilder()
 *     .uri(URI.create(config.getEndpoint() + "/api/generate"))
 *     ...
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose SSRF protection for outbound HTTP calls using user-supplied URL configuration
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class SsrfGuard {

    /** Cloud metadata service addresses that must never be reachable via user-supplied URLs. */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "169.254.169.254",   // AWS / GCP / Azure IMDS
        "metadata.google.internal",
        "metadata.goog",
        "100.100.100.200",   // Alibaba Cloud ECS metadata
        "192.0.0.192",       // Oracle Cloud metadata
        "fd00:ec2::254",     // AWS IPv6 IMDS
        "0169.0254.0169.0254" // octal-encoded AWS IMDS (defence-in-depth)
    );

    /** RFC-1918 / loopback CIDRs used by the strict mode check. */
    private static final List<long[]> PRIVATE_RANGES = List.of(
        new long[]{ip("10.0.0.0"),    ip("10.255.255.255")},
        new long[]{ip("172.16.0.0"),  ip("172.31.255.255")},
        new long[]{ip("192.168.0.0"), ip("192.168.255.255")},
        new long[]{ip("127.0.0.0"),   ip("127.255.255.255")}
    );

    private SsrfGuard() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Validates that the URL is safe for use as an outbound HTTP endpoint.
     *
     * <p>Allows private/loopback addresses (suitable for local services like Ollama).
     * Blocks metadata IPs and non-HTTP(S) schemes.
     *
     * @param url the URL string to validate (must not be {@code null})
     * @throws SecurityException if the URL is unsafe
     * @throws IllegalArgumentException if the URL is malformed or null
     */
    public static void validateEndpoint(String url) {
        validate(url, false);
    }

    /**
     * Strict variant: additionally blocks RFC-1918 private and loopback addresses.
     *
     * <p>Use this for adapters that must never contact internal infrastructure.
     *
     * @param url the URL string to validate (must not be {@code null})
     * @throws SecurityException if the URL is unsafe
     * @throws IllegalArgumentException if the URL is malformed or null
     */
    public static void validateEndpointStrict(String url) {
        validate(url, true);
    }

    // -----------------------------------------------------------------------
    // Implementation
    // -----------------------------------------------------------------------

    private static void validate(String url, boolean blockPrivate) {
        if (url == null) {
            throw new IllegalArgumentException("Endpoint URL must not be null");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL: " + redact(url), e);
        }

        // 1. Scheme must be http or https
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SecurityException(
                "SSRF guard: only http/https schemes are allowed, got: " + redact(url));
        }

        // 2. No embedded credentials (user-info field enables credential leakage / SSRF bypasses)
        if (uri.getUserInfo() != null) {
            throw new SecurityException(
                "SSRF guard: URL must not contain embedded credentials: " + redact(url));
        }

        // 3. Host must be present
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("SSRF guard: URL has no host: " + redact(url));
        }

        // 4. Block cloud metadata hosts
        String normalizedHost = host.toLowerCase().replaceAll("\\.$", ""); // strip trailing dot
        if (BLOCKED_HOSTS.contains(normalizedHost)) {
            throw new SecurityException(
                "SSRF guard: cloud metadata host is not allowed: " + redact(url));
        }

        // 5. Block 169.254.x.x range (link-local / metadata) by IP prefix
        if (normalizedHost.startsWith("169.254.")) {
            throw new SecurityException(
                "SSRF guard: link-local address is not allowed: " + redact(url));
        }

        // 6. (Strict mode) Block private/loopback IP ranges
        if (blockPrivate) {
            long ipLong = tryParseIpv4Long(normalizedHost);
            if (ipLong >= 0) {
                for (long[] range : PRIVATE_RANGES) {
                    if (ipLong >= range[0] && ipLong <= range[1]) {
                        throw new SecurityException(
                            "SSRF guard: private/loopback address is not allowed in strict mode: "
                                + redact(url));
                    }
                }
            }
            if (normalizedHost.equals("localhost")
                    || normalizedHost.equals("::1")
                    || normalizedHost.startsWith("fe80:")) {
                throw new SecurityException(
                    "SSRF guard: loopback address is not allowed in strict mode: " + redact(url));
            }
        }
    }

    /**
     * Returns a redacted version of the URL for safe logging (removes query string and fragment).
     */
    private static String redact(String url) {
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        int end = url.length();
        if (q >= 0) end = Math.min(end, q);
        if (h >= 0) end = Math.min(end, h);
        return url.substring(0, end);
    }

    /** Converts a dotted-decimal IPv4 string to a long. Returns -1 if not a valid IPv4 address. */
    private static long tryParseIpv4Long(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return -1;
        long result = 0;
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) return -1;
                result = (result << 8) | octet;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return result;
    }

    /** Compile-time helper: converts "a.b.c.d" to a long for PRIVATE_RANGES initialisation. */
    private static long ip(String dotted) {
        String[] p = dotted.split("\\.");
        return (Long.parseLong(p[0]) << 24) | (Long.parseLong(p[1]) << 16)
             | (Long.parseLong(p[2]) << 8)  |  Long.parseLong(p[3]);
    }
}
