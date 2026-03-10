package com.ghatana.yappc.api.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing a YAPPC pipeline definition loaded from YAML.
 *
 * <p>Models the {@code ghatana.aep/v1 Pipeline} manifest format used in
 * {@code products/yappc/config/pipelines/*.yaml}.
 *
 * <p><b>Schema (relevant fields)</b>
 * <pre>
 * apiVersion: ghatana.aep/v1
 * kind: Pipeline
 * metadata:
 *   name: "agent-orchestration-v1"
 *   version: "1.0.0"
 *   description: "…"
 *   tags: […]
 * spec:
 *   inputs:  [{name, topic, schema, description}]
 *   outputs: [{name, topic, description}]
 *   operators: [{name, type, config, description}]
 * </pre>
 *
 * @doc.type record
 * @doc.purpose Immutable DTO for pipeline YAML manifests
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class PipelineDefinition {

  private final String name;
  private final String version;
  private final String description;
  private final List<String> tags;
  private final List<Map<String, Object>> inputs;
  private final List<Map<String, Object>> outputs;
  private final List<Map<String, Object>> operators;

  /**
   * Construct a PipelineDefinition from parsed YAML data.
   *
   * @param name        unique pipeline name (non-null)
   * @param version     semantic version string
   * @param description human-readable description
   * @param tags        classification tags
   * @param inputs      input port definitions
   * @param outputs     output port definitions
   * @param operators   operator node definitions
   */
  public PipelineDefinition(
      String name,
      String version,
      String description,
      List<String> tags,
      List<Map<String, Object>> inputs,
      List<Map<String, Object>> outputs,
      List<Map<String, Object>> operators) {
    this.name = Objects.requireNonNull(name, "name");
    this.version = version != null ? version : "1.0.0";
    this.description = description != null ? description : "";
    this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.emptyList();
    this.inputs = inputs != null ? Collections.unmodifiableList(inputs) : Collections.emptyList();
    this.outputs = outputs != null ? Collections.unmodifiableList(outputs) : Collections.emptyList();
    this.operators = operators != null ? Collections.unmodifiableList(operators) : Collections.emptyList();
  }

  /** @return unique pipeline name */
  public String name() {
    return name;
  }

  /** @return semantic version string */
  public String version() {
    return version;
  }

  /** @return human-readable description */
  public String description() {
    return description;
  }

  /** @return read-only list of classification tags */
  public List<String> tags() {
    return tags;
  }

  /** @return read-only list of input port definitions */
  public List<Map<String, Object>> inputs() {
    return inputs;
  }

  /** @return read-only list of output port definitions */
  public List<Map<String, Object>> outputs() {
    return outputs;
  }

  /** @return read-only list of operator node definitions */
  public List<Map<String, Object>> operators() {
    return operators;
  }

  @Override
  public String toString() {
    return "PipelineDefinition{name='" + name + "', version='" + version
        + "', operators=" + operators.size() + '}';
  }
}
