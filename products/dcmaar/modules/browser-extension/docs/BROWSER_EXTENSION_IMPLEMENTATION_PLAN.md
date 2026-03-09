# DCMAAR Browser Extension – File-by-File Implementation Plan

This plan breaks down the work described in
`PLUGIN_MODEL_AND_APPS.md` into **concrete file-level steps**, with a
strong focus on:

- Reusing existing code in `libs/*` and `apps/*`.
- Keeping the framework **domain-neutral**.
- Avoiding duplicate concepts and configs.
- Following `.github/copilot-instructions.md` (reuse-first, strict
  types, clear module boundaries).

Paths are relative to the repo root.

---

## Phase 1 – Shared Host & Manifest Schema

### 1.1 Extension Host Runtime & Plugin Registry

#### 1.1.1 `libs/typescript/browser-extension-core/src/index.ts`

- **Type**: Existing file (public entrypoint).
- **Changes**:
  - Export new host/registry/bridge APIs introduced in this phase:
    - `PluginRegistry` / `PluginLifecycleManager`.
    - `ExtensionConnectorBridge`.
    - Any manifest-loading helper(s) that are part of the core API.
  - Keep exports minimal and stable; do not leak internal helper types.

#### 1.1.2 `libs/typescript/browser-extension-core/src/controller/*`

- **Type**: Existing controller module.
- **Changes**:
  - Integrate plugin lifecycle into the controller rather than
    introducing a parallel orchestrator.
  - Responsibilities:
    - Accept a validated `ExtensionPluginManifest` (see Section 1.2).
    - Use `PluginRegistry` to register plugin factories and
      instantiate plugins.
    - Use `PluginLifecycleManager` to init/start/stop plugins.
    - Connect plugin outputs to the existing
      Source–Processor–Sink pipeline.
  - Ensure no direct dependencies on Guardian/Device Health modules.

#### 1.1.3 `libs/typescript/browser-extension-core/src/pipeline/*`

- **Type**: Existing pipeline modules.
- **Changes**:
  - Verify that sources/processors/sinks can accept events from
    plugins without domain-specific assumptions.
  - Where Device Health has generic logic duplicated under
    `apps/device-health/src/...`, **lift only the generic parts** into
    these pipeline modules and keep app-specific logic in the app.

#### 1.1.4 `libs/typescript/browser-extension-core/src/adapters/*`

- **Type**: Existing adapters.
- **Changes**:
  - Add or extend an adapter layer that connects plugin output to the
    `ExtensionConnectorBridge` (see Section 1.1.7).
  - All connector usage from plugins should go through this adapter
    and the bridge, not direct HTTP/gRPC calls.

#### 1.1.5 `libs/typescript/browser-extension-core/src/plugins/PluginRegistry.ts`

- **Type**: New file.
- **Responsibilities**:
  - Maintain a registry of plugin factories by ID:
    - `registerPluginFactory(id: string, factory: FactoryFn)`.
    - `getPlugin(id: string)`.
    - `getAllPlugins()`.
  - Use interfaces from `@dcmaar/plugin-abstractions` for plugin
    shapes.
  - No product-specific imports.

#### 1.1.6 `libs/typescript/browser-extension-core/src/plugins/PluginLifecycleManager.ts`

- **Type**: New file.
- **Responsibilities**:
  - Encapsulate lifecycle operations for plugin instances:
    - `initialize(plugin)`, `start(plugin)`, `stop(plugin)`,
      `shutdown(plugin)`.
  - Provide batch operations (e.g. `initializeAll`, `stopAll`).
  - Surface observability hooks (log/metrics) via existing
    observability abstractions if available.

#### 1.1.7 `libs/typescript/browser-extension-core/src/plugins/ExtensionConnectorBridge.ts`

- **Type**: New file.
- **Responsibilities**:
  - Provide a thin abstraction over `@dcmaar/connectors` for plugins:
    - Instantiate connectors from **connector profiles** described in
      manifests.
    - Expose methods such as `sendMetric`, `sendEvent`,
      `sendPolicyDecision`, etc., parameterized by types from
      `@dcmaar/types` where applicable.
  - Enforce that plugins do **not** directly depend on concrete
    connector implementations.

> **Reuse-first note**: Before adding lifecycle/bridge logic, inspect
> existing plugin orchestration in
> `apps/device-health/src/core/PluginSystemAdapter.ts` and lift only the
> domain-neutral concepts into these new core files.

---

### 1.2 Shared Manifest & Connector-Profile Types

