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

import com.ghatana.yappc.api.YappcConfig;
import com.ghatana.yappc.api.model.PackInfo;
import com.ghatana.yappc.api.model.PackListRequest;
import com.ghatana.yappc.api.model.PackValidationResult;
import com.ghatana.yappc.api.service.PackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of PackService.
 *
 * @doc.type class
 * @doc.purpose Pack management implementation
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DefaultPackService implements PackService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPackService.class);

    private final YappcConfig config;
    private final Map<String, PackInfo> packCache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    public DefaultPackService(YappcConfig config) {
        this.config = config;
    }

    @Override
    public List<PackInfo> list() {
        ensureCacheLoaded();
        return new ArrayList<>(packCache.values());
    }

    @Override
    public List<PackInfo> list(PackListRequest request) {
        return list().stream()
                .filter(pack -> matchesFilter(pack, request))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PackInfo> get(String packName) {
        ensureCacheLoaded();
        return Optional.ofNullable(packCache.get(packName));
    }

    @Override
    public List<PackInfo> byLanguage(String language) {
        return list().stream()
                .filter(pack -> language.equalsIgnoreCase(pack.getLanguage()))
                .collect(Collectors.toList());
    }

    @Override
    public List<PackInfo> byCategory(String category) {
        return list().stream()
                .filter(pack -> category.equalsIgnoreCase(pack.getCategory()))
                .collect(Collectors.toList());
    }

    @Override
    public List<PackInfo> byPlatform(String platform) {
        return list().stream()
                .filter(pack -> platform.equalsIgnoreCase(pack.getPlatform()))
                .collect(Collectors.toList());
    }

    @Override
    public List<PackInfo> search(String query) {
        String lowerQuery = query.toLowerCase();
        return list().stream()
                .filter(pack ->
                        pack.getName().toLowerCase().contains(lowerQuery) ||
                        (pack.getDescription() != null && pack.getDescription().toLowerCase().contains(lowerQuery)))
                .collect(Collectors.toList());
    }

    @Override
    public PackValidationResult validate(String packName) {
        Optional<PackInfo> packOpt = get(packName);
        if (packOpt.isEmpty()) {
            return PackValidationResult.builder()
                    .valid(false)
                    .packName(packName)
                    .errors(List.of(new PackValidationResult.ValidationError(
                            "PACK_NOT_FOUND",
                            "Pack not found: " + packName,
                            null,
                            0
                    )))
                    .build();
        }

        PackInfo pack = packOpt.get();
        List<PackValidationResult.ValidationError> errors = new ArrayList<>();
        List<PackValidationResult.ValidationWarning> warnings = new ArrayList<>();

        // Validate pack structure
        Path packPath = config.getPacksPath().resolve(packName);
        if (!Files.exists(packPath)) {
            errors.add(new PackValidationResult.ValidationError(
                    "PACK_DIR_MISSING",
                    "Pack directory does not exist",
                    packPath.toString(),
                    0
            ));
        }

        // Validate templates
        Path templatesPath = packPath.resolve("templates");
        if (!Files.exists(templatesPath)) {
            warnings.add(new PackValidationResult.ValidationWarning(
                    "NO_TEMPLATES_DIR",
                    "No templates directory found",
                    templatesPath.toString()
            ));
        }

        return PackValidationResult.builder()
                .valid(errors.isEmpty())
                .packName(packName)
                .errors(errors)
                .warnings(warnings)
                .templateCount(pack.getTemplateCount())
                .variableCount(pack.getRequiredVariables().size())
                .build();
    }

    @Override
    public boolean exists(String packName) {
        return get(packName).isPresent();
    }

    @Override
    public List<String> getAvailableLanguages() {
        return list().stream()
                .map(PackInfo::getLanguage)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAvailableCategories() {
        return list().stream()
                .map(PackInfo::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAvailablePlatforms() {
        return list().stream()
                .map(PackInfo::getPlatform)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public void refresh() {
        LOG.info("Refreshing pack cache from: {}", config.getPacksPath());
        packCache.clear();
        loadPacks();
        cacheLoaded = true;
        LOG.info("Pack cache refreshed: {} packs loaded", packCache.size());
    }

    private void ensureCacheLoaded() {
        if (!cacheLoaded) {
            synchronized (this) {
                if (!cacheLoaded) {
                    loadPacks();
                    cacheLoaded = true;
                }
            }
        }
    }

    private void loadPacks() {
        Path packsPath = config.getPacksPath();
        if (!Files.exists(packsPath)) {
            LOG.warn("Packs path does not exist: {}", packsPath);
            return;
        }

        try (Stream<Path> dirs = Files.list(packsPath)) {
            dirs.filter(Files::isDirectory)
                    .forEach(this::loadPack);
        } catch (IOException e) {
            LOG.error("Failed to load packs from: {}", packsPath, e);
        }
    }

    private void loadPack(Path packDir) {
        String packName = packDir.getFileName().toString();
        Path metadataPath = packDir.resolve("pack.yaml");
        
        if (!Files.exists(metadataPath)) {
            metadataPath = packDir.resolve("pack.yml");
        }

        try {
            PackInfo packInfo = parsePackMetadata(packName, packDir, metadataPath);
            packCache.put(packName, packInfo);
            LOG.debug("Loaded pack: {}", packName);
        } catch (Exception e) {
            LOG.warn("Failed to load pack: {}", packName, e);
        }
    }

    private PackInfo parsePackMetadata(String packName, Path packDir, Path metadataPath) throws IOException {
        // Parse pack metadata from YAML
        // For now, create a basic PackInfo from directory structure
        List<String> templates = new ArrayList<>();
        Path templatesDir = packDir.resolve("templates");
        
        if (Files.exists(templatesDir)) {
            try (Stream<Path> files = Files.walk(templatesDir)) {
                files.filter(Files::isRegularFile)
                        .map(p -> templatesDir.relativize(p).toString())
                        .forEach(templates::add);
            }
        }

        // Extract language and category from pack name
        String[] parts = packName.split("-");
        String language = parts.length > 0 ? parts[0] : "unknown";
        String category = inferCategory(packName);

        return PackInfo.builder()
                .name(packName)
                .version("1.0.0")
                .description("Pack: " + packName)
                .language(language)
                .category(category)
                .platform(inferPlatform(packName))
                .buildSystem(inferBuildSystem(packName))
                .templates(templates)
                .requiredVariables(extractVariables(packDir))
                .build();
    }

    private String inferCategory(String packName) {
        if (packName.contains("fullstack")) return "fullstack";
        if (packName.contains("middleware")) return "middleware";
        if (packName.contains("platform")) return "platform";
        if (packName.contains("feature")) return "feature";
        return "backend";
    }

    private String inferPlatform(String packName) {
        if (packName.contains("tauri")) return "desktop";
        if (packName.contains("mobile") || packName.contains("react-native")) return "mobile";
        if (packName.contains("web") || packName.contains("vite") || packName.contains("next")) return "web";
        return "server";
    }

    private String inferBuildSystem(String packName) {
        if (packName.contains("gradle")) return "gradle";
        if (packName.contains("maven")) return "maven";
        if (packName.contains("npm") || packName.contains("typescript")) return "npm";
        if (packName.contains("cargo") || packName.contains("rust")) return "cargo";
        if (packName.contains("go")) return "go";
        return "unknown";
    }

    private List<String> extractVariables(Path packDir) {
        // Extract variables from templates
        Set<String> variables = new HashSet<>();
        Path templatesDir = packDir.resolve("templates");
        
        if (Files.exists(templatesDir)) {
            try (Stream<Path> files = Files.walk(templatesDir)) {
                files.filter(Files::isRegularFile)
                        .forEach(file -> extractVariablesFromFile(file, variables));
            } catch (IOException e) {
                LOG.warn("Failed to extract variables from: {}", templatesDir, e);
            }
        }

        return new ArrayList<>(variables);
    }

    private void extractVariablesFromFile(Path file, Set<String> variables) {
        try {
            String content = Files.readString(file);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
            java.util.regex.Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
        } catch (IOException e) {
            LOG.debug("Failed to read file for variable extraction: {}", file, e);
        }
    }

    private boolean matchesFilter(PackInfo pack, PackListRequest request) {
        if (request.getLanguage() != null && 
            !request.getLanguage().equalsIgnoreCase(pack.getLanguage())) {
            return false;
        }
        if (request.getCategory() != null && 
            !request.getCategory().equalsIgnoreCase(pack.getCategory())) {
            return false;
        }
        if (request.getPlatform() != null && 
            !request.getPlatform().equalsIgnoreCase(pack.getPlatform())) {
            return false;
        }
        if (request.getBuildSystem() != null && 
            !request.getBuildSystem().equalsIgnoreCase(pack.getBuildSystem())) {
            return false;
        }
        if (request.getSearchQuery() != null) {
            String query = request.getSearchQuery().toLowerCase();
            boolean matches = pack.getName().toLowerCase().contains(query) ||
                    (pack.getDescription() != null && pack.getDescription().toLowerCase().contains(query));
            if (!matches) return false;
        }
        if (!request.isIncludeCompositions() && pack.isComposition()) {
            return false;
        }
        if (!request.isIncludeFeaturePacks() && "feature".equalsIgnoreCase(pack.getCategory())) {
            return false;
        }
        return true;
    }
}
