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

package com.ghatana.yappc.core.cache;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Day 22: Cached artifact representation. Contains generated project artifacts with metadata for
 * cache management.
 *
 * @doc.type class
 * @doc.purpose Day 22: Cached artifact representation. Contains generated project artifacts with metadata for
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CachedArtifact implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String content;
    private final String contentType;
    private final java.util.HashMap<String, String> metadata;
    private final Instant createdAt;
    private final long sizeBytes;

    public CachedArtifact(String content, String contentType, Map<String, String> metadata) {
        this.content = content;
        this.contentType = contentType;
        this.metadata =
                metadata != null ? new java.util.HashMap<>(metadata) : new java.util.HashMap<>();
        this.createdAt = Instant.now();
        this.sizeBytes = content.getBytes().length;
    }

    public String getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    @Override
    public String toString() {
        return String.format(
                "CachedArtifact{contentType='%s', size=%d bytes, created=%s}",
                contentType, sizeBytes, createdAt);
    }
}
