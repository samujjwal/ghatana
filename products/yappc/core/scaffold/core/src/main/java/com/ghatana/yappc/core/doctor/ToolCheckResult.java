package com.ghatana.yappc.core.doctor;

/**
 * ToolCheckResult component within the YAPPC platform.
 *
 * @doc.type record
 * @doc.purpose ToolCheckResult component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record ToolCheckResult(ToolCheck check, boolean available, String output) {}
