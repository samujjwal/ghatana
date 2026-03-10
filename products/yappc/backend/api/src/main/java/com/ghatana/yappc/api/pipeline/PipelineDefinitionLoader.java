package com.ghatana.yappc.api.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Boot-time loader that reads all {@code *.yaml} pipeline manifests from a directory
 * and indexes them by name for fast lookup.
 *
 * <p>The pipeline directory is resolved in priority order:
 * <ol>
 *   <li>Environment variable {@code YAPPC_PIPELINE_DIR}</li>
 *   <li>Classpath resource {@code config/pipelines/} (via working directory)</li>
 *   <li>Bundled product default {@code products/yappc/config/pipelines/}</li>
 * </ol>
 *
 * <p>Pipelines that fail to parse are skipped with a WARN log rather than aborting boot.
 *
 * <p><b>Thread Safety:</b> After construction the registry is immutable. Safe for
 * concurrent reads.
 *
 * @see PipelineDefinition
 * @doc.type class
 * @doc.purpose Boot-time YAML pipeline manifest loader and registry
 * @doc.layer product
 * @doc.pattern Registry, Loader
 */
public final class PipelineDefinitionLoader {

  private static final Logger logger = LoggerFactory.getLogger(PipelineDefinitionLoader.class);

  /** Candidate directories to scan, in priority order. */
  private static final List<String> CANDIDATE_DIRS = List.of(
      System.getenv("YAPPC_PIPELINE_DIR") != null
          ? System.getenv("YAPPC_PIPELINE_DIR") : "",
      "config/pipelines",
      "products/yappc/config/pipelines"
  );

  private final Map<String, PipelineDefinition> registry; // name → definition
  private final ObjectMapper yaml;

  /**
   * Create the loader, immediately scanning and parsing all YAML manifests.
   *
   * @param jsonMapper base ObjectMapper used for configuration (not YAML — a new
   *                   {@code YAMLFactory}-backed mapper is created internally)
   */
  public PipelineDefinitionLoader(ObjectMapper jsonMapper) {
    Objects.requireNonNull(jsonMapper, "jsonMapper");
    this.yaml = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    Map<String, PipelineDefinition> loaded = new LinkedHashMap<>();
    Path pipelineDir = resolveDirectory();
    if (pipelineDir == null) {
      logger.warn("PipelineDefinitionLoader: no pipeline directory found — "
          + "set YAPPC_PIPELINE_DIR or place YAMLs in config/pipelines/. "
          + "Zero pipelines loaded.");
      this.registry = Collections.emptyMap();
      return;
    }

    logger.info("PipelineDefinitionLoader: scanning {}", pipelineDir.toAbsolutePath());
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(pipelineDir, "*.yaml")) {
      for (Path file : stream) {
        try {
          PipelineDefinition def = parse(file);
          if (loaded.containsKey(def.name())) {
            logger.warn("Pipeline '{}' in {} overrides earlier definition from another file",
                def.name(), file.getFileName());
          }
          loaded.put(def.name(), def);
          logger.info("  ✓ Loaded pipeline: {} v{} ({} operators)",
              def.name(), def.version(), def.operators().size());
        } catch (Exception e) {
          logger.warn("  ✗ Failed to parse pipeline YAML {}: {}", file.getFileName(), e.getMessage());
        }
      }
    } catch (IOException e) {
      logger.error("PipelineDefinitionLoader: failed to list pipeline directory {}: {}",
          pipelineDir, e.getMessage());
    }

    this.registry = Collections.unmodifiableMap(loaded);
    logger.info("PipelineDefinitionLoader: {} pipeline(s) registered", registry.size());
  }

  /**
   * Look up a pipeline definition by name.
   *
   * @param name pipeline name as declared in the YAML {@code metadata.name} field
   * @return the pipeline definition, or {@code null} if not found
   */
  public PipelineDefinition get(String name) {
    return registry.get(name);
  }

  /**
   * Return all loaded pipeline definitions indexed by name.
   *
   * @return unmodifiable view of the registry
   */
  public Map<String, PipelineDefinition> all() {
    return registry;
  }

  /**
   * Return the number of loaded pipelines.
   *
   * @return pipeline count
   */
  public int size() {
    return registry.size();
  }

  // ---- internal helpers ----

  /** Resolve the first readable pipeline directory from CANDIDATE_DIRS. */
  private static Path resolveDirectory() {
    for (String candidate : CANDIDATE_DIRS) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      Path p = Paths.get(candidate);
      if (Files.isDirectory(p) && Files.isReadable(p)) {
        return p;
      }
    }
    return null;
  }

  /**
   * Parse a single YAML pipeline manifest file into a {@link PipelineDefinition}.
   *
   * @param file YAML file to parse
   * @return the parsed pipeline definition
   * @throws IOException if the file cannot be read or parsed
   */
  @SuppressWarnings("unchecked")
  private PipelineDefinition parse(Path file) throws IOException {
    Map<String, Object> doc = yaml.readValue(file.toFile(), Map.class);

    Map<String, Object> metadata = mapOrEmpty(doc, "metadata");
    Map<String, Object> spec = mapOrEmpty(doc, "spec");

    String name = stringOrNull(metadata, "name");
    if (name == null || name.isBlank()) {
      throw new IOException("Pipeline YAML is missing metadata.name");
    }

    String version = stringOrNull(metadata, "version");
    String description = stringOrNull(metadata, "description");
    List<String> tags = (List<String>) metadata.getOrDefault("tags", Collections.emptyList());
    List<Map<String, Object>> inputs = listOrEmpty(spec, "inputs");
    List<Map<String, Object>> outputs = listOrEmpty(spec, "outputs");
    List<Map<String, Object>> operators = listOrEmpty(spec, "operators");

    return new PipelineDefinition(name, version, description, tags, inputs, outputs, operators);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapOrEmpty(Map<String, Object> parent, String key) {
    Object v = parent.get(key);
    return (v instanceof Map<?, ?>) ? (Map<String, Object>) v : Collections.emptyMap();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOrEmpty(Map<String, Object> parent, String key) {
    Object v = parent.get(key);
    if (v instanceof List<?>) {
      List<?> raw = (List<?>) v;
      List<Map<String, Object>> result = new ArrayList<>(raw.size());
      for (Object item : raw) {
        if (item instanceof Map<?, ?>) {
          result.add((Map<String, Object>) item);
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  private static String stringOrNull(Map<String, Object> m, String key) {
    Object v = m.get(key);
    return v instanceof String ? (String) v : null;
  }
}
