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
import com.ghatana.yappc.core.pack.PackEngine;
import com.ghatana.yappc.core.pack.PackMetadata;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * List and search available packs in the registry.
 * Provides filtering by type, language, platform, and search keywords.
 *
 * <p>Example usage:
 * <pre>
 * yappc packs                           # List all packs
 * yappc packs --type service            # Filter by type
 * yappc packs --language java           # Filter by language
 * yappc packs --search "react native"   # Search by keyword
 * yappc packs info java-service-spring-gradle  # Show pack details
 * </pre>
 *
 * @doc.type class
 * @doc.purpose List and search available scaffold packs
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
    name = "packs",
    description = "List and manage available scaffold packs",
    mixinStandardHelpOptions = true,
    subcommands = {PacksCommand.InfoCommand.class, PacksCommand.ValidateCommand.class}
)
public class PacksCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(PacksCommand.class);

    @Option(names = {"--type", "-t"}, description = "Filter by pack type (service, library, application, feature)")
    private String type;

    @Option(names = {"--language", "-l"}, description = "Filter by language (java, typescript, rust, go)")
    private String language;

    @Option(names = {"--platform", "-p"}, description = "Filter by platform (server, web, desktop, mobile)")
    private String platform;

    @Option(names = {"--search", "-s"}, description = "Search in pack names and descriptions")
    private String searchQuery;

    @Option(names = {"--pack-path"}, description = "Custom path to packs directory")
    private Path customPackPath;

    @Option(names = {"--verbose"}, description = "Show detailed pack information")
    private boolean verbose;

    private PackEngine packEngine;

    @Override
    public Integer call() throws Exception {
        try {
            initializePackEngine();
            return listPacks();
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

    private Integer listPacks() {
        List<PackMetadata> packs = packEngine.listAvailablePacks();

        // Apply filters
        packs = packs.stream()
                .filter(this::matchesType)
                .filter(this::matchesLanguage)
                .filter(this::matchesPlatform)
                .filter(this::matchesSearch)
                .sorted(Comparator.comparing(PackMetadata::name))
                .collect(Collectors.toList());

        if (packs.isEmpty()) {
            log.info("📦 No packs found matching the criteria.");
            return 0;
        }

        // Group by category
        List<PackMetadata> servicePacks = packs.stream()
                .filter(p -> p.type() == PackMetadata.PackType.SERVICE)
                .toList();
        List<PackMetadata> appPacks = packs.stream()
                .filter(p -> p.type() == PackMetadata.PackType.APPLICATION)
                .toList();
        List<PackMetadata> featurePacks = packs.stream()
                .filter(p -> p.type() == PackMetadata.PackType.FEATURE || p.name().startsWith("feature-"))
                .toList();
        List<PackMetadata> otherPacks = packs.stream()
                .filter(p -> !servicePacks.contains(p) && !appPacks.contains(p) && !featurePacks.contains(p))
                .toList();

        log.info("\n📦 Available YAPPC Packs\n");

        if (!servicePacks.isEmpty()) {
            log.info("━━━ Backend Services ━━━");
            printPackList(servicePacks);
        }

        if (!appPacks.isEmpty()) {
            log.info("\n━━━ Applications ━━━");
            printPackList(appPacks);
        }

        if (!featurePacks.isEmpty()) {
            log.info("\n━━━ Feature Packs ━━━");
            printPackList(featurePacks);
        }

        if (!otherPacks.isEmpty()) {
            log.info("\n━━━ Other Packs ━━━");
            printPackList(otherPacks);
        }

        log.info("\n" + "─".repeat(80));
        log.info("Total: {} packs", packs.size());
        log.info("\nUsage:");
        log.info("  yappc create <name> --pack <pack-name>   Create a new project");
        log.info("  yappc add <feature>                      Add feature to existing project");
        log.info("  yappc packs info <pack-name>             Show pack details");

        return 0;
    }

    private void printPackList(List<PackMetadata> packs) {
        if (verbose) {
            for (PackMetadata pack : packs) {
                log.info("  {} v{}", pack.name(), pack.version());
                log.info(String.format("    Language: %-12s  Framework: %s", pack.language() != null ? pack.language() : "-",
                        pack.framework() != null ? pack.framework() : "-"));
                log.info("    {}", pack.description() != null ? pack.description() : "-");
                log.info("");;
            }
        } else {
            log.info(String.format("  %-35s %-12s %-15s %s", "NAME", "LANGUAGE", "FRAMEWORK", "DESCRIPTION"));
            for (PackMetadata pack : packs) {
                log.info(String.format("  %-35s %-12s %-15s %s", pack.name(),
                        pack.language() != null ? pack.language() : "-",
                        pack.framework() != null ? pack.framework() : "-",
                        truncate(pack.description(), 40)));
            }
        }
    }

    private boolean matchesType(PackMetadata pack) {
        if (type == null) return true;
        if (pack.type() == null) return false;
        return pack.type().name().equalsIgnoreCase(type);
    }

    private boolean matchesLanguage(PackMetadata pack) {
        if (language == null) return true;
        if (pack.language() == null) return false;
        return pack.language().equalsIgnoreCase(language) || "multi".equalsIgnoreCase(pack.language());
    }

    private boolean matchesPlatform(PackMetadata pack) {
        if (platform == null) return true;
        // Check in tags or framework hints
        if (pack.tags() != null) {
            return pack.tags().stream().anyMatch(t -> t.equalsIgnoreCase(platform));
        }
        return false;
    }

    private boolean matchesSearch(PackMetadata pack) {
        if (searchQuery == null) return true;
        String query = searchQuery.toLowerCase();
        
        if (pack.name() != null && pack.name().toLowerCase().contains(query)) return true;
        if (pack.description() != null && pack.description().toLowerCase().contains(query)) return true;
        if (pack.framework() != null && pack.framework().toLowerCase().contains(query)) return true;
        if (pack.tags() != null) {
            return pack.tags().stream().anyMatch(t -> t.toLowerCase().contains(query));
        }
        return false;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Show detailed information about a specific pack.
     */
    @Command(name = "info", description = "Show detailed pack information")
    public static class InfoCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Pack name")
        private String packName;

        @Option(names = {"--pack-path"}, description = "Custom path to packs directory")
        private Path customPackPath;

        @Override
        public Integer call() throws Exception {
            PackEngine packEngine = new DefaultPackEngine(new SimpleTemplateEngine());

            Path defaultPacksPath = findDefaultPacksPath();
            if (defaultPacksPath != null) {
                packEngine.registerPackLocation(defaultPacksPath);
            }
            if (customPackPath != null) {
                packEngine.registerPackLocation(customPackPath);
            }

            // Find and load pack
            Path packPath = findPackPath(packName, defaultPacksPath, customPackPath);
            if (packPath == null) {
                log.error("❌ Pack not found: {}", packName);
                return 1;
            }

            try {
                var pack = packEngine.loadPack(packPath);
                var metadata = pack.getMetadata();

                log.info("\n📦 Pack: {}", metadata.name());
                log.info("─".repeat(60));
                log.info("Version:     {}", metadata.version());
                log.info("Description: {}", metadata.description());
                log.info("Author:      {}", (metadata.author() != null ? metadata.author() : "-"));
                log.info("License:     {}", (metadata.license() != null ? metadata.license() : "-"));
                log.info("Type:        {}", (metadata.type() != null ? metadata.type() : "-"));
                log.info("Language:    {}", (metadata.language() != null ? metadata.language() : "-"));
                log.info("Framework:   {}", (metadata.framework() != null ? metadata.framework() : "-"));

                if (metadata.tags() != null && !metadata.tags().isEmpty()) {
                    log.info("Tags:        {}", String.join(", ", metadata.tags()));
                }

                // Show templates
                if (metadata.templates() != null) {
                    log.info("\nTemplates ({}):", metadata.templates().size());
                    metadata.templates().forEach((name, template) -> 
                            log.info("  - {} → {}", name, template.target()));
                    }
                    // Show variables if (metadata.variables() != null && !metadata.variables().isEmpty()) {
                            log.info("\nVariables:");
                    metadata.variables().forEach((name, spec) -> {
                        String required = (spec.required() != null && spec.required()) ? " *required*" : "";
                        String defaultVal = spec.defaultValue() != null ? " (default: " + spec.defaultValue() + ")" : "";
                        log.info("  - {}: {}{}{}", name, spec.description(), defaultVal, required);
                    });
                }

                // Show dependencies
                if (metadata.dependencies() != null) {
                    var deps = metadata.dependencies();
                    if (deps.runtime() != null && !deps.runtime().isEmpty()) {
                        log.info("\nRuntime Dependencies:");
                            log.info("  - {}", d);
                    }
                    if (deps.devDependencies() != null && !deps.devDependencies().isEmpty()) {
                            log.info("\nDev Dependencies:");
                        deps.devDependencies.forEach(d ->
                            log.info("  - {}", d));
                    }
                }
                            log.info("\n" + "─".repeat(60));
                log.info("Usage: yappc create my-project --pack {}", metadata.name());

                return 0;
            } catch (Exception e) {
                log.error("❌ Error loading pack: {}", e.getMessage());
                return 1;
            }
        }

        private static Path findDefaultPacksPath() {
            String envPath = System.getenv("YAPPC_PACKS_PATH");
            if (envPath != null) {
                Path path = Paths.get(envPath);
                if (Files.isDirectory(path)) return path;
            }
            Path localPacks = Paths.get("packs");
            if (Files.isDirectory(localPacks)) return localPacks;
            Path homePacks = Paths.get(System.getProperty("user.home"), ".yappc", "packs");
            if (Files.isDirectory(homePacks)) return homePacks;
            return null;
        }

        private static Path findPackPath(String name, Path defaultPath, Path customPath) {
            if (defaultPath != null) {
                Path packDir = defaultPath.resolve(name);
                if (Files.isDirectory(packDir)) return packDir;
            }
            if (customPath != null) {
                Path packDir = customPath.resolve(name);
                if (Files.isDirectory(packDir)) return packDir;
            }
            return null;
        }
    }

    /**
     * Validate a pack structure.
     */
    @Command(name = "validate", description = "Validate a pack structure")
    public static class ValidateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Pack name or path")
        private String packNameOrPath;

        @Override
        public Integer call() throws Exception {
            PackEngine packEngine = new DefaultPackEngine(new SimpleTemplateEngine());

            Path packPath = Paths.get(packNameOrPath);
            if (!Files.isDirectory(packPath)) {
                // Try finding by name
                Path defaultPacksPath = InfoCommand.findDefaultPacksPath();
                if (defaultPacksPath != null) {
                    packPath = defaultPacksPath.resolve(packNameOrPath);
                }
            }

            if (!Files.isDirectory(packPath)) {
                log.error("❌ Pack not found: {}", packNameOrPath);
                return 1;
            }

            log.info("\n🔍 Validating pack: {}", packPath.getFileName());
            
            var result = packEngine.validatePack(packPath);
            
            if (result.valid()) {
                log.info("✅ Pack validation passed!");
            } else {
                log.info("❌ Pack validation failed:");
                    log.info("   ERROR: {}", e);
            }
            if (!result.warnings().isEmpty()) {
                    log.info("\nWarnings:");
                result.warnings().forEach(w ->
                    log.info("   ⚠️  {}", w));
            }
                    log.info("\n{}", result.summary());
            return result.valid() ? 0 : 1;
        }
    }
}
