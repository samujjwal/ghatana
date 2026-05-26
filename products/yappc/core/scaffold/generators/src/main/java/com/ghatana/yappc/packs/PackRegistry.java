package com.ghatana.yappc.packs;

import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        Objects.requireNonNull(template, "template must not be null");
        String id = template.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pack id must be non-empty");
        }
        TEMPLATES.put(id, template);
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

    public static List<PackDescriptor> listSupportedPacks() {
        return TEMPLATES.values().stream()
                .map(PackRegistry::describe)
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    public static List<PackDescriptor> listSupportedPacksForSurface(String surface) {
        if (surface == null || surface.isBlank()) {
            return List.of();
        }
        return listSupportedPacks().stream()
                .filter(pack -> pack.supportedSurfaces().contains(surface))
                .toList();
    }

    public static PackRegistryCompleteness validateCompleteness(Set<String> requiredSurfaces) {
        Set<String> supportedSurfaces = listSupportedPacks().stream()
                .flatMap(pack -> pack.supportedSurfaces().stream())
                .collect(Collectors.toUnmodifiableSet());
        List<String> missingSurfaces = requiredSurfaces == null
                ? List.of()
                : requiredSurfaces.stream()
                        .filter(surface -> !supportedSurfaces.contains(surface))
                        .sorted()
                        .toList();
        List<String> invalidPacks = listSupportedPacks().stream()
                .filter(pack -> pack.schemaVersion().isBlank()
                        || pack.version().isBlank()
                        || pack.supportedSurfaces().isEmpty()
                        || pack.compatibleLanguages().isEmpty()
                        || pack.compatibleFrameworks().isEmpty()
                        || pack.compatibleBuildSystems().isEmpty())
                .map(PackDescriptor::id)
                .toList();
        return new PackRegistryCompleteness(missingSurfaces.isEmpty() && invalidPacks.isEmpty(), missingSurfaces, invalidPacks);
    }

    public static WorkspaceSpec defaultWorkspace(String name) {
        return WorkspaceSpec.defaultMonorepo(name);
    }

    private static PackDescriptor describe(PackTemplate template) {
        return new PackDescriptor(
                template.id(),
                template.schemaVersion(),
                template.version(),
                List.copyOf(template.supportedSurfaces()),
                List.copyOf(template.compatibleLanguages()),
                List.copyOf(template.compatibleFrameworks()),
                List.copyOf(template.compatibleBuildSystems()));
    }

    /**
     * @doc.type record
     * @doc.purpose Describes a scaffold pack and its compatibility contract
     * @doc.layer platform
     * @doc.pattern Data Transfer Object
     */
    public record PackDescriptor(
            String id,
            String schemaVersion,
            String version,
            List<String> supportedSurfaces,
            List<String> compatibleLanguages,
            List<String> compatibleFrameworks,
            List<String> compatibleBuildSystems) {
        public PackDescriptor {
            supportedSurfaces = List.copyOf(supportedSurfaces);
            compatibleLanguages = List.copyOf(compatibleLanguages);
            compatibleFrameworks = List.copyOf(compatibleFrameworks);
            compatibleBuildSystems = List.copyOf(compatibleBuildSystems);
        }
    }

    /**
     * @doc.type record
     * @doc.purpose Reports scaffold pack registry completeness for required target surfaces
     * @doc.layer platform
     * @doc.pattern Data Transfer Object
     */
    public record PackRegistryCompleteness(
            boolean complete,
            List<String> missingSurfaces,
            List<String> invalidPacks) {
        public PackRegistryCompleteness {
            missingSurfaces = List.copyOf(missingSurfaces);
            invalidPacks = List.copyOf(invalidPacks);
        }
    }
}
