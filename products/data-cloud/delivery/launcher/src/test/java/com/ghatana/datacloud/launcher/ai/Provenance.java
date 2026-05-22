/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.ai;

import java.time.Instant;

/**
 * Provenance tracking for AI operations.
 *
 * @doc.type class
 * @doc.purpose Provenance tracking for AI operations
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class Provenance {

    private final String id;
    private final String model;
    private final String prompt;
    private final String output;
    private final Instant timestamp;
    private final int tokensUsed;
    private final double cost;

    public Provenance(
            String id,
            String model,
            String prompt,
            String output,
            Instant timestamp,
            int tokensUsed,
            double cost) {
        this.id = id;
        this.model = model;
        this.prompt = prompt;
        this.output = output;
        this.timestamp = timestamp;
        this.tokensUsed = tokensUsed;
        this.cost = cost;
    }

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOutput() {
        return output;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public double getCost() {
        return cost;
    }
}
