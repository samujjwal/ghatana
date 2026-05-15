/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.obsolescence;

import com.ghatana.agent.obsolescence.ObsolescenceSignal;
import com.ghatana.agent.obsolescence.ObsolescenceSignalType;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper for converting between {@link ObsolescenceSignal} and data maps for EntityRepository persistence.
 *
 * @doc.type class
 * @doc.purpose Maps ObsolescenceSignal to/from data maps
 * @doc.layer data-cloud
 * @doc.pattern Mapper
 */
public final class ObsolescenceSignalMapper {

    private ObsolescenceSignalMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts an ObsolescenceSignal to a data map for persistence.
     *
     * @param signal the obsolescence signal
     * @return data map representation
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull ObsolescenceSignal signal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("signalId", signal.signalId());
        map.put("masteryItemId", signal.masteryItemId());
        map.put("signalType", signal.signalType().name());
        map.put("source", signal.source());
        map.put("detectedAt", signal.detectedAt().toString());
        map.put("description", signal.description());
        map.put("metadata", new LinkedHashMap<>(signal.metadata()));
        map.put("severity", signal.severity());
        return map;
    }

    /**
     * Converts a data map to an ObsolescenceSignal.
     *
     * @param data the data map
     * @return obsolescence signal
     */
    @NotNull
    public static ObsolescenceSignal fromDataMap(@NotNull Map<String, Object> data) {
        String signalId = (String) data.get("signalId");
        String masteryItemId = (String) data.get("masteryItemId");
        String signalTypeStr = (String) data.get("signalType");
        ObsolescenceSignalType signalType = ObsolescenceSignalType.valueOf(signalTypeStr);
        String source = (String) data.get("source");
        String detectedAtStr = (String) data.get("detectedAt");
        Instant detectedAt = Instant.parse(detectedAtStr);
        String description = (String) data.get("description");

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) data.get("metadata");
        Map<String, String> metaCopy = metadata != null ? Map.copyOf(metadata) : Map.of();

        Double severityObj = (Double) data.get("severity");
        double severity = severityObj != null ? severityObj : 0.5;

        return new ObsolescenceSignal(
                signalId,
                masteryItemId,
                signalType,
                source,
                detectedAt,
                description,
                metaCopy,
                severity
        );
    }
}
