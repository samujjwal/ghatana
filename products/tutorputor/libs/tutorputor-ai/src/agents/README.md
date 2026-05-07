# TutorPutor AI Agent Java Boundary

The canonical Java content-generation agent implementation lives in:

- `products/tutorputor/libs/content-studio-agents/src/main/java/com/ghatana/tutorputor/agent/ContentGenerationRequest.java`
- `products/tutorputor/libs/content-studio-agents/src/main/java/com/ghatana/tutorputor/agent/ContentGenerationOutputGenerator.java`

`libs/tutorputor-ai` owns the TypeScript AI proxy/runtime surface. It must not keep Java copies of the content-generation request or output generator, because duplicate DTOs and generators drift across serialization, validation, and prompt behavior.

The guard is `scripts/validate-content-generation-canonical.mjs`.
