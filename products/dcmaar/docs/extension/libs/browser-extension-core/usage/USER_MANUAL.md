# DCMaar – Browser Extension Core – User Manual

## 1. Audience

This manual is for engineers building DCMaar browser extensions on top of `@dcmaar/browser-extension-core`.

## 2. Basic Usage

1. Add `@dcmaar/browser-extension-core` as a dependency.
2. Define sources, processors, and sinks in your extension code.
3. Compose them into pipelines that capture and process browser events.

## 3. Best Practices

- Keep data capture minimal and privacy-aware.
- Reuse shared pipeline components instead of duplicating logic.

This manual is self-contained and explains how to use `@dcmaar/browser-extension-core` in typical flows.
