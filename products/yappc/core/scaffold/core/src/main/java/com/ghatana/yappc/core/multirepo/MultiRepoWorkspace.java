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

import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.util.List;

/**
 * Day 21: Multi-repository workspace representation. Contains the complete configuration and
 * projects for a workspace spanning multiple repositories.
 *
 * @doc.type class
 * @doc.purpose Day 21: Multi-repository workspace representation. Contains the complete configuration and
 * @doc.layer platform
 * @doc.pattern Component
 */
public class MultiRepoWorkspace {

    private final WorkspaceSpec workspaceSpec;
    private final MultiRepoConfiguration configuration;
    private final List<MultiRepoProject> projects;

    public MultiRepoWorkspace(
            WorkspaceSpec workspaceSpec,
            MultiRepoConfiguration configuration,
            List<MultiRepoProject> projects) {
        this.workspaceSpec = workspaceSpec;
        this.configuration = configuration;
        this.projects = projects;
    }

    public WorkspaceSpec getWorkspaceSpec() {
        return workspaceSpec;
    }

    public MultiRepoConfiguration getConfiguration() {
        return configuration;
    }

    public List<MultiRepoProject> getProjects() {
        return projects;
    }

    /**
 * Gets a specific project by name. */
    public MultiRepoProject getProject(String name) {
        return projects.stream()
                .filter(project -> project.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
 * Gets the number of repositories in this workspace. */
    public int getRepositoryCount() {
        return projects.size();
    }

    /**
 * Checks if this workspace has cross-repository dependencies. */
    public boolean hasCrossRepoDependencies() {
        return projects.stream().anyMatch(MultiRepoProject::hasCrossRepoDependencies);
    }

    @Override
    public String toString() {
        return String.format(
                "MultiRepoWorkspace{name='%s', repositories=%d}",
                workspaceSpec.getName(), getRepositoryCount());
    }
}
