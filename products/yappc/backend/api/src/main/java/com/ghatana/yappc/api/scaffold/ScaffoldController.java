package com.ghatana.yappc.api.scaffold;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.scaffold.dto.*;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static io.activej.http.HttpHeaders.CONTENT_DISPOSITION;
import static io.activej.http.HttpHeaders.CONTENT_TYPE;
import static io.activej.http.HttpMethod.*;

/**
 * Scaffold Controller - Manages project generation and scaffolding.
 *
 * @doc.type class
 * @doc.purpose REST API for project scaffolding
 * @doc.layer product
 * @doc.pattern Controller
 */
public class ScaffoldController extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldController.class);
    private static final String BASE_PATH = "/api/v1/scaffold";

    private final ScaffoldService scaffoldService;

    @Inject
    public ScaffoldController(ScaffoldService scaffoldService) {
        this.scaffoldService = scaffoldService;
    }

    @Provides
    RoutingServlet scaffoldServlet(Reactor reactor) {
        return RoutingServlet.builder(reactor)
                .with(GET, BASE_PATH + "/templates", this::listTemplates)
                .with(GET, BASE_PATH + "/templates/:templateId", this::getTemplate)
                .with(GET, BASE_PATH + "/templates/:templateId/config", this::getTemplateConfig)
                .with(POST, BASE_PATH + "/generate", this::generateProject)
                .with(GET, BASE_PATH + "/jobs/:jobId", this::getJobStatus)
                .with(GET, BASE_PATH + "/jobs/:jobId/download", this::downloadProject)
                .with(DELETE, BASE_PATH + "/jobs/:jobId", this::cancelJob)
                .with(GET, BASE_PATH + "/feature-packs", this::listFeaturePacks)
                .with(POST, BASE_PATH + "/feature-packs/apply", this::applyFeaturePack)
                .with(GET, BASE_PATH + "/jobs/:jobId/conflicts", this::getConflicts)
                .with(POST, BASE_PATH + "/jobs/:jobId/resolve", this::resolveConflicts)
                .build();
    }

    private Promise<HttpResponse> listTemplates(HttpRequest request) {
        String category = request.getQueryParameter("category");
        return scaffoldService.listTemplates(category)
                .map(templates -> (HttpResponse) ApiResponse.ok(templates));
    }

    private Promise<HttpResponse> getTemplate(HttpRequest request) {
        String templateId = request.getPathParameter("templateId");
        return scaffoldService.getTemplate(templateId)
                .map(templateOpt -> templateOpt
                        .map(t -> (HttpResponse) ApiResponse.ok(t))
                        .orElseGet(() -> ApiResponse.error(404, "NOT_FOUND", "Template not found", request.getPath())));
    }

    private Promise<HttpResponse> getTemplateConfig(HttpRequest request) {
        String templateId = request.getPathParameter("templateId");
        return scaffoldService.getTemplateConfigSchema(templateId)
                .map(config -> ApiResponse.ok(config));
    }

    private Promise<HttpResponse> generateProject(HttpRequest request) {
        return JsonUtils.parseBody(request, ScaffoldRequest.class)
                .then(scaffoldRequest -> {
                    if (!scaffoldRequest.isValid()) {
                        return ApiResponse.error(400, "INVALID_REQUEST", "Invalid scaffold request", request.getPath()).toPromise();
                    }
                    return scaffoldService.scaffoldProject(scaffoldRequest)
                            .map(r -> ApiResponse.accepted(r));
                });
    }

    private Promise<HttpResponse> getJobStatus(HttpRequest request) {
        String jobId = request.getPathParameter("jobId");
        return scaffoldService.getJobStatus(jobId)
                .map(statusOpt -> statusOpt
                        .map(status -> ApiResponse.ok(status))
                        .orElseGet(() -> ApiResponse.error(404, "NOT_FOUND", "Job not found", request.getPath())));
    }

    private Promise<HttpResponse> downloadProject(HttpRequest request) {
        String jobId = request.getPathParameter("jobId");
        return scaffoldService.downloadProject(jobId)
                .map(projectOpt -> projectOpt
                        .map(project -> HttpResponse.ofCode(200)
                                .withHeader(CONTENT_TYPE, HttpHeaderValue.of("application/zip"))
                                .withHeader(CONTENT_DISPOSITION, HttpHeaderValue.of("attachment; filename=\"project.zip\""))
                                .withBody(project)
                                .build())
                        .orElseGet(() -> ApiResponse.error(404, "NOT_FOUND", "Project not found or not ready", request.getPath())));
    }

    private Promise<HttpResponse> cancelJob(HttpRequest request) {
        String jobId = request.getPathParameter("jobId");
        return scaffoldService.cancelJob(jobId)
                .map(success -> success
                        ? ApiResponse.ok("Job cancelled")
                        : ApiResponse.error(404, "NOT_FOUND", "Job not found or already completed", request.getPath()));
    }

    private Promise<HttpResponse> listFeaturePacks(HttpRequest request) {
        return scaffoldService.listFeaturePacks()
                .map(packs -> ApiResponse.ok(packs));
    }

    private Promise<HttpResponse> applyFeaturePack(HttpRequest request) {
         return JsonUtils.parseBody(request, FeaturePackRequest.class)
                .then(featurePackRequest -> {
                    if (!featurePackRequest.isValid()) {
                         return ApiResponse.error(400, "INVALID_REQUEST", "Invalid request", request.getPath()).toPromise();
                    }
                    return scaffoldService.addFeaturePack(featurePackRequest.projectId(), featurePackRequest)
                        .map(result -> ApiResponse.accepted(result));
                });
    }

    private Promise<HttpResponse> getConflicts(HttpRequest request) {
        String jobId = request.getPathParameter("jobId");
        return scaffoldService.getConflicts(jobId)
                .map(conflicts -> ApiResponse.ok(conflicts));
    }

    private Promise<HttpResponse> resolveConflicts(HttpRequest request) {
        String jobId = request.getPathParameter("jobId");
        return JsonUtils.parseBody(request, ConflictResolution.class)
                .then(resolution -> scaffoldService.resolveConflicts(jobId, resolution)
                        .map(success -> success
                                ? ApiResponse.ok("Conflicts resolved")
                                : ApiResponse.error(500, "INTERNAL_ERROR", "Failed to resolve conflicts", request.getPath())));
    }
}
