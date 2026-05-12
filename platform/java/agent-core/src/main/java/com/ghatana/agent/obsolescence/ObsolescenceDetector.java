/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Detector for identifying when mastery items become obsolete.
 *
 * <p>Detects obsolescence based on:
 * <ul>
 *   <li>API changes (signature, behavior, deprecation)</li>
 *   <li>Version mismatches (library/framework versions)</li>
 *   <li>Runtime incompatibility</li>
 *   <li>Repeated failures in execution</li>
 *   <li>Security vulnerabilities</li>
 *   <li>Documentation contradictions</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Detector for identifying obsolete mastery items
 * @doc.layer agent-core
 * @doc.pattern Detector
 */
public interface ObsolescenceDetector {

    /**
     * Detects obsolescence for a mastery item given the current environment.
     *
     * @param item mastery item to check
     * @param env current environment fingerprint
     * @return promise of list of obsolescence events (empty if not obsolete)
     */
    @NotNull
    Promise<List<ObsolescenceEvent>> detect(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env
    );

    /**
     * Scans all mastery items for obsolescence.
     *
     * @param env current environment fingerprint
     * @return promise of list of obsolescence events
     */
    @NotNull
    Promise<List<ObsolescenceEvent>> scanAll(@NotNull EnvironmentFingerprint env);
}
