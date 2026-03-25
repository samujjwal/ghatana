/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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
package com.ghatana.yappc.core.cache;

/**
 * Priority levels for recommendations.
 *
 * <p>
 * Defines priority ordering for recommendation actions.
 *
 * @doc.type enum
 * @doc.purpose Priority levels for recommendations
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum RecommendationPriority {
    CRITICAL, // Must address immediately
    HIGH, // Should address soon
    MEDIUM, // Address when convenient
    LOW        // Optional improvement
}
