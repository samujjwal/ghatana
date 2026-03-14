/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * W3C Baggage propagation for finance-domain context (STORY-K06-005).
 *
 * <p>Encodes and decodes the W3C {@code baggage} header, carrying tenant and jurisdiction
 * context across service boundaries without modifying the business payload.
 *
 * <h3>Carried fields</h3>
 * <ul>
 *   <li>{@code tenant_id} — tenant UUID</li>
 *   <li>{@code jurisdiction} — ISO 3166-1 alpha-2 country code (e.g., {@code NP})</li>
 *   <li>{@code fiscal_year} — Nepali/Gregorian fiscal year (e.g., {@code 2081-2082})</li>
 *   <li>{@code user_role} — principal's primary role (informational, not an auth decision)</li>
 * </ul>
 *
 * <h3>W3C Baggage format</h3>
 * <pre>baggage: tenant_id=9a8b7c6d,jurisdiction=NP,fiscal_year=2081-2082,user_role=auditor</pre>
 *
 * <p>Values are percent-encoded per the W3C spec; total header size is capped at 8 KB.
 *
 * <p>Reference: <a href="https://www.w3.org/TR/baggage/">W3C Baggage Specification</a>
 *
 * @doc.type class
 * @doc.purpose W3C Baggage propagation for finance context (K06-005)
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class BaggagePropagator {

    /** HTTP header name. */
    public static final String HEADER_BAGGAGE = "baggage";

    /** Maximum allowed W3C Baggage header size in bytes (spec recommendation). */
    static final int MAX_BAGGAGE_BYTES = 8192;

    // Known baggage key names
    public static final String KEY_TENANT_ID    = "tenant_id";
    public static final String KEY_JURISDICTION = "jurisdiction";
    public static final String KEY_FISCAL_YEAR  = "fiscal_year";
    public static final String KEY_USER_ROLE    = "user_role";

    /** Validates baggage key names per W3C token syntax: printable ASCII except delimiters. */
    private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E&&[^=,;\\\\]]+");

    private BaggagePropagator() {}

    // ──────────────────────────────────────────────────────────────────────
    // Encode
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Builds a W3C {@code baggage} header value from finance context fields.
     *
     * <p>Fields with null or blank values are omitted silently.
     * The header is truncated to 8 KB if necessary (with a log warning in production).
     *
     * @param tenantId     tenant UUID — may be null
     * @param jurisdiction ISO country code — may be null
     * @param fiscalYear   fiscal year string — may be null
     * @param userRole     principal's primary role — may be null
     * @return header value ready for the {@code baggage} HTTP header, never null
     */
    public static String encode(String tenantId, String jurisdiction,
                                String fiscalYear, String userRole) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (tenantId    != null && !tenantId.isBlank())    fields.put(KEY_TENANT_ID, tenantId);
        if (jurisdiction != null && !jurisdiction.isBlank()) fields.put(KEY_JURISDICTION, jurisdiction);
        if (fiscalYear  != null && !fiscalYear.isBlank())  fields.put(KEY_FISCAL_YEAR, fiscalYear);
        if (userRole    != null && !userRole.isBlank())    fields.put(KEY_USER_ROLE, userRole);
        return encode(fields);
    }

    /**
     * Builds a W3C {@code baggage} header from an arbitrary key-value map.
     * Keys must match the W3C token syntax or they will be silently skipped.
     *
     * @param entries baggage entries to encode
     * @return header value, never null
     */
    public static String encode(Map<String, String> entries) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (!isValidKey(e.getKey())) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(percentEncode(e.getValue()));
        }

        String header = sb.toString();
        if (header.getBytes(StandardCharsets.UTF_8).length > MAX_BAGGAGE_BYTES) {
            // Truncate to last complete entry within limit
            header = truncateToLimit(header);
        }
        return header;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Decode
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Parses a W3C {@code baggage} header value into a key-value map.
     * Malformed entries are silently skipped.
     *
     * @param headerValue raw {@code baggage} header value
     * @return parsed entries, possibly empty, never null
     */
    public static Map<String, String> decode(String headerValue) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headerValue == null || headerValue.isBlank()) return result;

        for (String part : headerValue.split(",")) {
            part = part.strip();
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key   = part.substring(0, eq).strip();
            String value = part.substring(eq + 1).strip();
            if (!isValidKey(key)) continue;
            result.put(key, percentDecode(value));
        }
        return result;
    }

    /**
     * Extracts a single well-known field from a {@code baggage} header.
     *
     * @param headerValue raw header value
     * @param key         one of the {@code KEY_*} constants
     * @return field value, or {@code null} if absent
     */
    public static String extract(String headerValue, String key) {
        return decode(headerValue).get(key);
    }

    /**
     * Injects current {@link StructuredLogContext} baggage fields (tenant_id, jurisdiction,
     * fiscal_year, user_role) into the MDC so they appear in structured log output.
     *
     * @param baggageHeader incoming {@code baggage} header value
     */
    public static void propagateToMdc(String baggageHeader) {
        Map<String, String> fields = decode(baggageHeader);
        fields.forEach(StructuredLogContext::put);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    private static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String percentDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value; // return raw if malformed
        }
    }

    private static boolean isValidKey(String key) {
        return key != null && !key.isBlank() && VALID_KEY.matcher(key).matches();
    }

    private static String truncateToLimit(String header) {
        byte[] bytes = header.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BAGGAGE_BYTES) return header;
        // Walk backwards to find last complete entry boundary
        String[] parts = header.split(",");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String candidate = sb.length() > 0 ? sb + "," + part : part;
            if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BAGGAGE_BYTES) break;
            if (sb.length() > 0) sb.append(',');
            sb.append(part);
        }
        return sb.toString();
    }
}
