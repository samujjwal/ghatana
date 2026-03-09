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

import java.nio.file.Path;

/**
 * Result of template rendering.
 *
 * @doc.type record
 * @doc.purpose Template render result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record RenderResult(
        boolean success,
        Path outputPath,
        String content,
        long sizeBytes,
        String errorMessage
) {
    public static RenderResult success(Path outputPath, String content) {
        return new RenderResult(true, outputPath, content, content.length(), null);
    }

    public static RenderResult failure(String errorMessage) {
        return new RenderResult(false, null, null, 0, errorMessage);
    }
}
