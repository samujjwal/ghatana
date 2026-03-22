package com.ghatana.yappc.packs.hooks;

import com.ghatana.yappc.core.hooks.PostGenerationHook;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.nio.file.Path;

/**
 * Placeholder hook that simulates invoking Polyfix after scaffolding.
 * @doc.type class
 * @doc.purpose Placeholder hook that simulates invoking Polyfix after scaffolding.
 * @doc.layer platform
 * @doc.pattern Hook
 */
public final class NoOpPolyfixHook implements PostGenerationHook {
    @Override
    public String name() {
        return "polyfix-noop";
    }

    @Override
    public void onGenerated(Path workspaceRoot, WorkspaceSpec workspaceSpec) {
        // In the real system this is where Polyfix codemods would run.
    }
}
