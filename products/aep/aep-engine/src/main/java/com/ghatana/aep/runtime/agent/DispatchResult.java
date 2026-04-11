/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * Result of tier resolution — contains the resolved tier and the catalog entry
 * (if found). Used internally by {@link CatalogAgentDispatcher} to decide
 * execution strategy.
 *
 * @doc.type record
 * @doc.purpose Internal resolution result for dispatch decisions
 * @doc.layer framework
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
@Value
@Builder
public class DispatchResult {

    /** The resolved execution tier. */
    ExecutionTier tier;

    /** The catalog entry (null if UNRESOLVABLE). */
    @Nullable
    CatalogAgentEntry catalogEntry;

    /** Whether a Java TypedAgent bean was found. */
    boolean hasJavaImplementation;
}
