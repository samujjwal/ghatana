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
 * Pattern Registry for the Data Cloud Brain architecture.
 *
 * <p>This module manages learned patterns that the brain has discovered
 * through its learning processes. Patterns represent recurring structures,
 * behaviors, or relationships found in the data.
 *
 * <h2>Key Concepts</h2>
 * <ul>
 *   <li><b>PatternRecord</b>: Immutable representation of a learned pattern</li>
 *   <li><b>PatternVersion</b>: Version tracking for pattern evolution</li>
 *   <li><b>PatternCatalog</b>: Central registry and search for patterns</li>
 *   <li><b>PatternMatcher</b>: Matches incoming records against known patterns</li>
 * </ul>
 *
 * <h2>Pattern Types</h2>
 * <ul>
 *   <li><b>Structural</b>: Data shape and schema patterns</li>
 *   <li><b>Temporal</b>: Time-based patterns and seasonality</li>
 *   <li><b>Behavioral</b>: User/system behavior patterns</li>
 *   <li><b>Causal</b>: Cause-effect relationship patterns</li>
 *   <li><b>Anomaly</b>: Deviation patterns from normal</li>
 * </ul>
 *
 * <h2>Pattern Lifecycle</h2>
 * <pre>
 * Discovery → Validation → Registration → Matching → Refinement
 *                                              ↓
 *                                         Deprecation
 * </pre>
 *
 * @since 1.0.0
 * @see com.ghatana.datacloud.ai.learning.LearningSignal
 */
@javax.annotation.processing.Generated("ghatana-brain")
package com.ghatana.datacloud.pattern;
