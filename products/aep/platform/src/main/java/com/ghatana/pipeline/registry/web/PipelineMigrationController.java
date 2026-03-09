package com.ghatana.pipeline.registry.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.pipeline.registry.migration.MigrationReport;
import com.ghatana.pipeline.registry.migration.PipelineMigrationService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

/**
 * Controller for trigger pipeline migrations.
 *
 * @doc.type class
 * @doc.purpose Endpoint for migration triggering
 * @doc.layer product
 * @doc.pattern Controller
 */
@Slf4j
@RequiredArgsConstructor
public class PipelineMigrationController {

    @Inject
    private final PipelineMigrationService migrationService;
    @Inject
    private final ObjectMapper objectMapper;

    /**
     * Trigger pipeline migration from legacy to structured format.
     * Expects query param ?dryRun=true|false (default false).
     */
    public Promise<HttpResponse> migratePipelines(HttpRequest request) {
        String dryRunParam = request.getQueryParameter("dryRun");
        boolean dryRun = "true".equalsIgnoreCase(dryRunParam);

        log.info("Received pipeline migration request. dryRun={}", dryRun);

        return migrationService.migrateAll(dryRun)
                .map(report -> ResponseBuilder.ok()
                        .json(report)
                        .build())
                .mapException(e -> new Exception(e));
    }
}

