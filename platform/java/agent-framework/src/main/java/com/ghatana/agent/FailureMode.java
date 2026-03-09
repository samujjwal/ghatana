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
 * How an agent behaves when processing fails.
 *
 * <p>Influences retry strategy, circuit-breaker configuration,
 * and dead-letter-queue routing.
 *
 * @doc.type enum
 * @doc.purpose Failure mode classification
 * @doc.layer core
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum FailureMode {

    /** Processing failure propagates immediately. No retry, no fallback. */
    FAIL_FAST,

    /** On failure, retry with configurable backoff before giving up. */
    RETRY,

    /** On failure, fall back to a pre-configured default output. */
    FALLBACK,

    /** On failure, skip this agent's contribution and continue. */
    SKIP,

    /** On failure, route the event to a dead-letter-queue for later inspection. */
    DEAD_LETTER,

    /** After N consecutive failures, open the circuit and reject all requests until half-open probe succeeds. */
    CIRCUIT_BREAKER
}