#### 1.2.1 `libs/typescript/connectors/src/types.ts`

- **Type**: Existing file.
- **Changes**:
  - Reuse/extend existing connector configuration primitives so that
    `ConnectorProfile` in the extension manifest does **not** invent a
    parallel structure.
  - Ensure profile fields (endpoint, TLS, retries, auth, etc.) are
    represented once here.

#### 1.2.2 `libs/typescript/plugin-abstractions/src/index.ts`

- **Type**: Existing file.
- **Changes**:
  - Export any additional plugin metadata types required for manifests
    (e.g. plugin categories, capabilities flags), keeping them generic.

#### 1.2.3 `libs/typescript/types/src/extension/extension-plugin-manifest.ts`

- **Type**: New file (exact path should align with existing `types`
  structure).
- **Responsibilities**:
  - Define domain-neutral manifest types, for example:
    - `ExtensionPluginManifest`.
    - `ExtensionPluginDescriptor` (id, type, enabled, config,
      connectorIds, capabilities).
    - `ConnectorProfile` types that reuse connector primitives.
  - Keep Guardian/Device Health specifics out; use generic
    `Record<string, unknown>` where product-level customization is
    required.

#### 1.2.4 `libs/typescript/types/src/extension/extension-plugin-manifest.schema.json`

- **Type**: New file.
- **Responsibilities**:
  - JSON Schema mirroring
    `extension-plugin-manifest.ts` for runtime validation.
  - Used by host and apps to validate manifests and install-time
    configuration.

#### 1.2.5 `libs/typescript/types/src/index.ts`

- **Type**: Existing file.
- **Changes**:
  - Re-export `ExtensionPluginManifest` and related types so app
    packages use the shared central definitions.

---

### 1.3 Docs & Tests for Phase 1

#### 1.3.1 `libs/typescript/browser-extension-core/docs/DESIGN_ARCHITECTURE.md`

- **Type**: Existing doc.
- **Changes**:
  - Describe new host responsibilities:
    - Plugin registry and lifecycle management.
    - Connector bridge as the sole path for extension telemetry.
  - Keep all language framework-neutral.

#### 1.3.2 `libs/typescript/connectors/docs/usage/TECHNICAL_REFERENCE.md`

- **Type**: Existing doc.
- **Changes**:
  - Add a small section
    "Using connectors via ExtensionConnectorBridge in browser extensions".

#### 1.3.3 `libs/typescript/types/docs/usage/*` (if present)

- **Type**: Existing docs.
- **Changes**:
  - Document `ExtensionPluginManifest` purpose and install-time
    configuration model.

#### 1.3.4 `libs/typescript/browser-extension-core/__tests__/*`

- **Type**: Existing or new test files.
- **Changes**:
  - Add unit tests for `PluginRegistry`, `PluginLifecycleManager`, and
    `ExtensionConnectorBridge`:
    - No `any`; use real TS types.
    - Test success and error paths.
    - Keep tests small and focused per `.github/copilot-instructions.md`.

---

## Phase 2 – Guardian as Reference Implementation

### 2.1 Guardian Plugin Package

#### 2.1.1 `products/dcmaar/apps/guardian/libs/typescript/guardian-plugins/package.json`

- **Type**: New file/package.
- **Responsibilities**:
  - Define a TS library for Guardian plugins:
    - `name: "@dcmaar/guardian-plugins"`.
    - Dependencies: `@dcmaar/plugin-abstractions`, `@dcmaar/types`.
  - Ensure this package is wired into the DCMAAR workspace (pnpm).

#### 2.1.2 `products/dcmaar/apps/guardian/libs/typescript/guardian-plugins/src/index.ts`

- **Type**: New file.
- **Responsibilities**:
  - Export Guardian plugin factories with typed identifiers compatible
    with `ExtensionPluginManifest`.
  - No direct references to `apps/guardian/...`; keep this as a
    reusable Guardian plugin library.

#### 2.1.3 `.../guardian-plugins/src/UsageCollectorPlugin.ts`

- **Type**: New file.
- **Responsibilities**:
  - Implement data-collector plugin using interfaces from
    `@dcmaar/plugin-abstractions`.
  - Collect usage-style events:
    - Domains, URL patterns, timestamps, duration.
    - Respect allow/deny lists and redaction flags from plugin config.

#### 2.1.4 `.../guardian-plugins/src/PolicyEvaluationPlugin.ts`

