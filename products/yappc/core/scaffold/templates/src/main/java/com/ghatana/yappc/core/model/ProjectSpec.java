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

package com.ghatana.yappc.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced project specification for multi-language, multi-platform scaffold generation.
 * Supports individual repositories within multi-repo workspaces as well as standalone projects.
 *
 * @doc.type class
 * @doc.purpose Project specification for polyglot, multi-platform scaffold generation
 * @doc.layer platform
 * @doc.pattern Specification
 */
public class ProjectSpec {

    // Core identification
    private String name;
    private String description;
    private String version;
    private String groupId;

    // Language and build system (use string for flexibility, with enum helpers)
    private String language;
    private String buildSystem;
    private String framework;

    // Platform and archetype
    private String platform;
    private String archetype;
    private String projectType; // Legacy field for backward compatibility

    // Multi-module support
    private List<ModuleSpec> modules;
    private boolean multiModule;
    private String rootModuleName;

    // Dependencies and features
    private List<String> dependencies;
    private List<String> devDependencies;
    private List<String> features;

    // Configuration and metadata
    private Map<String, Object> configuration;
    private Map<String, String> environment;
    private Map<String, String> templateVariables;

    // Platform-specific settings
    private MobileSettings mobileSettings;
    private DesktopSettings desktopSettings;

    /**
     * Default constructor for Jackson.
     */
    public ProjectSpec() {
        this.modules = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.devDependencies = new ArrayList<>();
        this.features = new ArrayList<>();
        this.configuration = new HashMap<>();
        this.environment = new HashMap<>();
        this.templateVariables = new HashMap<>();
    }

    private ProjectSpec(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.groupId = builder.groupId;
        this.language = builder.language;
        this.buildSystem = builder.buildSystem;
        this.framework = builder.framework;
        this.platform = builder.platform;
        this.archetype = builder.archetype;
        this.projectType = builder.projectType;
        this.modules = builder.modules;
        this.multiModule = builder.multiModule;
        this.rootModuleName = builder.rootModuleName;
        this.dependencies = builder.dependencies;
        this.devDependencies = builder.devDependencies;
        this.features = builder.features;
        this.configuration = builder.configuration;
        this.environment = builder.environment;
        this.templateVariables = builder.templateVariables;
        this.mobileSettings = builder.mobileSettings;
        this.desktopSettings = builder.desktopSettings;
    }

    public static Builder builder() {
        return new Builder();
    }

    // === Type-safe accessors ===

    /**
     * Get language as typed enum (may return null if non-standard).
     */
    public LanguageType getLanguageType() {
        return LanguageType.fromIdentifier(language);
    }

    /**
     * Get platform as typed enum (may return null if non-standard).
     */
    public PlatformType getPlatformType() {
        return PlatformType.fromIdentifier(platform);
    }

    /**
     * Get archetype as typed enum (may return null if non-standard).
     */
    public ProjectArchetype getArchetypeType() {
        return ProjectArchetype.fromIdentifier(archetype);
    }

    /**
     * Check if this project targets mobile platforms.
     */
    public boolean isMobileProject() {
        PlatformType pt = getPlatformType();
        return pt != null && pt.isMobile();
    }

    /**
     * Check if this project targets desktop platforms.
     */
    public boolean isDesktopProject() {
        return PlatformType.DESKTOP.getIdentifier().equals(platform);
    }

    /**
     * Check if this project is a web frontend.
     */
    public boolean isWebProject() {
        return PlatformType.WEB.getIdentifier().equals(platform);
    }

    /**
     * Check if this project is a backend service.
     */
    public boolean isBackendProject() {
        ProjectArchetype at = getArchetypeType();
        return at != null && at.isBackend();
    }

    // === Getters ===

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getLanguage() {
        return language;
    }

    public String getBuildSystem() {
        return buildSystem;
    }

    public String getFramework() {
        return framework;
    }

    public String getPlatform() {
        return platform;
    }

    public String getArchetype() {
        return archetype;
    }

    public String getProjectType() {
        return projectType;
    }

    public List<ModuleSpec> getModules() {
        return modules;
    }

    public boolean isMultiModule() {
        return multiModule;
    }

