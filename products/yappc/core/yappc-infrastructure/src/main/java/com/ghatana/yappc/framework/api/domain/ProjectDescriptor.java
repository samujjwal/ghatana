package com.ghatana.yappc.framework.api.domain;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal project descriptor used by framework/plugin contracts.
 *
 * <p>This is intentionally a thin value object — NOT a full domain model.
 * The canonical project domain model lives in {@code libs/java/yappc-domain}
 * ({@code com.ghatana.products.yappc.domain.model.Project}).
 *
 * @doc.type class
 * @doc.purpose Lightweight project descriptor for plugin contracts
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class ProjectDescriptor {
    private final String id;
    private final String name;
    private final Path rootPath;
    private final Map<String, Object> metadata;

    public ProjectDescriptor(String id, String name, Path rootPath, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.rootPath = rootPath;
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectDescriptor other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(name, other.name) && Objects.equals(rootPath, other.rootPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, rootPath);
    }

    @Override
    public String toString() {
        return "ProjectDescriptor{id='%s', name='%s', rootPath=%s}".formatted(id, name, rootPath);
    }
}
