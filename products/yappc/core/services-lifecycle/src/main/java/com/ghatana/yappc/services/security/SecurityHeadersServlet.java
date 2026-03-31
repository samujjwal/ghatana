/*
 * Copyright (c) 2026 Ghatana Technologies
 */
package com.ghatana.yappc.services.security;

import io.activej.bytebuf.ByteBuf;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Servlet decorator that injects OWASP-recommended security headers on every response.
 *
 * <p>Headers added:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME-type sniffing</li>
 *   <li>{@code X-Frame-Options: DENY} — prevents clickjacking</li>
 *   <li>{@code X-XSS-Protection: 0} — disables legacy XSS filter (CSP is preferred)</li>
 *   <li>{@code Content-Security-Policy: default-src 'self'} — strict CSP</li>
 *   <li>{@code Strict-Transport-Security: max-age=31536000; includeSubDomains}</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OWASP security headers filter for all lifecycle HTTP responses
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class SecurityHeadersServlet implements AsyncServlet {

    private static final HttpHeader X_CONTENT_TYPE_OPTIONS =
            HttpHeaders.register("X-Content-Type-Options");
    private static final HttpHeader X_FRAME_OPTIONS =
            HttpHeaders.register("X-Frame-Options");
    private static final HttpHeader X_XSS_PROTECTION =
            HttpHeaders.register("X-XSS-Protection");
    private static final HttpHeader CONTENT_SECURITY_POLICY =
            HttpHeaders.register("Content-Security-Policy");
    private static final HttpHeader STRICT_TRANSPORT_SECURITY =
            HttpHeaders.register("Strict-Transport-Security");
    private static final HttpHeader REFERRER_POLICY =
            HttpHeaders.register("Referrer-Policy");

    private final AsyncServlet delegate;

    public SecurityHeadersServlet(AsyncServlet delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        return delegate.serve(request).map(this::addSecurityHeaders);
    }

    private HttpResponse addSecurityHeaders(HttpResponse response) {
        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());

        // Propagate all existing headers
        for (var entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }

        // Propagate body
                ByteBuf body = response.getBody();
                if (body != null && body.readRemaining() > 0) {
            builder.withBody(body);
        }

        // Add security headers
        builder.withHeader(X_CONTENT_TYPE_OPTIONS, "nosniff");
        builder.withHeader(X_FRAME_OPTIONS, "DENY");
        // X-XSS-Protection 0 is the modern recommendation — rely on CSP instead
        builder.withHeader(X_XSS_PROTECTION, "0");
        builder.withHeader(CONTENT_SECURITY_POLICY,
                "default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'");
        builder.withHeader(STRICT_TRANSPORT_SECURITY,
                "max-age=31536000; includeSubDomains; preload");
        builder.withHeader(REFERRER_POLICY, "strict-origin-when-cross-origin");

        return builder.build();
    }
}
