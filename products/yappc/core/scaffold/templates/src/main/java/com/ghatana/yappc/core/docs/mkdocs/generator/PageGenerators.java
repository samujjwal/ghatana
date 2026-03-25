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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generator for documentation page content.
 *
 * <p>Creates markdown pages including index, getting started, API docs, user guides, tutorials,
 * and reference documentation.
 *
 * @doc.type class
 * @doc.purpose Generator for documentation page content.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PageGenerators {

    private static final Logger log = LoggerFactory.getLogger(PageGenerators.class);

    /**
     * Generates all documentation pages.
     *
     * @param docsPath Root path of the documentation site
     * @param request Documentation site request
     * @param projectInfo Information about the project
     * @throws IOException if file writing fails
     */
    public void generatePages(Path docsPath, MkDocsRequest request, ProjectInfo projectInfo)
            throws IOException {
        Path docsDir = docsPath.resolve("docs");

        // Generate all page types
        generateIndexPage(docsDir, request, projectInfo);
        generateGettingStartedGuide(docsDir, projectInfo);
        generateApiDocumentation(docsDir, projectInfo);
        generateUserGuides(docsDir, projectInfo);
        generateTutorials(docsDir, projectInfo);
        generateReferenceDocumentation(docsDir, projectInfo);

        log.info("📄 Generated documentation pages");
    }

    /**
 * Generates the main index/homepage. */
    public void generateIndexPage(Path docsDir, MkDocsRequest request, ProjectInfo projectInfo)
            throws IOException {
        String indexContent =
                String.format(
                        """
            # %s

            %s

            ## 🚀 Quick Start

            Get started with %s in just a few minutes!

            ### Installation

            ```bash
            # Clone the repository
            git clone %s
            cd %s

            # Install dependencies
            %s

            # Run the application
            %s
            ```

            ### First Steps

            1. **[Getting Started Guide](guides/getting-started.md)** - Learn the basics
            2. **[API Reference](api/index.md)** - Explore the API
            3. **[Tutorials](tutorials/index.md)** - Follow step-by-step guides

            ## 📚 Documentation Structure

            | Section | Description |
            |---------|-------------|
            | **[Getting Started](guides/getting-started.md)** | Installation and basic setup |
            | **[User Guides](guides/index.md)** | Comprehensive user documentation |
            | **[Tutorials](tutorials/index.md)** | Step-by-step learning paths |
            | **[API Reference](api/index.md)** | Complete API documentation |
            | **[Reference](reference/index.md)** | Technical reference materials |

            ## ✨ Key Features

            %s

            ## 🎯 Use Cases

            %s

            ## 🏗️ Architecture

            ```mermaid
            graph TB
                A[User Interface] --> B[Application Layer]
                B --> C[Business Logic]
                C --> D[Data Layer]
                D --> E[External Services]

                subgraph "Core Components"
                    B
                    C
                end

                subgraph "Infrastructure"
                    D
                    E
                end
            ```

            ## 🤝 Contributing

            We welcome contributions! See our [Contributing Guide](guides/contributing.md) for details.

            ## 📄 License

            %s is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

            ## 🆘 Support

            - 📖 **Documentation**: Browse this documentation
            - 🐛 **Bug Reports**: [Create an issue](%s/issues)
            - 💬 **Discussions**: [Join the conversation](%s/discussions)
            - 📧 **Contact**: [%s](mailto:%s)
            """,
                        projectInfo.name(),
                        request.siteDescription(),
                        projectInfo.name(),
                        projectInfo.repositoryUrl(),
                        projectInfo.name(),
                        projectInfo.buildTool().equals("gradle")
                                ? "./gradlew build"
                                : "mvn install",
                        projectInfo.buildTool().equals("gradle")
                                ? "./gradlew run"
                                : "mvn exec:java",
                        generateFeaturesList(projectInfo),
                        generateUseCasesList(projectInfo),
                        projectInfo.name(),
                        projectInfo.repositoryUrl(),
                        projectInfo.repositoryUrl(),
                        request.author(),
                        request.contactEmail());

        Files.writeString(docsDir.resolve("index.md"), indexContent);
    }

    /**
 * Generates the getting started guide. */
    public void generateGettingStartedGuide(Path docsDir, ProjectInfo projectInfo)
            throws IOException {
        Path guidesDir = docsDir.resolve("guides");
        Files.createDirectories(guidesDir);

        String gettingStartedContent =
                String.format(
                        """
            # Getting Started

            Welcome to %s! This guide will help you get up and running quickly.

            ## Prerequisites

            Before you begin, ensure you have the following installed:

            - **Java**: Version %s or higher
            - **Build Tool**: %s
            - **Git**: For version control

            ## Installation

            ### 1. Clone the Repository

            ```bash
            git clone %s
            cd %s
            ```

            ### 2. Build the Project

            ```bash
            %s
            ```

            ### 3. Run Tests

            ```bash
            %s
            ```

            ### 4. Start the Application

            ```bash
            %s
            ```

            ## Configuration

            ### Basic Configuration

            The application can be configured through various methods:

            1. **Configuration Files**: Edit `application.yml` or `application.properties`
            2. **Environment Variables**: Set environment-specific values
            3. **Command Line Arguments**: Override settings at runtime

            ### Example Configuration

            ```yaml
            app:
              name: %s
              version: %s
              debug: false

            server:
              port: 8080
              host: localhost

            logging:
              level: INFO
              file: logs/app.log
            ```

            ## Next Steps

            Now that you have %s running, explore these resources:

            - 📖 **[User Guides](index.md)**: Learn about core concepts
            - 🎓 **[Tutorials](../tutorials/index.md)**: Follow hands-on examples
            - 🔧 **[API Reference](../api/index.md)**: Explore the API
            - 🏗️ **[Architecture](../reference/architecture.md)**: Understand the design

            ## Troubleshooting

            ### Common Issues

            **Build Failures**
            : Ensure you have the correct Java version and build tool installed

            **Port Conflicts**
            : Change the server port in configuration if port 8080 is in use

            **Permission Errors**
            : Make sure you have write permissions in the project directory

            ### Getting Help

            If you encounter issues:

            1. Check the [FAQ](faq.md)
            2. Search [existing issues](%s/issues)
            3. Create a [new issue](%s/issues/new) with details
            """,
                        projectInfo.name(),
                        projectInfo.javaVersion(),
                        projectInfo.buildTool(),
                        projectInfo.repositoryUrl(),
                        projectInfo.name(),
                        projectInfo.buildTool().equals("gradle")
                                ? "./gradlew build"
                                : "mvn compile",
                        projectInfo.buildTool().equals("gradle") ? "./gradlew test" : "mvn test",
                        projectInfo.buildTool().equals("gradle")
                                ? "./gradlew run"
                                : "mvn exec:java",
                        projectInfo.name(),
                        projectInfo.version(),
                        projectInfo.name(),
                        projectInfo.repositoryUrl(),
                        projectInfo.repositoryUrl());

        Files.writeString(guidesDir.resolve("getting-started.md"), gettingStartedContent);
    }

    /**
 * Generates API documentation index. */
    public void generateApiDocumentation(Path docsDir, ProjectInfo projectInfo)
            throws IOException {
        Path apiDir = docsDir.resolve("api");
        Files.createDirectories(apiDir);

        String apiIndexContent =
                String.format(
                        """
            # API Reference

            Complete API documentation for %s.

            ## Overview

            The %s API provides programmatic access to all core functionality.

            ### Base URL

            ```
            https://api.%s.com/v1
            ```

            ### Authentication

            API requests require authentication using API keys:

            ```bash
            curl -H "Authorization: Bearer YOUR_API_KEY" \\
                 https://api.%s.com/v1/endpoint
            ```

            ## Core APIs

            | API | Description | Documentation |
            |-----|-------------|---------------|
            | **Projects** | Manage projects and configurations | [Projects API](projects.md) |
            | **Templates** | Access and manage templates | [Templates API](templates.md) |
            | **Build** | Trigger and monitor builds | [Build API](build.md) |
            | **Cache** | Cache management operations | [Cache API](cache.md) |

            ## OpenAPI Specification

            Download the complete API specification:

            - [OpenAPI 3.0 (JSON)](openapi.json)
            - [OpenAPI 3.0 (YAML)](openapi.yaml)
            - [Postman Collection](postman.json)

            ## SDKs and Libraries

            Official SDKs are available for popular programming languages:

            - **Java**: [%s-java](https://github.com/%s/%s-java)
            - **Python**: [%s-python](https://github.com/%s/%s-python)
            - **JavaScript**: [%s-js](https://github.com/%s/%s-js)

            ## Rate Limits

            API requests are subject to rate limiting:

            | Tier | Requests per minute | Requests per hour |
            |------|-------------------|------------------|
            | Free | 100 | 1,000 |
            | Pro | 1,000 | 10,000 |
            | Enterprise | 10,000 | 100,000 |

            ## Error Handling

            The API uses conventional HTTP response codes:

            | Code | Meaning |
            |------|---------|
            | 200 | Success |
            | 400 | Bad Request |
            | 401 | Unauthorized |
            | 403 | Forbidden |
            | 404 | Not Found |
            | 429 | Too Many Requests |
            | 500 | Internal Server Error |

            ### Error Response Format

            ```json
            {
              "error": {
                "code": "INVALID_REQUEST",
                "message": "The request was invalid",
                "details": "Missing required parameter: name"
              }
            }
            ```
            """,
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name().toLowerCase(),
                        projectInfo.name().toLowerCase(),
                        projectInfo.name(),
                        projectInfo.owner(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.owner(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.owner(),
                        projectInfo.name());

        Files.writeString(apiDir.resolve("index.md"), apiIndexContent);
    }

    /**
 * Generates user guides index. */
    public void generateUserGuides(Path docsDir, ProjectInfo projectInfo) throws IOException {
        Path guidesDir = docsDir.resolve("guides");

        String guidesIndexContent =
                String.format(
                        """
            # User Guides

            Comprehensive guides for using %s effectively.

            ## Getting Started

            - [Installation & Setup](getting-started.md)
            - [Basic Configuration](configuration.md)
            - [First Project](first-project.md)

            ## Core Concepts

            - [Project Structure](concepts/project-structure.md)
            - [Template System](concepts/templates.md)
            - [Build Process](concepts/build-process.md)
            - [Caching Strategy](concepts/caching.md)

            ## Advanced Topics

            - [Custom Templates](advanced/custom-templates.md)
            - [Plugin Development](advanced/plugins.md)
            - [Performance Tuning](advanced/performance.md)
            - [Security Best Practices](advanced/security.md)

            ## Integration Guides

            - [CI/CD Integration](integrations/cicd.md)
            - [IDE Setup](integrations/ide.md)
            - [Docker Usage](integrations/docker.md)
            - [Cloud Deployment](integrations/cloud.md)

            ## Best Practices

            - [Project Organization](best-practices/organization.md)
            - [Template Design](best-practices/templates.md)
            - [Testing Strategies](best-practices/testing.md)
            - [Deployment Patterns](best-practices/deployment.md)

            ## Troubleshooting

            - [Common Issues](troubleshooting/common-issues.md)
            - [Error Messages](troubleshooting/errors.md)
            - [Performance Problems](troubleshooting/performance.md)
            - [FAQ](faq.md)
            """,
                        projectInfo.name());

        Files.writeString(guidesDir.resolve("index.md"), guidesIndexContent);
    }

    /**
 * Generates tutorials index. */
    public void generateTutorials(Path docsDir, ProjectInfo projectInfo) throws IOException {
        Path tutorialsDir = docsDir.resolve("tutorials");

        String tutorialsIndexContent =
                String.format(
                        """
            # Tutorials

            Step-by-step tutorials for learning %s.

            ## Beginner Tutorials

            Perfect for developers new to %s:

            1. **[Your First Project](beginner/first-project.md)**
               Learn the basics by creating a simple project

            2. **[Understanding Templates](beginner/templates.md)**
               Explore how templates work and customize them

            3. **[Basic Configuration](beginner/configuration.md)**
               Configure %s for your development environment

            ## Intermediate Tutorials

            Build on your knowledge with more advanced topics:

            4. **[Custom Template Creation](intermediate/custom-templates.md)**
               Create your own project templates

            5. **[CI/CD Integration](intermediate/cicd-setup.md)**
               Set up continuous integration and deployment

            6. **[Performance Optimization](intermediate/performance.md)**
               Optimize %s for better performance

            ## Advanced Tutorials

            Master advanced features and concepts:

            7. **[Plugin Development](advanced/plugin-development.md)**
               Extend %s with custom plugins

            8. **[Enterprise Deployment](advanced/enterprise.md)**
               Deploy %s in enterprise environments

            9. **[Custom Integrations](advanced/integrations.md)**
               Integrate %s with external tools and services

            ## Workshop Series

            Comprehensive workshop materials for training:

            - **[Workshop 1: Foundations](workshops/foundations.md)**
              Core concepts and basic usage (2 hours)

            - **[Workshop 2: Advanced Features](workshops/advanced.md)**
              Template customization and integration (3 hours)

            - **[Workshop 3: Enterprise Usage](workshops/enterprise.md)**
              Deployment and scaling strategies (4 hours)

            ## Video Tutorials

            Visual learners can follow along with video content:

            - [YouTube Playlist](https://youtube.com/playlist?list=PLexample)
            - [Interactive Demos](https://demo.%s.com)

            ## Learning Path Recommendations

            ### For Individual Developers
            1. Complete Beginner tutorials (1-3)
            2. Try Intermediate tutorials (4-6)
            3. Explore Advanced topics based on needs

            ### For Teams
            1. Everyone completes Beginner tutorials
            2. Designate template creators for Tutorial 4
            3. DevOps team focuses on Tutorials 5, 8
            4. All attend Workshop 1

            ### For Enterprise
            1. All tutorials in sequence
            2. All workshops for key personnel
            3. Custom training sessions available
            """,
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name(),
                        projectInfo.name().toLowerCase());

        Files.writeString(tutorialsDir.resolve("index.md"), tutorialsIndexContent);
    }

    /**
 * Generates reference documentation index. */
    public void generateReferenceDocumentation(Path docsDir, ProjectInfo projectInfo)
            throws IOException {
        Path referenceDir = docsDir.resolve("reference");

        String referenceIndexContent =
                String.format(
                        """
            # Reference Documentation

            Technical reference materials for %s.

            ## Architecture Reference

            - [System Architecture](architecture.md)
            - [Component Diagram](components.md)
            - [Data Flow](data-flow.md)
            - [Security Model](security.md)

            ## Configuration Reference

            - [Configuration Options](configuration.md)
            - [Environment Variables](environment.md)
            - [Command Line Arguments](cli-reference.md)
            - [Properties Reference](properties.md)

            ## Template Reference

            - [Template Syntax](templates/syntax.md)
            - [Built-in Variables](templates/variables.md)
            - [Helper Functions](templates/helpers.md)
            - [Template Examples](templates/examples.md)

            ## API Reference

            - [REST API](../api/index.md)
            - [Java API](java-api.md)
            - [Plugin API](plugin-api.md)
            - [Event System](events.md)

            ## File Formats

            - [Project Descriptor](formats/project.md)
            - [Template Metadata](formats/template.md)
            - [Configuration Files](formats/config.md)
            - [Cache Manifest](formats/cache.md)

            ## Error Codes

            - [Error Code Reference](errors/codes.md)
            - [Troubleshooting Guide](errors/troubleshooting.md)
            - [Error Recovery](errors/recovery.md)

            ## Performance Reference

            - [Benchmarks](performance/benchmarks.md)
            - [Optimization Guidelines](performance/optimization.md)
            - [Monitoring Metrics](performance/monitoring.md)
            - [Tuning Parameters](performance/tuning.md)

            ## Security Reference

            - [Security Model](security/model.md)
            - [Authentication Methods](security/auth.md)
            - [Authorization Policies](security/authz.md)
            - [Security Best Practices](security/practices.md)

            ## Deployment Reference

            - [System Requirements](deployment/requirements.md)
            - [Installation Methods](deployment/installation.md)
            - [Configuration Examples](deployment/examples.md)
            - [Scaling Guidelines](deployment/scaling.md)
            """,
                        projectInfo.name());

        Files.writeString(referenceDir.resolve("index.md"), referenceIndexContent);
    }

    private String generateFeaturesList(ProjectInfo projectInfo) {
        return """
            - ✨ **Intelligent Project Generation**: AI-powered project scaffolding
            - 🚀 **High Performance**: Optimized build processes and caching
            - 🎨 **Customizable Templates**: Flexible template system
            - 🔧 **Extensible Architecture**: Plugin-based extensibility
            - 📊 **Rich Monitoring**: Comprehensive metrics and observability
            - 🛡️ **Enterprise Ready**: Security, scalability, and reliability
            """;
    }

    private String generateUseCasesList(ProjectInfo projectInfo) {
        return """
            - **Rapid Prototyping**: Quickly create project prototypes with best practices
            - **Team Standardization**: Ensure consistent project structure across teams
            - **Enterprise Development**: Scale development processes across large organizations
            - **Educational Projects**: Provide students with well-structured starter projects
            - **Open Source Templates**: Share and distribute project templates
            """;
    }
}
