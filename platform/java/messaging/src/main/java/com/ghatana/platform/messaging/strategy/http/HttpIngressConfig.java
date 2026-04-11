/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.http;

import com.ghatana.platform.messaging.config.ConnectorConfig;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for HTTP ingress/polling connectors.
 *
 * <p>Extends {@link ConnectorConfig} for shared TLS, retry and timeout settings.
 * Note: {@link #readTimeout()} from the base class governs HTTP request timeout.
 *
 * @doc.type class
 * @doc.purpose Immutable HTTP ingress connector configuration
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class HttpIngressConfig extends ConnectorConfig {

    private final String endpoint;
    private final int httpPort;
    private final String path;
    private final String method;

    private HttpIngressConfig(HttpIngressBuilder b) {
        super(b);
        this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
        this.httpPort = b.httpPort;
        this.path     = Objects.requireNonNull(b.path, "path");
        this.method   = b.method;
    }

    public static HttpIngressBuilder builder() { return new HttpIngressBuilder(); }

    public String endpoint() { return endpoint; }
    public int httpPort()    { return httpPort; }
    public String path()     { return path; }
    public String method()   { return method; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class HttpIngressBuilder extends Builder<HttpIngressBuilder> {
        private String endpoint;
        private int httpPort = 8080;
        private String path = "/";
        private String method = "POST";

        @Override protected HttpIngressBuilder self() { return this; }

        public HttpIngressBuilder endpoint(String e)  { endpoint = e; return this; }
        public HttpIngressBuilder httpPort(int p)     { httpPort = p; return this; }
        public HttpIngressBuilder path(String p)      { path = p; return this; }
        public HttpIngressBuilder method(String m)    { method = m; return this; }
        /** Convenience alias — sets {@link ConnectorConfig.Builder#readTimeout(Duration)}. */
        public HttpIngressBuilder timeoutMs(int ms)   { return readTimeout(Duration.ofMillis(ms)); }

        @Override public HttpIngressConfig build() { return new HttpIngressConfig(this); }
    }
}