- **Type**: New file.
- **Responsibilities**:
  - Implement policy evaluation plugin:
    - Read allow/block configuration from manifest + install-time
      config (domains, specific websites/pages/URL patterns,
      categories, schedules).
    - Return decisions (allow/block/warn) in a generic form consumed by
      the host.

#### 2.1.5 Optional UI Plugins: `.../guardian-plugins/src/ui/*.tsx`

- **Type**: New files, if implementing UI as plugins.
- **Candidates**:
  - `PopupStatusPlugin.tsx`.
  - `HistorySummaryPlugin.tsx`.
  - `SettingsConsentPlugin.tsx`.
- **Responsibilities**:
  - Use `@dcmaar/browser-extension-ui` primitives.
  - Render Guardian-specific views while consuming data from the plugin
    host.

---

### 2.2 Guardian Plugin Manifest & Wiring

#### 2.2.1 `products/dcmaar/apps/guardian/apps/browser-extension/manifest.config.ts`

- **Type**: Existing file.
- **Changes**:
  - Extend or refactor so that it yields or embeds an
    `ExtensionPluginManifest` for Guardian:
    - Declare plugin descriptors (usage collector, policy evaluator,
      optional UI plugins by ID).
    - Define connector profiles (e.g., `guardian_https`).
    - Include default Guardian policy config as data only.

#### 2.2.2 `products/dcmaar/apps/guardian/apps/browser-extension/src/config/guardian-plugin-manifest.ts`

- **Type**: New file (or reuse an existing config module if present).
- **Responsibilities**:
  - Define a TS object of type `ExtensionPluginManifest` for Guardian.
  - Reference plugin IDs exported from `@dcmaar/guardian-plugins`.
  - Reference connector profiles using shared types from
    `libs/typescript/types`.

#### 2.2.3 `products/dcmaar/apps/guardian/apps/browser-extension/src/config/install-time-config.ts`

- **Type**: New file.
- **Responsibilities**:
  - Read installation/first-run configuration (e.g., from browser
    managed storage or bundled JSON) and merge into manifest config
    sections.
  - Enforce that resulting config conforms to
    `ExtensionPluginManifest` + JSON Schema.

#### 2.2.4 Guardian Background Entry (e.g. `src/background/index.ts`)

- **Type**: Existing file (exact path to be confirmed inside `src/`).
- **Changes**:
  - Initialize the browser-extension-core host runtime.
  - Load/validate Guardian `ExtensionPluginManifest`.
  - Register Guardian plugins via `PluginRegistry` (from core).
  - Use `ExtensionConnectorBridge` for all telemetry emission
    (usage/policy events) instead of ad-hoc fetches.

#### 2.2.5 `apps/guardian/apps/browser-extension/docs/DESIGN_ARCHITECTURE.md`

- **Type**: Existing doc.
- **Changes**:
  - Update to describe:
    - Guardian’s usage monitoring and allow/block enforcement in
      terms of plugins + manifest.
    - The fact that Guardian is the reference implementation for the
      plugin model.

---

### 2.3 Guardian UI Surfaces

#### 2.3.1 Popup UI Components under `apps/guardian/apps/browser-extension/src`

- **Type**: Existing React components.
- **Changes**:
  - Align with plan:
    - Popup shows current site status (allowed/blocked/monitored).
    - Shows quick usage stats for current domain (time today, last
      visit).
  - Implement data access via plugin-backed background messages exposed
    by the host (e.g., `GET_ANALYTICS`, `GET_USAGE_SUMMARY`,
    `EVALUATE_POLICY`), not direct storage lookups where possible.

#### 2.3.2 History/Summary View Components

- **Type**: Existing or new components.
- **Changes**:
  - Present a simple activity or top-domain view fed by metrics from
    Guardian data-collector plugins.
  - Follow UI guidelines (feature folders, hooks, and stores as
    per `.github/copilot-instructions.md`).

#### 2.3.3 Settings / Consent UI Components

- **Type**: Existing or new components.
- **Changes**:
  - Provide Guardian-specific toggles (notification visibility,
    consent/acknowledgement flows, link to Guardian web/desktop
    portal).
  - Reflect config stored in the plugin manifest & install-time
    overrides.

---

## Phase 3 – Device Health on the Plugin Model

### 3.1 Align Plugin System with Core

#### 3.1.1 `apps/device-health/src/core/PluginSystemAdapter.ts`

- **Type**: Existing file.
- **Changes**:
  - Identify generic logic for interacting with plugin instances
    (collecting metrics, health reporting, notifications, storage).
  - Move generic parts up into:
    - `browser-extension-core/src/plugins/PluginLifecycleManager.ts`.
    - Or a small, shared adapter inside `browser-extension-core`
      (e.g., `ExtensionPluginSystemAdapter.ts`).
  - Leave only Device-Health-specific behavior (metric shape,
    domain-specific alerts) in this file.

