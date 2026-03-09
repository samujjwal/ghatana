/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Agent capabilities configuration from YAML files
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentCapabilities(List<Capability> capabilities, Map<String, List<String>> mappings) {
  public record Capability(
      String id,
      String name,
      String description,
      String category,
      List<String> prerequisites,
      Map<String, Object> parameters) {}
}
