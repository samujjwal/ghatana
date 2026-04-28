# Canvas Sketch Scope

This directory is the canonical sketch implementation for YAPPC web canvas.

## Decision

- Sketch is integrated into the canvas runtime.
- Do not create a separate `frontend/libs/sketch` package unless a concrete cross-product reuse case is approved.

## Why

- Current sketch mode is tightly coupled to canvas interaction state, camera transforms, and workspace atoms.
- Keeping sketch logic in the canvas module avoids duplicate state models and duplicate rendering pipelines.

## Boundaries

- Product-specific sketch orchestration belongs here (`EnhancedSketchLayer`, tool hooks, keyboard mappings).
- Shared low-level primitives can be extracted later only if reused across products.
