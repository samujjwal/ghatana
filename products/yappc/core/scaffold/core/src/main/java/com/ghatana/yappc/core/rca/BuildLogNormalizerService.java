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

package com.ghatana.yappc.core.rca;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Day 26: Build log normalizer service that manages multiple normalizers. Automatically detects the
 * appropriate normalizer for given build logs.
 *
 * @doc.type class
 * @doc.purpose Day 26: Build log normalizer service that manages multiple normalizers. Automatically detects the
 * @doc.layer platform
 * @doc.pattern Service
 */
public class BuildLogNormalizerService {

    private final List<BuildLogNormalizer> normalizers;

    public BuildLogNormalizerService() {
        this.normalizers = new ArrayList<>();
        initializeDefaultNormalizers();
    }

    public BuildLogNormalizerService(List<BuildLogNormalizer> normalizers) {
        this.normalizers = new ArrayList<>(normalizers);
    }

    /**
     * Detect build tool from log content
     *
     * @param rawLog Raw build log content
     * @return Detected build tool, or null if not detected
     */
    public NormalizedBuildLog.BuildTool detectBuildTool(String rawLog) {
        Optional<BuildLogNormalizer> normalizer = findNormalizer(rawLog);
        return normalizer.map(BuildLogNormalizer::getSupportedTool).orElse(null);
    }

    /**
     * Normalize build log by automatically detecting the appropriate normalizer
     *
     * @param rawLog Raw build log content
     * @return Normalized build log
     * @throws IOException if no suitable normalizer found or parsing fails
     */
    public NormalizedBuildLog normalize(String rawLog) throws IOException {
        Optional<BuildLogNormalizer> normalizer = findNormalizer(rawLog);

        if (normalizer.isEmpty()) {
            throw new IOException("No suitable normalizer found for the provided build log");
        }

        return normalizer.get().normalize(rawLog);
    }

    /**
     * Normalize build log with specified build tool
     *
     * @param rawLog Raw build log content
     * @param buildTool Specific build tool to use for normalization
     * @return Normalized build log
     * @throws IOException if no normalizer found for the specified tool
     */
    public NormalizedBuildLog normalize(String rawLog, NormalizedBuildLog.BuildTool buildTool)
            throws IOException {
        Optional<BuildLogNormalizer> normalizer =
                normalizers.stream()
                        .filter(n -> n.getSupportedTool().equals(buildTool))
                        .findFirst();

        if (normalizer.isEmpty()) {
            throw new IOException("No normalizer found for build tool: " + buildTool);
        }

        return normalizer.get().normalize(rawLog);
    }

    /**
     * Normalize build log from file
     *
     * @param logFile Path to build log file
     * @return Normalized build log
     * @throws IOException if file reading fails or no suitable normalizer found
     */
    public NormalizedBuildLog normalize(Path logFile) throws IOException {
        // Read the file first to detect the appropriate normalizer
        java.nio.file.Files.lines(logFile)
                .limit(100) // Check first 100 lines for performance
                .reduce((a, b) -> a + "\n" + b)
                .ifPresentOrElse(
                        content -> {
                            try {
                                Optional<BuildLogNormalizer> normalizer = findNormalizer(content);
                                if (normalizer.isEmpty()) {
                                    throw new RuntimeException("No suitable normalizer found");
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        () -> {
                            throw new RuntimeException("Empty log file");
                        });

        // Use first available normalizer that can handle the file
        for (BuildLogNormalizer normalizer : normalizers) {
            String sample =
                    java.nio.file.Files.lines(logFile)
                            .limit(50)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("");

            if (normalizer.canHandle(sample)) {
                return normalizer.normalize(logFile);
            }
        }

        throw new IOException("No suitable normalizer found for file: " + logFile);
    }

    /**
     * Get list of supported build tools
     *
     * @return List of supported build tools
     */
    public List<NormalizedBuildLog.BuildTool> getSupportedTools() {
        return normalizers.stream().map(BuildLogNormalizer::getSupportedTool).distinct().toList();
    }

    /**
     * Add a custom normalizer
     *
     * @param normalizer Custom build log normalizer
     */
    public void addNormalizer(BuildLogNormalizer normalizer) {
        this.normalizers.add(normalizer);
    }

    /**
     * Remove normalizer for specific tool
     *
     * @param tool Build tool to remove normalizer for
     * @return true if normalizer was removed
     */
    public boolean removeNormalizer(NormalizedBuildLog.BuildTool tool) {
        return normalizers.removeIf(normalizer -> normalizer.getSupportedTool().equals(tool));
    }

    /**
     * Find appropriate normalizer for the given log content
     *
     * @param rawLog Raw build log content
     * @return Optional normalizer that can handle the log
     */
    private Optional<BuildLogNormalizer> findNormalizer(String rawLog) {
        return normalizers.stream().filter(normalizer -> normalizer.canHandle(rawLog)).findFirst();
    }

    /**
 * Initialize default normalizers for common build tools */
    private void initializeDefaultNormalizers() {
        // Add Gradle normalizer
        normalizers.add(new GradleBuildLogNormalizer());

        // Add Nx normalizer
        normalizers.add(new NxBuildLogNormalizer());

        // Add other normalizers (to be implemented)
        // normalizers.add(new PnpmBuildLogNormalizer());
        // normalizers.add(new CargoBuildLogNormalizer());
        // normalizers.add(new MavenBuildLogNormalizer());
    }
}
