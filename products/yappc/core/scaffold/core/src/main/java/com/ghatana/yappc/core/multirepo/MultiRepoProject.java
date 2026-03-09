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

package com.ghatana.yappc.core.multirepo;

import com.ghatana.yappc.core.model.ProjectSpec;
import java.nio.file.Path;

/**
 * Day 21: Individual project within a multi-repository workspace. Represents a single repository
 * with its own yappc.project.json configuration.
 *
 * @doc.type class
 * @doc.purpose Day 21: Individual project within a multi-repository workspace. Represents a single repository
 * @doc.layer platform
 * @doc.pattern Component
 */
public class MultiRepoProject {

    private final String name;
    private final Path repositoryPath;
    private final ProjectSpec projectSpec;

    public MultiRepoProject(String name, Path repositoryPath, ProjectSpec projectSpec) {
        this.name = name;
        this.repositoryPath = repositoryPath;
        this.projectSpec = projectSpec;
    }

    public String getName() {
        return name;
    }

    public Path getRepositoryPath() {
        return repositoryPath;
    }

    public ProjectSpec getProjectSpec() {
        return projectSpec;
    }

    /**
 * Gets the project manifest file path for this repository. */
    public Path getProjectManifestPath() {
        return repositoryPath.resolve("yappc.project.json");
    }

    /**
 * Checks if this project has cross-repository dependencies. */
    public boolean hasCrossRepoDependencies() {
        return projectSpec.getDependencies() != null && !projectSpec.getDependencies().isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
                "MultiRepoProject{name='%s', path='%s', type='%s'}",
                name, repositoryPath, projectSpec.getProjectType());
    }
}
