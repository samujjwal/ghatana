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

package com.ghatana.yappc.core.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Language registry for managing supported languages and frameworks.
 * 
 * Provides:
 * - Language discovery and registration
 * - Framework management per language
 * - Build system registry
 * - Testing framework registry
 * - Version management
 * 
 * @doc.type class
 * @doc.purpose Language and framework registry for YAPPC scaffolding
 * @doc.layer platform
 * @doc.pattern Registry/Repository
 */
public class LanguageRegistry {

    private static final Logger log = LoggerFactory.getLogger(LanguageRegistry.class);

    private final ObjectMapper yamlMapper;
    private final Map<String, LanguageDefinition> languages;
    private final Map<String, Map<String, FrameworkDefinition>> frameworks;

    public LanguageRegistry() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.languages = new ConcurrentHashMap<>();
        this.frameworks = new ConcurrentHashMap<>();
        
        // Load built-in languages
        loadBuiltInLanguages();
    }

    /**
     * Get all supported languages.
     * 
     * @return list of language names
     */
    public List<String> getSupportedLanguages() {
        return new ArrayList<>(languages.keySet());
    }

    /**
     * Get language definition by name.
     * 
     * @param name language name
     * @return language definition or null if not found
     */
    public LanguageDefinition getLanguage(String name) {
        return languages.get(name.toLowerCase());
    }

    /**
     * Get all frameworks for a language.
     * 
     * @param language language name
     * @return list of framework definitions
     */
    public List<FrameworkDefinition> getFrameworks(String language) {
        Map<String, FrameworkDefinition> langFrameworks = frameworks.get(language.toLowerCase());
        return langFrameworks != null ? new ArrayList<>(langFrameworks.values()) : List.of();
    }

    /**
     * Get specific framework for a language.
     * 
     * @param language language name
     * @param framework framework name
     * @return framework definition or null if not found
     */
    public FrameworkDefinition getFramework(String language, String framework) {
        Map<String, FrameworkDefinition> langFrameworks = frameworks.get(language.toLowerCase());
        return langFrameworks != null ? langFrameworks.get(framework.toLowerCase()) : null;
    }

    /**
     * Register a language definition.
     * 
     * @param definition language definition
     */
    public void registerLanguage(LanguageDefinition definition) {
        languages.put(definition.name().toLowerCase(), definition);
        
        // Register frameworks
        if (definition.frameworks() != null) {
            Map<String, FrameworkDefinition> langFrameworks = 
                frameworks.computeIfAbsent(definition.name().toLowerCase(), k -> new ConcurrentHashMap<>());
            
            definition.frameworks().forEach((name, framework) -> 
                langFrameworks.put(name.toLowerCase(), framework)
            );
        }
        
        log.info("Registered language: {} with {} frameworks", 
            definition.name(), 
            definition.frameworks() != null ? definition.frameworks().size() : 0);
    }

    /**
     * Check if language is supported.
     * 
     * @param language language name
     * @return true if supported
     */
    public boolean isLanguageSupported(String language) {
        return languages.containsKey(language.toLowerCase());
    }

    /**
     * Check if framework is supported for language.
     * 
     * @param language language name
     * @param framework framework name
     * @return true if supported
     */
    public boolean isFrameworkSupported(String language, String framework) {
        return getFramework(language, framework) != null;
    }

    /**
     * Get supported versions for a language.
     * 
     * @param language language name
     * @return list of supported versions
     */
    public List<String> getSupportedVersions(String language) {
        LanguageDefinition def = getLanguage(language);
        return def != null ? def.versions() : List.of();
    }

    /**
     * Get latest version for a language.
     * 
     * @param language language name
     * @return latest version or null
     */
    public String getLatestVersion(String language) {
        List<String> versions = getSupportedVersions(language);
        return versions.isEmpty() ? null : versions.get(versions.size() - 1);
    }

    /**
     * Get build systems for a language.
     * 
     * @param language language name
     * @return map of build system definitions
     */
    public Map<String, BuildSystemDefinition> getBuildSystems(String language) {
        LanguageDefinition def = getLanguage(language);
        return def != null && def.buildSystems() != null ? def.buildSystems() : Map.of();
    }

    /**
     * Get package managers for a language.
     * 
     * @param language language name
     * @return package management definition
     */
    public PackageManagementDefinition getPackageManagement(String language) {
        LanguageDefinition def = getLanguage(language);
        return def != null ? def.packageManagement() : null;
    }

    /**
     * Load built-in language definitions from resources.
     */
    private void loadBuiltInLanguages() {
        String[] builtInLanguages = {"go", "typescript", "java", "python", "rust"};
        
        for (String lang : builtInLanguages) {
            try {
                LanguageDefinition definition = loadLanguageFromResource(lang);
                registerLanguage(definition);
            } catch (Exception e) {
                log.warn("Failed to load built-in language {}: {}", lang, e.getMessage());
            }
        }
        
        log.info("Loaded {} built-in languages", languages.size());
    }

    /**
     * Load language definition from resource.
     * 
     * @param languageName language name
     * @return language definition
     * @throws IOException if loading fails
     */
    private LanguageDefinition loadLanguageFromResource(String languageName) throws IOException {
        String resourcePath = "languages/" + languageName + ".yaml";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        
        if (stream == null) {
            throw new IOException("Language definition not found: " + resourcePath);
        }
        
        return yamlMapper.readValue(stream, LanguageDefinition.class);
    }

    /**
     * Load language definition from file.
     * 
     * @param yamlPath path to YAML file
     * @return language definition
     * @throws IOException if loading fails
     */
    public LanguageDefinition loadLanguageFromFile(java.nio.file.Path yamlPath) throws IOException {
        return yamlMapper.readValue(yamlPath.toFile(), LanguageDefinition.class);
    }
}
