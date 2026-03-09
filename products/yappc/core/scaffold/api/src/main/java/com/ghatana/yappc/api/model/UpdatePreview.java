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

package com.ghatana.yappc.api.model;

import java.util.List;

/**
 * Preview of update changes before applying.
 *
 * @doc.type record
 * @doc.purpose Update preview model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record UpdatePreview(
        String fromVersion,
        String toVersion,
        List<FileChange> fileChanges,
        List<DependencyChange> dependencyChanges,
        List<String> warnings,
        boolean hasConflicts
) {
    /**
     * Represents a file change.
     */
    public record FileChange(
            String path,
            ChangeType type,
            String diff
    ) {
        public enum ChangeType {
            ADD,
            MODIFY,
            DELETE,
            CONFLICT
        }
    }

    /**
     * Represents a dependency change.
     */
    public record DependencyChange(
            String name,
            String fromVersion,
            String toVersion,
            ChangeType type
    ) {
        public enum ChangeType {
            ADD,
            UPGRADE,
            DOWNGRADE,
            REMOVE
        }
    }
}
