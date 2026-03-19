/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.testing.dto;

/**
 * TestStatistics.
 *
 * @doc.type record
 * @doc.purpose test statistics
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestStatistics(int unitTests, int integrationTests, int totalLines) {}
