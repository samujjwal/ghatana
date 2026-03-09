package com.ghatana.yappc.sdlc.agent.specialists;

import java.time.Instant;
import java.util.Map;

/**
 * Output from Ops Publish Specialist.
 *
 * @doc.type record
 * @doc.purpose Result of publishing release notes
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PublishOutput(
    String releaseId,
    String version,
    Map<String, String> publishedUrls,
    Instant publishedAt,
    boolean success,
    String message) {}
