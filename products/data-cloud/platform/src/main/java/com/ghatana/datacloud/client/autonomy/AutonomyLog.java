package com.ghatana.datacloud.client.autonomy;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Record of an autonomy decision or action.
 *
 * @doc.type record
 * @doc.purpose Immutable log entry for autonomy actions
 * @doc.layer core
 */
@Value
@Builder
public class AutonomyLog {
    String id;
    String actionType;
    String tenantId;
    AutonomyLevel level;
    String decision; // "ALLOWED", "BLOCKED", "ADVISORY"
    double confidence;
    Map<String, Object> context;
    Instant timestamp;
}

