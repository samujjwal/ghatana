/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent;

/**
 * Determinism guarantee an agent provides.
 *
 * <p>Determines whether the same input will always produce the same output
 * and what caching/reuse strategies are safe.
 *
 * @doc.type enum
 * @doc.purpose Determinism classification
 * @doc.layer core
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum DeterminismGuarantee {

    /** Fully deterministic — same input, same output, always. Safe to cache, memoize, or replay. */
    FULL,

    /** Deterministic within a configuration version. Output may change when agent config/model is updated. */
    CONFIG_SCOPED,

    /** Probabilistic — output may vary for same input due to sampling, model stochasticity, or external dependencies. */
    NONE,

    /** Eventually consistent — output converges over time (e.g., adaptive agents that learn from feedback). */
    EVENTUAL
}
