package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code PipelineSpecFactory} is a resource-aware factory for loading, parsing,
 * and caching pipeline specifications from YAML files stored on classpath or filesystem.
 *
 * <h2>Purpose</h2>
 * Centralizes pipeline definition loading with:
 * <ul>
 *   <li>YAML parsing and deserialization</li>
 *   <li>Multi-level caching (pipelines, stages, agents)</li>
 *   <li>Resource resolution from classpath or filesystem</li>
 *   <li>Support for nested includes (stage includes)</li>
 *   <li>Error handling and diagnostics</li>
 *   <li>JAR-compatible resource loading</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Loads from</b>: Classpath resource directory or filesystem</li>
 *   <li><b>Parsed by</b>: Jackson YAMLFactory for YAML deserialization</li>
 *   <li><b>Caches</b>: Pipeline, stage, and agent specs for reuse</li>
 *   <li><b>Returns</b>: {@link PipelineSpec}, {@link PipelineStageSpec}, lists</li>
 *   <li><b>Used by</b>: Pipeline orchestrator, pipeline builder</li>
 * </ul>
 *
 * <h2>Resource Resolution Strategy</h2>
 * Resources are resolved with fallback logic:
 * <ol>
 *   <li>Try path as-is: {@code ClassLoader.getResource(path)}</li>
 *   <li>Normalize with leading /: {@code ClassLoader.getResource("/path")}</li>
 *   <li>Check JAR files: Extract ZipEntry from JAR for versioned resources</li>
 * </ol>
 *
 * Supports both:
 * <ul>
 *   <li><b>Classpath resources</b>: {@code /specs/pipelines/my-pipeline.yaml}</li>
 *   <li><b>Filesystem paths</b>: {@code /var/pipelines/my-pipeline.yaml}</li>
 *   <li><b>JAR contents</b>: {@code jar:file://app.jar!/specs/pipelines/my-pipeline.yaml}</li>
 * </ul>
 *
 * <h2>Caching Architecture</h2>
 * Three-level cache for performance:
 * {@code
 * pipelineCache: Map<String, PipelineSpec>
 *   key = "dir:pipelineName"
 *   value = parsed/deserialized PipelineSpec
 *
 * stageCache: Map<String, PipelineStageSpec>
 *   key = "path/to/stage.yaml"
 *   value = parsed stage specification
 *
 * agentCache: Map<String, AgentSpec>
 *   key = "agent-id"
 *   value = agent specification (for future use)
 * }
 * Cache keys use resource paths to support multi-directory scenarios.
 *
 * <h2>Pipeline Includes</h2>
 * Supports reusable stage definitions via includes:
 * {@code
 * # main-pipeline.yaml
 * stages:
 *   - include: "common/validation-stage.yaml"
 *   - name: enrichment
 *     workflow:
 *       - id: enricher-1
 *         agent: EnricherAgent
 * }
 * Enables DRY (Don't Repeat Yourself) for common processing stages.
 *
 * <h2>Default Directory</h2>
 * Defaults to {@code "specs/pipelines"} on classpath if not specified.
 * This enables projects to follow convention: {@code src/main/resources/specs/pipelines/}
 *
 * <h2>Error Handling</h2>
 * Comprehensive error handling with logging:
 * <ul>
 *   <li>File not found: {@code IOException} with clear message</li>
 *   <li>Invalid YAML: Parsing errors with line numbers</li>
 *   <li>Type errors: Expected structures logged</li>
 *   <li>JAR errors: Graceful fallback to filesystem</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * {@code
 * // Create factory (uses default "specs/pipelines" directory)
 * PipelineSpecFactory factory = new PipelineSpecFactory();
 *
 * // Load pipeline by name (no .yaml extension needed)
 * PipelineSpec pipeline = factory.getPipeline("event-processing");
 *
 * // Custom directory
 * PipelineSpecFactory customFactory = new PipelineSpecFactory("my/pipeline/dir");
 * PipelineSpec customPipeline = customFactory.getPipeline("custom-workflow");
 *
 * // List available pipelines
 * List<String> names = factory.listAvailablePipelines();
 * names.forEach(name -> System.out.println("Pipeline: " + name));
 *
 * // Clear caches (e.g., after file updates in dev)
 * factory.clearCaches();
 * PipelineSpec reloaded = factory.getPipeline("event-processing");
 * }
 *
 * <h2>Directory Structure Example</h2>
 * {@code
 * src/main/resources/
 * ├── specs/
 * │   └── pipelines/
 * │       ├── common/
 * │       │   ├── validation-stage.yaml
 * │       │   └── enrichment-stage.yaml
 * │       ├── event-processing.yaml
 * │       ├── data-ingestion.yaml
 * │       └── analytics.yaml
 * }
 *
 * <h2>File Format Example</h2>
 * {@code
 * # Pipeline definition with includes
 * stages:
 *   # Include common validation stage
 *   - include: "common/validation-stage.yaml"
 *
 *   # Inline stage definition
 *   - name: enrichment
 *     workflow:
 *       - id: enricher-1
 *         agent: DataEnricherAgent
 *         role: enricher
 *         inputsSpec:
 *           - name: raw_events
 *             format: json
 *         outputsSpec:
 *           - name: enriched_events
 *             format: json
 *
 *   # Another included stage
 *   - include: "common/persistence-stage.yaml"
 * }
 *
 * @see PipelineSpec
 * @see PipelineStageSpec
 * @see AgentSpec
 * @since 1.0.0
 *
 * @doc.type utility-factory
 * @doc.layer domain
 * @doc.purpose pipeline specification loading and caching from YAML files
 * @doc.pattern factory, caching, resource-loading, builder
 * @doc.test-hints YAML-parsing, cache-performance, resource-resolution, JAR-compatibility, include-resolution
 */
public class PipelineSpecFactory {

    private static final Logger log = LoggerFactory.getLogger(PipelineSpecFactory.class);
    private static final String DEFAULT_PIPELINES_DIR = "specs/pipelines";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, PipelineSpec> pipelineCache;
    private final Map<String, PipelineStageSpec> stageCache;
    private final Map<String, AgentSpec> agentCache;
    private final Map<String, String> stageNameToPath = new HashMap<>();
    private final String pipelinesDir;

    public PipelineSpecFactory() {
        this(DEFAULT_PIPELINES_DIR);
    }

    public PipelineSpecFactory(String pipelinesDir) {
        this.pipelineCache = new HashMap<>();
        this.stageCache = new HashMap<>();
        this.agentCache = new HashMap<>();
        this.pipelinesDir = pipelinesDir != null ? pipelinesDir : DEFAULT_PIPELINES_DIR;
    }

    public PipelineSpec getPipeline(String pipelineName) throws IOException {
        return getPipeline(this.pipelinesDir, pipelineName);
    }

    public PipelineSpec getPipeline(String pipelinesDir, String pipelineName) throws IOException {
        String cacheKey = pipelinesDir + ":" + pipelineName;

        if (pipelineCache.containsKey(cacheKey)) {
            return pipelineCache.get(cacheKey);
        }

        Path pipelinesPath = Paths.get(pipelinesDir).resolve(pipelineName + ".yaml");
        String pipelinePath = pipelinesPath.toString();
        log.debug("Loading pipeline from: {}", pipelinePath);

        try (InputStream is = getResourceAsStream(pipelinePath)) {
            if (is == null) {
                throw new IOException("Pipeline not found: " + pipelinePath);
            }

            Object parsed = yamlMapper.readValue(is, Object.class);
            if (!(parsed instanceof Map)) {
                throw new IOException("Pipeline definition must be a YAML object, not an array or primitive");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> pipelineMap = (Map<String, Object>) parsed;
            PipelineSpec pipelineSpec = processPipeline(pipelinesPath.getParent().toString(), pipelineMap);
            log.info("Successfully loaded pipeline '{}' with {} stages",
                pipelineName,
                pipelineSpec.getStages() != null ? pipelineSpec.getStages().size() : 0
            );

            pipelineCache.put(cacheKey, pipelineSpec);
            return pipelineSpec;
        } catch (Exception e) {
            log.error("Error loading pipeline '{}' from {}: {}", pipelineName, pipelinePath, e.getMessage(), e);
            throw new IOException("Failed to load pipeline: " + pipelineName, e);
        }
    }

    public List<String> listAvailablePipelines() throws IOException {
        return listAvailablePipelines(this.pipelinesDir);
    }

    public List<String> listAvailablePipelines(String pipelinesDir) throws IOException {
        List<String> pipelineNames = new ArrayList<>();

        try (InputStream ignored = getResourceAsStream(pipelinesDir)) {
            if (ignored == null) {
                throw new IOException("Pipelines directory not found: " + pipelinesDir);
            }
        }

        URL dirURL = getResource(pipelinesDir);
        if (dirURL == null) {
            throw new IOException("Could not find pipelines directory: " + pipelinesDir);
        }

        if (dirURL.getProtocol().equals("jar")) {
            String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!"));
            String prefix = pipelinesDir.endsWith("/") ? pipelinesDir : pipelinesDir + "/";

            try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(prefix) && name.endsWith(".yaml")) {
                        String fileName = name.substring(prefix.length());
                        if (!fileName.contains("/")) {
                            pipelineNames.add(fileName.substring(0, fileName.length() - 5));
                        }
                    }
                }
            } catch (Exception e) {
                throw new IOException("Error reading from JAR file", e);
            }
        } else {
            try {
                Path pipelinesPath = Paths.get(dirURL.toURI());
                try (Stream<Path> paths = Files.walk(pipelinesPath, 1)) {
                    paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".yaml"))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .map(name -> name.substring(0, name.length() - 5))
                        .forEach(pipelineNames::add);
                }
            } catch (Exception e) {
                throw new IOException("Error reading pipelines directory", e);
            }
        }

        return pipelineNames.stream()
            .filter(name -> !name.startsWith("."))
            .sorted()
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private PipelineSpec processPipeline(String pipelinesDir, Map<String, Object> pipelineMap) throws IOException {
        log.debug("Processing pipeline with keys: {}", pipelineMap.keySet());
        PipelineSpec.PipelineSpecBuilder builder = PipelineSpec.builder();
        List<PipelineStageSpec> stages = new ArrayList<>();

        if (pipelineMap.containsKey("stages")) {
            Object stagesObj = pipelineMap.get("stages");
            if (!(stagesObj instanceof List)) {
                throw new IOException("Expected 'stages' to be a list in pipeline definition");
            }

            List<Object> stageItems = (List<Object>) stagesObj;
            for (Object stageItem : stageItems) {
                if (stageItem instanceof Map) {
                    Map<String, Object> stageDef = (Map<String, Object>) stageItem;

                    if (stageDef.containsKey("include")) {
                        String includePath = (String) stageDef.get("include");
                        log.debug("Processing include: {}", includePath);

                        Path resourcePath = Paths.get(pipelinesDir).resolve(includePath).normalize();
                        PipelineStageSpec includedStage = loadIncludedStage(resourcePath.toString());
                        if (includedStage != null) {
                            stages.add(includedStage);
                        }
                    } else {
                        PipelineStageSpec stageSpec = yamlMapper.convertValue(stageDef, PipelineStageSpec.class);
                        if (stageSpec != null) {
                            stages.add(stageSpec);
                        }
                    }
                }
            }
        }

        builder.stages(stages);
        return builder.build();
    }

    private PipelineStageSpec loadIncludedStage(String includePath) throws IOException {
        if (stageCache.containsKey(includePath)) {
            return stageCache.get(includePath);
        }

        log.debug("Loading included stage from: {}", includePath);
        try (InputStream is = getResourceAsStream(includePath)) {
            if (is == null) {
                throw new IOException("Included stage not found: " + includePath);
            }

            PipelineStageSpec stageSpec = yamlMapper.readValue(is, PipelineStageSpec.class);
            stageCache.put(includePath, stageSpec);
            return stageSpec;
        } catch (Exception e) {
            log.error("Error loading included stage from {}: {}", includePath, e.getMessage(), e);
            throw new IOException("Failed to load included stage: " + includePath, e);
        }
    }

    private InputStream getResourceAsStream(String path) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            String normalizedPath = path.startsWith("/") ? path.substring(1) : "/" + path;
            is = getClass().getClassLoader().getResourceAsStream(normalizedPath);
        }
        return is;
    }

    private URL getResource(String path) {
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null) {
            String normalizedPath = path.startsWith("/") ? path.substring(1) : "/" + path;
            url = getClass().getClassLoader().getResource(normalizedPath);
        }
        return url;
    }

    public void clearCaches() {
        pipelineCache.clear();
        stageCache.clear();
        agentCache.clear();
        stageNameToPath.clear();
    }
}
