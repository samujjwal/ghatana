/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.s3;

import com.ghatana.aep.connector.config.ConnectorConfig;

import java.util.Objects;

/**
 * Immutable configuration for AWS S3 storage connectors.
 *
 * <p>Extends {@link ConnectorConfig} for shared TLS, retry and timeout settings.
 *
 * @doc.type class
 * @doc.purpose Immutable S3 connector configuration
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class S3Config extends ConnectorConfig {

    private final String bucketName;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String prefix;

    private S3Config(S3Builder b) {
        super(b);
        this.bucketName = Objects.requireNonNull(b.bucketName, "bucketName");
        this.region     = Objects.requireNonNull(b.region, "region");
        this.accessKey  = b.accessKey;
        this.secretKey  = b.secretKey;
        this.prefix     = b.prefix;
    }

    public static S3Builder builder() { return new S3Builder(); }

    public String bucketName() { return bucketName; }
    public String region()     { return region; }
    public String accessKey()  { return accessKey; }
    public String secretKey()  { return secretKey; }
    public String prefix()     { return prefix; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class S3Builder extends Builder<S3Builder> {
        private String bucketName;
        private String region;
        private String accessKey;
        private String secretKey;
        private String prefix = "";

        @Override protected S3Builder self() { return this; }

        public S3Builder bucketName(String b) { bucketName = b; return this; }
        public S3Builder region(String r)     { region = r; return this; }
        public S3Builder accessKey(String k)  { accessKey = k; return this; }
        public S3Builder secretKey(String k)  { secretKey = k; return this; }
        public S3Builder prefix(String p)     { prefix = p; return this; }

        @Override public S3Config build() { return new S3Config(this); }
    }
}
