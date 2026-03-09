# DCMAAR – Browser Extension Plugin Model – Design & Plan

## 1. Purpose

This document defines the **plugin-based model** for the DCMAAR browser
extension framework and describes how product apps such as **Guardian**
and **Device Health** build on that framework.

Guardian serves as the **primary reference implementation** of this
plugin model; Device Health and future apps follow the same patterns.

It aligns:

- `@dcmaar/browser-extension-core` (Source–Processor–Sink runtime).
- `@dcmaar/browser-extension-ui` and shared UI libraries.
- `@dcmaar/plugin-abstractions` and `@dcmaar/plugin-extension`.
- `@dcmaar/connectors` (egress/ingress).
- App-specific extensions: `@dcmaar/guardian-browser-extension`,
  `@dcmaar/device-health-extension`.

## 2. Scope

This model covers:

- Browser-side capture, processing, and transmission of telemetry.
- Plugin lifecycle and configuration for extension-hosted plugins.
- Secure use of connectors to send/receive data.
- How apps (Guardian, Device Health, future apps) configure
  **app-specific content** and build extensions on top of the framework.

It does **not** prescribe backend or desktop plugin models, beyond their
interactions with browser-side connectors.

## 3. Roles & Components

### 3.1 Framework & Libraries

- `framework/browser-extension`
  - Conceptual DCMAAR browser extension host.
  - Provides in-browser capture, local policy enforcement, and minimal
    UI surfaces for status/controls.

- `libs/typescript/browser-extension-core`
  - Core extension runtime based on a **Source–Processor–Sink** pipeline.
  - Owns the extension-side plugin registry and pipeline orchestration.

- `libs/typescript/browser-extension-ui`
  - Reusable React components and styles for popup/options UI, status
    panels, etc.
  - Built on top of `browser-extension-core`.
  - Optional library; extensions may run headless (no UI) or use
    domain-specific UI plugins and components.

- `libs/typescript/plugin-abstractions`
  - Defines stable TypeScript interfaces for plugins (e.g. data
    collectors, notification, storage, UI plugins).

- `libs/typescript/plugin-extension`
  - Implements concrete device-monitoring plugins (CPU, memory, battery,
    etc.) using `plugin-abstractions`.

- `libs/typescript/connectors`
  - Provides connector implementations (HTTP, gRPC, WebSockets, etc.)
    that extensions and plugins use to talk to DCMAAR agents/servers.

### 3.2 Application Extensions

Examples of application extensions built on the domain-neutral
framework include:

- `apps/guardian/apps/browser-extension`
  - Guardian browser extension (`@dcmaar/guardian-browser-extension`).
  - Monitors web usage and enforces allow/block decisions for pages,
    using the shared extension core and connectors.

- `apps/device-health`
  - Device Health browser extension
    (`@dcmaar/device-health-extension`).
  - Hosts device-monitoring plugins from `@dcmaar/plugin-extension`.
  - Uses `@dcmaar/connectors` to communicate with backend/agent services.
  - Provides its own UI using shared UI libraries.

Both apps must remain **thin layers** on top of the shared frameworks
and libraries, per `ARCHITECTURE_GUARDRAILS.md`.

## 4. Target Plugin Model

### 4.1 Host Runtime

The **host runtime** lives in `@dcmaar/browser-extension-core` and
(optionally) the project under `framework/browser-extension`.

Responsibilities:

- Manage plugin lifecycle:
  - Registration, initialization, start, shutdown.
- Provide a **Source–Processor–Sink** pipeline:
  - Sources: browser events, navigation, page metrics, OS signals.
  - Processors: filters, transformations, policy evaluation.
  - Sinks: connectors, storage, UI update channels.
- Expose minimal APIs to plugins:
  - Access to approved browser APIs (`webextension-polyfill`).
  - Access to storage (via abstracted storage plugins).
  - Access to connectors via a bridge layer.

The host must be **product-neutral** and must not depend on any
Guardian- or Device Health-specific code.

### 4.2 Plugins

Plugins are implemented as TypeScript modules that follow the interfaces
defined in `@dcmaar/plugin-abstractions`.

Representative plugin categories:

- **DataCollector** plugins
  - Collect metrics from browser or device (e.g., CPU usage, memory
    stats, web performance metrics).
- **Notification** plugins
  - Deliver alerts via channels such as Slack or webhooks.
- **Storage** plugins
  - Persist metrics to local or remote storage, often via connectors.
- **UI** plugins
  - Provide extension UI panels, metrics widgets, and settings pages,
    built on top of `@dcmaar/browser-extension-ui` and shared UI libs.
  - UI plugins are **optional**; extensions may run headless
    (background-only) or select different UI plugin sets per domain/app.