### 3.2 Device Health Manifest & Wiring

#### 3.2.1 `apps/device-health/manifest.config.ts`

- **Type**: Existing file.
- **Changes**:
  - Have this config produce or embed an `ExtensionPluginManifest`
    for Device Health.
  - Use the same manifest/connector types defined in
    `libs/typescript/types`.

#### 3.2.2 `apps/device-health/src/app/background/index.ts`

- **Type**: Existing file.
- **Changes**:
  - Replace bespoke plugin initialization with calls into the shared
    host runtime and registry.
  - Route storage/egress via `ExtensionConnectorBridge`.

#### 3.2.3 `apps/device-health/src/ui/hooks/usePluginConfig.ts`

- **Type**: Existing hook.
- **Changes**:
  - Align local config shape with Device Health’s portion of
    `ExtensionPluginManifest`.
  - Ensure storage keys and configs match manifest-driven behavior.

#### 3.2.4 `apps/device-health/src/ui/hooks/usePluginMetrics.ts`

- **Type**: Existing hook.
- **Changes**:
  - Replace mock metrics generation with real calls to plugin system
    APIs from the host (e.g., via background messaging).
  - Use `browser.runtime.sendMessage` to call `GET_CPU_METRICS`,
    `GET_MEMORY_METRICS`, and `GET_BATTERY_METRICS` background handlers
    that read from the shared monitor plugins (`cpu-monitor`,
    `memory-monitor`, `battery-monitor`) via `PluginSystemAdapter`.

#### 3.2.5 `apps/device-health/docs/DESIGN_ARCHITECTURE.md`

- **Type**: Existing doc.
- **Changes**:
  - Explicitly state that Device Health now uses the shared
    Extension Plugin Manifest and host runtime, following the Guardian
    pattern.

---

## Phase 4 – Optional Unified DCMAAR Host Extension

### 4.1 Host Extension App (Optional)

#### 4.1.1 `framework/browser-extension/host/package.json`

- **Type**: New (optional) package.
- **Responsibilities**:
  - Define a host extension app that bundles the shared runtime and
    multiple plugin packs (Guardian, Device Health, others).

#### 4.1.2 `framework/browser-extension/host/src/background/index.ts`

- **Type**: New file.
- **Responsibilities**:
  - Initialize the host runtime.
  - Load a chosen `ExtensionPluginManifest` profile at startup.
  - Register only plugin IDs from pre-bundled plugin packs.

#### 4.1.3 `framework/browser-extension/host/src/config/profiles/*.ts`

- **Type**: New files.
- **Responsibilities**:
  - Provide pre-defined profile manifests (Guardian-only,
    Device-Health-only, combined, internal).
  - All profiles must conform to the shared manifest types.

---

## Cross-Cutting Consistency & Cleanup

### 5.1 Guardian Package Naming

#### 5.1.1 `apps/guardian/apps/browser-extension/package.json`

- **Type**: Existing file.
- **Changes**:
  - Ensure `"name"` is `"@dcmaar/guardian-browser-extension"` and
    matches all references in docs and manifests.

### 5.2 Workspace & Tooling

#### 5.2.1 `products/dcmaar/pnpm-workspace.yaml`, `products/dcmaar/package.json`

- **Type**: Existing files.
- **Changes**:
  - Add new Guardian plugin package and optional host extension app as
    workspace members.
  - Avoid duplicating existing build/test config blocks.

### 5.3 Tests, Linting, and Types

- **Across all changed/added TS packages**:
  - Ensure `tsconfig.json` remains strict and shared ESLint/Prettier
    configs are reused.
  - Add focused tests under existing `__tests__/` or `tests/` folders
    rather than inventing new test hierarchies.
  - Follow testing and style rules from
    `.github/copilot-instructions.md`.
  - Ensure plugin-backed background message handlers for Guardian
    (`GET_USAGE_SUMMARY`, `EVALUATE_POLICY`) and Device Health
    (`GET_CPU_METRICS`, `GET_MEMORY_METRICS`, `GET_BATTERY_METRICS`)
    are covered by focused tests (controller/unit-level and hook-level
    where appropriate).

---

This implementation plan is intentionally file-scoped so that you can
execute work in small, reviewable PRs while keeping the architecture
coherent, domain-neutral, and free of duplicated concepts.
