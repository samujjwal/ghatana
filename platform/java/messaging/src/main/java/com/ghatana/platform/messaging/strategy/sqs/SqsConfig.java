/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.sqs;

import com.ghatana.platform.messaging.config.ConnectorConfig;

import java.util.Objects;

/**
 * Immutable configuration for AWS SQS connectors.
 *
 * <p>Extends {@link ConnectorConfig} for shared TLS, retry and timeout settings.
 *
 * @doc.type class
 * @doc.purpose Immutable SQS connector configuration
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class SqsConfig extends ConnectorConfig {

    private final String queueUrl;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String endpointOverride;
    private final int maxMessages;
    private final int waitTimeSeconds;

    private SqsConfig(SqsBuilder b) {
        super(b);
        this.queueUrl       = Objects.requireNonNull(b.queueUrl, "queueUrl");
        this.region         = Objects.requireNonNull(b.region, "region");
        this.accessKey      = b.accessKey;
        this.secretKey      = b.secretKey;
        this.endpointOverride = b.endpointOverride;
        this.maxMessages    = b.maxMessages;
        this.waitTimeSeconds = b.waitTimeSeconds;
    }

    public static SqsBuilder builder() { return new SqsBuilder(); }

    public String queueUrl()       { return queueUrl; }
    public String region()         { return region; }
    public String accessKey()      { return accessKey; }
    public String secretKey()      { return secretKey; }
    public String endpointOverride() { return endpointOverride; }
    public int maxMessages()       { return maxMessages; }
    public int waitTimeSeconds()   { return waitTimeSeconds; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class SqsBuilder extends Builder<SqsBuilder> {
        private String queueUrl;
        private String region;
        private String accessKey;
        private String secretKey;
        private String endpointOverride;
        private int maxMessages = 10;
        private int waitTimeSeconds = 20;

        @Override protected SqsBuilder self() { return this; }

        public SqsBuilder queueUrl(String u)       { queueUrl = u; return this; }
        public SqsBuilder region(String r)         { region = r; return this; }
        public SqsBuilder accessKey(String k)      { accessKey = k; return this; }
        public SqsBuilder secretKey(String k)      { secretKey = k; return this; }
        public SqsBuilder endpointOverride(String e) { endpointOverride = e; return this; }
        public SqsBuilder maxMessages(int n)       { maxMessages = n; return this; }
        public SqsBuilder waitTimeSeconds(int s)   { waitTimeSeconds = s; return this; }

        @Override public SqsConfig build() { return new SqsConfig(this); }
    }
}
