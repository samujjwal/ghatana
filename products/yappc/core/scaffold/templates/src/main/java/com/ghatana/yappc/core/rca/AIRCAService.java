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

package com.ghatana.yappc.core.rca;

import io.activej.promise.Promise;

/**
 * Day 27: AI-powered Root Cause Analysis service interface. Analyzes build failures and generates
 * explanations with fix suggestions.
 *
 * @doc.type interface
 * @doc.purpose Day 27: AI-powered Root Cause Analysis service interface. Analyzes build failures and generates
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AIRCAService {

    /**
     * Analyze a build failure and provide AI-generated explanation and fixes
     *
     * @param buildLog Normalized build log to analyze
     * @return Future containing RCA result with explanations and fix suggestions
     */
    Promise<RCAResult> analyzeFailure(NormalizedBuildLog buildLog);

    /**
     * Analyze a build failure with additional context
     *
     * @param buildLog Normalized build log to analyze
     * @param context Additional context information (project structure, recent changes, etc.)
     * @return Future containing RCA result with explanations and fix suggestions
     */
    Promise<RCAResult> analyzeFailure(NormalizedBuildLog buildLog, RCAContext context);

    /**
     * Get historical analysis for similar failures
     *
     * @param buildLog Current build log
     * @return Future containing list of similar past failures and their resolutions
     */
    Promise<java.util.List<RCAResult>> getSimilarFailures(NormalizedBuildLog buildLog);

    /**
     * Check if the AI service is available and configured
     *
     * @return true if service is ready to analyze failures
     */
    boolean isAvailable();
}
