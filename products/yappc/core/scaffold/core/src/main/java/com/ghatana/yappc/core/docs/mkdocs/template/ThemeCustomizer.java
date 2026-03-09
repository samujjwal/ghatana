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

package com.ghatana.yappc.core.docs.mkdocs.template;

import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.MkDocsRequest;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.ProjectInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Customizes the Material theme for MkDocs documentation.
 *
 * <p>Generates custom CSS, JavaScript, and template overrides to personalize the documentation
 * site appearance and behavior.
 *
 * @doc.type class
 * @doc.purpose Customizes the Material theme for MkDocs documentation.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ThemeCustomizer {

    private static final Logger log = LoggerFactory.getLogger(ThemeCustomizer.class);

    /**
     * Applies theme customizations to the documentation site.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @throws IOException if file writing fails
     */
    public void customizeTheme(Path docsPath, MkDocsRequest request, ProjectInfo projectInfo)
            throws IOException {
        generateCustomCSS(docsPath, request);
        generateCustomJavaScript(docsPath, request);
        generateThemeOverrides(docsPath, request, projectInfo);

        log.info("🎨 Generated custom theme files");
    }

    /**
     * Generates custom CSS stylesheet.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @throws IOException if file writing fails
     */
    private void generateCustomCSS(Path docsPath, MkDocsRequest request) throws IOException {
        String customCSS =
                String.format(
                        """
            /* Custom styles for %s documentation */

            :root {
              --md-primary-fg-color: %s;
              --md-accent-fg-color: %s;
              --md-text-font: "Roboto", -apple-system, BlinkMacSystemFont, Helvetica, Arial, sans-serif;
              --md-code-font: "Roboto Mono", SFMono-Regular, Consolas, Menlo, monospace;
            }

            /* Enhanced code blocks */
            .highlight {
              border-radius: 0.2rem;
              margin: 1em 0;
            }

            /* Custom admonitions */
            .md-typeset .admonition.tip {
              border-color: #00BCD4;
            }

            .md-typeset .admonition.tip > .admonition-title {
              background-color: rgba(0, 188, 212, 0.1);
              border-color: #00BCD4;
            }

            /* Navigation enhancements */
            .md-nav__title {
              font-weight: 600;
            }

            /* Custom homepage hero */
            .md-hero {
              background: linear-gradient(135deg, %s 0%%, %s 100%%);
            }

            /* Enhanced tables */
            .md-typeset table:not([class]) {
              border-radius: 0.2rem;
              overflow: hidden;
              box-shadow: 0 0.2rem 0.5rem rgba(0, 0, 0, 0.05);
            }

            /* Code copy button styling */
            .md-clipboard {
              color: var(--md-default-fg-color--light);
            }

            /* Search highlighting */
            .md-search-result__teaser mark {
              background-color: rgba(255, 235, 59, 0.5);
              color: inherit;
            }

            /* Custom footer */
            .md-footer {
              background-color: var(--md-default-bg-color);
              border-top: 0.05rem solid var(--md-default-fg-color--lightest);
            }
            """,
                        request.siteName(),
                        request.primaryColor(),
                        request.accentColor(),
                        request.primaryColor(),
                        request.accentColor());

        Files.writeString(docsPath.resolve("docs/assets/stylesheets/extra.css"), customCSS);
    }

    /**
     * Generates custom JavaScript for MathJax configuration.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @throws IOException if file writing fails
     */
    private void generateCustomJavaScript(Path docsPath, MkDocsRequest request) throws IOException {
        String mathjaxConfig =
                """
            window.MathJax = {
              tex: {
                inlineMath: [["\\\\(", "\\\\)"]],
                displayMath: [["\\\\[", "\\\\]"]],
                processEscapes: true,
                processEnvironments: true
              },
              options: {
                ignoreHtmlClass: ".*|",
                processHtmlClass: "arithmatex"
              }
            };

            document$.subscribe(() => {
              MathJax.typesetPromise()
            })
            """;

        Files.writeString(docsPath.resolve("docs/assets/javascripts/mathjax.js"), mathjaxConfig);
    }

    /**
     * Generates Jinja2 template overrides for the Material theme.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @throws IOException if file writing fails
     */
    private void generateThemeOverrides(
            Path docsPath, MkDocsRequest request, ProjectInfo projectInfo) throws IOException {
        // Generate main template override with meta tags and announcement banner
        String mainTemplate =
                String.format(
                        """
            {%%- extends "base.html" %%}

            {%%- block extrahead %%}
              <meta name="author" content="%s">
              <meta name="description" content="%s">
              <meta property="og:title" content="%s Documentation">
              <meta property="og:description" content="%s">
              <meta property="og:image" content="{{ config.site_url }}assets/images/og-image.png">
              <meta property="og:url" content="{{ config.site_url }}">
              <meta name="twitter:card" content="summary_large_image">
              <meta name="twitter:title" content="%s Documentation">
              <meta name="twitter:description" content="%s">
              <meta name="twitter:image" content="{{ config.site_url }}assets/images/twitter-card.png">
              <link rel="canonical" href="{{ page.canonical_url }}">
            {%%- endblock %%}

            {%%- block announce %%}
              <div class="md-banner">
                <div class="md-banner__inner md-grid md-typeset">
                  📢 Welcome to %s documentation!
                  <a href="%s">⭐ Star us on GitHub</a>
                </div>
              </div>
            {%%- endblock %%}
            """,
                        request.author(),
                        request.siteDescription(),
                        projectInfo.name(),
                        request.siteDescription(),
                        projectInfo.name(),
                        request.siteDescription(),
                        projectInfo.name(),
                        projectInfo.repositoryUrl());

        Files.writeString(docsPath.resolve("overrides/main.html"), mainTemplate);
    }
}