    public String getRootModuleName() {
        return rootModuleName;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<String> getDevDependencies() {
        return devDependencies;
    }

    public List<String> getFeatures() {
        return features;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public Map<String, String> getTemplateVariables() {
        return templateVariables;
    }

    public MobileSettings getMobileSettings() {
        return mobileSettings;
    }

    public DesktopSettings getDesktopSettings() {
        return desktopSettings;
    }

    // === Setters for Jackson ===

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setBuildSystem(String buildSystem) {
        this.buildSystem = buildSystem;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setArchetype(String archetype) {
        this.archetype = archetype;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public void setModules(List<ModuleSpec> modules) {
        this.modules = modules;
    }

    public void setMultiModule(boolean multiModule) {
        this.multiModule = multiModule;
    }

    public void setRootModuleName(String rootModuleName) {
        this.rootModuleName = rootModuleName;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public void setDevDependencies(List<String> devDependencies) {
        this.devDependencies = devDependencies;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public void setTemplateVariables(Map<String, String> templateVariables) {
        this.templateVariables = templateVariables;
    }

    public void setMobileSettings(MobileSettings mobileSettings) {
        this.mobileSettings = mobileSettings;
    }

    public void setDesktopSettings(DesktopSettings desktopSettings) {
        this.desktopSettings = desktopSettings;
    }

    @Override
    public String toString() {
        return String.format(
                "ProjectSpec{name='%s', language='%s', buildSystem='%s', platform='%s', archetype='%s'}",
                name, language, buildSystem, platform, archetype);
    }

    /**
     * Module specification for multi-module projects.
     */
    public static class ModuleSpec {
        private String name;
        private String path;
        private String language;
        private String buildSystem;
        private List<String> dependencies;
        private Map<String, Object> configuration;

        public ModuleSpec() {
            this.dependencies = new ArrayList<>();
            this.configuration = new HashMap<>();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getBuildSystem() {
            return buildSystem;
        }

        public void setBuildSystem(String buildSystem) {
            this.buildSystem = buildSystem;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }

        public Map<String, Object> getConfiguration() {
            return configuration;
        }

        public void setConfiguration(Map<String, Object> configuration) {
            this.configuration = configuration;
        }
    }

    /**
     * Mobile-specific settings for iOS/Android projects.
     */
    public static class MobileSettings {
        private String bundleId;
        private String packageName;
        private String minIosVersion;
        private int minAndroidSdk;
        private int targetAndroidSdk;
        private List<String> iosPermissions;
        private List<String> androidPermissions;

        public MobileSettings() {
            this.iosPermissions = new ArrayList<>();
            this.androidPermissions = new ArrayList<>();
        }

        public String getBundleId() {
            return bundleId;
        }

        public void setBundleId(String bundleId) {
            this.bundleId = bundleId;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getMinIosVersion() {
            return minIosVersion;
        }

        public void setMinIosVersion(String minIosVersion) {
            this.minIosVersion = minIosVersion;
        }

        public int getMinAndroidSdk() {
            return minAndroidSdk;
        }

        public void setMinAndroidSdk(int minAndroidSdk) {
            this.minAndroidSdk = minAndroidSdk;
        }

        public int getTargetAndroidSdk() {
            return targetAndroidSdk;
        }

        public void setTargetAndroidSdk(int targetAndroidSdk) {
            this.targetAndroidSdk = targetAndroidSdk;
        }

        public List<String> getIosPermissions() {
            return iosPermissions;
        }

        public void setIosPermissions(List<String> iosPermissions) {
            this.iosPermissions = iosPermissions;
        }

        public List<String> getAndroidPermissions() {
            return androidPermissions;
        }

        public void setAndroidPermissions(List<String> androidPermissions) {
            this.androidPermissions = androidPermissions;
        }
    }

    /**
     * Desktop-specific settings for Tauri/Electron projects.
     */
    public static class DesktopSettings {
        private String appId;
        private List<String> targetPlatforms;
        private boolean signApp;
        private boolean notarize;
        private String windowTitle;
        private int windowWidth;
        private int windowHeight;
        private boolean resizable;

        public DesktopSettings() {
            this.targetPlatforms = new ArrayList<>();
            this.windowWidth = 1200;
            this.windowHeight = 800;
            this.resizable = true;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public List<String> getTargetPlatforms() {
            return targetPlatforms;
        }

        public void setTargetPlatforms(List<String> targetPlatforms) {
            this.targetPlatforms = targetPlatforms;
        }

        public boolean isSignApp() {
            return signApp;
        }

        public void setSignApp(boolean signApp) {
            this.signApp = signApp;
        }

        public boolean isNotarize() {
            return notarize;
        }

        public void setNotarize(boolean notarize) {
            this.notarize = notarize;
        }

        public String getWindowTitle() {
            return windowTitle;
        }

        public void setWindowTitle(String windowTitle) {
            this.windowTitle = windowTitle;
        }

        public int getWindowWidth() {
            return windowWidth;
        }

        public void setWindowWidth(int windowWidth) {
            this.windowWidth = windowWidth;
        }

        public int getWindowHeight() {
            return windowHeight;
        }

        public void setWindowHeight(int windowHeight) {
            this.windowHeight = windowHeight;
        }

        public boolean isResizable() {
            return resizable;
        }

        public void setResizable(boolean resizable) {
            this.resizable = resizable;
        }
    }

    public static class Builder {
        private String name;
        private String description;
        private String version = "0.1.0";
        private String groupId;
        private String language;
        private String buildSystem;
        private String framework;
        private String platform;
        private String archetype;
        private String projectType;
        private List<ModuleSpec> modules = new ArrayList<>();
        private boolean multiModule = false;
        private String rootModuleName;
        private List<String> dependencies = new ArrayList<>();
        private List<String> devDependencies = new ArrayList<>();
        private List<String> features = new ArrayList<>();
        private Map<String, Object> configuration = new HashMap<>();
        private Map<String, String> environment = new HashMap<>();
        private Map<String, String> templateVariables = new HashMap<>();
        private MobileSettings mobileSettings;
        private DesktopSettings desktopSettings;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder language(LanguageType language) {
            this.language = language != null ? language.getIdentifier() : null;
            return this;
        }

        public Builder buildSystem(String buildSystem) {
            this.buildSystem = buildSystem;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder platform(PlatformType platform) {
            this.platform = platform != null ? platform.getIdentifier() : null;
            return this;
        }

        public Builder archetype(String archetype) {
            this.archetype = archetype;
            return this;
        }

        public Builder archetype(ProjectArchetype archetype) {
            this.archetype = archetype != null ? archetype.getIdentifier() : null;
            return this;
        }

        public Builder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder modules(List<ModuleSpec> modules) {
            this.modules = modules != null ? modules : new ArrayList<>();
            return this;
        }

        public Builder addModule(ModuleSpec module) {
            this.modules.add(module);
            return this;
        }

        public Builder multiModule(boolean multiModule) {
            this.multiModule = multiModule;
            return this;
        }

        public Builder rootModuleName(String rootModuleName) {
            this.rootModuleName = rootModuleName;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
            return this;
        }

        public Builder addDependency(String dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder devDependencies(List<String> devDependencies) {
            this.devDependencies = devDependencies != null ? devDependencies : new ArrayList<>();
            return this;
        }

        public Builder addDevDependency(String dependency) {
            this.devDependencies.add(dependency);
            return this;
        }

        public Builder features(List<String> features) {
            this.features = features != null ? features : new ArrayList<>();
            return this;
        }

        public Builder addFeature(String feature) {
            this.features.add(feature);
            return this;
        }

        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration != null ? configuration : new HashMap<>();
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment != null ? environment : new HashMap<>();
            return this;
        }

        public Builder templateVariables(Map<String, String> templateVariables) {
            this.templateVariables = templateVariables != null ? templateVariables : new HashMap<>();
            return this;
        }

        public Builder mobileSettings(MobileSettings mobileSettings) {
            this.mobileSettings = mobileSettings;
            return this;
        }

        public Builder desktopSettings(DesktopSettings desktopSettings) {
            this.desktopSettings = desktopSettings;
            return this;
        }

        public ProjectSpec build() {
            return new ProjectSpec(this);
        }
    }
}
