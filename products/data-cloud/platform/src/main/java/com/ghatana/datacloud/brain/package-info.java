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
 * Unified Brain API for the Data Cloud.
 *
 * <p>This package provides the central facade for the brain architecture,
 * orchestrating all cognitive subsystems into a unified processing pipeline.
 *
 * <h2>Architecture Overview</h2>
 * <pre>
 *                              ┌─────────────────────────────────────┐
 *                              │          DataCloudBrain             │
 *                              │       (Unified API Facade)          │
 *                              └──────────────┬──────────────────────┘
 *                                             │
 *          ┌──────────────────────────────────┼──────────────────────────────────┐
 *          │                                  │                                  │
 *    ┌─────▼─────┐                     ┌──────▼──────┐                    ┌──────▼──────┐
 *    │ Attention │                     │  Workspace  │                    │    Reflex   │
 *    │  Manager  │                     │   (Global)  │                    │   Engine    │
 *    └─────┬─────┘                     └──────┬──────┘                    └──────┬──────┘
 *          │                                  │                                  │
 *          │                                  │                                  │
 *    ┌─────▼─────┐                     ┌──────▼──────┐                    ┌──────▼──────┐
 *    │  Memory   │                     │   Pattern   │                    │   Autonomy  │
 *    │  Router   │                     │   Catalog   │                    │  Controller │
 *    └─────┬─────┘                     └──────┬──────┘                    └──────┬──────┘
 *          │                                  │                                  │
 *    ┌─────▼─────┐                     ┌──────▼──────┐                    ┌──────▼──────┐
 *    │  Vector   │                     │   Learning  │                    │  Feedback   │
 *    │  Memory   │                     │    Loop     │                    │  Collector  │
 *    └───────────┘                     └─────────────┘                    └─────────────┘
 * </pre>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li><b>DataCloudBrain</b>: Unified facade for all brain operations</li>
 *   <li><b>BrainContext</b>: Request-scoped context for processing</li>
 *   <li><b>BrainCapabilities</b>: Available cognitive capabilities</li>
 *   <li><b>BrainConfig</b>: Configuration for the brain</li>
 * </ul>
 *
 * <h2>Processing Flow</h2>
 * <ol>
 *   <li>Records enter through process()</li>
 *   <li>Salience scoring determines priority</li>
 *   <li>Reflexes handle fast-path responses</li>
 *   <li>Workspace broadcasts for deliberative processing</li>
 *   <li>Patterns are matched and learned</li>
 *   <li>Memory tiers receive appropriate records</li>
 *   <li>Feedback refines the system</li>
 * </ol>
 *
 * @since 1.0.0
 */
@javax.annotation.processing.Generated("ghatana-brain")
package com.ghatana.datacloud.brain;
