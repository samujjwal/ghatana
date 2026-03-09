/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.nlq;

import java.util.List;

/**
 * Represents a parsed NLQ query plan.
 */
public record QueryPlan(
    String planId,
    String originalQuery,
    Object querySpec,
    double confidence,
    int filterCount,
    int sortCount,
    String tenantId,
    String collectionName
) {}
