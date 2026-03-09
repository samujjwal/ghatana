package com.ghatana.yappc.packs;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * PackRegistry component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose PackRegistry component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class PackRegistry {
    private static final Map<String, PackTemplate> TEMPLATES = new HashMap<>();

    static {
        register(new NxPnpmMonorepoPack());
    }

    private PackRegistry() {}

    public static void register(PackTemplate template) {
        TEMPLATES.put(template.id(), template);
    }

    public static PackTemplate get(String id) {
        PackTemplate template = TEMPLATES.get(Objects.requireNonNull(id));
        if (template == null) {
            throw new IllegalArgumentException("Unknown pack: " + id);
        }
        return template;
    }

    public static PackTemplate defaultNxMonorepo() {
        return get(NxPnpmMonorepoPack.ID);
    }

    public static WorkspaceSpec defaultWorkspace(String name) {
        return WorkspaceSpec.defaultMonorepo(name);
    }
}
