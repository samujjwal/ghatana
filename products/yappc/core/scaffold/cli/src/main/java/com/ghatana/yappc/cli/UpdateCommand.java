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

package com.ghatana.yappc.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.core.pack.DefaultPackEngine;
import com.ghatana.yappc.core.pack.Pack;
import com.ghatana.yappc.core.pack.PackEngine;
import com.ghatana.yappc.core.pack.PackMetadata;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update/sync an existing project with pack updates. Supports checking for
 * outdated files, merging updates, and regenerating specific templates.
 *
 * <p>Example usage:
 * <pre>
 * yappc update                       # Check for updates
 * yappc update --apply               # Apply updates
 * yappc update --template dockerfile # Update specific template
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Update existing projects with pack changes
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
    name = "update",
    description = "Update/sync project with pack updates",
    mixinStandardHelpOptions = true
)
public class UpdateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(UpdateCommand.class);

    @Parameters(index = "0", arity = "0..1", description = "Project directory (default: current directory)")
    private Path projectPath;

    @Option(names = {"--apply", "-a"}, description = "Apply the updates (default: dry-run check)")
    private boolean apply;

    @Option(names = {"--template", "-t"}, description = "Update specific template only")
    private String templateName;

    @Option(names = {"--force", "-f"}, description = "Force overwrite even for modified files")
    private boolean force;

    @Option(names = {"--pack-path"}, description = "Custom path to packs directory")
    private Path customPackPath;

    @Option(names = {"--backup"}, description = "Create backups before updating")
    private boolean backup = true;

    @Option(names = {"--diff"}, description = "Show diff of changes")
    private boolean showDiff;

    private PackEngine packEngine;
    private ObjectMapper objectMapper;

    @Override
    public Integer call() throws Exception {
        try {
            objectMapper = JsonUtils.getDefaultMapper();
            initializePackEngine();

            // Determine project path
            Path targetProject = projectPath != null ? projectPath : Paths.get(".");
            if (!Files.isDirectory(targetProject)) {
                log.error("❌ Error: Project directory does not exist: {}", targetProject);
                return 1;
            }

            // Read project state
            ProjectState state = readProjectState(targetProject);
            if (state == null) {
                log.error("❌ Error: No .yappc/state.json found. This project was not created with YAPPC.");
                log.error("   Use 'yappc init' to initialize tracking for an existing project.");
                return 1;
            }

            log.info("📦 Project: {}", state.projectName);
            log.info("   Pack: {} v{}", state.packName, state.packVersion);
            log.info("   Created: {}", state.createdAt);

            // Load current pack version
            Pack currentPack = loadPack(state.packName);
            if (currentPack == null) {
                log.error("❌ Error: Pack no longer available: {}", state.packName);
                return 1;
            }

            String currentPackVersion = currentPack.getMetadata().version();
            log.info("   Latest pack version: {}", currentPackVersion);

            // Compare versions
            boolean hasUpdates = !currentPackVersion.equals(state.packVersion);
            
            // Check for template changes
            List<TemplateChange> changes = detectChanges(targetProject, currentPack, state);

            if (changes.isEmpty() && !hasUpdates) {
                log.info("\n✅ Project is up to date. No changes detected.");
                return 0;
            }

            // Display changes
            log.info("\n📋 Detected Changes:\n");

            int newFiles = 0, modifiedFiles = 0, outdatedFiles = 0;
            for (TemplateChange change : changes) {
                switch (change.type) {
                    case NEW -> {
                        log.info("   ➕ NEW: {}", change.filePath);
                        newFiles++;
                    }
                    case MODIFIED -> {
                        log.info("   📝 MODIFIED: {} (local changes)", change.filePath);
                        modifiedFiles++;
                    }
                    case OUTDATED -> {
                        log.info("   🔄 OUTDATED: {}", change.filePath);
                        outdatedFiles++;
                    }
                    log.info("   ❌ DELETED: {}", change.filePath);
                }
            }

            log.info("\n   Summary: {} new, {} outdated, {} locally modified", newFiles, outdatedFiles, modifiedFiles);

            if (!apply) {
                log.info("\n🔍 Dry run complete. Use --apply to apply these updates.");
                return 0;
            }

            // Apply updates
            log.info("\n🚀 Applying updates...\n");
            int applied = 0, skipped = 0;

            for (TemplateChange change : changes) {
                if (templateName != null && !change.templateName.equals(templateName)) {
                    continue;
                }

                if (change.type == ChangeType.MODIFIED && !force) {
                    log.info("   ⏭️  Skipping modified file: {}", change.filePath);
                    skipped++;
                    continue;
                }

                if (backup && Files.exists(targetProject.resolve(change.filePath))) {
                    Path backupPath = targetProject.resolve(change.filePath + ".bak");
                    Files.copy(targetProject.resolve(change.filePath), backupPath);
                }

                // Apply the change
                applyChange(targetProject, change, currentPack, state.variables);
                log.info("   ✅ Updated: {}", change.filePath);
                applied++;
            }

            // Update state file
            state.packVersion = currentPackVersion;
            state.updatedAt = java.time.Instant.now().toString();
            saveProjectState(targetProject, state);

            log.info("\n✅ Update complete. Applied {} changes, skipped {}.", applied, skipped);
            
            if (skipped > 0) {
                log.info("   Use --force to overwrite locally modified files.");
            }

            return 0;

        } catch (Exception e) {
            log.error("❌ Error: {}", e.getMessage());
            return 1;
        }
    }

    private void initializePackEngine() throws IOException {
        packEngine = new DefaultPackEngine(new SimpleTemplateEngine());

        Path defaultPacksPath = findDefaultPacksPath();
        if (defaultPacksPath != null && Files.isDirectory(defaultPacksPath)) {
            packEngine.registerPackLocation(defaultPacksPath);
        }

        if (customPackPath != null && Files.isDirectory(customPackPath)) {
            packEngine.registerPackLocation(customPackPath);
        }
    }

    private Path findDefaultPacksPath() {
        String envPath = System.getenv("YAPPC_PACKS_PATH");
        if (envPath != null) {
            Path path = Paths.get(envPath);
            if (Files.isDirectory(path)) {
                return path;
            }
        }

        Path localPacks = Paths.get("packs");
        if (Files.isDirectory(localPacks)) {
            return localPacks;
        }

        Path homePacks = Paths.get(System.getProperty("user.home"), ".yappc", "packs");
        if (Files.isDirectory(homePacks)) {
            return homePacks;
        }

        return null;
    }

    private ProjectState readProjectState(Path projectPath) {
        Path stateFile = projectPath.resolve(".yappc/state.json");
        if (!Files.exists(stateFile)) {
            return null;
        }

        try {
            JsonNode json = objectMapper.readTree(Files.readString(stateFile));
            ProjectState state = new ProjectState();
            state.projectName = json.path("projectName").asText();
            state.packName = json.path("packName").asText();
            state.packVersion = json.path("packVersion").asText();
            state.createdAt = json.path("createdAt").asText();
            state.updatedAt = json.path("updatedAt").asText();

            // Read variables
            state.variables = new HashMap<>();
            JsonNode varsNode = json.path("variables");
            if (varsNode.isObject()) {
                varsNode.fields().forEachRemaining(entry -> 
                    state.variables.put(entry.getKey(), entry.getValue().asText()));
            }

            // Read file checksums
            state.fileChecksums = new HashMap<>();
            JsonNode checksumsNode = json.path("fileChecksums");
            if (checksumsNode.isObject()) {
                checksumsNode.fields().forEachRemaining(entry -> 
                    state.fileChecksums.put(entry.getKey(), entry.getValue().asText()));
            }

            return state;
        } catch (IOException e) {
            return null;
        }
    }

    private void saveProjectState(Path projectPath, ProjectState state) throws IOException {
        Path stateFile = projectPath.resolve(".yappc/state.json");
        Files.createDirectories(stateFile.getParent());

        Map<String, Object> stateMap = new LinkedHashMap<>();
        stateMap.put("projectName", state.projectName);
        stateMap.put("packName", state.packName);
        stateMap.put("packVersion", state.packVersion);
        stateMap.put("createdAt", state.createdAt);
        stateMap.put("updatedAt", state.updatedAt);
        stateMap.put("variables", state.variables);
        stateMap.put("fileChecksums", state.fileChecksums);

        Files.writeString(stateFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(stateMap));
    }

    private Pack loadPack(String name) {
        try {
            Path packPath = findPackPath(name);
            if (packPath != null) {
                return packEngine.loadPack(packPath);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private Path findPackPath(String name) {
        Path defaultPacksPath = findDefaultPacksPath();
        if (defaultPacksPath != null) {
            Path packDir = defaultPacksPath.resolve(name);
            if (Files.isDirectory(packDir)) {
                return packDir;
            }
        }

        if (customPackPath != null) {
            Path packDir = customPackPath.resolve(name);
            if (Files.isDirectory(packDir)) {
                return packDir;
            }
        }

        return null;
    }

    private List<TemplateChange> detectChanges(Path projectPath, Pack pack, ProjectState state) {
        List<TemplateChange> changes = new ArrayList<>();
        PackMetadata metadata = pack.getMetadata();

        if (metadata.templates() == null) {
            return changes;
        }

        for (Map.Entry<String, PackMetadata.TemplateFile> entry : metadata.templates().entrySet()) {
            String templateName = entry.getKey();
            PackMetadata.TemplateFile templateSpec = entry.getValue();
            
            String targetPath = templateSpec.target();
            // Substitute variables in target path
            for (Map.Entry<String, Object> var : state.variables.entrySet()) {
                targetPath = targetPath.replace("{{" + var.getKey() + "}}", var.getValue().toString());
            }

            Path filePath = projectPath.resolve(targetPath);
            
            if (!Files.exists(filePath)) {
                // New file from pack
                changes.add(new TemplateChange(templateName, targetPath, ChangeType.NEW, null));
            } else {
                // Check if file was modified locally
                String originalChecksum = state.fileChecksums.get(targetPath);
                String currentChecksum = calculateChecksum(filePath);
                
                if (originalChecksum != null && !originalChecksum.equals(currentChecksum)) {
                    changes.add(new TemplateChange(templateName, targetPath, ChangeType.MODIFIED, currentChecksum));
                } else {
                    // Check if pack template changed
                    // For now, mark as potentially outdated if pack version changed
                    if (!pack.getMetadata().version().equals(state.packVersion)) {
                        changes.add(new TemplateChange(templateName, targetPath, ChangeType.OUTDATED, currentChecksum));
                    }
                }
            }
        }

        return changes;
    }

    private void applyChange(Path projectPath, TemplateChange change, Pack pack, Map<String, Object> variables) throws Exception {
        String templateContent = pack.getTemplateContent(change.templateName);
        if (templateContent == null) {
            throw new RuntimeException("Template not found: " + change.templateName);
        }

        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        String rendered = templateEngine.render(templateContent, variables);

        Path targetFile = projectPath.resolve(change.filePath);
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, rendered);
    }

    private String calculateChecksum(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static class ProjectState {
        String projectName;
        String packName;
        String packVersion;
        String createdAt;
        String updatedAt;
        Map<String, Object> variables = new HashMap<>();
        Map<String, String> fileChecksums = new HashMap<>();
    }

    private record TemplateChange(String templateName, String filePath, ChangeType type, String checksum) {}

    private enum ChangeType {
        NEW, MODIFIED, OUTDATED, DELETED
    }
}
