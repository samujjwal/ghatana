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
import com.ghatana.yappc.core.template.SimpleTemplateEngine;

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
 * Add a feature pack to an existing project. Supports adding database, auth,
 * observability, and other cross-cutting features to any compatible project.
 *
 * <p>Example usage:
 * <pre>
 * yappc add database --type postgresql
 * yappc add auth --type jwt
 * yappc add observability --type otel
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Add feature packs to existing projects
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
    name = "add",
    description = "Add a feature to an existing project",
    mixinStandardHelpOptions = true
)
public class AddCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(AddCommand.class);

    @Parameters(index = "0", description = "Feature to add (database, auth, observability)")
    private String featureName;

    @Option(names = {"--type", "-t"}, description = "Feature type/variant (e.g., postgresql, jwt, otel)")
    private String featureType;

    @Option(names = {"--project", "-p"}, description = "Project directory (default: current directory)")
    private Path projectPath;

    @Option(names = {"--var", "-v"}, description = "Template variable in key=value format")
    private Map<String, String> variables = new HashMap<>();

    @Option(names = {"--dry-run"}, description = "Show what would be generated without writing files")
    private boolean dryRun;

    @Option(names = {"--force", "-f"}, description = "Overwrite existing files")
    private boolean force;

    @Option(names = {"--list", "-l"}, description = "List available features")
    private boolean listFeatures;

    @Option(names = {"--pack-path"}, description = "Custom path to packs directory")
    private Path customPackPath;

    private PackEngine packEngine;

    @Override
    public Integer call() throws Exception {
        try {
            initializePackEngine();

            // List features mode
            if (listFeatures) {
                return listAvailableFeatures();
            }

            // Validate required parameters
            if (featureName == null || featureName.isBlank()) {
                log.error("❌ Error: Feature name is required. Use --list to see available features.");
                return 1;
            }

            // Determine project path
            Path targetProject = projectPath != null ? projectPath : Paths.get(".");
            if (!Files.isDirectory(targetProject)) {
                log.error("❌ Error: Project directory does not exist: {}", targetProject);
                return 1;
            }

            // Detect project type
            ProjectContext projectContext = detectProjectContext(targetProject);
            if (projectContext == null) {
                log.error("❌ Error: Could not detect project type. Ensure you're in a valid project directory.");
                return 1;
            }

            log.info("📦 Detected project: {} / {}", projectContext.language, projectContext.buildSystem);

            // Load the feature pack
            String featurePackName = "feature-" + featureName.toLowerCase();
            Pack featurePack = loadPack(featurePackName);
            if (featurePack == null) {
                return 1;
            }

            // Check compatibility
            if (!isCompatible(featurePack, projectContext)) {
                log.error("❌ Error: Feature '{}' is not compatible with this project type.", featureName);
                log.error("   Supported: {}", getSupportedLanguages(featurePack));
                return 1;
            }

            // Prepare variables
            Map<String, Object> templateVars = prepareVariables(featurePack, projectContext);

            // Dry run mode
            if (dryRun) {
                return performDryRun(featurePack, targetProject, templateVars, projectContext);
            }

            // Add feature
            return addFeature(featurePack, targetProject, templateVars, projectContext);

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
        }

        // Register custom pack path if provided
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

    private Integer listAvailableFeatures() {
        List<PackMetadata> packs = packEngine.listAvailablePacks();

        List<PackMetadata> featurePacks = packs.stream()
                .filter(p -> p.name().startsWith("feature-"))
                .toList();

        if (featurePacks.isEmpty()) {
            log.info("📦 No feature packs found.");
            return 0;
        }

        log.info("\n📦 Available Feature Packs:\n");
        log.info(String.format("  %-25s %-15s %s", "FEATURE", "LANGUAGES", "DESCRIPTION"));
        log.info("  " + "-".repeat(75));

        for (PackMetadata metadata : featurePacks) {
            String featureName = metadata.name().replace("feature-", "");
            String languages = metadata.language() != null ? metadata.language() : "multi";
            log.info(String.format("  %-25s %-15s %s", featureName,
                    languages,
                    truncate(metadata.description(), 45)));
        }

        log.info("\nUsage: yappc add <feature> [--type <variant>]");
        log.info("       yappc add database --type postgresql");
        return 0;
    }

    private Pack loadPack(String name) throws PackException {
        List<PackMetadata> available = packEngine.listAvailablePacks();
        for (PackMetadata metadata : available) {
            if (metadata.name().equals(name)) {
                Path packPath = findPackPath(name);
                if (packPath != null) {
                    Pack pack = packEngine.loadPack(packPath);
                    log.info("✓ Loaded feature: {} v{}", pack.getName(), pack.getVersion());
                    return pack;
                }
            }
        }

        log.error("❌ Feature pack not found: {}", name);
        log.error("   Use --list to see available features.");
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

    private ProjectContext detectProjectContext(Path projectPath) {
        // Detect based on build files
        if (Files.exists(projectPath.resolve("build.gradle.kts")) 
                || Files.exists(projectPath.resolve("build.gradle"))) {
            return new ProjectContext("java", "gradle", detectJavaPackage(projectPath));
        }
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            return new ProjectContext("java", "maven", detectJavaPackage(projectPath));
        }
        if (Files.exists(projectPath.resolve("Cargo.toml"))) {
            return new ProjectContext("rust", "cargo", null);
        }
        if (Files.exists(projectPath.resolve("go.mod"))) {
            return new ProjectContext("go", "go", detectGoModule(projectPath));
        }
        if (Files.exists(projectPath.resolve("package.json"))) {
            return new ProjectContext("typescript", "pnpm", null);
        }

        return null;
    }

    private String detectJavaPackage(Path projectPath) {
        // Try to detect package from existing source files
        Path srcMain = projectPath.resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            try (var stream = Files.walk(srcMain, 5)) {
                return stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .findFirst()
                        .map(p -> {
                            String relative = srcMain.relativize(p.getParent()).toString();
                            return relative.replace('/', '.').replace('\\', '.');
                        })
                        .orElse("com.example");
            } catch (IOException e) {
                return "com.example";
            }
        }
        return "com.example";
    }

    private String detectGoModule(Path projectPath) {
        try {
            String content = Files.readString(projectPath.resolve("go.mod"));
            for (String line : content.split("\n")) {
                if (line.startsWith("module ")) {
                    return line.substring(7).trim();
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return "github.com/example/project";
    }

    private boolean isCompatible(Pack pack, ProjectContext context) {
        String language = pack.getMetadata().language();
        if ("multi".equals(language)) {
            return true; // Multi-language packs are compatible with all
        }
        return language != null && language.equalsIgnoreCase(context.language);
    }

    private String getSupportedLanguages(Pack pack) {
        String language = pack.getMetadata().language();
        return language != null ? language : "all";
    }

    private Map<String, Object> prepareVariables(Pack pack, ProjectContext context) {
        Map<String, Object> vars = new HashMap<>();

        // Add context-based variables
        vars.put("language", context.language);
        vars.put("buildSystem", context.buildSystem);
        vars.put("packageName", context.packageName != null ? context.packageName : "com.example");
        vars.put("packagePath", (context.packageName != null ? context.packageName : "com.example").replace('.', '/'));
        vars.put("modulePath", context.packageName != null ? context.packageName : "github.com/example/project");

        // Add feature type if specified
        if (featureType != null) {
            vars.put("type", featureType);
            vars.put("databaseType", featureType);
            vars.put("authType", featureType);
        }

        // Add user-provided variables
        vars.putAll(variables);

        // Add defaults from pack
        PackMetadata metadata = pack.getMetadata();
        if (metadata.variables() != null) {
            for (var entry : metadata.variables().entrySet()) {
                if (!vars.containsKey(entry.getKey()) && entry.getValue().defaultValue() != null) {
                    vars.put(entry.getKey(), entry.getValue().defaultValue());
                }
            }
        }

        return vars;
    }

    private Integer performDryRun(Pack pack, Path targetPath, Map<String, Object> vars, ProjectContext context) {
        log.info("\n🔍 Dry Run - Would add feature:\n");
        log.info("   Feature: {} v{}", pack.getName(), pack.getVersion());
        log.info("   Project: {}", targetPath.toAbsolutePath());
        log.info("   Language: {}", context.language);
        log.info("\n   Variables:");
        vars.forEach((k, v) ->
            log.info("     {} = {}", k, v));
        log.info("\n   Files to generate (for {}):", context.language);
        // For feature packs, we need to filter by language
        PackMetadata metadata = pack.getMetadata();
        if (metadata.templates() != null) {
            for (var entry : metadata.templates().entrySet()) {
                String target = entry.getValue().target();
                // Simple variable substitution for display
                for (var var : vars.entrySet()) {
                    target = target.replace("{{" + var.getKey() + "}}", var.getValue().toString());
                }
                log.info("     - {}", target);
            }
        }

        log.info("\n✓ Dry run complete. Use without --dry-run to add the feature.");
        return 0;
    }

    private Integer addFeature(Pack pack, Path targetPath, Map<String, Object> vars, ProjectContext context) {
        log.info("\n🚀 Adding feature: {}\n", pack.getName());

        try {
            GenerationResult result = packEngine.generateFromPack(pack, targetPath, vars);

            if (result.successful()) {
                log.info("✅ {}", result.summary());
                log.info("\n   Added files:");
                for (String file : result.generatedFiles()) {
                    log.info("     ✓ {}", file);
                }

                printNextSteps(pack, context);
                return 0;
            } else {
                log.error("❌ Feature addition failed:");
                for (String error : result.errors()) {
                    log.error("   - {}", error);
                }
                return 1;
            }
        } catch (PackException e) {
            log.error("❌ Feature addition failed: {}", e.getMessage());
            return 1;
        }
    }

    private void printNextSteps(Pack pack, ProjectContext context) {
        log.info("\n📝 Next steps:");
        
        String featureName = pack.getName().replace("feature-", "");
        switch (featureName) {
            case "database" -> {
                if ("java".equals(context.language)) {
                    log.info("   1. Add the db profile: -Dspring.profiles.active=db");
                    log.info("   2. Run Flyway migrations: ./gradlew flywayMigrate");
                } else if ("typescript".equals(context.language)) {
                    log.info("   1. Set DATABASE_URL in .env");
                    log.info("   2. Run: pnpm prisma migrate dev");
                } else if ("rust".equals(context.language)) {
                    log.info("   1. Set DATABASE_URL in .env");
                    log.info("   2. Run: sqlx database create && sqlx migrate run");
                } else if ("go".equals(context.language)) {
                    log.info("   1. Set DATABASE_URL in .env");
                    log.info("   2. Run migrations: go run ./cmd/migrate up");
                }
            }
            case "auth" -> {
                log.info("   1. Set JWT_SECRET in your environment");
                log.info("   2. Configure authentication routes");
            }
            case "observability" -> {
                log.info("   1. Configure OTEL_EXPORTER_OTLP_ENDPOINT");
                log.info("   2. Start your observability stack (docker-compose up -d)");
            }
            default -> log.info("   Check the generated files for configuration instructions.");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Detected project context containing language and build system.
     */
    private record ProjectContext(String language, String buildSystem, String packageName) {}
}
