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
package com.ghatana.yappc.core.recommendations;

/**
 * Types of recommendations that can be generated.
 *
 * <p>
 * Categorizes recommendations by functional area.
 *
 * @doc.type enum
 * @doc.purpose Types of recommendations
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum RecommendationType {
    CACHE_OPTIMIZATION, // Cache configuration improvements
    DEPENDENCY_UPDATE, // Dependency version updates
    CONFIGURATION_CHANGE, // Configuration adjustments
    PERFORMANCE_TUNING, // Performance optimization
    SECURITY_HARDENING, // Security improvements
    CODE_QUALITY, // Code quality enhancements
    BUILD_OPTIMIZATION       // Build process improvements
}
