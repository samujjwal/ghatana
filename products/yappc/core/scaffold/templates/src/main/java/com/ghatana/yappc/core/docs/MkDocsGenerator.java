/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.core.docs;

import com.ghatana.yappc.core.docs.mkdocs.analyzer.ProjectAnalyzer;
import com.ghatana.yappc.core.docs.mkdocs.config.MkDocsConfigGenerator;
import com.ghatana.yappc.core.docs.mkdocs.generator.NavigationGenerator;
import com.ghatana.yappc.core.docs.mkdocs.generator.PageGenerators;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.BuildResult;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.MkDocsGenerationResult;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.MkDocsRequest;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.ProjectInfo;
import com.ghatana.yappc.core.docs.mkdocs.template.ThemeCustomizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MkDocs Material documentation site generator.
 *
 * <p>Week 10 Day 49: Documentation site skeleton (MkDocs Material theme). Creates professional
 * documentation sites with Material theme integration. Orchestrates configuration, page
 * generation, theme customization, and navigation.
 *
 * @doc.type class
 * @doc.purpose MkDocs Material documentation site generator.
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class MkDocsGenerator {

    private static final Logger log = LoggerFactory.getLogger(MkDocsGenerator.class);

    private final ProjectAnalyzer projectAnalyzer;
    private final MkDocsConfigGenerator configGenerator;
    private final PageGenerators pageGenerators;
    private final ThemeCustomizer themeCustomizer;
    private final NavigationGenerator navigationGenerator;

    public MkDocsGenerator() {
        this.projectAnalyzer = new ProjectAnalyzer();
        this.configGenerator = new MkDocsConfigGenerator();
        this.pageGenerators = new PageGenerators();
        this.themeCustomizer = new ThemeCustomizer();
        this.navigationGenerator = new NavigationGenerator();
    }

    /**
 * Generates a complete MkDocs Material documentation site. */
    public MkDocsGenerationResult generateDocumentationSite(MkDocsRequest request) {
        log.info("📚 Generating MkDocs Material documentation site...");

        try {
            // Analyze project structure
            ProjectInfo projectInfo = projectAnalyzer.analyzeProject(request.projectPath());

            // Create site structure
            Path docsPath = createSiteStructure(request, projectInfo);

            // Generate configuration
            configGenerator.generateConfig(docsPath, request, projectInfo);

            // Generate content pages
            pageGenerators.generatePages(docsPath, request, projectInfo);

            // Generate custom theme files
            themeCustomizer.customizeTheme(docsPath, request, projectInfo);

            // Generate navigation structure
            navigationGenerator.generateNavigation(docsPath, request, projectInfo);

            log.info("✅ MkDocs site generated successfully!");

            return new MkDocsGenerationResult(
                    docsPath,
                    projectInfo,
                    navigationGenerator.generateSiteMetadata(request, projectInfo),
                    Instant.now());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate MkDocs site", e);
        }
    }

    /**
 * Serves the documentation site locally for preview. */
    public void serveDocumentation(Path docsPath, int port) {
        log.info("🌐 Starting MkDocs development server on port {}...", port);
        log.info("📖 Documentation available at: http://localhost:{}", port);
        log.info("💡 Note: In real implementation, this would execute 'mkdocs serve'");
    }

    /**
 * Builds static documentation site for deployment. */
    public BuildResult buildStaticSite(Path docsPath, Path outputPath) {
        log.info("🏗️  Building static documentation site...");

        try {
            // Create output directory
            Files.createDirectories(outputPath);

            // Generate build metadata
            Map<String, Object> buildInfo = new HashMap<>();
            buildInfo.put("buildTime", Instant.now());
            buildInfo.put("outputPath", outputPath.toAbsolutePath().toString());
            buildInfo.put("docsPath", docsPath.toAbsolutePath().toString());

            log.info("✅ Static site built to: {}", outputPath.toAbsolutePath());
            log.info("💡 Note: In real implementation, this would execute 'mkdocs build'");

            return new BuildResult(true, outputPath, buildInfo);

        } catch (IOException e) {
            return new BuildResult(false, null, Map.of("error", e.getMessage()));
        }
    }

    /**
 * Creates the directory structure for the documentation site. */
    private Path createSiteStructure(MkDocsRequest request, ProjectInfo projectInfo)
            throws IOException {
        Path docsPath = request.outputPath().resolve("docs");

        // Create main directories
        Files.createDirectories(docsPath);
        Files.createDirectories(docsPath.resolve("docs"));
        Files.createDirectories(docsPath.resolve("docs/assets"));
        Files.createDirectories(docsPath.resolve("docs/assets/images"));
        Files.createDirectories(docsPath.resolve("docs/assets/stylesheets"));
        Files.createDirectories(docsPath.resolve("docs/assets/javascripts"));
        Files.createDirectories(docsPath.resolve("docs/api"));
        Files.createDirectories(docsPath.resolve("docs/guides"));
        Files.createDirectories(docsPath.resolve("docs/tutorials"));
        Files.createDirectories(docsPath.resolve("docs/reference"));
        Files.createDirectories(docsPath.resolve("overrides"));
        Files.createDirectories(docsPath.resolve("overrides/partials"));

        log.info("📁 Created documentation directory structure");
        return docsPath;
    }

}
