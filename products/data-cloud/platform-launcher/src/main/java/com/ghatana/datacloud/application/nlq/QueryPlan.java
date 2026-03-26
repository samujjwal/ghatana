/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.nlq;

import java.util.List;

/**
 * Component for QueryPlan
 *
 * @doc.type record
 * @doc.purpose Component for QueryPlan
 * @doc.layer product
 * @doc.pattern Service
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
