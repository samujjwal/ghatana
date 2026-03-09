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
import com.ghatana.yappc.api.model.ConflictInfo;
import com.ghatana.yappc.api.model.DependencyAnalysis;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

/**
 * REST controller for dependency operations using ActiveJ HTTP.
 *
 * @doc.type class
 * @doc.purpose Dependency API endpoints
 * @doc.layer platform
 * @doc.pattern Controller
 */
public final class DependencyController {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyController.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final YappcApi api;

    public DependencyController(YappcApi api) {
        this.api = api;
    }

    /**
     * GET /api/v1/dependencies/analyze/pack/:name
     */
    public Promise<HttpResponse> analyzePack(HttpRequest request) {
        String packName = request.getPathParameter("name");
        LOG.debug("Analyzing dependencies for pack: {}", packName);
        DependencyAnalysis analysis = api.dependencies().analyzePack(packName);
        return Promise.of(ResponseBuilder.ok().json(analysis).build());
    }

    /**
     * POST /api/v1/dependencies/analyze/project
     */
    public Promise<HttpResponse> analyzeProject(HttpRequest request) {
        return request.loadBody().map(body -> {
            AnalyzeProjectRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), AnalyzeProjectRequest.class);
            LOG.debug("Analyzing dependencies for project: {}", req.projectPath());
            DependencyAnalysis analysis = api.dependencies().analyzeProject(Paths.get(req.projectPath()));
            return ResponseBuilder.ok().json(analysis).build();
        });
    }

    /**
     * POST /api/v1/dependencies/conflicts
     */
    public Promise<HttpResponse> checkConflicts(HttpRequest request) {
        return request.loadBody().map(body -> {
            CheckConflictsRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), CheckConflictsRequest.class);
            LOG.debug("Checking conflicts between packs: {}", req.packNames());
            List<ConflictInfo> conflicts = api.dependencies().checkConflicts(req.packNames());
            return ResponseBuilder.ok().json(new ConflictsResponse(conflicts, conflicts.isEmpty())).build();
        });
    }

    /**
     * POST /api/v1/dependencies/add-conflicts
     */
    public Promise<HttpResponse> checkAddConflicts(HttpRequest request) {
        return request.loadBody().map(body -> {
            AddConflictsRequest req = MAPPER.readValue(
                    body.getString(StandardCharsets.UTF_8), AddConflictsRequest.class);
            LOG.debug("Checking conflicts for adding pack {} to project {}",
                    req.packName(), req.projectPath());
            List<ConflictInfo> conflicts = api.dependencies().checkAddConflicts(
                    Paths.get(req.projectPath()), req.packName());
            return ResponseBuilder.ok().json(new ConflictsResponse(conflicts, conflicts.isEmpty())).build();
        });
    }

    // Request types
    public record AnalyzeProjectRequest(String projectPath) {}
    public record CheckConflictsRequest(List<String> packNames) {}
    public record AddConflictsRequest(String projectPath, String packName) {}

    // Response types
    public record ConflictsResponse(List<ConflictInfo> conflicts, boolean compatible) {}
}
