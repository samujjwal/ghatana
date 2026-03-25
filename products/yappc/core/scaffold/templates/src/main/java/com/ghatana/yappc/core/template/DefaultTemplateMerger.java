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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of TemplateMerger with basic conflict detection. Week 2, Day 6 deliverable
 * - conservative 3-way merge for text files.
 *
 * <p>This is a simplified implementation for the foundation. Will be enhanced with proper diff
 * algorithms in future iterations.
 *
 * @doc.type class
 * @doc.purpose Simple implementation of TemplateMerger with basic conflict detection. Week 2, Day 6 deliverable
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DefaultTemplateMerger implements TemplateMerger {

    private static final Logger log = LoggerFactory.getLogger(DefaultTemplateMerger.class);

    private boolean hasConflicts;
    private List<ConflictMarker> conflicts;

    public DefaultTemplateMerger() {
        this.hasConflicts = false;
        this.conflicts = new ArrayList<>();
    }

    @Override
    public String merge(Path basePath, Path userPath, Path newPath) throws TemplateException {
        try {
            String baseContent = Files.readString(basePath);
            String userContent = Files.readString(userPath);
            String newContent = Files.readString(newPath);

            return merge(baseContent, userContent, newContent);
        } catch (IOException e) {
            throw new TemplateException("Failed to read merge input files", e);
        }
    }

    @Override
    public String merge(String baseContent, String userContent, String newContent)
            throws TemplateException {
        // Reset conflict state
        hasConflicts = false;
        conflicts.clear();

        try {
            // Simple merge strategy:
            // 1. If user content == base content, use new content
            // 2. If new content == base content, use user content
            // 3. If user content == new content, use either
            // 4. Otherwise, create conflict markers

            if (userContent.equals(baseContent)) {
                // User made no changes, use new template
                log.debug("User made no changes, using new template");
                return newContent;
            }

            if (newContent.equals(baseContent)) {
                // Template unchanged, use user modifications
                log.debug("Template unchanged, using user modifications");
                return userContent;
            }

            if (userContent.equals(newContent)) {
                // Both made same changes
                log.debug("User and template changes are identical");
                return userContent;
            }

            // Conflict detected - create conflict markers
            hasConflicts = true;
            List<String> mergedLines = createConflictMarkers(userContent, newContent);
            log.warn("Merge conflicts detected, created conflict markers");

            return String.join("\n", mergedLines);

        } catch (Exception e) {
            throw new TemplateException("Failed to perform 3-way merge", e);
        }
    }

    @Override
    public boolean hasConflicts() {
        return hasConflicts;
    }

    @Override
    public ConflictMarker[] getConflicts() {
        return conflicts.toArray(new ConflictMarker[0]);
    }

    /**
 * Create conflict markers when automatic merge fails. */
    private List<String> createConflictMarkers(String userContent, String newContent) {
        List<String> result = new ArrayList<>();

        List<String> userLines = userContent.lines().toList();
        List<String> newLines = newContent.lines().toList();

        result.add("<<<<<<< USER CHANGES");
        result.addAll(userLines);
        result.add("=======");
        result.addAll(newLines);
        result.add(">>>>>>> NEW TEMPLATE");

        // Record conflict marker positions
        conflicts.add(new ConflictMarker(1, result.size(), "User vs Template changes"));

        return result;
    }
}
