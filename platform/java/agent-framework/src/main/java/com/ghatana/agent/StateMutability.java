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
 * Describes how an agent manages mutable state.
 *
 * <p>Influences checkpointing strategy, horizontal scaling,
 * and recovery behaviour.
 *
 * @doc.type enum
 * @doc.purpose State mutability classification
 * @doc.layer core
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum StateMutability {

    /** Agent is purely functional — no mutable state at all. Can be trivially scaled horizontally or replaced. */
    STATELESS,

    /** Agent uses local in-memory state (e.g., sliding window counters). State is lost on restart unless checkpointed. */
    LOCAL_STATE,

    /** Agent uses externally-persisted state (Redis, database). State survives restarts. */
    EXTERNAL_STATE,

    /** Agent has both local and external state components. Requires coordinated checkpoint/recovery. */
    HYBRID_STATE
}
