/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Repository for Report persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Data access layer for reports
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public interface ReportRepository {

    Promise<Report> save(Report report);

    Promise<Report> findById(String tenantId, String reportId);

    Promise<List<Report>> findAllByTenant(String tenantId);

    Promise<Void> delete(String tenantId, String reportId);
}
