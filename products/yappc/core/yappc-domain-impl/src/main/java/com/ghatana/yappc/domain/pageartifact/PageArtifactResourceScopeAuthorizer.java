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

package com.ghatana.yappc.domain.pageartifact;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource-scoped authorization hook for page artifact operations.
 *
 * <p>This seam allows the page artifact controller to enforce workspace/project/artifact
 * ownership against a real source of truth without baking that dependency into the domain-impl
 * module. Callers may provide a repository-backed implementation from a higher layer.
 *
 * @doc.type interface
 * @doc.purpose Resource-scoped authorization hook for page artifact access
 * @doc.layer product
 * @doc.pattern Port
 */
public interface PageArtifactResourceScopeAuthorizer {

    Promise<Void> authorize(
            @Nullable String userId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String requiredPermission
    );

    static PageArtifactResourceScopeAuthorizer allowAll() {
        return (userId, tenantId, workspaceId, projectId, artifactId, requiredPermission) -> Promise.complete();
    }
}
