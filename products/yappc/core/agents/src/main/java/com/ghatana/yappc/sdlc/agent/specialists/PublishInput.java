package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;

/**
 * Input for Ops Publish Specialist.
 *
 * @doc.type record
 * @doc.purpose Input for publishing release notes and announcements
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PublishInput(
    String releaseId,
    String version,
    List<String> artifacts,
    String changelogUrl,
    List<String> channels) {}
