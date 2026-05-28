# Route Manifest System (DC-P0-03)

**Status**: Implementation Framework Complete  
**Last Updated**: 2026-05-18

## Overview

The Route Manifest System is the **single source of truth** for Data Cloud HTTP route metadata across:
- Backend routing and security enforcement
- OpenAPI contract validation
- UI feature gating and runtime truth
- SDK code generation
- API documentation

## Problem Statement

Before P0-3, three separate "truths" existed for the same routes:

| System | Truth Source | Problem |
|--------|--------------|---------|
| **Backend** | RouteSecurityRegistry.java | Authoritative but Java-only |
| **OpenAPI** | action-plane.yaml | Drifted from runtime implementation |
| **UI** | RuntimeTruthPosture.ts (manual) | Stale, missing new routes |

This created:
- Inconsistent route definitions
- Manual UI updates (error-prone)
- CI/CD unable to detect drift
- SDK generation mismatches

## Solution: P0-3 Route Manifest

### Architecture

```
RouteSecurityRegistry.java (Source of Truth)
         ↓ (parse via generate-route-manifest.mjs)
    Route Manifest JSON
         ↓ (validate against schema)
    Validation Report
         ↓ (generate from manifest)
    ┌────────────────────────────┐
    │   OpenAPI validation       │
    │   UI RuntimeTruthPosture   │
    │   SDK metadata             │
    │   API documentation        │
    └────────────────────────────┘
         ↓ (fail CI if drift)
    CI/CD Gate (Pre-merge)
```

### Files

**Java Classes**:
- [`RouteSecurityRegistry.java`](../../delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java) - Authoritative backend registry
- [`RouteSecurityMetadata.java`](../../delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/RouteSecurityMetadata.java) - Metadata value object
- [`RouteManifest.java`](../../delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/RouteManifest.java) - Manifest model (Jackson-serializable)

**Configuration**:
- [`config/route-manifest-schema.json`](../config/route-manifest-schema.json) - JSON Schema for validation
- [`config/route-manifest.json`](../config/route-manifest.json) - Generated canonical manifest

**Generation**:
- [`scripts/generate-route-manifest.mjs`](../scripts/generate-route-manifest.mjs) - Generation script

## Usage

### 1. Generate Manifest (During Build)

```bash
cd products/data-cloud

# One-time setup
npm install

# Generate canonical manifest
npm run generate:route-manifest

# Or manually
node scripts/generate-route-manifest.mjs \
  --java-registry-path delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java \
  --output-manifest config/route-manifest.json \
  --output-ui-truth web/src/runtime/RuntimeTruthPosture.generated.ts \
  --manifest-schema config/route-manifest-schema.json
```

### 2. Validate Manifest

```bash
# Validate generated manifest against schema
npm run validate:route-manifest

# Or using JSON Schema validator
npm install -g ajv-cli
ajv validate -s config/route-manifest-schema.json -d config/route-manifest.json
```

### 3. Add New Route (Developer Workflow)

**Step 1**: Add route to `RouteSecurityRegistry.java`
```java
routes.put(
    new RouteActionAccessRegistry.RouteKey("POST", "/api/v1/action/new-feature"),
    new RouteSecurityMetadata(
        "POST",
        "/api/v1/action/new-feature",
        Sensitivity.SENSITIVE,
        ...
    )
);
```

**Step 2**: Regenerate manifest
```bash
npm run generate:route-manifest
```

**Step 3**: Validate no drift
```bash
npm run validate:route-manifest
```

**Step 4**: Commit generated files
```bash
git add config/route-manifest.json web/src/runtime/RuntimeTruthPosture.generated.ts
git commit -m "feat: Add new-feature route (DC-P0-03)"
```

## Route Manifest Structure

Each route entry includes:

```json
{
  "method": "POST",
  "path": "/api/v1/action/pipelines",
  "sensitivity": "CRITICAL",
  "requiresAuth": true,
  "requiresTenant": true,
  "requiresPolicy": true,
  "idempotent": false,
  "description": "Execute a pipeline...",
  "operationId": "postActionPipelines",
  "tags": ["Action Plane"],
  "requestSchema": "#/components/schemas/ExecuteActionRequest",
  "responseSchema": "#/components/schemas/ExecuteActionResponse",
  "runtimeTruthSurface": "VISIBLE",
  "category": "Action Plane",
  "metadata": { ... }
}
```

**Key Fields**:
- `method`, `path`: HTTP route identifier
- `sensitivity`: CRITICAL/SENSITIVE/INTERNAL/PUBLIC (policy level)
- `requires*`: Auth, tenant, policy requirements
- `operationId`: OpenAPI unique identifier
- `runtimeTruthSurface`: VISIBLE/HIDDEN/DEVELOPER_ONLY (UI gating)
- `metadata`: Route-specific metadata (audit, idempotency, etc.)

## Generated Artifacts

### 1. Route Manifest JSON

