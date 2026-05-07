# Context Plane

The Context Plane owns lineage, provenance, freshness, semantic context, memory, retrieval grounding, and context assembly.

No Gradle module is currently attached to this folder. Existing implementation remains in the closest owning modules until the package-level cleanup phase separates context-specific code from Data, Intelligence, and Action Plane internals.

Boundary rule: Context Plane code must not depend on Action Plane implementation internals.
