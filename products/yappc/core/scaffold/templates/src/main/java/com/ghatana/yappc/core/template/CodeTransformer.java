/*
 * Copyright (c) 2024 Ghatana, Inc.
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

package com.ghatana.yappc.core.template;

import com.ghatana.yappc.core.error.TemplateException;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for code transformation operations using OpenRewrite. Week 2, Day 6 deliverable -
 * OpenRewrite integration for Java/Gradle edits.
 *
 * @doc.type interface
 * @doc.purpose Interface for code transformation operations using OpenRewrite. Week 2, Day 6 deliverable -
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface CodeTransformer {

    /**
     * Apply a transformation recipe to Java source files.
     *
     * @param recipeName The OpenRewrite recipe name
     * @param sourcePaths List of source file paths to transform
     * @return Transformation results
     * @throws TemplateException If transformation fails
     */
    TransformationResult applyJavaRecipe(String recipeName, List<Path> sourcePaths)
            throws TemplateException;

    /**
     * Apply a transformation recipe to Gradle build files.
     *
     * @param recipeName The OpenRewrite recipe name
     * @param buildFiles List of build file paths to transform
     * @return Transformation results
     * @throws TemplateException If transformation fails
     */
    TransformationResult applyGradleRecipe(String recipeName, List<Path> buildFiles)
            throws TemplateException;

    /**
     * Add a dependency to a Gradle build file.
     *
     * @param buildFile Path to build.gradle file
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Dependency version
     * @param configuration Gradle configuration (implementation, testImplementation, etc.)
     * @return Transformation result
     * @throws TemplateException If transformation fails
     */
    TransformationResult addGradleDependency(
            Path buildFile, String groupId, String artifactId, String version, String configuration)
            throws TemplateException;

    /**
     * Add an import statement to a Java file.
     *
     * @param javaFile Path to Java source file
     * @param importClass Fully qualified class name to import
     * @return Transformation result
     * @throws TemplateException If transformation fails
     */
    TransformationResult addJavaImport(Path javaFile, String importClass) throws TemplateException;

    /**
     * Apply custom YAPPC-specific transformations.
     *
     * @param transformationType Type of transformation (activej-setup, otel-setup, etc.)
     * @param targetPaths List of paths to transform
     * @return Transformation results
     * @throws TemplateException If transformation fails
     */
    TransformationResult applyYappcTransformation(String transformationType, List<Path> targetPaths)
            throws TemplateException;

    /**
 * Result of a code transformation operation. */
    record TransformationResult(
            boolean successful,
            int filesChanged,
            List<String> changedFiles,
            List<String> errors,
            String summary) {}
}
