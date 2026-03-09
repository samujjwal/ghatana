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

package com.ghatana.yappc.core.docs.mkdocs.generator;

import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.MkDocsRequest;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.ProjectInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generator for MkDocs navigation structure and site metadata.
 *
 * <p>Creates the navigation configuration and collects site metadata for documentation generation.
 *
 * @doc.type class
 * @doc.purpose Generator for MkDocs navigation structure and site metadata.
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class NavigationGenerator {

    private static final Logger log = LoggerFactory.getLogger(NavigationGenerator.class);

    /**
     * Generates navigation structure and appends to mkdocs.yml.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @throws IOException if file operations fail
     */
    public void generateNavigation(Path docsPath, MkDocsRequest request, ProjectInfo projectInfo)
            throws IOException {
        // Generate navigation configuration
        String navConfig =
                """
            nav:
              - Home: index.md
              - Getting Started:
                - guides/getting-started.md
                - guides/configuration.md
                - guides/first-project.md
              - User Guides:
                - guides/index.md
                - Core Concepts:
                  - guides/concepts/project-structure.md
                  - guides/concepts/templates.md
                  - guides/concepts/build-process.md
                  - guides/concepts/caching.md
                - Advanced Topics:
                  - guides/advanced/custom-templates.md
                  - guides/advanced/plugins.md
                  - guides/advanced/performance.md
                  - guides/advanced/security.md
              - Tutorials:
                - tutorials/index.md
                - Beginner:
                  - tutorials/beginner/first-project.md
                  - tutorials/beginner/templates.md
                  - tutorials/beginner/configuration.md
                - Intermediate:
                  - tutorials/intermediate/custom-templates.md
                  - tutorials/intermediate/cicd-setup.md
                  - tutorials/intermediate/performance.md
                - Advanced:
                  - tutorials/advanced/plugin-development.md
                  - tutorials/advanced/enterprise.md
                  - tutorials/advanced/integrations.md
              - API Reference:
                - api/index.md
                - api/projects.md
                - api/templates.md
                - api/build.md
                - api/cache.md
              - Reference:
                - reference/index.md
                - Architecture:
                  - reference/architecture.md
                  - reference/components.md
                  - reference/data-flow.md
                  - reference/security.md
                - Configuration:
                  - reference/configuration.md
                  - reference/environment.md
                  - reference/cli-reference.md
                  - reference/properties.md
            """;

        // Append to mkdocs.yml
        String existingConfig = Files.readString(docsPath.resolve("mkdocs.yml"));
        Files.writeString(docsPath.resolve("mkdocs.yml"), existingConfig + "\n" + navConfig);

        log.info("🧭 Generated navigation structure");
    }

    /**
     * Generates metadata about the documentation site.
     *
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @return Map of metadata key-value pairs
     */
    public Map<String, Object> generateSiteMetadata(
            MkDocsRequest request, ProjectInfo projectInfo) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("siteName", request.siteName());
        metadata.put("siteDescription", request.siteDescription());
        metadata.put("author", request.author());
        metadata.put("version", projectInfo.version());
        metadata.put("buildTool", "mkdocs");
        metadata.put("theme", "material");
        metadata.put(
                "features",
                Arrays.asList(
                        "Material Design theme",
                        "Responsive layout",
                        "Search functionality",
                        "Code syntax highlighting",
                        "Mermaid diagrams",
                        "Mathematical expressions",
                        "Social media integration",
                        "Git integration",
                        "Multi-language support"));

        return metadata;
    }
}
