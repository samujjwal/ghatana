# @yappc/mobile (shim scope)

This folder is intentionally minimal and exists to support web builds of the YAPPC frontend.

## Purpose

- Provide no-op runtime shims for Capacitor packages when building for web.
- Prevent bundling and resolution failures for native-only imports in browser targets.

## Current Contract

- Entry file: `capacitor-shims.ts`
- Consumed via Vite aliases in `frontend/web/vite.config.ts`:
  - `@capacitor/haptics`
  - `@capacitor/share`
  - `@capacitor/network`
  - `@capacitor/local-notifications`
  - `@capacitor/camera`
  - `@capacitor/filesystem`
  - `@capacitor/core`

## Non-Goals

- This is not a production mobile runtime layer.
- This folder does not implement native behavior; it only provides safe web stubs.

## Decision

Keep this module as a narrowly-scoped compatibility shim until native/mobile packaging is formalized in a dedicated app/runtime.
