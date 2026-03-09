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

package com.ghatana.yappc.api.service;

import com.ghatana.yappc.api.model.PackInfo;
import com.ghatana.yappc.api.model.PackListRequest;
import com.ghatana.yappc.api.model.PackValidationResult;

import java.util.List;
import java.util.Optional;

/**
 * Service for pack discovery, listing, and validation.
 *
 * @doc.type interface
 * @doc.purpose Pack management operations
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PackService {

    /**
     * List all available packs.
     *
     * @return List of all packs
     */
    List<PackInfo> list();

    /**
     * List packs with filtering options.
     *
     * @param request The filter criteria
     * @return Filtered list of packs
     */
    List<PackInfo> list(PackListRequest request);

    /**
     * Get pack by name.
     *
     * @param packName The pack name
     * @return The pack info if found
     */
    Optional<PackInfo> get(String packName);

    /**
     * Get packs by language.
     *
     * @param language The language (java, typescript, rust, go)
     * @return List of matching packs
     */
    List<PackInfo> byLanguage(String language);

    /**
     * Get packs by category.
     *
     * @param category The category (backend, fullstack, middleware, platform, feature)
     * @return List of matching packs
     */
    List<PackInfo> byCategory(String category);

    /**
     * Get packs by platform.
     *
     * @param platform The platform (server, desktop, mobile, web)
     * @return List of matching packs
     */
    List<PackInfo> byPlatform(String platform);

    /**
     * Search packs by name or description.
     *
     * @param query The search query
     * @return List of matching packs
     */
    List<PackInfo> search(String query);

    /**
     * Validate a pack structure and templates.
     *
     * @param packName The pack to validate
     * @return Validation results
     */
    PackValidationResult validate(String packName);

    /**
     * Check if a pack exists.
     *
     * @param packName The pack name
     * @return true if the pack exists
     */
    boolean exists(String packName);

    /**
     * Get all available languages across packs.
     *
     * @return List of languages
     */
    List<String> getAvailableLanguages();

    /**
     * Get all available categories across packs.
     *
     * @return List of categories
     */
    List<String> getAvailableCategories();

    /**
     * Get all available platforms across packs.
     *
     * @return List of platforms
     */
    List<String> getAvailablePlatforms();

    /**
     * Refresh the pack cache.
     */
    void refresh();
}
