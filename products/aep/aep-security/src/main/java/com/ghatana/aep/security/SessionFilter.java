/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP session filter for AEP.
 *
 * <p>Generates a short-lived opaque session token on first access
 * (via {@code POST /api/v1/session}) and validates it on subsequent
 * requests via the {@code X-AEP-Session} header.
 *
 * <p>The session filter is designed to run <em>after</em> {@link AepAuthFilter}
 * in the servlet chain. An authenticated caller may request a session
 * token and then use that token for repeated requests within the same
 * connection lifetime.
 *
 * @doc.type class
 * @doc.purpose Per-request session management for AEP HTTP server
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class SessionFilter implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(SessionFilter.class);

    static final String SESSION_HEADER   = "X-AEP-Session";
    static final String SESSION_PATH     = "/api/v1/session";
    static final Duration DEFAULT_TTL    = Duration.ofHours(1);

    private final AsyncServlet next;
    private final Duration sessionTtl;
    /** In-memory session store: token → expiry epoch-second */
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    /**
     * @param next       downstream servlet; never {@code null}
     * @param sessionTtl how long a session token remains valid; never {@code null}
     */
    public SessionFilter(AsyncServlet next, Duration sessionTtl) {
        this.next       = Objects.requireNonNull(next,       "next");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "sessionTtl");
    }

    /**
     * Creates a {@code SessionFilter} with the default TTL ({@value DEFAULT_TTL}).
     */
    public SessionFilter(AsyncServlet next) {
        this(next, DEFAULT_TTL);
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        evictExpiredSessions();

        String path = request.getPath();

        // Session creation endpoint — returns a new token
        if (SESSION_PATH.equals(path) && "POST".equalsIgnoreCase(request.getMethod().name())) {
            return issueSession(request);
        }

        // Validate existing session on all other requests
        String token = request.getHeader(HttpHeaders.of(SESSION_HEADER));
        if (token == null || !isValid(token)) {
            // No session — allow the request through; session is optional (not a hard gate)
            MDC.remove("sessionId");
            return next.serve(request);
        }

        // Valid session — stamp the correlation context and continue
        MDC.put("sessionId", token.substring(0, Math.min(8, token.length())));
        try {
            return next.serve(request);
        } finally {
            MDC.remove("sessionId");
        }
    }

    // ---- Internals ---------------------------------------------------------

    private Promise<HttpResponse> issueSession(HttpRequest request) {
        String token       = UUID.randomUUID().toString().replace("-", "");
        long   expiryEpoch = System.currentTimeMillis() / 1000 + sessionTtl.toSeconds();
        sessions.put(token, expiryEpoch);

        log.debug("[session] Issued token={} ttlSeconds={}", token.substring(0, 8), sessionTtl.toSeconds());

        byte[] body = ("{\"session\":\"" + token + "\","
            + "\"expiresInSeconds\":" + sessionTtl.toSeconds() + "}")
            .getBytes(StandardCharsets.UTF_8);

        return Promise.of(HttpResponse.ok200()
            .withHeader(HttpHeaders.of(SESSION_HEADER), HttpHeaderValue.of(token))
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json; charset=utf-8"))
            .withBody(body)
            .build());
    }

    private boolean isValid(String token) {
        Long expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() / 1000 > expiry) {
            sessions.remove(token);
            log.debug("[session] Expired token={}", token.substring(0, Math.min(8, token.length())));
            return false;
        }
        return true;
    }

    private void evictExpiredSessions() {
        long now = System.currentTimeMillis() / 1000;
        sessions.entrySet().removeIf(e -> e.getValue() < now);
    }
}
