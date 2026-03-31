# ADR-018: BlockSuite / AFFiNE Evaluation for Collaborative Canvas

- **Status**: Accepted
- **Date**: 2026-03-31
- **Decision Makers**: Platform engineering
- **Relates To**: `@ghatana/canvas`, YAPPC canvas, collaborative editing

## Context

YAPPC and other products use `@ghatana/canvas` for topology visualization, diagram editing, and whiteboard features. The current implementation is built on React Flow (`@xyflow/react`) with custom performance optimizations (viewport culling, virtual nodes, throttled updates).

BlockSuite / AFFiNE is an open-source framework providing a CRDT-backed collaborative editing surface (block editor + whiteboard + database). Evaluating it as a potential replacement or supplement for the existing canvas infrastructure.

## Decision Drivers

1. **Collaboration**: Real-time multi-user editing with CRDT-based conflict resolution
2. **Rich block editing**: Block-based document editing (text, code, tables, diagrams)
3. **Whiteboard**: Built-in infinite canvas with drawing, sticky notes, connectors
4. **Maintenance cost**: Custom canvas infrastructure requires ongoing performance work
5. **Bundle size**: Impact on existing product JS bundles

## Evaluation

### BlockSuite / AFFiNE Strengths

- Native CRDT backbone (Y.js-based) solves real-time collaboration out of the box
- Block editor + whiteboard in a single framework — reduces integration surface
- Active open-source community with structured releases
- Plugin architecture for extending block types

### BlockSuite / AFFiNE Weaknesses

- **Large bundle**: ~500KB+ gzipped for the full suite (editor + whiteboard + CRDT runtime)
- **Opinionated DOM model**: Does not integrate cleanly into existing React component trees without an adapter layer
- **Topology graph mismatch**: BlockSuite whiteboard is optimized for freeform drawing, not structured node-edge topology graphs (which is the primary use case for `@ghatana/canvas`)
- **CRDT overhead**: Y.js document sync adds latency and memory overhead for workloads that do not need collaborative editing
- **Maturity gap**: Whiteboard surface is less battle-tested than the block editor

### Current Stack Assessment

`@ghatana/canvas` is built on React Flow which:
- Is purpose-built for node-edge diagrams and topology visualization
- Has sub-50ms render for 500+ nodes with custom viewport culling
- Integrates natively into React component trees
- Has a lightweight footprint (~45KB gzipped)
- Already has real-time collaboration via `@ghatana/realtime` SSE + Jotai state sync

## Decision

**Retain `@ghatana/canvas` (React Flow-based) as the primary canvas implementation.**

Do not adopt BlockSuite/AFFiNE for the following reasons:

1. The primary canvas use case (topology graphs, workflow diagrams, agent pipelines) is better served by React Flow which is purpose-built for directed graph visualization.
2. Bundle size impact (500KB+) is unacceptable for products that already optimize for sub-200KB initial JS payloads.
3. Real-time collaboration needs are already addressed by `@ghatana/realtime` (SSE-based state sync).
4. Introducing a second DOM management layer (BlockSuite's Lit-based rendering) alongside React would increase complexity without proportional benefit.

### Future Reconsideration Triggers

Re-evaluate if any of these conditions change:
- A product requires rich block-based document editing (not just canvas diagrams)
- BlockSuite releases a lightweight whiteboard-only package (<100KB gzipped)
- CRDT-based collaboration becomes a hard requirement that `@ghatana/realtime` cannot satisfy

## Consequences

- `@ghatana/canvas` remains the single canvas abstraction for all products
- Performance improvements continue in the existing React Flow-based implementation
- No new dependency on `@blocksuite/*` or `@toeverything/*` packages
- Teams needing collaborative document editing should evaluate standalone block editor options (ProseMirror, Tiptap) scoped to that use case
