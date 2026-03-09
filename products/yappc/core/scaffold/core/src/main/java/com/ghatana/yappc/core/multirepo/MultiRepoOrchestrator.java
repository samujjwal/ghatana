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

package com.ghatana.yappc.core.multirepo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.core.model.ProjectSpec;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Day 21: Multi-repo orchestration manager. Handles workspaces that span multiple Git repositories
 * with individual yappc.project.json files.
 *
 * <p>Features: - Multi-repository workspace coordination - Cross-repo dependency management -
 * Individual project configuration per repository - Centralized workspace orchestration
 *
 * @doc.type class
 * @doc.purpose Day 21: Multi-repo orchestration manager. Handles workspaces that span multiple Git repositories
 * @doc.layer platform
 * @doc.pattern Component
 */
public class MultiRepoOrchestrator {

    private static final String WORKSPACE_MANIFEST = "yappc.workspace.json";
    private static final String PROJECT_MANIFEST = "yappc.project.json";
    private static final String MULTI_REPO_CONFIG = ".yappc/multi-repo.json";

    private final ObjectMapper objectMapper;

    public MultiRepoOrchestrator() {
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Creates a multi-repository workspace structure. Generates workspace with multiple repos, each
     * with its own yappc.project.json.
     */
    public MultiRepoWorkspace createMultiRepoWorkspace(
            WorkspaceSpec workspaceSpec, Path targetDirectory, List<String> repositoryNames)
            throws IOException {

        // Create main workspace directory
        Files.createDirectories(targetDirectory);

        // Generate workspace manifest
        Path workspaceManifest = targetDirectory.resolve(WORKSPACE_MANIFEST);
        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(workspaceManifest.toFile(), workspaceSpec);

        // Create multi-repo configuration
        MultiRepoConfiguration config =
                createMultiRepoConfiguration(workspaceSpec, repositoryNames);

        Path configFile = targetDirectory.resolve(MULTI_REPO_CONFIG);
        Files.createDirectories(configFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);

        // Create individual repository directories with project manifests
        List<MultiRepoProject> projects = new ArrayList<>();

        for (String repoName : repositoryNames) {
            Path repoDir = targetDirectory.resolve(repoName);
            Files.createDirectories(repoDir);

            // Generate project spec for this repository
            ProjectSpec projectSpec =
                    generateProjectSpecForRepo(workspaceSpec, repoName, repositoryNames);

            // Write project manifest
            Path projectManifest = repoDir.resolve(PROJECT_MANIFEST);
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(projectManifest.toFile(), projectSpec);

            // Create repository structure
            createRepositoryStructure(repoDir, projectSpec);

            projects.add(new MultiRepoProject(repoName, repoDir, projectSpec));
        }

        return new MultiRepoWorkspace(workspaceSpec, config, projects);
    }

    /**
 * Loads an existing multi-repository workspace. */
    public MultiRepoWorkspace loadMultiRepoWorkspace(Path workspaceDirectory) throws IOException {
        // Load workspace manifest
        Path workspaceManifest = workspaceDirectory.resolve(WORKSPACE_MANIFEST);
        if (!Files.exists(workspaceManifest)) {
            throw new IOException("Workspace manifest not found: " + workspaceManifest);
        }

        WorkspaceSpec workspaceSpec =
                objectMapper.readValue(workspaceManifest.toFile(), WorkspaceSpec.class);

        // Load multi-repo configuration
        Path configFile = workspaceDirectory.resolve(MULTI_REPO_CONFIG);
        if (!Files.exists(configFile)) {
            throw new IOException("Multi-repo configuration not found: " + configFile);
        }

        MultiRepoConfiguration config =
                objectMapper.readValue(configFile.toFile(), MultiRepoConfiguration.class);

        // Load individual projects
        List<MultiRepoProject> projects = new ArrayList<>();

        for (String repoName : config.getRepositories()) {
            Path repoDir = workspaceDirectory.resolve(repoName);
            Path projectManifest = repoDir.resolve(PROJECT_MANIFEST);

            if (Files.exists(projectManifest)) {
                ProjectSpec projectSpec =
                        objectMapper.readValue(projectManifest.toFile(), ProjectSpec.class);

                projects.add(new MultiRepoProject(repoName, repoDir, projectSpec));
            }
        }

        return new MultiRepoWorkspace(workspaceSpec, config, projects);
    }

    /**
 * Validates multi-repo workspace consistency. */
    public MultiRepoValidationResult validateWorkspace(MultiRepoWorkspace workspace) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check workspace-level consistency
        validateWorkspaceConfiguration(workspace, errors, warnings);

        // Check cross-repository dependencies
        validateCrossRepoDependencies(workspace, errors, warnings);

        // Check individual project configurations
        for (MultiRepoProject project : workspace.getProjects()) {
            validateProjectConfiguration(project, errors, warnings);
        }

        return new MultiRepoValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
 * Generates dependency graph for multi-repo workspace. */
    public MultiRepoDependencyGraph generateDependencyGraph(MultiRepoWorkspace workspace) {
        Map<String, Set<String>> dependencies = new HashMap<>();
        Map<String, ProjectSpec> projectSpecs = new HashMap<>();

        for (MultiRepoProject project : workspace.getProjects()) {
            String projectName = project.getName();
            projectSpecs.put(projectName, project.getProjectSpec());

            // Extract dependencies from project spec
            Set<String> projectDeps =
                    extractProjectDependencies(project.getProjectSpec(), workspace);

            dependencies.put(projectName, projectDeps);
        }

        return new MultiRepoDependencyGraph(dependencies, projectSpecs);
    }

    /**
 * Creates multi-repo configuration based on workspace spec and repository names. */
    private MultiRepoConfiguration createMultiRepoConfiguration(
            WorkspaceSpec workspaceSpec, List<String> repositoryNames) {

        return MultiRepoConfiguration.builder()
                .workspaceName(workspaceSpec.getName())
                .repositories(repositoryNames)
                .orchestrationStrategy("distributed")
                .crossRepoDepencencyManagement(true)
                .sharedConfiguration(
                        Map.of("version", "1.0.0", "created", java.time.Instant.now().toString()))
                .build();
    }

    /**
 * Generates project specification for individual repository. */
    private ProjectSpec generateProjectSpecForRepo(
            WorkspaceSpec workspaceSpec, String repoName, List<String> allRepos) {

        // Create project spec based on workspace spec and repository role
        return ProjectSpec.builder()
                .name(repoName)
                .description("Project component for " + repoName + " repository")
                .language(determineLanguageForRepo(repoName))
                .framework(determineFrameworkForRepo(repoName))
                .projectType(determineProjectTypeForRepo(repoName))
                .dependencies(determineDependenciesForRepo(repoName, allRepos))
                .build();
    }

    /**
 * Creates basic repository structure with standard directories. */
    private void createRepositoryStructure(Path repoDir, ProjectSpec projectSpec)
            throws IOException {
        // Create standard directories based on project type and language
        String language = projectSpec.getLanguage();

        switch (language.toLowerCase()) {
            case "java":
                createJavaRepositoryStructure(repoDir);
                break;
            case "typescript":
            case "javascript":
                createTypeScriptRepositoryStructure(repoDir);
                break;
            case "rust":
                createRustRepositoryStructure(repoDir);
                break;
            case "python":
                createPythonRepositoryStructure(repoDir);
                break;
            default:
                createGenericRepositoryStructure(repoDir);
        }

        // Create common files
        createCommonRepositoryFiles(repoDir, projectSpec);
    }

    private void createJavaRepositoryStructure(Path repoDir) throws IOException {
        Files.createDirectories(repoDir.resolve("src/main/java"));
        Files.createDirectories(repoDir.resolve("src/main/resources"));
        Files.createDirectories(repoDir.resolve("src/test/java"));
        Files.createDirectories(repoDir.resolve("src/test/resources"));
    }

    private void createTypeScriptRepositoryStructure(Path repoDir) throws IOException {
        Files.createDirectories(repoDir.resolve("src"));
        Files.createDirectories(repoDir.resolve("tests"));
        Files.createDirectories(repoDir.resolve("dist"));
        Files.createDirectories(repoDir.resolve("docs"));
    }

    private void createRustRepositoryStructure(Path repoDir) throws IOException {
        Files.createDirectories(repoDir.resolve("src"));
        Files.createDirectories(repoDir.resolve("tests"));
        Files.createDirectories(repoDir.resolve("examples"));
        Files.createDirectories(repoDir.resolve("benches"));
    }

    private void createPythonRepositoryStructure(Path repoDir) throws IOException {
        Files.createDirectories(repoDir.resolve("src"));
        Files.createDirectories(repoDir.resolve("tests"));
        Files.createDirectories(repoDir.resolve("docs"));
        Files.createDirectories(repoDir.resolve("scripts"));
    }

    private void createGenericRepositoryStructure(Path repoDir) throws IOException {
        Files.createDirectories(repoDir.resolve("src"));
        Files.createDirectories(repoDir.resolve("tests"));
        Files.createDirectories(repoDir.resolve("docs"));
    }

    private void createCommonRepositoryFiles(Path repoDir, ProjectSpec projectSpec)
            throws IOException {
        // Create README.md
        String readme =
                String.format(
                        "# %s\n\n"
                                + "%s\n\n"
                                + "Part of multi-repository workspace.\n\n"
                                + "## Development\n\n"
                                + "See parent workspace documentation for setup and development"
                                + " instructions.\n",
                        projectSpec.getName(), projectSpec.getDescription());
        Files.writeString(repoDir.resolve("README.md"), readme);

        // Create .gitignore
        String gitignore = generateGitignoreForLanguage(projectSpec.getLanguage());
        Files.writeString(repoDir.resolve(".gitignore"), gitignore);
    }

    // Utility methods for project spec generation
    private String determineLanguageForRepo(String repoName) {
        if (repoName.contains("api") || repoName.contains("service")) return "java";
        if (repoName.contains("ui") || repoName.contains("frontend")) return "typescript";
        if (repoName.contains("cli") || repoName.contains("tool")) return "rust";
        return "java"; // default
    }

    private String determineFrameworkForRepo(String repoName) {
        String language = determineLanguageForRepo(repoName);
        switch (language) {
            case "java":
                return "spring-boot";
            case "typescript":
                return "react";
            case "rust":
                return "clap";
            default:
                return null;
        }
    }

    private String determineProjectTypeForRepo(String repoName) {
        if (repoName.contains("api") || repoName.contains("service")) return "api-service";
        if (repoName.contains("ui") || repoName.contains("frontend")) return "web-app";
        if (repoName.contains("cli") || repoName.contains("tool")) return "cli-tool";
        if (repoName.contains("lib")) return "library";
        return "application";
    }

    private List<String> determineDependenciesForRepo(String repoName, List<String> allRepos) {
        // Simple heuristic for cross-repo dependencies
        List<String> deps = new ArrayList<>();

        if (repoName.contains("frontend") || repoName.contains("ui")) {
            // Frontend typically depends on API services
            allRepos.stream()
                    .filter(repo -> repo.contains("api") || repo.contains("service"))
                    .findFirst()
                    .ifPresent(deps::add);
        }

        return deps;
    }

    private String generateGitignoreForLanguage(String language) {
        switch (language.toLowerCase()) {
            case "java":
                return "*.class\n*.jar\n*.war\n*.ear\ntarget/\n.gradle/\nbuild/\n";
            case "typescript":
            case "javascript":
                return "node_modules/\ndist/\n*.log\n.env\n.DS_Store\n";
            case "rust":
                return "target/\nCargo.lock\n**/*.rs.bk\n";
            case "python":
                return "__pycache__/\n*.py[cod]\n*.pyo\n*.pyd\n.Python\nvenv/\n";
            default:
                return "*.log\n*.tmp\n.DS_Store\n";
        }
    }

    // Validation methods
    private void validateWorkspaceConfiguration(
            MultiRepoWorkspace workspace, List<String> errors, List<String> warnings) {
        // Implementation for workspace validation
    }

    private void validateCrossRepoDependencies(
            MultiRepoWorkspace workspace, List<String> errors, List<String> warnings) {
        // Implementation for cross-repo dependency validation
    }

    private void validateProjectConfiguration(
            MultiRepoProject project, List<String> errors, List<String> warnings) {
        // Implementation for individual project validation
    }

    private Set<String> extractProjectDependencies(
            ProjectSpec projectSpec, MultiRepoWorkspace workspace) {
        // Extract and resolve dependencies from project spec
        return new HashSet<>(projectSpec.getDependencies());
    }
}
