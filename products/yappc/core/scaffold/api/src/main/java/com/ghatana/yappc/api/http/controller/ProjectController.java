/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.api.http.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.yappc.api.YappcApi;
import com.ghatana.yappc.api.http.websocket.ProgressWebSocket;
import com.ghatana.yappc.api.model.*;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for project operations using ActiveJ HTTP.
 *
 * @doc.type class
 * @doc.purpose Project API endpoints
 * @doc.layer platform
 * @doc.pattern Controller
 */
public final class ProjectController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final YappcApi api;
    private final ProgressWebSocket progressWebSocket;

    public ProjectController(YappcApi api, ProgressWebSocket progressWebSocket) {
        this.api = api;
        this.progressWebSocket = progressWebSocket;
    }

    /**
     * POST /api/v1/projects
     */
    public Promise<HttpResponse> create(HttpRequest request) {
        return request.loadBody().map(body -> {
            CreateProjectHttpRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), CreateProjectHttpRequest.class);
            LOG.info("Creating project: {} from pack: {}", req.projectName(), req.packName());
            String sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID().toString();

            CreateRequest createRequest = CreateRequest.builder()
                    .packName(req.packName())
                    .projectName(req.projectName())
                    .outputPath(Paths.get(req.targetPath()))
                    .variables(req.variables() != null ? req.variables() : Map.of())
                    .build();

            CreateResult result = api.projects().create(createRequest);
            return ResponseBuilder.ok().json(new CreateProjectResponse(
                    result.isSuccess(),
                    result.getProjectPath() != null ? result.getProjectPath().toString() : null,
                    result.getFilesCreated(),
                    result.getWarnings(),
                    sessionId
            )).build();
        });
    }

    /**
     * POST /api/v1/projects/add-feature
     */
    public Promise<HttpResponse> addFeature(HttpRequest request) {
        return request.loadBody().map(body -> {
            AddFeatureHttpRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), AddFeatureHttpRequest.class);
            LOG.info("Adding feature {} to project at: {}", req.featureName(), req.projectPath());
            String sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID().toString();

            AddFeatureRequest addReq = AddFeatureRequest.builder()
                    .projectPath(Paths.get(req.projectPath()))
                    .feature(req.featureName())
                    .type(req.packName())
                    .variables(req.variables() != null ? req.variables() : Map.of())
                    .force(req.force() != null && req.force())
                    .build();

            AddResult result = api.projects().addFeature(addReq);
            return ResponseBuilder.ok().json(new AddFeatureResponse(
                    result.isSuccess(),
                    result.getFilesCreated(),
                    result.getFilesModified(),
                    result.getWarnings(),
                    sessionId
            )).build();
        });
    }

    /**
     * POST /api/v1/projects/update
     */
    public Promise<HttpResponse> update(HttpRequest request) {
        return request.loadBody().map(body -> {
            UpdateProjectHttpRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), UpdateProjectHttpRequest.class);
            LOG.info("Updating project at: {}", req.projectPath());
            String sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID().toString();

            UpdateRequest updateReq = UpdateRequest.builder()
                    .projectPath(Paths.get(req.projectPath()))
                    .dryRun(req.dryRun() != null && req.dryRun())
                    .build();

            UpdateResult result = api.projects().update(updateReq);
            return ResponseBuilder.ok().json(new UpdateProjectResponse(
                    result.isSuccess(),
                    result.getFilesUpdated(),
                    result.getFilesAdded(),
                    result.getFilesRemoved(),
                    result.getConflicts(),
                    result.getFromVersion() + " -> " + result.getToVersion(),
                    sessionId
            )).build();
        });
    }

    /**
     * GET /api/v1/projects/info
     */
    public Promise<HttpResponse> getInfo(HttpRequest request) {
        String projectPath = request.getQueryParameter("path");
        if (projectPath == null || projectPath.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(new ErrorResponse("INVALID_REQUEST", "Missing required parameter: path")).build());
        }
        LOG.debug("Getting project info for: {}", projectPath);
        Optional<ProjectInfo> info = api.projects().getInfo(Paths.get(projectPath));
        if (info.isPresent()) {
            return Promise.of(ResponseBuilder.ok().json(info.get()).build());
        }
        return Promise.of(ResponseBuilder.notFound()
                .json(new ErrorResponse("PROJECT_NOT_FOUND", "Not a YAPPC project: " + projectPath)).build());
    }

    /**
     * GET /api/v1/projects/state
     */
    public Promise<HttpResponse> getState(HttpRequest request) {
        String projectPath = request.getQueryParameter("path");
        if (projectPath == null || projectPath.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(new ErrorResponse("INVALID_REQUEST", "Missing required parameter: path")).build());
        }
        LOG.debug("Getting project state for: {}", projectPath);
        Optional<ProjectState> state = api.projects().getState(Paths.get(projectPath));
        if (state.isPresent()) {
            return Promise.of(ResponseBuilder.ok().json(state.get()).build());
        }
        return Promise.of(ResponseBuilder.notFound()
                .json(new ErrorResponse("PROJECT_NOT_FOUND", "Not a YAPPC project: " + projectPath)).build());
    }

    /**
     * GET /api/v1/projects/validate
     */
    public Promise<HttpResponse> validate(HttpRequest request) {
        String projectPath = request.getQueryParameter("path");
        if (projectPath == null || projectPath.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(new ErrorResponse("INVALID_REQUEST", "Missing required parameter: path")).build());
        }
        LOG.debug("Validating project at: {}", projectPath);
        ProjectValidationResult result = api.projects().validate(Paths.get(projectPath));
        return Promise.of(ResponseBuilder.ok().json(result).build());
    }

    /**
     * GET /api/v1/projects/check-updates
     */
    public Promise<HttpResponse> checkUpdates(HttpRequest request) {
        String projectPath = request.getQueryParameter("path");
        if (projectPath == null || projectPath.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(new ErrorResponse("INVALID_REQUEST", "Missing required parameter: path")).build());
        }
        LOG.debug("Checking updates for: {}", projectPath);
        UpdateAvailability result = api.projects().checkUpdates(Paths.get(projectPath));
        return Promise.of(ResponseBuilder.ok().json(result).build());
    }

    /**
     * POST /api/v1/projects/preview-update
     */
    public Promise<HttpResponse> previewUpdate(HttpRequest request) {
        return request.loadBody().map(body -> {
            PreviewUpdateHttpRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), PreviewUpdateHttpRequest.class);
            LOG.debug("Previewing update for: {}", req.projectPath());
            UpdateRequest updateReq = UpdateRequest.builder()
                    .projectPath(Paths.get(req.projectPath()))
                    .dryRun(true)
                    .build();
            UpdatePreview result = api.projects().previewUpdate(updateReq);
            return ResponseBuilder.ok().json(result).build();
        });
    }

    /**
     * GET /api/v1/projects/features
     */
    public Promise<HttpResponse> getFeatures(HttpRequest request) {
        String projectPath = request.getQueryParameter("path");
        if (projectPath == null || projectPath.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(new ErrorResponse("INVALID_REQUEST", "Missing required parameter: path")).build());
        }
        LOG.debug("Getting features for: {}", projectPath);
        List<FeatureInfo> features = api.projects().getAddedFeatures(Paths.get(projectPath));
        return Promise.of(ResponseBuilder.ok().json(new FeaturesResponse(features, features.size())).build());
    }

    /**
     * POST /api/v1/projects/export
     */
    public Promise<HttpResponse> exportState(HttpRequest request) {
        return request.loadBody().map(body -> {
            ExportHttpRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), ExportHttpRequest.class);
            LOG.info("Exporting project state from: {}", req.projectPath());
            String exported = api.projects().exportState(Paths.get(req.projectPath()));
            return ResponseBuilder.ok().json(new ExportResponse(true, exported)).build();
        });
    }

    /**
     * POST /api/v1/projects/import
     */
    public Promise<HttpResponse> importState(HttpRequest request) {
        return request.loadBody().map(body -> {
            ImportHttpRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), ImportHttpRequest.class);
            LOG.info("Importing project state to: {}", req.projectPath());
            api.projects().importState(Paths.get(req.projectPath()), req.stateJson());
            return ResponseBuilder.ok().json(new ImportResponse(true, "Project state imported successfully")).build();
        });
    }

    // HTTP Request types
    public record CreateProjectHttpRequest(String packName, String projectName, String targetPath,
            Map<String, Object> variables, String sessionId) {}
    public record AddFeatureHttpRequest(String projectPath, String packName, String featureName,
            Map<String, Object> variables, Boolean force, String sessionId) {}
    public record UpdateProjectHttpRequest(String projectPath, String targetVersion, Boolean dryRun,
            String sessionId) {}
    public record PreviewUpdateHttpRequest(String projectPath, String targetVersion) {}
    public record ExportHttpRequest(String projectPath) {}
    public record ImportHttpRequest(String projectPath, String stateJson) {}

    // Response types
    public record CreateProjectResponse(boolean success, String projectPath, List<String> filesCreated,
            List<String> warnings, String sessionId) {}
    public record AddFeatureResponse(boolean success, List<String> filesAdded, List<String> filesModified,
            List<String> warnings, String sessionId) {}
    public record UpdateProjectResponse(boolean success, List<String> filesUpdated, List<String> filesAdded,
            List<String> filesRemoved, List<String> conflicts, String summary, String sessionId) {}
    public record FeaturesResponse(List<FeatureInfo> features, int count) {}
    public record ExportResponse(boolean success, String data) {}
    public record ImportResponse(boolean success, String message) {}
    public record ErrorResponse(String code, String message) {}
}
