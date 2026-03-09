/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.scaffold;

import java.util.List;
import java.util.Map;

/**
 * Represents a feature pack that can be applied to a project.
  *
 * @doc.type record
 * @doc.purpose feature pack
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FeaturePack(
    String id,
    String name,
    String description,
    List<String> compatibleProjectTypes,
    List<String> dependencies,
    Map<String, Object> configurationSchema
) {}
