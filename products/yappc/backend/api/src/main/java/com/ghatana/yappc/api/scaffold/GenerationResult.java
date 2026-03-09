package com.ghatana.yappc.api.scaffold;

import java.util.List;

/**
 * GenerationResult.
 *
 * @doc.type record
 * @doc.purpose generation result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GenerationResult(
    String projectPath,
    List<String> filesGenerated,
    boolean success
) {}
