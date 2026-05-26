package com.ghatana.yappc.packs;

import com.ghatana.yappc.core.io.ScaffoldFile;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.util.List;

/**
 * PackTemplate component within the YAPPC platform.
 *
 * @doc.type interface
 * @doc.purpose PackTemplate component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface PackTemplate {
    String id();

    List<ScaffoldFile> render(WorkspaceSpec spec);

    default String schemaVersion() {
        return "pack-v1";
    }

    default String version() {
        return "1.0.0";
    }

    default List<String> supportedSurfaces() {
        return List.of();
    }

    default List<String> compatibleLanguages() {
        return List.of();
    }

    default List<String> compatibleFrameworks() {
        return List.of();
    }

    default List<String> compatibleBuildSystems() {
        return List.of();
    }
}
