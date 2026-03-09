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

import com.ghatana.yappc.core.pack.DefaultPackEngine;
import com.ghatana.yappc.core.pack.Pack;
import com.ghatana.yappc.core.pack.PackEngine;
import com.ghatana.yappc.core.pack.PackEngine.GenerationResult;
import com.ghatana.yappc.core.pack.PackException;
import com.ghatana.yappc.core.pack.PackMetadata;
import com.ghatana.yappc.core.telemetry.TelemetryInstrumentation;
import com.ghatana.yappc.core.telemetry.TelemetryManager;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a new project from a pack template. Supports pack selection, variable
 * substitution, and project generation.
 *
 * @doc.type class
 * @doc.purpose Create command for generating projects from pack templates
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
    name = "create",
    description = "Create a new project from a pack template",
    mixinStandardHelpOptions = true
)
public class CreateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CreateCommand.class);

    @Parameters(index = "0", description = "Project name", arity = "0..1")
    private String projectName;

    @Option(names = {"--pack", "-p"}, description = "Pack name to use for generation")
    private String packName;

    @Option(names = {"--output", "-o"}, description = "Output directory (default: current directory)")
    private Path outputPath;

    @Option(names = {"--var", "-v"}, description = "Template variable in key=value format")
    private Map<String, String> variables = new HashMap<>();

    @Option(names = {"--list", "-l"}, description = "List available packs")
    private boolean listPacks;

    @Option(names = {"--dry-run"}, description = "Show what would be generated without writing files")
    private boolean dryRun;

    @Option(names = {"--force", "-f"}, description = "Overwrite existing files")
    private boolean force;

    @Option(names = {"--pack-path"}, description = "Custom path to packs directory")
    private Path customPackPath;

    private PackEngine packEngine;

    @Override
    public Integer call() throws Exception {
        // Initialize telemetry for CLI context
        TelemetryManager.initializeForCli();
        TelemetryInstrumentation instrumentation = TelemetryManager.getInstrumentation("yappc-cli");

        // Instrument the command execution
        return instrumentation.instrumentCommand(
                "create",
                Attributes.of(
                        AttributeKey.stringKey("project.name"),
                        projectName != null ? projectName : "unknown",
                        AttributeKey.stringKey("pack.name"),
                        packName != null ? packName : "none"),
                this::executeCreate);
    }

    private Integer executeCreate() {
        try {
            // Initialize pack engine
            initializePackEngine();

            // Handle list packs mode
            if (listPacks) {
                return listAvailablePacks();
            }

            // Validate required parameters
            if (packName == null || packName.isBlank()) {
                log.error("❌ Error: Pack name is required. Use --pack <name> or --list to see available packs.");
                return 1;
            }

            if (projectName == null || projectName.isBlank()) {
                log.error("❌ Error: Project name is required.");
                return 1;
            }

            // Load the specified pack
            Pack pack = loadPack(packName);
            if (pack == null) {
                return 1;
            }

            // Determine output path
            Path targetPath = resolveOutputPath();

            // Check if output path exists
            if (Files.exists(targetPath) && !force) {
                log.error("❌ Error: Output directory already exists: {}", targetPath);
                log.error("   Use --force to overwrite existing files.");
                return 1;
            }

            // Prepare variables
            Map<String, Object> templateVars = prepareVariables(pack);

            // Dry run mode
            if (dryRun) {
                return performDryRun(pack, targetPath, templateVars);
            }

            // Generate project
            return generateProject(pack, targetPath, templateVars);

        } catch (Exception e) {
            log.error("❌ Error: {}", e.getMessage());
            return 1;
        }
    }

    private void initializePackEngine() throws IOException {
        packEngine = new DefaultPackEngine(new SimpleTemplateEngine());

        // Register default pack locations
        Path defaultPacksPath = findDefaultPacksPath();
        if (defaultPacksPath != null && Files.isDirectory(defaultPacksPath)) {
            packEngine.registerPackLocation(defaultPacksPath);
            log.info("📦 Registered pack location: {}", defaultPacksPath);
        }

        // Register custom pack path if provided
        if (customPackPath != null && Files.isDirectory(customPackPath)) {
            packEngine.registerPackLocation(customPackPath);
            log.info("📦 Registered custom pack location: {}", customPackPath);
        }
    }

    private Path findDefaultPacksPath() {
        // Try to find packs directory relative to execution location
        // Order of precedence:
        // 1. YAPPC_PACKS_PATH environment variable
        // 2. ./packs (current directory)
        // 3. ~/.yappc/packs (user home)
        // 4. /usr/local/share/yappc/packs (system-wide)

        String envPath = System.getenv("YAPPC_PACKS_PATH");
        if (envPath != null) {
            Path path = Paths.get(envPath);
            if (Files.isDirectory(path)) {
                return path;
            }
        }

        // Current directory packs
        Path localPacks = Paths.get("packs");
        if (Files.isDirectory(localPacks)) {
            return localPacks;
        }

        // User home packs
        Path homePacks = Paths.get(System.getProperty("user.home"), ".yappc", "packs");
        if (Files.isDirectory(homePacks)) {
            return homePacks;
        }

        // System-wide packs
        Path systemPacks = Paths.get("/usr/local/share/yappc/packs");
        if (Files.isDirectory(systemPacks)) {
            return systemPacks;
        }

        return null;
    }

    private Integer listAvailablePacks() {
        List<PackMetadata> packs = packEngine.listAvailablePacks();

        if (packs.isEmpty()) {
            log.info("📦 No packs found in registered locations.");
            log.info("   Use --pack-path to specify a custom packs directory.");
            return 0;
        }

        log.info("\n📦 Available Packs:\n");
        log.info(String.format("  %-35s %-12s %-15s %s", "NAME", "LANGUAGE", "TYPE", "DESCRIPTION"));
        log.info("  " + "-".repeat(90));

        for (PackMetadata metadata : packs) {
            log.info(String.format("  %-35s %-12s %-15s %s", metadata.name(),
                    metadata.language() != null ? metadata.language() : "-",
                    metadata.type() != null ? metadata.type().name().toLowerCase() : "-",
                    truncate(metadata.description(), 40)));
        }

        log.info("\nUsage: yappc create <project-name> --pack <pack-name>");
        return 0;
    }

    private Pack loadPack(String name) throws PackException {
        // First try to find by name in registered locations
        List<PackMetadata> available = packEngine.listAvailablePacks();
        for (PackMetadata metadata : available) {
            if (metadata.name().equals(name)) {
                // Find and load the pack
                Path packPath = findPackPath(name);
                if (packPath != null) {
                    Pack pack = packEngine.loadPack(packPath);
                    log.info("✓ Loaded pack: {} v{}", pack.getName(), pack.getVersion());
                    return pack;
                }
            }
        }

        // Try as a direct path
        Path directPath = Paths.get(name);
        if (Files.isDirectory(directPath)) {
            Pack pack = packEngine.loadPack(directPath);
            log.info("✓ Loaded pack from path: {}", pack.getName());
            return pack;
        }

        log.error("❌ Pack not found: {}", name);
        log.error("   Use --list to see available packs.");
        return null;
    }

    private Path findPackPath(String name) {
        // Search in registered locations
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

    private Path resolveOutputPath() {
        if (outputPath != null) {
            return outputPath.resolve(projectName);
        }
        return Paths.get(projectName);
    }

    private Map<String, Object> prepareVariables(Pack pack) {
        Map<String, Object> vars = new HashMap<>();

        // Add project name
        vars.put("projectName", projectName);
        vars.put("name", projectName);

        // Add common derived variables
        vars.put("className", toPascalCase(projectName));
        vars.put("packageName", toPackageName(projectName));
        vars.put("packagePath", toPackageName(projectName).replace('.', '/'));

        // Add user-provided variables
        vars.putAll(variables);

        // Validate required variables from pack
        PackMetadata metadata = pack.getMetadata();
        if (metadata.variables() != null) {
            for (Map.Entry<String, PackMetadata.VariableSpec> entry : metadata.variables().entrySet()) {
                String varName = entry.getKey();
                PackMetadata.VariableSpec spec = entry.getValue();

                if (!vars.containsKey(varName)) {
                    if (spec.defaultValue() != null) {
                        vars.put(varName, spec.defaultValue());
                    } else if (spec.required() != null && spec.required()) {
                        log.error("⚠️  Required variable not provided: {}", varName);
                        log.error("   Description: {}", spec.description());
                    }
                }
            }
        }

        return vars;
    }

    private Integer performDryRun(Pack pack, Path targetPath, Map<String, Object> vars) {
        log.info("\n🔍 Dry Run - Would generate project:\n");
        log.info("   Pack: {} v{}", pack.getName(), pack.getVersion());
        log.info("   Output: {}", targetPath.toAbsolutePath());
        log.info("\n   Variables:");
        vars.forEach((k, v) ->
            log.info("     {} = {}", k, v));
        PackMetadata metadata = pack.getMetadata();
        if (metadata.templates() != null) {
            log.info("\n   Files to generate:");
            for (Map.Entry<String, PackMetadata.TemplateFile> entry : metadata.templates().entrySet()) {
                String target = entry.getValue().target();
                // Substitute variables in target path
                for (Map.Entry<String, Object> var : vars.entrySet()) {
                    target = target.replace("{{" + var.getKey() + "}}", var.getValue().toString());
                }
                log.info("     - {}", target);
            }
        }

        log.info("\n✓ Dry run complete. Use without --dry-run to generate files.");
        return 0;
    }

    private Integer generateProject(Pack pack, Path targetPath, Map<String, Object> vars) {
        log.info("\n🚀 Creating project from pack: {}\n", pack.getName());

        try {
            GenerationResult result = packEngine.generateFromPack(pack, targetPath, vars);

            if (result.successful()) {
                log.info("✅ {}", result.summary());
                log.info("\n   Generated files:");
                for (String file : result.generatedFiles()) {
                    log.info("     ✓ {}", file);
                }

                // Save project state for future updates
                saveProjectState(pack, targetPath, vars, result.generatedFiles());

                log.info("\n📁 Project created at: {}", targetPath.toAbsolutePath());
                printNextSteps(pack, targetPath);
                return 0;
            } else {
                log.error("❌ Generation failed:");
                for (String error : result.errors()) {
                    log.error("   - {}", error);
                }
                return 1;
            }
        } catch (PackException e) {
            log.error("❌ Generation failed: {}", e.getMessage());
            return 1;
        }
    }

    private void saveProjectState(Pack pack, Path targetPath, Map<String, Object> vars, List<String> generatedFiles) {
        try {
            Path stateDir = targetPath.resolve(".yappc");
            Files.createDirectories(stateDir);

            // Calculate checksums for generated files
            Map<String, String> checksums = new java.util.LinkedHashMap<>();
            for (String file : generatedFiles) {
                Path filePath = targetPath.resolve(file);
                if (Files.exists(filePath)) {
                    checksums.put(file, calculateChecksum(filePath));
                }
            }

            // Build state JSON
            Map<String, Object> state = new java.util.LinkedHashMap<>();
            state.put("projectName", projectName);
            state.put("packName", pack.getName());
            state.put("packVersion", pack.getVersion());
            state.put("createdAt", java.time.Instant.now().toString());
            state.put("variables", vars);
            state.put("fileChecksums", checksums);

            // Write state file
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Files.writeString(stateDir.resolve("state.json"), 
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));

            // Add .yappc to .gitignore if not already
            Path gitignore = targetPath.resolve(".gitignore");
            if (Files.exists(gitignore)) {
                String content = Files.readString(gitignore);
                if (!content.contains(".yappc/")) {
                    Files.writeString(gitignore, content + "\n# YAPPC state\n.yappc/\n");
                }
            }
        } catch (Exception e) {
            // Non-fatal: just log a warning
            log.info("⚠️  Could not save project state: {}", e.getMessage());
        }
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

    private void printNextSteps(Pack pack, Path targetPath) {
        log.info("\n📝 Next steps:");
        log.info("   cd {}", targetPath);

        String language = pack.getMetadata().language();
        if (language != null) {
            switch (language.toLowerCase()) {
                case "java" -> {
                    log.info("   ./gradlew build");
                    log.info("   ./gradlew run");
                }
                case "typescript", "javascript" -> {
                    log.info("   pnpm install");
                    log.info("   pnpm dev");
                }
                case "rust" -> {
                    log.info("   cargo build");
                    log.info("   cargo run");
                }
                case "go" -> {
                    log.info("   go mod tidy");
                    log.info("   go run ./...");
                }
                case "multi" -> {
                    log.info("   make install");
                    log.info("   make dev");
                }
                log.info("   # Check README.md for build instructions");
            }
        }
    }

    // Utility methods

    private String toPascalCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_' || c == '.') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String toPackageName(String name) {
        if (name == null || name.isEmpty()) return name;
        // Convert to valid Java package name
        return "com.example." + name.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceAll("^[0-9]+", "");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
