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
 * Processing outcome status for an {@link AgentResult}.
 *
 * @doc.type enum
 * @doc.purpose Agent result status
 * @doc.layer core
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum AgentResultStatus {

    /** Processing completed successfully with output. */
    SUCCESS,

    /** Processing completed but output is below the confidence threshold. */
    LOW_CONFIDENCE,

    /** Agent chose to skip (e.g., input didn't match preconditions). */
    SKIPPED,

    /** Processing failed due to an error. */
    FAILED,

    /** Processing exceeded the agent's latency SLA / timeout. */
    TIMEOUT,

    /** Agent is in a degraded state; result may be partial. */
    DEGRADED,

    /** Agent explicitly delegated processing to another agent. */
    DELEGATED
}
