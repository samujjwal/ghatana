# DTO / Enum Duplicate Inventory (DC-P1-388)

**Created**: 2026-05-04  
**Canonical Source of Truth**: `products/data-cloud/contracts/openapi/data-cloud.yaml`  
**Status**: Active — migration pending for items marked DRIFT

---

## Summary

| Enum / DTO | Java Locations | TypeScript Locations | OpenAPI | Status |
|------------|---------------|---------------------|---------|--------|
| `StorageTier` | 3 (1 canonical + 2 duplicates) | 1 (theme-only, no contract) | As `RetentionTier` | **DUPLICATE (Java)** |
| `RetentionTier` | `StorageTier.java` in shared-spi | `RetentionTierSchema` in governance.service.ts | ✅ Defined (line ~300) | **ALIGNED** |
| `RecordType` | 2 (1 canonical + 1 duplicate) | Mixed string literals | ✅ Defined (line ~344) | **DUPLICATE (Java), DRIFT (case)** |
| `FieldType` | 2 (different domains) | Inferred string (`inferFieldType`) | Not explicitly named | **DUPLICATE (Java)** |
| `CollectionStatus` | Java `LifecycleStatus` | 2 Zod schemas with different values | ✅ Defined as `[ACTIVE, INACTIVE, TESTING, ERROR, SYNCING]` | **DRIFT** |
| `CollectionSchemaType` | `RecordType` in shared-spi | `schemaType` in contracts/schemas.ts (lowercase) | ✅ Defined as uppercase `[ENTITY, EVENT, TIMESERIES, DOCUMENT, GRAPH]` | **DRIFT (case)** |
| `ReportType` | 2 locations (analytics + AEP namespace) | Not defined | Not in data-cloud.yaml | **DUPLICATE (Java)** |
| `NodeStatus` | 2 locations (ClusterManagement, LoadBalancer) | Not defined | Not in data-cloud.yaml | **INTENTIONAL (different contexts)** |

---

## 1. StorageTier — Java Duplicates

**Canonical**: `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/StorageTier.java`

**Duplicates** (must be migrated to use canonical):
1. `products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/model/CompiledPluginConfig.java` — inner `enum StorageTier` at line 72
2. `products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/model/CompiledStorageProfileConfig.java` — inner `enum StorageTier` at line 33

**Migration action**: Remove inner enum definitions, import and use `com.ghatana.datacloud.StorageTier` directly.

**Risk**: `CompiledPluginConfig` and `CompiledStorageProfileConfig` may map values differently. Verify value alignment before removing.

---

## 2. RecordType — Java Duplicates

**Canonical**: `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/RecordType.java`

**Duplicate**:
- `products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/model/CompiledCollectionConfig.java` — inner `enum RecordType` at line 168

**Migration action**: Migrate inner enum to use `com.ghatana.datacloud.RecordType`.

---

## 3. FieldType — Java Duplicates (Different Domains)

**Location 1** (SPI domain):  
`products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/FieldDefinition.java` — inner `enum FieldType` at line 150

**Location 2** (Config domain):  
`products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/model/FieldType.java` — top-level enum at line 15

**Decision**: These serve different bounded contexts (SPI vs Config). The config `FieldType` is used exclusively in config model compilation, while `FieldDefinition.FieldType` is used in runtime queries. **Treat as intentional** but add explicit `@doc.*` tags and comments noting they are distinct.

**Action required**: Add Javadoc to both noting the intentional separation and that they must not be merged without cross-team review.

---

## 4. ReportType — Java Duplicates Across Namespaces

**Location 1** (Analytics):  
`products/data-cloud/planes/intelligence/analytics/src/main/java/com/ghatana/datacloud/analytics/report/ReportType.java`

**Location 2** (AEP namespace, inside Data Cloud planes):  
`products/data-cloud/planes/action/server/src/main/java/com/ghatana/aep/server/report/AepReportingService.java` — inner `enum ReportType` at line 405

**Decision**: These are in completely different namespaces (`datacloud.analytics` vs `aep.server`). Both may be valid. However if the AEP enum is only used within `AepReportingService`, it should remain as a local inner enum. The analytics one is more broadly reusable.

