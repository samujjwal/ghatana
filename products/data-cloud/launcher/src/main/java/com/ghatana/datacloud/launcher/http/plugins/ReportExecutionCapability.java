package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.platform.plugin.PluginCapability;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Plugin capability for report generation and retrieval
 * @doc.layer product
 * @doc.pattern Capability
 */
public interface ReportExecutionCapability extends PluginCapability {

    Promise<ReportResult> generate(String tenantId, ReportDefinition definition);

    Map<String, String> listCachedReports();

    ReportResult getResult(String reportId);
}