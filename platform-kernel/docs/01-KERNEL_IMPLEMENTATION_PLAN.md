I reviewed the pinned `samujjwal/ghatana` snapshot at commit `8f7603ba194abaa10b8b5cc52ff69ed04d32e9e0` as a static code/architecture scan. I did **not** run tests or release-readiness checks locally.

## Verdict

Data Cloud is moving in the right direction: the plane architecture is explicit, active modules are centrally included, SPI/platform boundaries are documented, and ArchUnit-style boundary tests exist. However, the code still has **several high-risk semantic duplicates**: parallel route truth, parallel surface/runtime truth, duplicated route-to-surface mapping, and a very large `DataCloudHttpServer` that mixes composition, feature truth, handler wiring, fallback logic, and UI-facing metadata.

The main issue is not “copy-pasted classes.” It is **duplicated sources of truth** that will drift as the product grows.

---

## What is good and should be preserved

Data Cloud has a clear canonical plane model: Experience, Contract, Runtime Truth, Data, Event, Context, Intelligence, Governance, Action, and Operations planes. The architecture explicitly says Data Cloud is one product organized as interoperable planes, with Action Plane integrated as the governed automation runtime.  

The active module set is centralized in generated Gradle includes. Data Cloud includes shared SPI, data/entity, event/core and store, operations/config, intelligence, governance, runtime composition, API, launcher, SDK, contracts, integration tests, extensions, and Action Plane modules. 

The SPI/platform boundary guide is strong: platform code must stay product-agnostic, `planes/shared-spi` must contain contracts only, plane modules own implementations, and runtime composition is supposed to wire modules without becoming a dependency target.  

There is also a real boundary test preventing platform modules from depending on Data Cloud, AEP, or YAPPC product namespaces. It specifically guards platform observability, agent-core, cache, and feature enum boundaries.   

---

# High-priority findings

## 1. Route truth is still duplicated across at least three places

**Where**

* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`
* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java`
* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSurfaceMapping.java`
* `products/data-cloud/docs/ROUTE_MANIFEST_SYSTEM.md`

**Problem**

The route manifest documentation says the goal is a single source of truth across backend routing/security, OpenAPI, UI gating/runtime truth, SDK generation, and docs.  But the same document still tells developers to add a route to `RouteSecurityRegistry.java` and also ensure it is declared in `DataCloudRouterBuilder.java`. 

That means the current implementation still depends on parity between multiple route definitions instead of one canonical route model.

`RouteSecurityRegistry` contains a large static route map plus dynamic metadata support.   `DataCloudRouterBuilder` directly registers route lists with `builder.with(...)`, while only some paths use explicit `recordRoutePolicy(...)`.  

The media routes show the duplication clearly: the route list is registered once, and then the same route set is repeated manually as `recordRoutePolicy(...)` calls. 

**Root cause**

There is no single `RouteDefinition` abstraction that owns:

* HTTP method
* canonical path
* handler binding
* surface ID
* permission
* sensitivity
* lifecycle
* idempotency
* OpenAPI operation metadata
* UI/runtime truth metadata

Instead, route truth is split across builder methods, a security registry, a manifest generator, and surface mapping.

**Fix**

Create one canonical route declaration model, for example:

`products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteDefinition.java`

Then each route group should return `List<RouteDefinition>` and the builder should register from that list. Generate or derive `RouteSecurityRegistry`, `RouteSurfaceMapping`, OpenAPI manifest metadata, and UI route metadata from those definitions. Do not manually duplicate route metadata in static maps.

---

## 2. Runtime Truth has both legacy map truth and typed `SurfaceRecord` truth

**Where**

* `DataCloudHttpServer.java`
* `SurfaceRecord.java`
* `SurfaceRegistryHandler.java`

**Problem**

`SurfaceRecord` is explicitly described as replacing loose `Map<String,Object>` runtime truth and becoming the backend source of truth for UI navigation, route access, availability, disabled state, dependency status, owner plane, and user actions. 

`SurfaceRegistryHandler` also says `GET /api/v1/surfaces` is the canonical endpoint returning typed `SurfaceRecord` records. 

But `DataCloudHttpServer` still maintains both:

* `buildSurfaceSnapshot()` returning a legacy map/capability model
* `buildTypedSurfaceSnapshot()` returning typed `SurfaceRecord` records

The legacy method starts at `buildSurfaceSnapshot()` and constructs many runtime capability entries manually.  The typed method starts later and repeats the same posture computation before building `SurfaceRecord` instances. 

**Root cause**

The migration from “capabilities” to “surfaces” is incomplete. Both models still exist as independently constructed truth instead of one being a compatibility projection from the other.

**Fix**

Make `buildTypedSurfaceSnapshot()` the only canonical runtime truth builder. Replace `buildSurfaceSnapshot()` with a compatibility adapter:

`List<SurfaceRecord> -> Map<String,Object> legacyCapabilities`

Also change `buildSurfaceSummaryLog()` so it reads from typed records, not from the legacy map. The current summary still calls `buildSurfaceSnapshot()`. 

---

## 3. `RouteSurfaceMapping` duplicates route metadata and is already showing canonical/legacy drift

**Where**

* `RouteSurfaceMapping.java`
* `RouteSecurityRegistry.java`
* `DataCloudRouterBuilder.java`

**Problem**

`RouteSurfaceMapping` claims to provide canonical mapping from HTTP routes to surface IDs for runtime truth, UI gating, authorization metadata, audit event types, OpenAPI metadata, and SDK feature flags. 

But the route security metadata already carries `runtimeTruthSurface`, and `DataCloudRouterBuilder.recordRoutePolicy(...)` first looks at registry metadata, then falls back to `RouteSurfaceMapping`. 

This creates two competing route-to-surface sources.

There are also signs of stale canonical route naming. For example, `RouteSurfaceMapping` maps plugin/autonomy/learning routes under legacy-looking paths such as `/api/v1/plugins`, `/api/v1/autonomy`, and `/api/v1/learning/...`, while the builder registers canonical Action Plane routes under `/api/v1/action/...`.   

**Root cause**

Surface mapping is being maintained as a separate manual registry instead of being derived from route definitions or route security metadata.

**Fix**

Delete manual `RouteSurfaceMapping` route lists after introducing canonical `RouteDefinition`. Until then, generate `RouteSurfaceMapping` from `RouteSecurityRegistry.allRoutes()` and fail if any route has different lifecycle/surface metadata between the two.

---

## 4. `DataCloudHttpServer` is too large and owns too many reasons to change

**Where**

* `DataCloudHttpServer.java`

**Problem**

`DataCloudHttpServer` imports and wires nearly every subsystem: entity stores, event stores, media, mastery, brain, learning, anomaly detection, compaction, idempotency, settings, plugins, routes, security, observability, AI, voice, governance, connectors, conformance, and runtime truth. The imports alone show duplicated imports and broad responsibility creep: `DataCloudMediaArtifactRepository` and `MediaArtifactRepository` appear twice, and `OperationsJobHandler` appears twice.  

It also constructs handlers, validates runtime profile dependencies, builds route chains, builds runtime truth, handles production fallback rules, and creates media components in one class.   

**Root cause**

The launcher has become a composition root plus a runtime catalog plus a surface catalog plus a feature-state engine.

**Fix**

Split it into these classes:

* `DataCloudRuntimeComposition` — builds handlers and stores.
* `DataCloudRouteComposition` — builds `RoutingServlet` from route groups.
* `DataCloudSurfaceCatalog` — builds typed `SurfaceRecord` list only.
* `DataCloudRuntimePostureFactory` — builds `RuntimePosture`.
* `DataCloudProductionDependencyValidator` — owns production/staging/sovereign checks.

Keep `DataCloudHttpServer` as a thin lifecycle shell: validate, compose, start, stop.

---

## 5. Runtime Truth misrepresents or under-represents some wired features

### Media artifacts are wired, but the surface is commented out

`DataCloudHttpServer.start()` creates `MediaArtifactRepository`, `MediaArtifactEventEmitter`, `MediaArtifactService`, and `MediaArtifactController`, then wires media routes. 

But the typed surface snapshot has a commented-out TODO for `media.artifacts`. 

**Fix:** Add a real `media.artifacts` surface based on the actual `MediaArtifactService`/repository/event emitter state.

### Voice/audio-video can appear live because the handler exists, even when adapters are NOP

The server creates NOP STT/TTS adapters when config is absent.   But the `media.audioVideo` surface is marked live when `voiceHandler != null`, and `voiceHandler` is always instantiated. 

**Fix:** Surface state must depend on real adapter capability, not handler object existence. Split this into `audioVideo.voiceGateway`, `audioVideo.stt`, and `audioVideo.tts`.

### Connectors are wired, but their surface is commented out

The connector handler is created and registered in the route chain.   But the `data.connectors` surface is commented out as a TODO. 

**Fix:** Add a real `data.connectors` surface and stop mapping connector routes through `data.storageProfiles`.

### Context Plane is target-only but may become user-visible

The architecture says `planes/context/` is target-only and not user-visible until promoted.  But the typed surface snapshot sets `context.plane` as discoverable and primary navigation when `contextLayerHandler != null`.  Since the handler is instantiated in startup, this can expose a target-only plane as navigable preview. 

**Fix:** Gate `context.plane` discoverability with an explicit `DATA_CLOUD_CONTEXT_PLANE_PREVIEW` or product registry status, not handler existence.

---

# File-by-file action list

## `DataCloudRouterBuilder.java`

Replace direct `builder.with(...)` route declarations with route-group definitions that include route metadata. The existing `withPolicyRoute(...)` and `recordRoutePolicy(...)` abstraction should not remain optional or partial; every route should flow through one canonical path. 

## `RouteSecurityRegistry.java`

Stop hand-maintaining the static route table. Keep lookup/runtime matching behavior, but generate metadata from canonical route definitions. The current registry has both static and dynamic sources, plus lifecycle fallback from surface records.  

## `RouteSurfaceMapping.java`

Remove the manual route map or generate it. The route-to-surface relationship should come from the same route metadata used by security, OpenAPI, SDK, UI gating, and audit.  

## `DataCloudHttpServer.java`

Split composition, validation, runtime truth, and handler wiring. Also remove duplicate imports and stale TODO blocks. The class currently wires media, connectors, settings, route registration, surface truth, operation recorder fallback, and runtime profile validation in one place.  

## `SurfaceRecord.java`

Keep the typed record, but consider splitting runtime truth from UI presentation. The current record includes runtime state, dependency probes, posture, path, label, i18n keys, icon, role, navigation group, sort order, fallback reason, and recommended action. 

A cleaner model would be:

* `SurfaceRuntimeRecord`
* `SurfaceDependencyState`
* `SurfacePresentation`
* `SurfaceNavigationPolicy`

Then serialize a combined response at the API boundary.

## `SurfaceRegistryHandler.java`

Keep it as the canonical endpoint, but ensure it only serves typed records from `DataCloudSurfaceCatalog`. It should not know about compatibility capability maps.  

## `PLANE_ARCHITECTURE.md` and `SPI_VS_PLATFORM_BOUNDARY_GUIDE.md`

Keep these as canonical. The rules are good. The next gap is enforcement: add an architecture check that verifies `planes/shared-spi` contains no implementation classes and that `delivery/runtime-composition` remains a leaf. The guide already says `shared-spi` must never contain implementations and runtime composition should be a leaf node. 

---

## Priority order

1. **Unify route truth** with a canonical `RouteDefinition` model.
2. **Make typed `SurfaceRecord` the only runtime truth** and derive legacy capability maps from it.
3. **Delete/generated `RouteSurfaceMapping`** so route-to-surface truth cannot drift.
4. **Extract `DataCloudHttpServer` composition responsibilities** into smaller factories/catalogs.
5. **Fix surface availability correctness** for media, voice, connectors, storage profiles, and context.
6. **Add architecture tests** for `shared-spi` contract-only and `runtime-composition` leaf-only rules.
7. **Remove duplicate imports, stale TODO blocks, and commented-out feature truth** after the above refactor.

The strongest simplification move is not deleting features. It is making **routes, surfaces, runtime posture, and shared-library ownership derive from one canonical source each** instead of maintaining parallel registries.
