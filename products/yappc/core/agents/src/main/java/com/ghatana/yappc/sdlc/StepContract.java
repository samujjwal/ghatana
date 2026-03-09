package com.ghatana.yappc.sdlc;

import java.util.List;
import java.util.Map;

/**
 * Step contract defining input/output schemas and required capabilities.
 *
 * @param name step name
 * @param inputSchemaRef JSON schema reference for input validation
 * @param outputSchemaRef JSON schema reference for output validation
 * @param requiredCapabilities list of required platform capabilities (DATA_CLOUD, EVENTCLOUD, etc.)
 * @param metadata additional contract metadata
 * @doc.type record
 * @doc.purpose Contract definition for workflow step schemas
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StepContract(
    String name,
    String inputSchemaRef,
    String outputSchemaRef,
    List<String> requiredCapabilities,
    Map<String, String> metadata) {}
