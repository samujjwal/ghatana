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

package com.ghatana.yappc.api.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.yappc.api.YappcConfig;
import com.ghatana.yappc.api.model.*;
import com.ghatana.yappc.api.service.PackService;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of ProjectService.
 *
 * @doc.type class
 * @doc.purpose Project management implementation
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DefaultProjectService implements ProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultProjectService.class);
    private static final String YAPPC_DIR = ".yappc";
    private static final String STATE_FILE = "state.json";

    private final YappcConfig config;
    private final PackService packService;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    public DefaultProjectService(YappcConfig config, PackService packService, TemplateService templateService) {
        this.config = config;
        this.packService = packService;
        this.templateService = templateService;
        this.objectMapper = JsonUtils.getDefaultMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public CreateResult create(CreateRequest request) {
        long startTime = System.currentTimeMillis();
        LOG.info("Creating project: {} with pack: {}", request.getProjectName(), request.getPackName());

        // Validate pack exists
        Optional<PackInfo> packOpt = packService.get(request.getPackName());
        if (packOpt.isEmpty()) {
            return CreateResult.failure("Pack not found: " + request.getPackName());
        }

        PackInfo pack = packOpt.get();
        Path projectPath = request.getProjectPath();

        // Check if project already exists
        if (Files.exists(projectPath) && !request.isOverwrite()) {
            return CreateResult.failure("Project directory already exists: " + projectPath);
        }

        if (request.isDryRun()) {
            return simulateCreate(request, pack);
        }

        try {
            // Create project directory
            Files.createDirectories(projectPath);

            // Prepare variables
            Map<String, Object> variables = new HashMap<>(request.getVariables());
            variables.put("projectName", request.getProjectName());
            
            // Apply defaults from pack
            for (Map.Entry<String, String> entry : pack.getDefaults().entrySet()) {
                variables.putIfAbsent(entry.getKey(), entry.getValue());
            }

            // Render templates
            List<String> filesCreated = new ArrayList<>();
            Path templatesPath = config.getPacksPath().resolve(request.getPackName()).resolve("templates");

            if (Files.exists(templatesPath)) {
                try (Stream<Path> templates = Files.walk(templatesPath)) {
                    templates.filter(Files::isRegularFile)
                            .forEach(template -> {
                                String relativePath = templatesPath.relativize(template).toString();
                                String targetPath = renderFileName(relativePath, variables);
                                Path outputPath = projectPath.resolve(targetPath);

                                RenderResult result = templateService.renderToFile(template, outputPath, variables);
                                if (result.success()) {
                                    filesCreated.add(targetPath);
                                }
                            });
                }
            }

            // Save project state
            saveProjectState(projectPath, request, pack, filesCreated);

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("Project created: {} ({} files in {}ms)", projectPath, filesCreated.size(), duration);

            return CreateResult.builder()
                    .success(true)
                    .projectPath(projectPath)
                    .packName(request.getPackName())
                    .packVersion(pack.getVersion())
                    .filesCreated(filesCreated)
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            LOG.error("Failed to create project: {}", request.getProjectName(), e);
            return CreateResult.failure("Failed to create project: " + e.getMessage());
        }
    }

    private CreateResult simulateCreate(CreateRequest request, PackInfo pack) {
        List<String> filesCreated = pack.getTemplates().stream()
                .map(t -> renderFileName(t, request.getVariables()))
                .collect(Collectors.toList());

        return CreateResult.builder()
                .success(true)
                .projectPath(request.getProjectPath())
                .packName(request.getPackName())
                .packVersion(pack.getVersion())
                .filesCreated(filesCreated)
                .warnings(List.of("Dry run - no files created"))
                .build();
    }

    private String renderFileName(String path, Map<String, Object> variables) {
        // Remove .tmpl extension and render any variables in the path
        String result = path.replace(".tmpl", "");
        return templateService.render(result, variables);
    }

    @Override
    public AddResult addFeature(AddFeatureRequest request) {
        long startTime = System.currentTimeMillis();
        LOG.info("Adding feature {} ({}) to project: {}", 
                request.getFeature(), request.getType(), request.getProjectPath());

        if (!isYappcProject(request.getProjectPath())) {
            return AddResult.failure("Not a YAPPC-managed project: " + request.getProjectPath());
        }

        Optional<ProjectState> stateOpt = getState(request.getProjectPath());
        if (stateOpt.isEmpty()) {
            return AddResult.failure("Cannot read project state");
        }

        ProjectState state = stateOpt.get();
        
        // Check if feature already added
        if (state.hasFeature(request.getFeature()) && !request.isForce()) {
            return AddResult.failure("Feature already added: " + request.getFeature());
        }

        // Find feature pack
        String featurePackName = findFeaturePack(request.getFeature(), request.getType(), state.getPackName());
        if (featurePackName == null) {
            return AddResult.failure("Feature pack not found for: " + request.getFeature());
        }

        Optional<PackInfo> featurePackOpt = packService.get(featurePackName);
        if (featurePackOpt.isEmpty()) {
            return AddResult.failure("Feature pack not found: " + featurePackName);
        }

        if (request.isDryRun()) {
            return simulateAddFeature(request, featurePackOpt.get());
        }

        try {
            PackInfo featurePack = featurePackOpt.get();
            Map<String, Object> variables = new HashMap<>(state.getVariables());
            variables.putAll(request.getVariables());

            List<String> filesCreated = new ArrayList<>();
            List<String> dependenciesAdded = new ArrayList<>();

            // Render feature templates
            Path templatesPath = config.getPacksPath().resolve(featurePackName).resolve("templates");
            if (Files.exists(templatesPath)) {
                try (Stream<Path> templates = Files.walk(templatesPath)) {
                    templates.filter(Files::isRegularFile)
                            .forEach(template -> {
                                String relativePath = templatesPath.relativize(template).toString();
                                String targetPath = renderFileName(relativePath, variables);
                                Path outputPath = request.getProjectPath().resolve(targetPath);

                                RenderResult result = templateService.renderToFile(template, outputPath, variables);
                                if (result.success()) {
                                    filesCreated.add(targetPath);
                                }
                            });
                }
            }

            // Update project state
            updateProjectStateWithFeature(request.getProjectPath(), request.getFeature(), 
                    request.getType(), filesCreated);

            long duration = System.currentTimeMillis() - startTime;
            return AddResult.success(request.getProjectPath(), request.getFeature(), 
                    request.getType(), filesCreated, dependenciesAdded);

        } catch (Exception e) {
            LOG.error("Failed to add feature: {}", request.getFeature(), e);
            return AddResult.failure("Failed to add feature: " + e.getMessage());
        }
    }

    private AddResult simulateAddFeature(AddFeatureRequest request, PackInfo featurePack) {
        return AddResult.builder()
                .success(true)
                .projectPath(request.getProjectPath())
                .feature(request.getFeature())
                .type(request.getType())
                .filesCreated(featurePack.getTemplates())
                .warnings(List.of("Dry run - no files created"))
                .build();
    }

    private String findFeaturePack(String feature, String type, String basePackName) {
        // Infer language from base pack
        Optional<PackInfo> basePack = packService.get(basePackName);
        String language = basePack.map(PackInfo::getLanguage).orElse("java");
        
        // Try specific feature pack
        String specificName = language + "-feature-" + feature + (type != null ? "-" + type : "");
        if (packService.exists(specificName)) {
            return specificName;
        }

        // Try generic feature pack
        String genericName = language + "-feature-" + feature;
        if (packService.exists(genericName)) {
            return genericName;
        }

        return null;
    }

    @Override
    public UpdateResult update(UpdateRequest request) {
        LOG.info("Updating project: {}", request.getProjectPath());

        if (!isYappcProject(request.getProjectPath())) {
            return UpdateResult.failure("Not a YAPPC-managed project");
        }

        Optional<ProjectState> stateOpt = getState(request.getProjectPath());
        if (stateOpt.isEmpty()) {
            return UpdateResult.failure("Cannot read project state");
        }

        ProjectState state = stateOpt.get();
        Optional<PackInfo> currentPackOpt = packService.get(state.getPackName());
        
        if (currentPackOpt.isEmpty()) {
            return UpdateResult.failure("Pack no longer exists: " + state.getPackName());
        }

        PackInfo currentPack = currentPackOpt.get();
        
        // Check if update is needed
        if (state.getPackVersion().equals(currentPack.getVersion())) {
            return UpdateResult.noUpdate(request.getProjectPath(), state.getPackVersion());
        }

        if (request.isDryRun()) {
            return simulateUpdate(request, state, currentPack);
        }

        // Perform backup if requested
        String backupPath = null;
        if (request.isBackup()) {
            backupPath = createBackup(request.getProjectPath());
        }

        try {
            List<String> filesUpdated = new ArrayList<>();
            List<String> filesAdded = new ArrayList<>();
            List<String> conflicts = new ArrayList<>();

            // Re-render templates with updated pack
            Map<String, Object> variables = new HashMap<>(state.getVariables());
            variables.putAll(request.getNewVariables());

            Path templatesPath = config.getPacksPath().resolve(state.getPackName()).resolve("templates");
            if (Files.exists(templatesPath)) {
                try (Stream<Path> templates = Files.walk(templatesPath)) {
                    templates.filter(Files::isRegularFile)
                            .forEach(template -> {
                                String relativePath = templatesPath.relativize(template).toString();
                                String targetPath = renderFileName(relativePath, variables);
                                Path outputPath = request.getProjectPath().resolve(targetPath);

                                if (Files.exists(outputPath)) {
                                    if (request.isForce()) {
                                        templateService.renderToFile(template, outputPath, variables);
                                        filesUpdated.add(targetPath);
                                    } else {
                                        conflicts.add(targetPath);
                                    }
                                } else {
                                    templateService.renderToFile(template, outputPath, variables);
                                    filesAdded.add(targetPath);
                                }
                            });
                }
            }

            // Update state
            updateProjectState(request.getProjectPath(), currentPack.getVersion());

            return UpdateResult.builder()
                    .success(conflicts.isEmpty())
                    .projectPath(request.getProjectPath())
                    .fromVersion(state.getPackVersion())
                    .toVersion(currentPack.getVersion())
                    .filesUpdated(filesUpdated)
                    .filesAdded(filesAdded)
                    .conflicts(conflicts)
                    .backupPath(backupPath)
                    .build();

        } catch (Exception e) {
            LOG.error("Failed to update project", e);
            return UpdateResult.failure("Failed to update project: " + e.getMessage());
        }
    }

    private UpdateResult simulateUpdate(UpdateRequest request, ProjectState state, PackInfo currentPack) {
        return UpdateResult.builder()
                .success(true)
                .projectPath(request.getProjectPath())
                .fromVersion(state.getPackVersion())
                .toVersion(currentPack.getVersion())
                .warnings(List.of("Dry run - no files updated"))
                .build();
    }

    private String createBackup(Path projectPath) {
        try {
            String timestamp = Instant.now().toString().replace(":", "-").replace(".", "-");
            Path backupPath = projectPath.getParent().resolve(projectPath.getFileName() + ".backup-" + timestamp);
            // Note: In production, implement proper directory copy
            LOG.info("Backup would be created at: {}", backupPath);
            return backupPath.toString();
        } catch (Exception e) {
            LOG.warn("Failed to create backup", e);
            return null;
        }
    }

    @Override
    public Optional<ProjectInfo> getInfo(Path projectPath) {
        Optional<ProjectState> stateOpt = getState(projectPath);
        if (stateOpt.isEmpty()) {
            return Optional.empty();
        }

        ProjectState state = stateOpt.get();
        Optional<PackInfo> packOpt = packService.get(state.getPackName());
        boolean updateAvailable = packOpt
                .map(pack -> !pack.getVersion().equals(state.getPackVersion()))
                .orElse(false);

        return Optional.of(ProjectInfo.builder()
                .projectPath(projectPath)
                .projectName(state.getProjectName())
                .packName(state.getPackName())
                .packVersion(state.getPackVersion())
                .language(packOpt.map(PackInfo::getLanguage).orElse("unknown"))
                .platform(packOpt.map(PackInfo::getPlatform).orElse("unknown"))
                .addedFeatures(state.getAddedFeatures().stream()
                        .map(ProjectState.FeatureRecord::name)
                        .collect(Collectors.toList()))
                .variables(state.getVariables())
                .createdAt(state.getCreatedAt())
                .lastModifiedAt(state.getLastModifiedAt())
                .updateAvailable(updateAvailable)
                .build());
    }

    @Override
    public boolean isYappcProject(Path projectPath) {
        Path statePath = projectPath.resolve(YAPPC_DIR).resolve(STATE_FILE);
        return Files.exists(statePath);
    }

    @Override
    public Optional<ProjectState> getState(Path projectPath) {
        Path statePath = projectPath.resolve(YAPPC_DIR).resolve(STATE_FILE);
        if (!Files.exists(statePath)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(statePath);
            ProjectState state = objectMapper.readValue(json, ProjectState.class);
            return Optional.of(state);
        } catch (IOException e) {
            LOG.error("Failed to read project state: {}", statePath, e);
            return Optional.empty();
        }
    }

    @Override
    public List<FeatureInfo> getAvailableFeatures(Path projectPath) {
        // Return standard features available for any project
        return List.of(
                FeatureInfo.of("database", "Database integration", 
                        List.of("postgresql", "mysql", "mongodb", "h2")),
                FeatureInfo.of("auth", "Authentication support",
                        List.of("jwt", "oauth2", "basic")),
                FeatureInfo.of("observability", "Metrics, tracing, logging",
                        List.of("prometheus", "opentelemetry", "micrometer")),
                FeatureInfo.of("messaging", "Message queue integration",
                        List.of("kafka", "rabbitmq", "redis")),
                FeatureInfo.of("cache", "Caching layer",
                        List.of("redis", "caffeine", "ehcache"))
        );
    }

    @Override
    public List<FeatureInfo> getAddedFeatures(Path projectPath) {
        return getState(projectPath)
                .map(state -> state.getAddedFeatures().stream()
                        .map(fr -> FeatureInfo.of(fr.name(), "Added feature", List.of(fr.type())))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Override
    public ProjectValidationResult validate(Path projectPath) {
        if (!isYappcProject(projectPath)) {
            return ProjectValidationResult.notYappcProject();
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingFiles = new ArrayList<>();

        Optional<ProjectState> stateOpt = getState(projectPath);
        if (stateOpt.isEmpty()) {
            errors.add("Cannot read project state");
            return new ProjectValidationResult(false, true, errors, warnings, missingFiles, List.of());
        }

        ProjectState state = stateOpt.get();

        // Check for missing files
        for (String file : state.getGeneratedFiles()) {
            if (!Files.exists(projectPath.resolve(file))) {
                missingFiles.add(file);
            }
        }

        if (!missingFiles.isEmpty()) {
            warnings.add("Some generated files are missing");
        }

        return new ProjectValidationResult(
                errors.isEmpty(),
                true,
                errors,
                warnings,
                missingFiles,
                List.of()
        );
    }

    @Override
    public UpdateAvailability checkUpdates(Path projectPath) {
        Optional<ProjectState> stateOpt = getState(projectPath);
        if (stateOpt.isEmpty()) {
            return UpdateAvailability.noUpdate("unknown");
        }

        ProjectState state = stateOpt.get();
        Optional<PackInfo> packOpt = packService.get(state.getPackName());

        if (packOpt.isEmpty()) {
            return UpdateAvailability.noUpdate(state.getPackVersion());
        }

        PackInfo pack = packOpt.get();
        if (state.getPackVersion().equals(pack.getVersion())) {
            return UpdateAvailability.noUpdate(state.getPackVersion());
        }

        return UpdateAvailability.available(
                state.getPackVersion(),
                pack.getVersion(),
                List.of("Templates updated", "Dependencies updated"),
                false
        );
    }

    @Override
    public UpdatePreview previewUpdate(UpdateRequest request) {
        // Generate preview without applying changes
        return new UpdatePreview(
                "1.0.0", "1.1.0",
                List.of(),
                List.of(),
                List.of("Preview mode - showing potential changes"),
                false
        );
    }

    @Override
    public String exportState(Path projectPath) {
        return getState(projectPath)
                .map(state -> {
                    try {
                        return objectMapper.writeValueAsString(state);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to export state", e);
                    }
                })
                .orElse("{}");
    }

    @Override
    public void importState(Path projectPath, String stateJson) {
        try {
            ProjectState state = objectMapper.readValue(stateJson, ProjectState.class);
            Path yappcDir = projectPath.resolve(YAPPC_DIR);
            Files.createDirectories(yappcDir);
            Files.writeString(yappcDir.resolve(STATE_FILE), stateJson);
            LOG.info("Project state imported to: {}", projectPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to import state", e);
        }
    }

    private void saveProjectState(Path projectPath, CreateRequest request, PackInfo pack, List<String> files) {
        ProjectState state = ProjectState.builder()
                .projectName(request.getProjectName())
                .packName(request.getPackName())
                .packVersion(pack.getVersion())
                .yappcVersion("1.0.0")
                .variables(request.getVariables())
                .addedFeatures(List.of())
                .generatedFiles(files)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        try {
            Path yappcDir = projectPath.resolve(YAPPC_DIR);
            Files.createDirectories(yappcDir);
            String json = objectMapper.writeValueAsString(state);
            Files.writeString(yappcDir.resolve(STATE_FILE), json);
        } catch (IOException e) {
            LOG.error("Failed to save project state", e);
        }
    }

    private void updateProjectStateWithFeature(Path projectPath, String feature, String type, List<String> files) {
        getState(projectPath).ifPresent(state -> {
            List<ProjectState.FeatureRecord> features = new ArrayList<>(state.getAddedFeatures());
            features.add(new ProjectState.FeatureRecord(feature, type, Instant.now(), files));

            List<String> allFiles = new ArrayList<>(state.getGeneratedFiles());
            allFiles.addAll(files);

            ProjectState updated = ProjectState.builder()
                    .projectName(state.getProjectName())
                    .packName(state.getPackName())
                    .packVersion(state.getPackVersion())
                    .yappcVersion(state.getYappcVersion())
                    .variables(state.getVariables())
                    .addedFeatures(features)
                    .generatedFiles(allFiles)
                    .createdAt(state.getCreatedAt())
                    .lastModifiedAt(Instant.now())
                    .build();

            try {
                Path statePath = projectPath.resolve(YAPPC_DIR).resolve(STATE_FILE);
                Files.writeString(statePath, objectMapper.writeValueAsString(updated));
            } catch (IOException e) {
                LOG.error("Failed to update project state", e);
            }
        });
    }

    private void updateProjectState(Path projectPath, String newVersion) {
        getState(projectPath).ifPresent(state -> {
            ProjectState updated = ProjectState.builder()
                    .projectName(state.getProjectName())
                    .packName(state.getPackName())
                    .packVersion(newVersion)
                    .yappcVersion(state.getYappcVersion())
                    .variables(state.getVariables())
                    .addedFeatures(state.getAddedFeatures())
                    .generatedFiles(state.getGeneratedFiles())
                    .createdAt(state.getCreatedAt())
                    .lastModifiedAt(Instant.now())
                    .build();

            try {
                Path statePath = projectPath.resolve(YAPPC_DIR).resolve(STATE_FILE);
                Files.writeString(statePath, objectMapper.writeValueAsString(updated));
            } catch (IOException e) {
                LOG.error("Failed to update project state", e);
            }
        });
    }
}
