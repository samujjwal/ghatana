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

package com.ghatana.yappc.core.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Plugin interface for project analysis.
 *
 * @doc.type interface
 * @doc.purpose Analyzer plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface AnalyzerPlugin extends YappcPlugin {

    /**
     * Analyzes a project.
     *
     * @param projectPath path to project
     * @return analysis result
     * @throws PluginException if analysis fails
     */
    AnalysisResult analyze(Path projectPath) throws PluginException;

    /**
     * Returns the analysis types this plugin supports.
     *
     * @return list of analysis types
     */
    List<AnalysisType> getSupportedAnalysisTypes();

    /**
     * Analysis types.
     */
    enum AnalysisType {
        STRUCTURE,
        DEPENDENCIES,
        SECURITY,
        PERFORMANCE,
        CODE_QUALITY,
        BEST_PRACTICES
    }

    /**
     * Analysis result.
     */
    record AnalysisResult(
            AnalysisType type,
            List<Finding> findings,
            int score,
            String summary) {
    }

    /**
     * Analysis finding.
     */
    record Finding(
            Severity severity,
            String category,
            String message,
            Path file,
            Integer line,
            String suggestion) {

        public enum Severity {
            INFO,
            WARNING,
            ERROR,
            CRITICAL
        }
    }
}
