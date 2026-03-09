/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.nlq;

import java.util.List;

/**
 * Represents the result of executing an NLQ query plan.
 */
public record QueryResult(
    String planId,
    List<?> rows,
    String status,
    double confidence,
    long executionTimeMs
) {}
