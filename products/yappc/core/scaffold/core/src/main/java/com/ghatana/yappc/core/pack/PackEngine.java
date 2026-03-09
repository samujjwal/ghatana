/*
 * Copyright (c) 2024 Ghatana, Inc.
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

package com.ghatana.yappc.core.pack;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Pack generation engine interface. Week 2, Day 7 deliverable - Pack generation system.
 * @doc.type interface
 * @doc.purpose Pack generation engine interface. Week 2, Day 7 deliverable - Pack generation system.
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface PackEngine {

    /**
     * Load a pack from its directory.
     *
     * @param packPath Path to the pack directory containing pack.json
     * @return Pack instance
     * @throws PackException If pack loading fails
     */
    Pack loadPack(Path packPath) throws PackException;

    /**
     * Generate project files from a pack.
     *
     * @param pack The pack to generate from
     * @param outputPath Target directory for generated files
     * @param variables Template variables for generation
     * @return Generation result
     * @throws PackException If generation fails
     */
    GenerationResult generateFromPack(Pack pack, Path outputPath, Map<String, Object> variables)
            throws PackException;

    /**
     * List all available packs in the pack registry.
     *
     * @return List of available pack metadata
     */
    List<PackMetadata> listAvailablePacks();

    /**
     * Validate a pack structure and metadata.
     *
     * @param packPath Path to pack directory
     * @return Validation result
     */
    ValidationResult validatePack(Path packPath);

    /**
     * Register a pack discovery location.
     *
     * @param packRegistryPath Path to directory containing packs
     */
    void registerPackLocation(Path packRegistryPath);

    /**
 * Generation result information. */
    record GenerationResult(
            boolean successful,
            int filesGenerated,
            List<String> generatedFiles,
            List<String> errors,
            List<String> warnings,
            String summary,
            Map<String, Object> metadata) {}

    /**
 * Pack validation result. */
    record ValidationResult(
            boolean valid, List<String> errors, List<String> warnings, String summary) {}
}
