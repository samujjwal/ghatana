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
import com.ghatana.yappc.api.model.PackInfo;
import com.ghatana.yappc.api.model.PackValidationResult;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for pack operations using ActiveJ HTTP.
 *
 * @doc.type class
 * @doc.purpose Pack API endpoints
 * @doc.layer platform
 * @doc.pattern Controller
 */
public final class PackController {

    private static final Logger LOG = LoggerFactory.getLogger(PackController.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final YappcApi api;

    public PackController(YappcApi api) {
        this.api = api;
    }

    /**
     * GET /api/v1/packs
     */
    public Promise<HttpResponse> list(HttpRequest request) {
        LOG.debug("Listing packs");
        String language = request.getQueryParameter("language");
        String category = request.getQueryParameter("category");
        String platform = request.getQueryParameter("platform");
        String search = request.getQueryParameter("search");
        int page = parseIntOrDefault(request.getQueryParameter("page"), 0);
        int pageSize = parseIntOrDefault(request.getQueryParameter("pageSize"), 20);

        List<PackInfo> packs;
        if (language != null) {
            packs = api.packs().byLanguage(language);
        } else if (category != null) {
            packs = api.packs().byCategory(category);
        } else if (platform != null) {
            packs = api.packs().byPlatform(platform);
        } else if (search != null && !search.isBlank()) {
            packs = api.packs().search(search);
        } else {
            packs = api.packs().list();
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, packs.size());
        List<PackInfo> pagedPacks = start < packs.size() ? packs.subList(start, end) : List.of();
        return Promise.of(ResponseBuilder.ok()
                .json(new PackListResponse(pagedPacks, packs.size(), page, pageSize)).build());
    }

    /**
     * GET /api/v1/packs/:name
     */
    public Promise<HttpResponse> get(HttpRequest request) {
        String name = request.getPathParameter("name");
        LOG.debug("Getting pack: {}", name);
        Optional<PackInfo> pack = api.packs().get(name);
        if (pack.isPresent()) {
            return Promise.of(ResponseBuilder.ok().json(pack.get()).build());
        }
        return Promise.of(ResponseBuilder.notFound()
                .json(new ErrorResponse("PACK_NOT_FOUND", "Pack not found: " + name)).build());
    }

    /**
     * GET /api/v1/packs/:name/validate
     */
    public Promise<HttpResponse> validate(HttpRequest request) {
        String name = request.getPathParameter("name");
        LOG.debug("Validating pack: {}", name);
        PackValidationResult result = api.packs().validate(name);
        return Promise.of(ResponseBuilder.ok().json(result).build());
    }

    /**
     * GET /api/v1/packs/:name/templates
     */
    public Promise<HttpResponse> listTemplates(HttpRequest request) {
        String name = request.getPathParameter("name");
        LOG.debug("Listing templates for pack: {}", name);
        Optional<PackInfo> pack = api.packs().get(name);
        if (pack.isPresent()) {
            return Promise.of(ResponseBuilder.ok()
                    .json(new TemplatesResponse(pack.get().getName() + " templates", 0)).build());
        }
        return Promise.of(ResponseBuilder.notFound()
                .json(new ErrorResponse("PACK_NOT_FOUND", "Pack not found: " + name)).build());
    }

    /**
     * GET /api/v1/packs/:name/variables
     */
    public Promise<HttpResponse> getVariables(HttpRequest request) {
        String name = request.getPathParameter("name");
        LOG.debug("Getting variables for pack: {}", name);
        Optional<PackInfo> pack = api.packs().get(name);
        if (pack.isPresent()) {
            PackInfo info = pack.get();
            return Promise.of(ResponseBuilder.ok().json(Map.of(
                    "required", info.getRequiredVariables(),
                    "optional", info.getOptionalVariables(),
                    "defaults", info.getDefaults()
            )).build());
        }
        return Promise.of(ResponseBuilder.notFound()
                .json(new ErrorResponse("PACK_NOT_FOUND", "Pack not found: " + name)).build());
    }

    /**
     * GET /api/v1/packs/languages
     */
    public Promise<HttpResponse> getLanguages(HttpRequest request) {
        LOG.debug("Getting languages");
        List<String> languages = api.packs().getAvailableLanguages();
        return Promise.of(ResponseBuilder.ok().json(new LanguagesResponse(languages)).build());
    }

    /**
     * GET /api/v1/packs/categories
     */
    public Promise<HttpResponse> getCategories(HttpRequest request) {
        LOG.debug("Getting categories");
        List<String> categories = api.packs().getAvailableCategories();
        return Promise.of(ResponseBuilder.ok().json(new CategoriesResponse(categories)).build());
    }

    /**
     * GET /api/v1/packs/platforms
     */
    public Promise<HttpResponse> getPlatforms(HttpRequest request) {
        LOG.debug("Getting platforms");
        List<String> platforms = api.packs().getAvailablePlatforms();
        return Promise.of(ResponseBuilder.ok().json(new PlatformsResponse(platforms)).build());
    }

    /**
     * POST /api/v1/packs/refresh
     */
    public Promise<HttpResponse> refresh(HttpRequest request) {
        LOG.info("Refreshing pack cache");
        api.packs().refresh();
        return Promise.of(ResponseBuilder.ok()
                .json(new RefreshResponse(true, "Pack cache refreshed successfully")).build());
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // Response types
    public record PackListResponse(List<PackInfo> packs, long total, int page, int pageSize) {}
    public record TemplatesResponse(String message, int count) {}
    public record LanguagesResponse(List<String> languages) {}
    public record CategoriesResponse(List<String> categories) {}
    public record PlatformsResponse(List<String> platforms) {}
    public record RefreshResponse(boolean success, String message) {}
    public record ErrorResponse(String code, String message) {}
}
