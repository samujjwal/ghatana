package com.ghatana.yappc.core.hooks;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * PostGenerationHooks component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose PostGenerationHooks component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class PostGenerationHooks {
    private PostGenerationHooks() {}

    public static void runAll(Path workspaceRoot, WorkspaceSpec spec, PrintWriter writer) {
        ServiceLoader<PostGenerationHook> loader = ServiceLoader.load(PostGenerationHook.class);
        for (PostGenerationHook hook : loader) {
            writer.printf("[hook] %s...%n", hook.name());
            try {
                hook.onGenerated(workspaceRoot, spec);
                writer.printf("[hook] %s completed%n", hook.name());
            } catch (Exception ex) {
                writer.printf("[hook] %s failed: %s%n", hook.name(), ex.getMessage());
            }
        }
    }
}
