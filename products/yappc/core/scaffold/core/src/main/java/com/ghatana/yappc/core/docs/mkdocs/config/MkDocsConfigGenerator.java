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

package com.ghatana.yappc.core.docs.mkdocs.config;

import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.MkDocsRequest;
import com.ghatana.yappc.core.docs.mkdocs.model.MkDocsModels.ProjectInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generator for MkDocs configuration files.
 *
 * <p>Creates mkdocs.yml configuration with Material theme settings, plugins, and markdown
 * extensions.
 *
 * @doc.type class
 * @doc.purpose Generator for MkDocs configuration files.
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class MkDocsConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(MkDocsConfigGenerator.class);

    /**
     * Generates the mkdocs.yml configuration file.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @throws IOException if file writing fails
     */
    public void generateConfig(Path docsPath, MkDocsRequest request, ProjectInfo projectInfo)
            throws IOException {
        String config = generateMkDocsYaml(request, projectInfo);
        Files.writeString(docsPath.resolve("mkdocs.yml"), config);
        log.info("⚙️  Generated mkdocs.yml configuration");
    }

    /**
     * Generates the YAML content for mkdocs.yml.
     *
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @return YAML configuration as string
     */
    private String generateMkDocsYaml(MkDocsRequest request, ProjectInfo projectInfo) {
        return String.format(
                """
            site_name: %s Documentation
            site_description: %s
            site_author: %s
            site_url: %s

            repo_name: %s
            repo_url: %s

            copyright: Copyright &copy; %d %s

            theme:
              name: material
              language: en
              palette:
                # Palette toggle for automatic mode
                - media: "(prefers-color-scheme)"
                  toggle:
                    icon: material/brightness-auto
                    name: Switch to light mode

                # Palette toggle for light mode
                - media: "(prefers-color-scheme: light)"
                  scheme: default
                  primary: %s
                  accent: %s
                  toggle:
                    icon: material/brightness-7
                    name: Switch to dark mode

                # Palette toggle for dark mode
                - media: "(prefers-color-scheme: dark)"
                  scheme: slate
                  primary: %s
                  accent: %s
                  toggle:
                    icon: material/brightness-4
                    name: Switch to system preference

              font:
                text: Roboto
                code: Roboto Mono

              features:
                - announce.dismiss
                - content.action.edit
                - content.action.view
                - content.code.annotate
                - content.code.copy
                - content.tabs.link
                - content.tooltips
                - header.autohide
                - navigation.expand
                - navigation.footer
                - navigation.indexes
                - navigation.sections
                - navigation.tabs
                - navigation.tabs.sticky
                - navigation.top
                - navigation.tracking
                - search.highlight
                - search.share
                - search.suggest
                - toc.follow
                - toc.integrate

              icon:
                repo: fontawesome/brands/github
                edit: material/pencil
                view: material/eye

              logo: assets/images/logo.png
              favicon: assets/images/favicon.ico

            extra:
              version:
                provider: mike
              social:
                - icon: fontawesome/brands/github
                  link: %s
                - icon: fontawesome/brands/twitter
                  link: https://twitter.com/%s
                - icon: fontawesome/brands/linkedin
                  link: https://linkedin.com/company/%s
              analytics:
                provider: google
                property: !ENV [GOOGLE_ANALYTICS_KEY, ""]
                feedback:
                  title: Was this page helpful?
                  ratings:
                    - icon: material/thumb-up-outline
                      name: This page was helpful
                      data: 1
                      note: >-
                        Thanks for your feedback!
                    - icon: material/thumb-down-outline
                      name: This page could be improved
                      data: 0
                      note: >-
                        Thanks for your feedback! Help us improve this page by
                        <a href="https://github.com/%s/issues/new/?title=[Feedback]+{title}+-+{url}" target="_blank" rel="noopener">telling us what you found helpful or what could be improved</a>.

            plugins:
              - search:
                  separator: '[\\s\\-,:!=\\[\\]()\"/]+|(?!\\b)(?=[A-Z][a-z])|\\b(?=[A-Z][a-z])'
              - minify:
                  minify_html: true
              - git-revision-date-localized:
                  enable_creation_date: true
              - git-committers:
                  repository: %s
                  branch: main
              - awesome-pages
              - macros
              - mermaid2

            markdown_extensions:
              - abbr
              - admonition
              - attr_list
              - def_list
              - footnotes
              - md_in_html
              - toc:
                  permalink: true
                  title: On this page
              - tables
              - pymdownx.arithmatex:
                  generic: true
              - pymdownx.betterem:
                  smart_enable: all
              - pymdownx.caret
              - pymdownx.details
              - pymdownx.emoji:
                  emoji_generator: !!python/name:material.extensions.emoji.to_svg
                  emoji_index: !!python/name:material.extensions.emoji.twemoji
              - pymdownx.highlight:
                  anchor_linenums: true
                  line_spans: __span
                  pygments_lang_class: true
              - pymdownx.inlinehilite
              - pymdownx.keys
              - pymdownx.magiclink:
                  normalize_issue_symbols: true
                  repo_url_shorthand: true
                  user: %s
                  repo: %s
              - pymdownx.mark
              - pymdownx.smartsymbols
              - pymdownx.snippets:
                  auto_append:
                    - includes/abbreviations.md
              - pymdownx.superfences:
                  custom_fences:
                    - name: mermaid
                      class: mermaid
                      format: !!python/name:pymdownx.superfences.fence_code_format
              - pymdownx.tabbed:
                  alternate_style: true
              - pymdownx.tasklist:
                  custom_checkbox: true
              - pymdownx.tilde

            extra_css:
              - assets/stylesheets/extra.css

            extra_javascript:
              - assets/javascripts/mathjax.js
              - https://polyfill.io/v3/polyfill.min.js?features=es6
              - https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js

            watch:
              - docs
              - overrides
            """,
                projectInfo.name(),
                request.siteDescription(),
                request.author(),
                request.siteUrl(),
                projectInfo.repositoryName(),
                projectInfo.repositoryUrl(),
                LocalDate.now().getYear(),
                request.author(),
                request.primaryColor(),
                request.accentColor(),
                request.primaryColor(),
                request.accentColor(),
                projectInfo.repositoryUrl(),
                request.twitterHandle(),
                request.linkedinHandle(),
                projectInfo.repositoryName(),
                projectInfo.repositoryUrl(),
                projectInfo.owner(),
                projectInfo.name());
    }
}
