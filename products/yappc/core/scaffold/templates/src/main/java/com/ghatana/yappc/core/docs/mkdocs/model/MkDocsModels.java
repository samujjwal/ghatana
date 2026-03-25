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

package com.ghatana.yappc.core.docs.mkdocs.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * Data models for MkDocs documentation generation.
 *
 * <p>Contains all record classes and builders used in MkDocs site generation.
 *
 * @doc.type class
 * @doc.purpose Data models for MkDocs documentation generation.
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class MkDocsModels {

    private MkDocsModels() {
        // Utility class
    }

    /**
     * Request for generating an MkDocs documentation site.
     *
     * @param projectPath Path to the project to document
     * @param outputPath Path where the docs site will be generated
     * @param siteName Name of the documentation site
     * @param siteDescription Description of the site
     * @param author Author/owner of the documentation
     * @param contactEmail Contact email address
     * @param siteUrl URL where the site will be hosted
     * @param primaryColor Primary color for the theme (hex code)
     * @param accentColor Accent color for the theme (hex code)
     * @param twitterHandle Twitter handle for social integration
     * @param linkedinHandle LinkedIn handle for social integration
     * @param enableAnalytics Whether to enable Google Analytics
     * @param enableSearch Whether to enable site search
     * @param enableComments Whether to enable comments
     */
    public record MkDocsRequest(
            Path projectPath,
            Path outputPath,
            String siteName,
            String siteDescription,
            String author,
            String contactEmail,
            String siteUrl,
            String primaryColor,
            String accentColor,
            String twitterHandle,
            String linkedinHandle,
            boolean enableAnalytics,
            boolean enableSearch,
            boolean enableComments) {

        public static Builder builder() {
            return new Builder();
        }

        /**
 * Builder for MkDocsRequest with sensible defaults. */
        public static class Builder {
            private Path projectPath = Paths.get(".");
            private Path outputPath = Paths.get(".");
            private String siteName = "Project Documentation";
            private String siteDescription = "Comprehensive project documentation";
            private String author = "Development Team";
            private String contactEmail = "team@example.com";
            private String siteUrl = "https://docs.example.com";
            private String primaryColor = "#2196F3";
            private String accentColor = "#FF9800";
            private String twitterHandle = "example";
            private String linkedinHandle = "example";
            private boolean enableAnalytics = false;
            private boolean enableSearch = true;
            private boolean enableComments = false;

            public Builder projectPath(Path projectPath) {
                this.projectPath = projectPath;
                return this;
            }

            public Builder outputPath(Path outputPath) {
                this.outputPath = outputPath;
                return this;
            }

            public Builder siteName(String siteName) {
                this.siteName = siteName;
                return this;
            }

            public Builder siteDescription(String siteDescription) {
                this.siteDescription = siteDescription;
                return this;
            }

            public Builder author(String author) {
                this.author = author;
                return this;
            }

            public Builder contactEmail(String contactEmail) {
                this.contactEmail = contactEmail;
                return this;
            }

            public Builder siteUrl(String siteUrl) {
                this.siteUrl = siteUrl;
                return this;
            }

            public Builder primaryColor(String primaryColor) {
                this.primaryColor = primaryColor;
                return this;
            }

            public Builder accentColor(String accentColor) {
                this.accentColor = accentColor;
                return this;
            }

            public Builder twitterHandle(String twitterHandle) {
                this.twitterHandle = twitterHandle;
                return this;
            }

            public Builder linkedinHandle(String linkedinHandle) {
                this.linkedinHandle = linkedinHandle;
                return this;
            }

            public Builder enableAnalytics(boolean enableAnalytics) {
                this.enableAnalytics = enableAnalytics;
                return this;
            }

            public Builder enableSearch(boolean enableSearch) {
                this.enableSearch = enableSearch;
                return this;
            }

            public Builder enableComments(boolean enableComments) {
                this.enableComments = enableComments;
                return this;
            }

            public MkDocsRequest build() {
                return new MkDocsRequest(
                        projectPath,
                        outputPath,
                        siteName,
                        siteDescription,
                        author,
                        contactEmail,
                        siteUrl,
                        primaryColor,
                        accentColor,
                        twitterHandle,
                        linkedinHandle,
                        enableAnalytics,
                        enableSearch,
                        enableComments);
            }
        }
    }

    /**
     * Result of generating an MkDocs documentation site.
     *
     * @param siteDirectory Directory containing the generated site
     * @param projectInfo Information about the analyzed project
     * @param metadata Additional site metadata
     * @param generatedAt Timestamp when the site was generated
     */
    public record MkDocsGenerationResult(
            Path siteDirectory,
            ProjectInfo projectInfo,
            Map<String, Object> metadata,
            Instant generatedAt) {}

    /**
     * Information about a project being documented.
     *
     * @param name Project name
     * @param version Project version
     * @param description Project description
     * @param owner Project owner/organization
     * @param repositoryName Repository name
     * @param repositoryUrl Full repository URL
     * @param javaVersion Java version used
     * @param buildTool Build tool (gradle or maven)
     */
    public record ProjectInfo(
            String name,
            String version,
            String description,
            String owner,
            String repositoryName,
            String repositoryUrl,
            String javaVersion,
            String buildTool) {}

    /**
     * Result of building a static MkDocs site.
     *
     * @param success Whether the build succeeded
     * @param outputPath Path to the built static site
     * @param buildInfo Additional build information
     */
    public record BuildResult(boolean success, Path outputPath, Map<String, Object> buildInfo) {}
}
