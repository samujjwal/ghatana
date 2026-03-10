/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.plugin;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.framework.core.plugin.audit.PluginAuditStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller exposing plugin audit records.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   GET /api/v1/plugins/{pluginId}/audit    — list audit records for a plugin
 * </pre>
 *
 * @doc.type class
 * @doc.purpose HTTP controller for plugin audit log retrieval (10.2.3)
 * @doc.layer product
 * @doc.pattern Controller
 */
public class PluginAuditController {

    private static final Logger log = LoggerFactory.getLogger(PluginAuditController.class);

    private final PluginAuditStore auditStore;

    /**
     * @param auditStore in-memory or persistent store of plugin audit records
     */
    @Inject
    public PluginAuditController(PluginAuditStore auditStore) {
        this.auditStore = auditStore;
    }

    /**
     * GET /api/v1/plugins/{pluginId}/audit
     *
     * <p>Returns the audit trail for the given plugin id filtered to the
     * authenticated tenant.
     *
     * @param request HTTP request; must carry valid auth principal
     * @return JSON array of audit record objects
     */
    public Promise<HttpResponse> getPluginAudit(HttpRequest request) {
        return ApiResponse.wrap(
                TenantContextExtractor.requireAuthenticated(request)
                        .then(ctx -> {
                            String pluginId = request.getPathParameter("pluginId");
                            log.debug("Plugin audit requested for pluginId={} tenantId={}", pluginId, ctx.tenantId());
                            return auditStore.getRecords(pluginId, ctx.tenantId())
                                    .map(ApiResponse::ok);
                        }));
    }
}
