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

package com.ghatana.yappc.core.ai.model;

/**
 * Day 16: Pack recommendation model from AI analysis. Represents a scored recommendation with
 * explanation from LangChain4J service.
 *
 * @doc.type class
 * @doc.purpose Day 16: Pack recommendation model from AI analysis. Represents a scored recommendation with
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PackRecommendation {
    private final String packName;
    private final String projectType;
    private final String language;
    private final String framework;
    private final double score;
    private final String explanation;
    private final String[] tags;

    public PackRecommendation(
            String packName,
            String projectType,
            String language,
            String framework,
            double score,
            String explanation,
            String[] tags) {
        this.packName = packName;
        this.projectType = projectType;
        this.language = language;
        this.framework = framework;
        this.score = score;
        this.explanation = explanation;
        this.tags = tags != null ? tags.clone() : new String[0];
    }

    public String getPackName() {
        return packName;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getLanguage() {
        return language;
    }

    public String getFramework() {
        return framework;
    }

    public double getScore() {
        return score;
    }

    public String getExplanation() {
        return explanation;
    }

    public String[] getTags() {
        return tags.clone();
    }

    @Override
    public String toString() {
        return String.format(
                "PackRecommendation{name='%s', type='%s', lang='%s', score=%.2f}",
                packName, projectType, language, score);
    }
}
