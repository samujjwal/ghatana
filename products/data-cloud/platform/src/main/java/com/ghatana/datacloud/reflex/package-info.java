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

/**
 * Reflex Module for fast-path pattern activation.
 *
 * <p>This module implements the reflexive processing capability of the
 * brain architecture. Reflexes are pre-learned, automatic responses to
 * recognized patterns that bypass deliberative processing for speed.
 *
 * <h2>Key Concepts</h2>
 * <ul>
 *   <li><b>ReflexRule</b>: Condition-action pair for automatic response</li>
 *   <li><b>ReflexTrigger</b>: Event that activates a reflex</li>
 *   <li><b>ReflexEngine</b>: Orchestrates reflex detection and execution</li>
 *   <li><b>ReflexOutcome</b>: Result of reflex execution</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Speed</b>: Sub-millisecond activation for known patterns</li>
 *   <li><b>Safety</b>: Bounded scope, reversible actions preferred</li>
 *   <li><b>Learning</b>: Reflexes are learned from deliberative processes</li>
 *   <li><b>Override</b>: Higher-level cognition can inhibit reflexes</li>
 * </ul>
 *
 * <h2>Reflex Lifecycle</h2>
 * <pre>
 * Pattern Recognition → Trigger Detection → Rule Matching →
 *     → Condition Check → Action Execution → Outcome Recording
 * </pre>
 *
 * @since 1.0.0
 * @see com.ghatana.datacloud.pattern.PatternRecord
 */
@javax.annotation.processing.Generated("ghatana-brain")
package com.ghatana.datacloud.reflex;
