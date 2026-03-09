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
import com.ghatana.yappc.api.model.*;
import com.ghatana.yappc.api.service.DependencyService;
import com.ghatana.yappc.api.service.PackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of DependencyService.
 *
 * @doc.type class
 * @doc.purpose Dependency analysis implementation
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DefaultDependencyService implements DependencyService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDependencyService.class);

    private final YappcConfig config;
    private final PackService packService;

    public DefaultDependencyService(YappcConfig config, PackService packService) {
        this.config = config;
        this.packService = packService;
    }

    @Override
    public DependencyAnalysis analyzePack(String packName) {
        LOG.debug("Analyzing dependencies for pack: {}", packName);
        
        Optional<PackInfo> packOpt = packService.get(packName);
        if (packOpt.isEmpty()) {
            return DependencyAnalysis.ofPack(packName, List.of());
        }

        PackInfo pack = packOpt.get();
        List<DependencyInfo> dependencies = extractDependenciesFromPack(pack);

        return new DependencyAnalysis(
                packName,
                DependencyAnalysis.AnalysisType.PACK,
                dependencies,
                List.of(),
                List.of(),
                dependencies.size(),
                false
        );
    }

    @Override
    public DependencyAnalysis analyzeProject(Path projectPath) {
        LOG.debug("Analyzing dependencies for project: {}", projectPath);
        
        List<DependencyInfo> dependencies = extractDependenciesFromProject(projectPath);
        
        return new DependencyAnalysis(
                projectPath.getFileName().toString(),
                DependencyAnalysis.AnalysisType.PROJECT,
                dependencies,
                List.of(),
                List.of(),
                dependencies.size(),
                false
        );
    }

    @Override
    public List<DependencyInfo> getPackDependencies(String packName) {
        return analyzePack(packName).directDependencies();
    }

    @Override
    public List<DependencyInfo> getProjectDependencies(Path projectPath) {
        return analyzeProject(projectPath).directDependencies();
    }

    @Override
    public List<ConflictInfo> checkConflicts(List<String> packNames) {
        Map<String, List<DependencyVersion>> dependencyVersions = new HashMap<>();

        for (String packName : packNames) {
            List<DependencyInfo> deps = getPackDependencies(packName);
            for (DependencyInfo dep : deps) {
                String key = dep.artifactId();
                dependencyVersions.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new DependencyVersion(packName, dep.version()));
            }
        }

        List<ConflictInfo> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<DependencyVersion>> entry : dependencyVersions.entrySet()) {
            List<DependencyVersion> versions = entry.getValue();
            if (versions.size() > 1) {
                Set<String> uniqueVersions = versions.stream()
                        .map(DependencyVersion::version)
                        .collect(Collectors.toSet());
                
                if (uniqueVersions.size() > 1) {
                    DependencyVersion v1 = versions.get(0);
                    DependencyVersion v2 = versions.get(1);
                    conflicts.add(ConflictInfo.versionMismatch(
                            entry.getKey(),
                            v1.version(), v1.source(),
                            v2.version(), v2.source()
                    ));
                }
            }
        }

        return conflicts;
    }

    @Override
    public List<ConflictInfo> checkAddConflicts(Path projectPath, String packName) {
        List<DependencyInfo> projectDeps = getProjectDependencies(projectPath);
        List<DependencyInfo> packDeps = getPackDependencies(packName);

        List<ConflictInfo> conflicts = new ArrayList<>();

        Map<String, String> projectVersions = projectDeps.stream()
                .collect(Collectors.toMap(DependencyInfo::artifactId, DependencyInfo::version));

        for (DependencyInfo packDep : packDeps) {
            String existingVersion = projectVersions.get(packDep.artifactId());
            if (existingVersion != null && !existingVersion.equals(packDep.version())) {
                conflicts.add(ConflictInfo.versionMismatch(
                        packDep.artifactId(),
                        existingVersion, "project",
                        packDep.version(), packName
                ));
            }
        }

        return conflicts;
    }

    @Override
    public List<DependencyInfo> getTransitiveDependencies(String packName) {
        // For now, return direct dependencies
        // In a full implementation, this would resolve transitive dependencies
        return getPackDependencies(packName);
    }

    @Override
    public List<DependencyInfo> findOutdated(Path projectPath) {
        // Placeholder - would check against a dependency version database
        return List.of();
    }

    @Override
    public List<DependencyUpgrade> suggestUpgrades(Path projectPath) {
        // Placeholder - would analyze and suggest upgrades
        return List.of();
    }

    private List<DependencyInfo> extractDependenciesFromPack(PackInfo pack) {
        List<DependencyInfo> dependencies = new ArrayList<>();

        // Parse dependencies from pack based on build system
        String buildSystem = pack.getBuildSystem();
        String language = pack.getLanguage();

        // Add standard dependencies based on pack type
        if ("java".equalsIgnoreCase(language)) {
            if ("gradle".equalsIgnoreCase(buildSystem)) {
                addStandardJavaDependencies(dependencies, DependencyInfo.DependencyType.GRADLE);
            } else if ("maven".equalsIgnoreCase(buildSystem)) {
                addStandardJavaDependencies(dependencies, DependencyInfo.DependencyType.MAVEN);
            }
        } else if ("typescript".equalsIgnoreCase(language)) {
            addStandardTypeScriptDependencies(dependencies);
        } else if ("rust".equalsIgnoreCase(language)) {
            addStandardRustDependencies(dependencies);
        } else if ("go".equalsIgnoreCase(language)) {
            addStandardGoDependencies(dependencies);
        }

        return dependencies;
    }

    private void addStandardJavaDependencies(List<DependencyInfo> deps, DependencyInfo.DependencyType type) {
        if (type == DependencyInfo.DependencyType.GRADLE) {
            deps.add(DependencyInfo.gradle("org.slf4j", "slf4j-api", "2.0.9"));
            deps.add(DependencyInfo.gradle("ch.qos.logback", "logback-classic", "1.4.11"));
        } else {
            deps.add(DependencyInfo.maven("org.slf4j", "slf4j-api", "2.0.9"));
            deps.add(DependencyInfo.maven("ch.qos.logback", "logback-classic", "1.4.11"));
        }
    }

    private void addStandardTypeScriptDependencies(List<DependencyInfo> deps) {
        deps.add(DependencyInfo.npm("typescript", "5.3.0"));
        deps.add(DependencyInfo.npm("@types/node", "20.0.0"));
    }

    private void addStandardRustDependencies(List<DependencyInfo> deps) {
        deps.add(DependencyInfo.cargo("serde", "1.0"));
        deps.add(DependencyInfo.cargo("tokio", "1.0"));
    }

    private void addStandardGoDependencies(List<DependencyInfo> deps) {
        deps.add(DependencyInfo.goMod("github.com/sirupsen/logrus", "v1.9.0"));
    }

    private List<DependencyInfo> extractDependenciesFromProject(Path projectPath) {
        // Would parse build files (build.gradle, pom.xml, package.json, Cargo.toml, go.mod)
        return List.of();
    }

    private record DependencyVersion(String source, String version) {}
}
