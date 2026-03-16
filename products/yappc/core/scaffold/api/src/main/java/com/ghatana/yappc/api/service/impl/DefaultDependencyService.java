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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    // Minimum recommended versions by artifactId (security/stability baseline)
    private static final Map<String, String> MIN_RECOMMENDED = Map.ofEntries(
        Map.entry("slf4j-api",           "2.0.0"),
        Map.entry("logback-classic",     "1.4.0"),
        Map.entry("log4j-core",          "2.17.1"),
        Map.entry("log4j-api",           "2.17.1"),
        Map.entry("jackson-databind",    "2.14.0"),
        Map.entry("spring-boot",         "3.0.0"),
        Map.entry("spring-core",         "6.0.0"),
        Map.entry("junit-jupiter",       "5.9.0"),
        Map.entry("junit-jupiter-api",   "5.9.0"),
        Map.entry("mockito-core",        "5.0.0"),
        Map.entry("guava",               "32.0.0-jre"),
        Map.entry("commons-text",        "1.10.0"),
        Map.entry("snakeyaml",           "2.0"),
        Map.entry("netty-all",           "4.1.94.Final"),
        Map.entry("typescript",          "5.0.0"),
        Map.entry("react",               "18.0.0"),
        Map.entry("react-dom",           "18.0.0"),
        Map.entry("vite",                "4.0.0"),
        Map.entry("axios",               "1.4.0"),
        Map.entry("express",             "4.18.0"),
        Map.entry("fastify",             "4.0.0")
    );

    @Override
    public List<DependencyInfo> findOutdated(Path projectPath) {
        List<DependencyInfo> deps = extractDependenciesFromProject(projectPath);
        return deps.stream()
            .filter(dep -> isOutdated(dep.artifactId(), dep.version()))
            .collect(Collectors.toList());
    }

    @Override
    public List<DependencyUpgrade> suggestUpgrades(Path projectPath) {
        return findOutdated(projectPath).stream()
            .map(dep -> {
                String recommended = MIN_RECOMMENDED.get(dep.artifactId());
                return new DependencyUpgrade(
                    dep.getCoordinates(),
                    dep.version(),
                    recommended,
                    "Minimum recommended version for stability and security",
                    isMajorBump(dep.version(), recommended)
                );
            })
            .collect(Collectors.toList());
    }

    /**
     * Compare semver strings; returns true when {@code current} is strictly below
     * the {@code minimum} version known for this artifact.
     */
    private boolean isOutdated(String artifactId, String current) {
        String minimum = MIN_RECOMMENDED.get(artifactId);
        if (minimum == null || current == null || current.isBlank()) return false;
        return compareSemver(current, minimum) < 0;
    }

    /**
     * Coarse semver comparison: splits on {@code [-+]} then compares numeric
     * dot-segments lexicographically by integer value.
     * Returns negative if {@code a < b}, zero if equal, positive if {@code a > b}.
     */
    private int compareSemver(String a, String b) {
        String[] partsA = a.split("[-+]")[0].split("\\.");
        String[] partsB = b.split("[-+]")[0].split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int va = i < partsA.length ? parseSegment(partsA[i]) : 0;
            int vb = i < partsB.length ? parseSegment(partsB[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private int parseSegment(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private boolean isMajorBump(String current, String target) {
        if (current == null || target == null) return false;
        int curMajor = parseSegment(current.split("[.\\-+]")[0]);
        int tgtMajor = parseSegment(target.split("[.\\-+]")[0]);
        return tgtMajor > curMajor;
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
        List<DependencyInfo> deps = new ArrayList<>();
        if (projectPath == null || !Files.exists(projectPath)) return deps;
        try (Stream<Path> walk = Files.walk(projectPath, 4)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString();
                try {
                    String content = Files.readString(file);
                    if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                        deps.addAll(parseGradle(content));
                    } else if (name.equals("pom.xml")) {
                        deps.addAll(parseMaven(content));
                    } else if (name.equals("package.json")) {
                        deps.addAll(parseNpm(content));
                    } else if (name.equals("Cargo.toml")) {
                        deps.addAll(parseCargo(content));
                    } else if (name.equals("go.mod")) {
                        deps.addAll(parseGoMod(content));
                    }
                } catch (Exception ignored) {
                    LOG.debug("Could not parse dependency file: {}", file);
                }
            });
        } catch (Exception e) {
            LOG.warn("Could not walk project path: {}", projectPath, e);
        }
        return deps;
    }

    // ── Build-file parsers ─────────────────────────────────────────────────────

    /** Extracts Gradle dependency declarations: implementation("g:a:v") or 'g:a:v' */
    private List<DependencyInfo> parseGradle(String content) {
        List<DependencyInfo> result = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(?:implementation|api|compile|testImplementation|runtimeOnly)\\s*[\\(\"']([\\w.\\-]+):([\\w.\\-]+):([\\w.\\-]+)");
        Matcher m = p.matcher(content);
        while (m.find()) {
            result.add(DependencyInfo.gradle(m.group(1), m.group(2), m.group(3)));
        }
        return result;
    }

    /** Extracts Maven <dependency> blocks. */
    private List<DependencyInfo> parseMaven(String content) {
        List<DependencyInfo> result = new ArrayList<>();
        Pattern block = Pattern.compile("<dependency>([\\s\\S]*?)</dependency>");
        Pattern gId  = Pattern.compile("<groupId>([^<]+)</groupId>");
        Pattern aId  = Pattern.compile("<artifactId>([^<]+)</artifactId>");
        Pattern ver  = Pattern.compile("<version>([^<]+)</version>");
        Matcher bm = block.matcher(content);
        while (bm.find()) {
            String blk = bm.group(1);
            Matcher gm = gId.matcher(blk), am = aId.matcher(blk), vm = ver.matcher(blk);
            if (gm.find() && am.find() && vm.find()) {
                result.add(DependencyInfo.maven(gm.group(1).trim(), am.group(1).trim(), vm.group(1).trim()));
            }
        }
        return result;
    }

    /** Extracts NPM dependencies from package.json JSON text. */
    private List<DependencyInfo> parseNpm(String content) {
        List<DependencyInfo> result = new ArrayList<>();
        Pattern p = Pattern.compile("\"([\\w@/][\\w./\\-@]*)\"\\s*:\\s*\"[~^]?([\\d][\\w.\\-]*)\"");
        Matcher m = p.matcher(content);
        while (m.find()) {
            String pkg = m.group(1);
            // Skip keys that are not package names (e.g. "version", "name", "description")
            if (!pkg.startsWith("@") && pkg.chars().noneMatch(c -> c == '.') && pkg.length() < 4) continue;
            result.add(DependencyInfo.npm(pkg, m.group(2)));
        }
        return result;
    }

    /** Extracts Cargo.toml [dependencies] crate versions. */
    private List<DependencyInfo> parseCargo(String content) {
        List<DependencyInfo> result = new ArrayList<>();
        Pattern p = Pattern.compile("^([\\w\\-]+)\\s*=\\s*\"([\\d][\\w.\\-]*)\"\s*$", Pattern.MULTILINE);
        Matcher m = p.matcher(content);
        while (m.find()) {
            result.add(DependencyInfo.cargo(m.group(1), m.group(2)));
        }
        return result;
    }

    /** Extracts go.mod require directives. */
    private List<DependencyInfo> parseGoMod(String content) {
        List<DependencyInfo> result = new ArrayList<>();
        Pattern p = Pattern.compile("require\\s+([\\S]+)\\s+(v[\\d][\\w.\\-]*)");
        Matcher m = p.matcher(content);
        while (m.find()) {
            result.add(DependencyInfo.goMod(m.group(1), m.group(2)));
        }
        return result;
    }

    private record DependencyVersion(String source, String version) {}
}