Concrete plugin libraries:

- `@dcmaar/plugin-extension`
  - Generic, reusable monitoring plugins.
- Guardian plugin package (to be introduced)
  - Guardian-specific collector/policy/UI plugins.

### 4.3 Plugin Manifests

Each extension app maintains a **plugin manifest** that describes:

- Which plugins to enable.
- Plugin configuration (intervals, thresholds, UI visibility).
- Connector profiles to use for egress.
- Optional feature flags or app/tenant identifiers.
- Installation-time configuration values injected during extension
  install or first-run (for example, environment/tenant IDs, default
  policy sets, or domain allow/block lists).

The manifest is **data only** and validated against a shared JSON
schema. It does **not** contain or reference executable code.

The host runtime reads the manifest at startup and initializes plugins
accordingly.

In practice, configuration can be injected at installation time via
build-time manifest flavors, enterprise/managed policies, or an initial
configuration document bundled with the extension and written to
extension storage on first run. All such configuration remains
data-only and is validated against the shared schema.

## 5. Connectors Integration

Extensions use `@dcmaar/connectors` to communicate with backends.

We standardize on an **ExtensionConnectorBridge** in
`@dcmaar/browser-extension-core`:

- Accepts **connector profiles** from the plugin manifest.
- Instantiates the appropriate connector implementation from
  `@dcmaar/connectors`.
- Exposes a small, typed API to plugins for sending telemetry/events and
  receiving responses if needed.
- Enforces connector-level policies:
  - Allowed endpoints.
  - TLS/MTLS requirements.
  - Timeouts and retry behavior.

Plugins themselves do *not* embed ad-hoc HTTP or gRPC clients;
they call into the bridge, which uses the shared connector framework.

## 6. Deployment Models

### 6.1 App-specific Extension Builds (Preferred)

Each app (Guardian, Device Health, future apps) continues to own its
extension project:

- `apps/guardian/apps/browser-extension`
- `apps/device-health`
- `apps/<future-app>/apps/browser-extension` (pattern)

Each project:

- Depends on `@dcmaar/browser-extension-core`,
  `@dcmaar/browser-extension-ui`, `@dcmaar/plugin-abstractions`,
  `@dcmaar/connectors`, and any relevant plugin libraries.
- Defines an **app-specific plugin manifest**.
- Bundles only the plugins required for that app.

Security characteristics:

- All executable code is bundled at build time and reviewed.
- The manifest **selects among** known plugin IDs and connector
  profiles; it does not load new code.

### 6.2 Optional Single DCMAAR Host Extension

As an advanced option, we may provide a single "DCMAAR Host" extension
under `framework/browser-extension` that:

- Bundles the shared host runtime.
- Includes multiple **plugin packs** (Guardian, Device Health, etc.).
- Activates a particular **appId** or profile based on configuration.

Even in this model:

- All plugin code resides inside the extension bundle.
- Configuration selects which plugin sets and UI surfaces to activate.
- No remote code loading is allowed.

This model is useful for internal/operator deployments or environments
where managing multiple extension packages is undesirable.

## 7. Security & Privacy Model

The following constraints apply to all plugin-based extensions:

1. **No remote code loading**
   - No dynamic evaluation (`eval`, `new Function`).
   - No dynamic importing of remote scripts.
   - Plugins are compiled into the extension bundle.

2. **Manifest = Data**
   - Plugin and connector configuration is described in static data
     structures (JSON/TS).
   - Manifests are validated against a schema before use.

3. **Whitelisted IDs**
   - The host keeps internal registries:
     - `pluginId -> factoryFunction`.
     - `connectorId -> connectorFactory`.
   - Manifests can only reference IDs present in these registries.

4. **Scoped Connectors**
   - Connector profiles define:
     - Endpoint URLs.
     - TLS/MTLS policies.
     - Authentication and headers.
   - Profiles are app-specific but must pass central validation.

5. **Least Privilege**
   - Plugins declare required capabilities (browser APIs, connectors).
   - The host loads a plugin only if its capabilities are compatible with
     the extension's manifest and MV3 permissions.

6. **Auditability**
   - The host may log plugin lifecycle events and connector usage
     (within privacy constraints) to assist with debugging and
     compliance.

## 8. Implementation Plan

### Phase 1 – Shared Host & Manifest Schema

- Add a plugin registry and lifecycle manager to
  `@dcmaar/browser-extension-core`.
- Define manifest types and JSON schema under `libs/typescript/types`
  (e.g. `ExtensionPluginManifest`, `PluginConfig`, `ConnectorProfile`).
