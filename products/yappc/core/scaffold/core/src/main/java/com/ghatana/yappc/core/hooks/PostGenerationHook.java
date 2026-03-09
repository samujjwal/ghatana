package com.ghatana.yappc.core.hooks;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.nio.file.Path;

/**
 * Interface that allows Polyfix or other systems to run after scaffolding.
 * @doc.type interface
 * @doc.purpose Interface that allows Polyfix or other systems to run after scaffolding.
 * @doc.layer platform
 * @doc.pattern Hook
 */
public interface PostGenerationHook {
    String name();

    void onGenerated(Path workspaceRoot, WorkspaceSpec workspaceSpec) throws Exception;
}
