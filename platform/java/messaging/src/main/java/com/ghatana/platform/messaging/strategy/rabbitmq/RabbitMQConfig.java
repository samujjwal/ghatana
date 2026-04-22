/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.rabbitmq;

import com.ghatana.platform.messaging.config.ConnectorConfig;

import java.util.Objects;

/**
 * Immutable configuration for RabbitMQ connectors.
 *
 * <p>Extends {@link ConnectorConfig} for shared TLS, retry and timeout settings.
 *
 * @doc.type class
 * @doc.purpose Immutable RabbitMQ connector configuration
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class RabbitMQConfig extends ConnectorConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String virtualHost;
    private final String queueName;
    private final int maxDeliveryAttempts;

    private RabbitMQConfig(RabbitMQBuilder b) {
        super(b);
        this.host        = Objects.requireNonNull(b.host, "host");
        this.port        = b.port;
        this.username    = Objects.requireNonNull(b.username, "username");
        this.password    = Objects.requireNonNull(b.password, "password");
        this.virtualHost = b.virtualHost;
        this.queueName   = Objects.requireNonNull(b.queueName, "queueName");
        this.maxDeliveryAttempts = b.maxDeliveryAttempts;
    }

    public static RabbitMQBuilder builder() { return new RabbitMQBuilder(); }

    public String rabbitHost()   { return host; }
    public int rabbitPort()      { return port; }
    public String username()     { return username; }
    public String password()     { return password; }
    public String virtualHost()  { return virtualHost; }
    public String queueName()    { return queueName; }
    public int maxDeliveryAttempts() { return maxDeliveryAttempts; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class RabbitMQBuilder extends Builder<RabbitMQBuilder> {
        private String host;
        private int port = 5672;
        private String username;
        private String password;
        private String virtualHost = "/";
        private String queueName;
        private int maxDeliveryAttempts = Integer.MAX_VALUE;

        @Override protected RabbitMQBuilder self() { return this; }

        public RabbitMQBuilder host(String h)           { host = h; return this; }
        public RabbitMQBuilder port(int p)              { port = p; return this; }
        public RabbitMQBuilder username(String u)       { username = u; return this; }
        public RabbitMQBuilder password(String p)       { password = p; return this; }
        public RabbitMQBuilder virtualHost(String vh)   { virtualHost = vh; return this; }
        public RabbitMQBuilder queueName(String q)      { queueName = q; return this; }
        public RabbitMQBuilder maxDeliveryAttempts(int attempts) {
            if (attempts < 1) {
                throw new IllegalArgumentException("maxDeliveryAttempts must be >= 1");
            }
            maxDeliveryAttempts = attempts;
            return this;
        }

        @Override public RabbitMQConfig build() { return new RabbitMQConfig(this); }
    }
}
