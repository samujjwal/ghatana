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

/**
 * Permission constants for page artifact operations.
 * <p>
 * These permissions are used for authorization checks in the PageArtifactController.
 * Follows the pattern from platform:java:security rbac.Permission.
 *
 * @doc.type class
 * @doc.purpose Permission constants for page artifact operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class PageArtifactPermission {

    // ---- Read Permissions ----

    /**
     * Permission to read page artifacts within a project.
     */
    public static final String READ = "page_artifact.read";

    /**
     * Permission to read project information (required for accessing page artifacts).
     */
    public static final String PROJECT_READ = "project.read";

    // ---- Write Permissions ----

    /**
     * Permission to edit page artifacts within a project.
     */
    public static final String EDIT = "page_artifact.edit";

    /**
     * Permission to edit project information.
     */
    public static final String PROJECT_EDIT = "project.edit";

    // ---- Advanced Permissions ----

    /**
     * Permission to force-save over conflict.
     */
    public static final String FORCE_SAVE = "page_artifact.force_save";

    /**
     * Permission to import/decompile artifacts.
     */
    public static final String IMPORT_DECOMPILE = "page_artifact.import_decompile";

    /**
     * Permission to approve generated/suggested changes.
     */
    public static final String APPROVE_CHANGES = "page_artifact.approve_changes";

    // ---- Admin Permissions ----

    /**
     * Permission to delete page artifacts.
     */
    public static final String DELETE = "page_artifact.delete";

    /**
     * Permission to manage page artifact settings.
     */
    public static final String MANAGE = "page_artifact.manage";

    private PageArtifactPermission() {
        // Prevent instantiation
    }
}