- Implement `ExtensionConnectorBridge` inside the core runtime, using
  `@dcmaar/connectors`.

### Phase 2 – Guardian as Reference Implementation

- Introduce or refine a Guardian plugin package with
  `@dcmaar/plugin-abstractions` dependencies.
- Define a Guardian plugin manifest under
  `apps/guardian/apps/browser-extension`.
- Wire the Guardian extension to:
  - Initialize plugins from the manifest using the shared host runtime.
  - Send telemetry via `@dcmaar/connectors` through
    `ExtensionConnectorBridge`.
  - Use `@dcmaar/browser-extension-ui` for reusable UI pieces.
- Update `apps/guardian/apps/browser-extension/docs/DESIGN_ARCHITECTURE.md`
  to reference the shared plugin model.

As the reference implementation, Guardian should exercise the plugin
model across multiple feature areas:

- **Usage monitoring**
  - Capture high-level browsing activity (domains, URL patterns,
    timestamps, duration) via data-collector plugins, respecting
    allowlists/denylists and redaction rules.
  - Derive simple usage summaries (time spent per domain/category,
    recent activity) that can be surfaced in the UI.

- **Allow/block enforcement**
  - Implement a policy-evaluation plugin that reads allow/block
    configuration from the manifest and installation-time config
    (e.g., domain lists, specific websites/pages/URL patterns,
    categories, schedules).
  - Intercept navigation events and apply policy decisions (allow,
    block, warn) for those websites and pages using the browser
    extension APIs.
  - Record policy decisions as telemetry events via the connector
    bridge, without embedding transport-specific logic in plugins.

- **Configuration & policy management**
  - Use the manifest and installation-time config injection to provide
    default Guardian policy sets (e.g., baseline allow/block rules,
    monitoring levels).
  - Support per-tenant or per-environment overrides by varying the
    configuration data while keeping Guardian plugin code unchanged.

- **Telemetry & connectors**
  - Send anonymized or policy-compliant usage and policy events through
    `@dcmaar/connectors` to Guardian/backend services.
  - Use connector profiles to enforce endpoint, TLS, and retry
    behavior, demonstrating the connector bridge pattern.

Guardian should also demonstrate the **optional UI** model by
implementing a focused but representative set of UI surfaces using
`@dcmaar/browser-extension-ui` and UI plugins:

- **Popup panel**
  - Shows current site status (allowed/blocked/monitored).
  - Displays concise usage stats for the current domain (e.g., time
    spent today, last visit).
  - Indicates whether monitoring is active and any relevant policy
    flags (e.g., "restricted content").

- **Basic history/summary view**
  - Presents a lightweight view of recent activity or top domains,
    leveraging metrics produced by Guardian data-collector plugins.
  - Can be realized as one or more UI plugins that consume metrics from
    the plugin system.

- **Settings/consent surface**
  - Provides minimal controls for guardian-specific settings such as
    visibility of notifications, optional consent/acknowledgement
    flows, or links out to a richer web/desktop guardian portal.

These features are intended to exercise the plugin, connector, and
optional-UI patterns end-to-end while leaving the underlying framework
and libraries fully domain-neutral.

### Phase 3 – Device Health on the Plugin Model

- Align `apps/device-health` with the same manifest-driven approach:
  - Use a manifest to declare its plugin set and configuration.
  - Initialize plugins via the shared plugin registry.
  - Route storage/egress via `ExtensionConnectorBridge`.
- Update `apps/device-health/docs/DESIGN_ARCHITECTURE.md` to reference
  the shared plugin model.

### Phase 4 – (Optional) Unified DCMAAR Host Extension

- Create a host extension project under `framework/browser-extension`
  that:
  - Bundles shared runtime and multiple plugin packs.
  - Selects app/profile via configuration.
- Define configuration delivery mechanisms (build-time flavors and/or
  signed configuration from a trusted backend).
- Keep the same security posture: configuration selects from pre-bundled
  plugin modules.

## 9. Future Work

- Align this plugin model with desktop and agent plugin systems for more
  consistent multi-surface behavior.
- Add formal ADRs for:
  - Manifest schema changes.
  - Connector policies.
  - Any move toward a unified DCMAAR host extension.
- Extend automated guardrails to validate that:
  - Frameworks remain product-neutral.
  - Apps use only published framework/library APIs.
  - Plugin manifests reference valid, versioned plugin IDs.

This model allows DCMAAR to provide a **single, secure browser extension
framework** while enabling product apps like **Guardian** and
**Device Health** to configure **app-specific content** cleanly and
safely.