**File**: `config/route-manifest.json`

Jackson-deserializable JSON containing all routes with full metadata.

Used for:
- OpenAPI validation
- SDK generation
- CI/CD drift detection

### 2. UI RuntimeTruthPosture.generated.ts

**File**: `web/src/runtime/RuntimeTruthPosture.generated.ts`

TypeScript module providing:

```typescript
// Find route by method and path
findRoute(method: string, pathPattern: string): RuntimeRoute | undefined

// Get routes by sensitivity level
getRoutesBySensitivity(level: 'CRITICAL' | 'SENSITIVE' | 'INTERNAL' | 'PUBLIC'): RuntimeRoute[]

// Check if route requires policy
requiresPolicy(method: string, path: string): boolean
```

Used for:
- Feature gating (show/hide actions)
- Form validation (check if endpoint exists)
- Error messages (accurate route info)

## CI/CD Integration

### .github/workflows/data-cloud-ci.yml

```yaml
- name: DC-P0-03 Generate Route Manifest
  run: |
    cd products/data-cloud
    npm run generate:route-manifest

- name: DC-P0-03 Validate Route Manifest
  run: |
    cd products/data-cloud
    npm run validate:route-manifest
    git diff --exit-code config/route-manifest.json \
      || (echo "Route manifest drift detected!" && exit 1)

- name: DC-P0-03 Verify UI Truth Sync
  run: |
    cd products/data-cloud
    git diff --exit-code web/src/runtime/RuntimeTruthPosture.generated.ts \
      || (echo "RuntimeTruthPosture out of sync!" && exit 1)
```

**Execution**:
1. ✅ Merged PR regenerates manifest
2. ✅ Manifest validated against schema
3. ✅ OpenAPI drift detected
4. ✅ UI truth sync verified
5. ❌ CI fails if any drift found

## Implementation Roadmap

### Phase 1: Foundation (COMPLETE ✅)
- [x] RouteSecurityRegistry.java (30+ routes documented)
- [x] RouteManifest.java (Jackson-serializable model)
- [x] route-manifest-schema.json (JSON Schema validation)
- [x] generate-route-manifest.mjs (generation script)
- [x] config/route-manifest.json (example manifest)

### Phase 2: UI Generation (NEXT)
- [ ] Implement manifest → RuntimeTruthPosture.generated.ts generation
- [ ] Add UI tests using RuntimeTruthPosture
- [ ] Update TypeScript strict mode to verify usage

### Phase 3: OpenAPI Validation (FOLLOWING)
- [ ] Create OpenAPI → manifest validator
- [ ] Update action-plane.yaml from manifest
- [ ] Add OpenAPI spec validation to CI

### Phase 4: SDK Generation (LATER)
- [ ] Generate SDK metadata from manifest
- [ ] SDK client library generation (Java, TypeScript)

### Phase 5: Documentation (LATER)
- [ ] Auto-generate API route matrix
- [ ] Generate deprecation notices from manifest
- [ ] Create interactive API explorer

## Testing

### Unit Tests

**File**: `delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/RouteManifestTest.java`

```java
@Test
void manifestSerializationRoundtrip() {
  // Serialize and deserialize
  String json = mapper.writeValueAsString(manifest);
  RouteManifest deserialized = mapper.readValue(json, RouteManifest.class);
  
  // Verify all routes preserved
  assertEquals(manifest.getRoutes().size(), deserialized.getRoutes().size());
}

@Test
void manifestValidatesAgainstSchema() {
  // Load schema and manifest
  // Validate using JSON Schema validator
  // Expect: validation passes
}

@Test
void allRoutesHaveOperationId() {
  manifest.getRoutes().forEach(route -> 
    assertNotNull(route.getOperationId(), 
      "Route missing operationId: " + route.getMethod() + " " + route.getPath())
  );
}
```

### Integration Tests

**File**: `web/__tests__/runtime/RuntimeTruthPosture.generated.test.ts`

```typescript
it('should find route by method and path', () => {
  const route = findRoute('POST', '/api/v1/action/pipelines');
  expect(route).toBeDefined();
  expect(route!.sensitivity).toBe('CRITICAL');
});

it('should return routes filtered by sensitivity', () => {
  const critical = getRoutesBySensitivity('CRITICAL');
  expect(critical.length).toBeGreaterThan(0);
  expect(critical.every(r => r.sensitivity === 'CRITICAL')).toBe(true);
});
```

## References

- [Ghatana Platform Guidelines - API Design Patterns](../../../docs/GHATANA_PLATFORM_GUIDELINES.md#20-api-design-patterns)
- [Route Security Classification](../../../docs/route-security-classification.md) (forthcoming)
- [OpenAPI Spec](../docs/api/action-plane.yaml)
- [Event Store Routes](../docs/api/data-cloud.yaml)

## Questions & Support

For questions about route manifest generation, contact:
- Platform Architecture team
- Data Cloud product lead
- Integration team (SDK generation)
