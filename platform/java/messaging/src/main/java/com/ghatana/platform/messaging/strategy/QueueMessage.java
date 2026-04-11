/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

import java.util.Map;

/**
 * Represents a message in a queue system.
 * Simplified for platform-connectors module.
 */
public class QueueMessage {
    private final String id;
    private final String body;
    private final Map<String, String> headers;
    private final long timestamp;

    public QueueMessage(String id, String body, Map<String, String> headers) {
        this.id = id;
        this.body = body;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
