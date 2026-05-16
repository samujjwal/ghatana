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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Exception thrown when a page artifact save detects a version conflict.
 * <p>
 * Indicates that the document being saved has a documentId (ETag) that doesn't match
 * the current version in the repository. The client should reload the document and re-apply changes.
 *
 * @doc.type class
 * @doc.purpose Exception for optimistic concurrency conflicts
 * @doc.layer product
 * @doc.pattern Domain Exception
 */
public final class PageArtifactConflictException extends RuntimeException {

    private final String artifactId;
    private final String remoteVersion;

    public PageArtifactConflictException(String artifactId, String remoteVersion) {
        super(String.format(
                "Conflict saving artifact '%s': remote version is '%s'. Reload and re-apply changes.",
                artifactId,
                remoteVersion
        ));
        this.artifactId = artifactId;
        this.remoteVersion = remoteVersion;
    }

    public String artifactId() {
        return artifactId;
    }

    public String remoteVersion() {
        return remoteVersion;
    }

    @Override
    @JsonIgnore
    public Throwable getCause() {
        return super.getCause();
    }
}
