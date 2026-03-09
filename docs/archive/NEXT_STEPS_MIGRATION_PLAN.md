# Ghatana Migration: Next Steps Strategy & Plan
**Date:** 2026-02-05
**Reference:** [REMAINING_MIGRATION_PLAN.md](REMAINING_MIGRATION_PLAN.md) (Previous Plan)

## 1. Current Status Overview
We have successfully established the **Core Platform** foundation. The following modules are Migrated, Built, and Verified (Green):
*   `platform:java:core`, `domain`, `runtime`, `config`, `testing`
*   `platform:java:auth`, `observability`, `governance`
*   `platform:java:workflow`, `platform:java:plugin` (Recently Fixed)

The `ghatana-new` workspace is now ready to receive the **Product Layers** and **Applications**.

## 2. Gap Analysis & Architecture Decisions

### 2.1 HTTP & Networking
*   **Finding**: `platform:java:http` exists but lacks the full implementation found in legacy `libs/java/http-server` and `http-client`.
*   **Decision**: We must migrate the verified HTTP server/client implementations from legacy to `platform:java:http` to ensure products have a robust networking layer.

### 2.2 YAPPC (Yet Another Platform Creator)
*   **Finding**: The legacy `products/yappc` is a massive self-contained monorepo with its own backend, frontend, and build system.
*   **Decision**: Migrate `yappc` as a **Product** under `:products:yappc`.
    *   `yappc/backend` -> `:products:yappc:services` (or `platform`)
    *   `yappc/frontend` -> `:products:yappc:frontend` (or keep as `apps` entry if it's an app)
    *   Strategy: "Lift and Shift" first, then Refactor to use new `platform:java` libs.

### 2.3 Flashit & Context Policy
*   **Finding**: Legacy `libs/java/context-policy` is missing. Previous plan assigned it to `products/flashit`.
*   **Decision**: Follow the previous plan. Migrate `context-policy` code to `:products:flashit:platform:java`.

### 2.4 Application Layer
*   **Finding**: `ghatana/apps/canvas-demo` exists but is missing in the new workspace. `ghatana-new/apps` folder is missing.
*   **Decision**: Create `apps/` directory and migrate `canvas-demo` to prove the platform works for end-user applications.

## 3. Migration Execution Plan (Step-by-Step)

### Phase 1: Platform Consolidation (Immediate)
**Goal**: Ensure `platform:java:http` and `platform:java:cache` are feature-complete.
1.  **HTTP**: Copy source from `ghatana/libs/java/http-server/src` and `http-client/src` to `ghatana-new/platform/java/http/src`.
2.  **Redis**: Verify if `redis-cache` is needed by Core. If yes, migrate to `:platform:java:cache`. If strictly for Data Cloud, migrate to `:products:data-cloud`.

### Phase 2: Product Migration (High Priority - YAPPC)
**Goal**: Migrate the largest product to validate the modular architecture.
1.  **Scaffold**: Create `ghatana-new/products/yappc` structure.
2.  **Backend**: Copy `ghatana/products/yappc/backend` to `ghatana-new/products/yappc/services`.
3.  **Build**: Create `products/yappc/build.gradle.kts` that depends on `:platform:java:core`, `:platform:java:runtime`, etc.
4.  **Refactor**: Fix imports to use new Platform packages.

### Phase 3: Secondary Products (Software Org, Virtual Org)
**Goal**: Complete the product inventory.
1.  **Move**: Copy `products/software-org` and `products/virtual-org` to `ghatana-new/products/`.
2.  **Align**: Update build files to use the shared platform.

### Phase 4: Frontend & Apps
**Goal**: Full stack capability.
1.  **Structure**: Create `ghatana-new/apps`.
2.  **Canvas Demo**: Migrate `ghatana/apps/canvas-demo`.

## 4. Work Breakdown Structure (Next 24 Hours)

1.  **Message**: "Migrating HTTP Infrastructure"
    *   Target: `platform:java:http`
    *   Source: `libs/java/http-*`
2.  **Message**: "Migrating YAPPC Backend"
    *   Target: `products:yappc`
    *   Source: `products/yappc/backend`

## 5. Verification Strategy
*   For each migration step, run `./gradlew :module:build`.
*   Once a Product is migrated, run its tests.
*   Final Check: `./gradlew build` (Aggregate build).
