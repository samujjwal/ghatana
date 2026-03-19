package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for PublishArchitectureSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Input parameters for architecture documentation publishing
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record PublishArchitectureInput(
    @NotNull String architectureId,
    @NotNull String designDocument,
    @NotNull List<ContractSpec> contracts,
    @NotNull List<DataModelSpec> dataModels,
    @NotNull Map<String, String> diagrams,
    @NotNull String version,
    @NotNull List<String> targetChannels) {

  /**
   * Contract specification.
   *
   * @doc.type record
   * @doc.purpose API contract specification
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record ContractSpec(String name, String type, String content) {}

  /**
   * Data model specification.
   *
   * @doc.type record
   * @doc.purpose Data model specification
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record DataModelSpec(String name, String type, List<String> fields) {}
}
