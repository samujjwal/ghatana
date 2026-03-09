package com.ghatana.aep.domain.pattern;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PatternRegistration {
    String registrationId;
    String tenantId;
    String patternId;
    String patternName;
    String schemaVersion;
    Map<String, String> agentHints;
    Map<String, String> metadata;
    Instant createdAt;
    Instant updatedAt;
}
