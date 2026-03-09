package com.ghatana.aep.domain.pattern;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PatternVersion {
    String patternId;
    String version;
    Instant createdAt;
    Instant updatedAt;
}