**Action required**: Verify `AepReportingService.ReportType` values match `analytics.report.ReportType`. If values overlap, extract shared enum to shared-spi.

---

## 5. RetentionTier — ALIGNED ✅

**OpenAPI** (data-cloud.yaml, ~line 300): `[transient, short-term, standard, compliance, permanent]`

**TypeScript** (governance.service.ts):
```ts
const RetentionTierSchema = z.enum(['transient', 'short-term', 'standard', 'compliance', 'permanent']);
```

**Java** (StorageTier.java in shared-spi): Values are `HOT, WARM, COLD, ARCHIVE, LOCKED`  
Note: Java `StorageTier` is a *storage infrastructure* concept (HOT/WARM/COLD) while OpenAPI `retentionTier` is a *compliance/governance* concept (transient/permanent). These are intentionally different — the naming similarity is misleading.

**No action required.** The TypeScript Zod schema matches the OpenAPI exactly.

---

## 6. CollectionSchemaType — DRIFT ❌

**OpenAPI** (data-cloud.yaml, ~line 344): `[ENTITY, EVENT, TIMESERIES, DOCUMENT, GRAPH]` (UPPERCASE)

**TypeScript** (contracts/schemas.ts, line 46):
```ts
schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document'])
```
(lowercase)

**Drift**: Case mismatch. If the API returns uppercase values (as per OpenAPI), the Zod schema would fail to parse responses. However, the current UI may be normalising case before validation.

**Migration action**: Align Zod enum to uppercase values matching OpenAPI. Update any UI rendering logic that depends on lowercase string comparison.

---

## 7. CollectionStatus — DRIFT ❌

**OpenAPI** (data-cloud.yaml, ~line 252): `[ACTIVE, INACTIVE, TESTING, ERROR, SYNCING]`

**TypeScript — Location 1** (lib/schemas.ts, line 17):
```ts
export const CollectionStatusSchema = z.enum(['active', 'inactive', 'archived']);
```

**TypeScript — Location 2** (contracts/schemas.ts, line 47):
```ts
status: z.enum(['active', 'draft', 'archived', 'processing'])
```

**Drift**:
- Two TypeScript definitions with different values
- Neither matches OpenAPI (`archived`, `draft`, `processing` absent from OpenAPI; `TESTING`, `ERROR`, `SYNCING` absent from TypeScript)

**Migration action**:
1. Choose canonical Zod schema (recommend `contracts/schemas.ts` as the API-contract-aligned one)
2. Align values to match OpenAPI (uppercase `ACTIVE`, `INACTIVE`, `TESTING`, `ERROR`, `SYNCING`)
3. Remove `lib/schemas.ts::CollectionStatusSchema` or make it derive from the canonical one
4. Update components that use lowercase status strings

---

## 8. NodeStatus — Intentional Duplicates ✅

**Location 1**: `ClusterManagementModels.java` — represents cluster node lifecycle
**Location 2**: `LoadBalancerModels.java` — represents load balancer backend node health

These are different bounded contexts (cluster management vs load balancing) and the enums may have different values. Keep as separate enums. Document with `@doc.*` tags.

---

## Enforcement

The drift test covering the TypeScript/OpenAPI alignment is in:
```
products/data-cloud/delivery/ui/src/__tests__/api/schemaDrift.test.ts
```

The Java ArchUnit boundary tests are in:
```
platform/java/core/src/test/java/com/ghatana/platform/architecture/PlatformDataCloudSemanticBoundaryTest.java
```

---

## Migration Priority

| Priority | Item | Effort |
|----------|------|--------|
| P0 | Fix CollectionStatus drift — two incompatible Zod schemas | Low |
| P0 | Fix CollectionSchemaType case drift (lowercase vs uppercase) | Low |
| P1 | Remove StorageTier Java duplicates | Medium |
| P1 | Remove RecordType Java duplicate | Low |
| P2 | Add @doc tags to intentional FieldType and NodeStatus duplicates | Low |
| P2 | Cross-verify AEP ReportType against analytics ReportType | Low |
