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

/**
 * Interface for conservative 3-way merge operations on templates. Week 2, Day 6 deliverable -
 * handling merge conflicts in generated files.
 *
 * @doc.type interface
 * @doc.purpose Interface for conservative 3-way merge operations on templates. Week 2, Day 6 deliverable -
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface TemplateMerger {

    /**
     * Perform a 3-way merge of template content. Combines base template, user modifications, and
     * new template changes.
     *
     * @param basePath Path to the original template (base)
     * @param userPath Path to user-modified version (theirs)
     * @param newPath Path to the new template version (ours)
     * @return Merged content or conflict markers if merge fails
     * @throws TemplateException If merge operation fails
     */
    String merge(Path basePath, Path userPath, Path newPath) throws TemplateException;

    /**
     * Perform a 3-way merge with string content.
     *
     * @param baseContent Original template content (base)
     * @param userContent User-modified content (theirs)
     * @param newContent New template content (ours)
     * @return Merged content or conflict markers if merge fails
     * @throws TemplateException If merge operation fails
     */
    String merge(String baseContent, String userContent, String newContent)
            throws TemplateException;

    /**
     * Check if the merge resulted in conflicts.
     *
     * @return true if the last merge had conflicts
     */
    boolean hasConflicts();

    /**
     * Get conflict markers from the last merge.
     *
     * @return Array of conflict marker positions
     */
    ConflictMarker[] getConflicts();

    /**
 * Represents a conflict marker in merged content. */
    record ConflictMarker(int startLine, int endLine, String description) {}
}
