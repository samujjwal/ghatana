/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.nlq;

import java.util.List;

/**
 * Result object for asynchronous operations
 *
 * @doc.type record
 * @doc.purpose Result object for asynchronous operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QueryResult(
    String planId,
    List<?> rows,
    String status,
    double confidence,
    long executionTimeMs
) {}
