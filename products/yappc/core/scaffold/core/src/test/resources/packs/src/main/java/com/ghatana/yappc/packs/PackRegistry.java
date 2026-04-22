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
    private static final Map<String, PackTemplate> TEMPLATES = new HashMap<>(); // GH-90000

    static {
        register(new NxPnpmMonorepoPack()); // GH-90000
    }

    private PackRegistry() {} // GH-90000

    public static void register(PackTemplate template) { // GH-90000
        TEMPLATES.put(template.id(), template); // GH-90000
    }

    public static PackTemplate get(String id) { // GH-90000
        PackTemplate template = TEMPLATES.get(Objects.requireNonNull(id)); // GH-90000
        if (template == null) { // GH-90000
            throw new IllegalArgumentException("Unknown pack: " + id); // GH-90000
        }
        return template;
    }

    public static PackTemplate defaultNxMonorepo() { // GH-90000
        return get(NxPnpmMonorepoPack.ID); // GH-90000
    }

    public static WorkspaceSpec defaultWorkspace(String name) { // GH-90000
        return WorkspaceSpec.defaultMonorepo(name); // GH-90000
    }
}
