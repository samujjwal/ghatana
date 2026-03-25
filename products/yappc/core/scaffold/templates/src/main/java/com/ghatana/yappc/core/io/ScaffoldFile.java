package com.ghatana.yappc.core.io;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a file that should be materialised during scaffolding.
 * @doc.type record
 * @doc.purpose Represents a file that should be materialised during scaffolding.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record ScaffoldFile(Path relativePath, String content) {
    public ScaffoldFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
    }
}
