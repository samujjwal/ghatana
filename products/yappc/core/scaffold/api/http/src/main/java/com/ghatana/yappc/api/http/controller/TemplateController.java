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
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * REST controller for template operations using ActiveJ HTTP.
 *
 * @doc.type class
 * @doc.purpose Template API endpoints
 * @doc.layer platform
 * @doc.pattern Controller
 */
public final class TemplateController {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateController.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final YappcApi api;

    public TemplateController(YappcApi api) {
        this.api = api;
    }

    /**
     * POST /api/v1/templates/render
     */
    public Promise<HttpResponse> render(HttpRequest request) {
        return request.loadBody().map(body -> {
            RenderRequest req = MAPPER.readValue(body.getString(StandardCharsets.UTF_8), RenderRequest.class);
            LOG.debug("Rendering template");
            try {
                String result = api.templates().render(
                        req.template(),
                        req.variables() != null ? req.variables() : Map.of()
                );
                return ResponseBuilder.ok().json(new RenderResponse(true, result, List.of())).build();
            } catch (Exception e) {
                LOG.error("Template rendering failed", e);
                return ResponseBuilder.ok().json(new RenderResponse(false, "", List.of(e.getMessage()))).build();
            }
        });
    }

    /**
     * GET /api/v1/templates/helpers
     */
    public Promise<HttpResponse> getHelpers(HttpRequest request) {
        LOG.debug("Getting template helpers");
        List<String> helpers = api.templates().getAvailableHelpers();
        return Promise.of(ResponseBuilder.ok().json(new HelpersResponse(helpers, helpers.size())).build());
    }

    /**
     * POST /api/v1/templates/validate
     */
    public Promise<HttpResponse> validate(HttpRequest request) {
        return request.loadBody().map(body -> {
            ValidateRequest req = MAPPER.readValue(body.getString(StandardCharsets.UTF_8), ValidateRequest.class);
            LOG.debug("Validating template");
            boolean valid = api.templates().validateSyntax(req.template());
            List<String> errors = valid ? List.of() : List.of("Template syntax is invalid");
            return ResponseBuilder.ok().json(new ValidateResponse(valid, errors)).build();
        });
    }

    // Request types
    public record RenderRequest(String template, Map<String, Object> variables) {}
    public record ValidateRequest(String template) {}

    // Response types
    public record RenderResponse(boolean success, String output, List<String> errors) {}
    public record HelpersResponse(List<String> helpers, int count) {}
    public record ValidateResponse(boolean valid, List<String> errors) {}
}
