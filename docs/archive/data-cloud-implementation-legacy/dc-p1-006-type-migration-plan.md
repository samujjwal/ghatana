# DC-P1-006: Frontend API Type Migration Plan

**Date:** 2026-05-09
**Status:** Migration Plan Created

---

## Overview

This document maps ad hoc TypeScript types in API services to generated types from the OpenAPI specification (`data-cloud.yaml`). The goal is to eliminate ad hoc type definitions and use the generated types as the single source of truth for frontend-backend API contracts.

---

## Ad Hoc Types Audit

### events.service.ts
**Ad Hoc Types:**
- `EventTier` = 'HOT' | 'WARM' | 'COOL' | 'COLD'
- `EventEntry` - interface with id, tenantId, eventType, tier, payload, timestamp, idempotencyKey, correlationId, source, metadata
- `EventStats` - interface with total, byTier, byType, eventsPerMinute
- `EventListResponse` - interface with events, total, hasMore
- `EventQueryParams` - interface with tenantId, eventType, tier, from, to, limit, cursor

**Migration Strategy:**
- Map `EventTier` to generated type from OpenAPI if available
- Map `EventEntry` to generated event type from OpenAPI
- Map `EventStats` to generated stats type from OpenAPI
- Map `EventListResponse` to generated list response type from OpenAPI
- Map `EventQueryParams` to generated query params type from OpenAPI

**Note:** Some types may need to be retained as domain-specific transformations (e.g., tier derivation logic).

---

### surfaces.service.ts
**Ad Hoc Types:**
- `SurfaceStatus` = 'LIVE' | 'DEGRADED' | 'DISABLED' | 'PREVIEW' | 'UNAVAILABLE' | 'MISCONFIGURED'
- `SurfaceSignal` - interface with key, label, status, summary, detail, rawValue
- `SurfaceRegistrySnapshot` - interface with generatedAt, requestId, tenantId, surfaces
- `CapabilityStatus` = 'active' | 'degraded' | 'unavailable' | 'unknown'
- `CapabilitySignal` - interface with key, label, status, summary, detail, rawValue

**Migration Strategy:**
- Map `SurfaceStatus` to generated type from OpenAPI if available
- Map `SurfaceSignal` to generated capability signal type from OpenAPI
- Map `SurfaceRegistrySnapshot` to generated registry type from OpenAPI
- Map `CapabilityStatus` to generated type from OpenAPI if available
- Map `CapabilitySignal` to generated type from OpenAPI if available

**Note:** These are canonical normalization types (DC-P1-001) that may not have direct OpenAPI equivalents. May need to retain with proper documentation.

---

### agent-registry.service.ts
**Ad Hoc Types:**
- `AgentStatus` = 'ACTIVE' | 'INACTIVE' | 'ERROR' | 'REGISTERING' | 'DEREGISTERING'
- `ExecutionStatus` = 'CREATED' | 'INITIALIZED' | 'RUNNING' | 'STOPPED' | 'FAILED'
- `AgentCapability` = BackendAgentCapability (re-export)
- `AgentDefinition` - interface with agentId, name, description, version, capabilities, status, executionStatus

**Migration Strategy:**
- Map `AgentStatus` to generated type from OpenAPI if available
- Map `ExecutionStatus` to generated type from OpenAPI if available
- Map `AgentCapability` to generated type from OpenAPI
- Map `AgentDefinition` to generated agent definition type from OpenAPI

---

### ai-operations.service.ts
**Ad Hoc Types:**
- `AiOperationSurface` = z.infer<typeof AiOperationSurfaceSchema>
- `AiConfidenceBand` = z.infer<typeof AiConfidenceBandSchema>
- `AiOperationSuggestion` = z.infer<typeof AiOperationSuggestionSchema>
- `AiApplySuggestionResponse` = z.infer<typeof AiApplySuggestionResponseSchema>
- `AiCrossCorrelation` = z.infer<typeof AiCrossCorrelationSchema>
- `AiWorkflowAdvisory` = z.infer<typeof AiWorkflowAdvisorySchema>
- `AiQualityAdvisory` = z.infer<typeof AiQualityAdvisorySchema>
- `AiFabricAdvisory` = z.infer<typeof AiFabricAdvisorySchema>

**Migration Strategy:**
- These are already using Zod schemas from `contracts/schemas`
- Migration to generated types should align with Zod schema definitions
- Consider keeping Zod schemas for runtime validation and using generated types for type safety

---

### alerts.service.ts
**Ad Hoc Types:**
- `AlertSeverity` = "critical" | "warning" | "info"
- `AlertStatus` = "active" | "acknowledged" | "resolved"
- `Alert` - interface with id, severity, status, title, description, timestamp, source, metadata

**Migration Strategy:**
- Map `AlertSeverity` to generated type from OpenAPI if available
- Map `AlertStatus` to generated type from OpenAPI if available
- Map `Alert` to generated alert type from OpenAPI

---

### analytics.service.ts
**Ad Hoc Types:**
- `AnalyticsExplainResult` = AnalyticsExplainResponse (re-export)
- `QueryResultData` = AnalyticsSqlQueryResponse (re-export)

**Migration Strategy:**
- These are already re-exporting backend types from contracts/schemas
- Migration to generated types should align with existing schema definitions

---

### brain.service.ts
**Ad Hoc Types:**
- `LearningSignal` = BackendLearningSignal (re-export)
- `BrainStats` - interface with totalRecordsProcessed, totalSignals, lastSignalTimestamp

**Migration Strategy:**
- Map `LearningSignal` to generated type from OpenAPI if available
- Map `BrainStats` to generated stats type from OpenAPI if available

---

### cost.service.ts
**Ad Hoc Types:**
- `MigrationTargetTier` = 'WARM' | 'COLD'
- `MigrateCollectionResult` = SharedMigrateCollectionResult (re-export)

**Migration Strategy:**
- Map `MigrationTargetTier` to generated type from OpenAPI if available
- Map `MigrateCollectionResult` to generated type from OpenAPI

---

### governance.service.ts
**Ad Hoc Types:**
- `RetentionTier` = z.infer<typeof RetentionTierSchema>
- `RetentionClassificationRequest` - interface with collection, tier
- `GovernanceRecommendationAction` = 'classify-retention' | 'redact-pii' | 'refresh-compliance'
- `GovernanceOperationalAction` = GovernanceRecommendationAction | 'purge-retention' | 'create-policy'
- `PolicySimulationResult` = GovernancePolicySimulationResult (re-export)
- `TenantGovernanceInventory` = GovernanceInventory (re-export)
- `Policy` - interface with id, name, type, scope, rules, status, createdAt, updatedAt

**Migration Strategy:**
- Map `RetentionTier` to generated type from OpenAPI if available
- Map `RetentionClassificationRequest` to generated type from OpenAPI
- Map governance action types to generated types from OpenAPI
- Map policy types to generated types from OpenAPI

---

### memory.service.ts
**Ad Hoc Types:**
- `MemoryType` = 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL' | 'PREFERENCE'
- `MemoryItem` = BackendMemoryItem (re-export)
- `MemorySearchParams` - interface with tenantId, type, query, limit

**Migration Strategy:**
- Map `MemoryType` to generated type from OpenAPI if available
- Map `MemoryItem` to generated type from OpenAPI
- Map `MemorySearchParams` to generated type from OpenAPI

---

### plugin.service.ts
**Ad Hoc Types:**
- `PluginStatus` = 'active' | 'inactive' | 'error' | 'installing' | 'uninstalling'
- `PluginCategory` = 'connector' | 'transformer' | 'quality' | 'governance' | 'visualization' | 'integration' | 'ai'
- `PluginMetadata` - interface with id, name, version, description, category, status, capabilities

**Migration Strategy:**
- Map `PluginStatus` to generated type from OpenAPI if available
- Map `PluginCategory` to generated type from OpenAPI if available
- Map `PluginMetadata` to generated plugin type from OpenAPI

---

### settings.service.ts
**Ad Hoc Types:**
- `ApiKey` = z.infer<typeof ApiKeySchema>
- `ApiKeyCreateRequest` = z.infer<typeof ApiKeyCreateRequestSchema>
- `ApiKeyCreateResponse` = z.infer<typeof ApiKeyCreateResponseSchema>
- `UserProfile` = z.infer<typeof UserProfileSchema>
- `UserProfileUpdateRequest` = z.infer<typeof UserProfileUpdateRequestSchema>
- `UserPreferences` = z.infer<typeof UserPreferencesSchema>
- `UserPreferencesUpdateRequest` = z.infer<typeof UserPreferencesUpdateRequestSchema>
- `NotificationPreferences` = z.infer<typeof NotificationPreferencesSchema>
- `NotificationPreferencesUpdateRequest` = z.infer<typeof NotificationPreferencesUpdateRequestSchema>

**Migration Strategy:**
- These are already using Zod schemas from `contracts/schemas`
- Migration to generated types should align with Zod schema definitions
- Consider keeping Zod schemas for runtime validation and using generated types for type safety

---

## Migration Strategy

### Phase 1: Type Mapping (Week 1)
1. **Analyze generated types**
   - Examine `src/contracts/generated/data-cloud.ts` to understand available types
   - Map each ad hoc type to corresponding generated type
   - Identify types that don't have OpenAPI equivalents

2. **Create type mapping document**
   - Document which ad hoc types map to which generated types
   - Identify types that should be retained as domain-specific
   - Identify types that need to be added to OpenAPI spec

### Phase 2: Service Migration (Week 2)
1. **Migrate services with direct mappings**
   - Start with services that have clear OpenAPI equivalents
   - Replace ad hoc types with generated types
   - Update imports and type annotations
   - Ensure transformation logic is preserved

2. **Handle domain-specific types**
   - For types without OpenAPI equivalents, document why they're needed
   - Consider adding them to OpenAPI spec if appropriate
   - Otherwise, retain with clear documentation

### Phase 3: Validation Tests (Week 3)
1. **Add type compatibility tests**
   - Verify generated types are compatible with existing code
   - Test transformation logic with generated types
   - Ensure no breaking changes in API contracts

2. **Add contract tests**
   - Verify API responses match generated types
   - Test error handling with generated types
   - Ensure type safety is maintained

### Phase 4: Documentation (Week 4)
1. **Update developer documentation**
   - Document migration patterns
   - Document how to use generated types
   - Document when to add types to OpenAPI vs. retain domain-specific types

2. **Update ARCHITECTURE.md**
   - Document type generation strategy
   - Remove DC-P1-006 note (completed)

---

## Migration Priority

### High Priority (Direct OpenAPI Mappings)
- settings.service.ts (API keys, user profiles)
- governance.service.ts (retention, policies)
- plugin.service.ts (plugins)
- agent-registry.service.ts (agents)

### Medium Priority (Partial OpenAPI Mappings)
- events.service.ts (events - some types may be domain-specific)
- analytics.service.ts (analytics queries)
- cost.service.ts (cost, migrations)

### Low Priority (Domain-Specific Types)
- surfaces.service.ts (canonical normalization types)
- ai-operations.service.ts (AI-specific types already using Zod)
- brain.service.ts (learning signals)

---

## Success Criteria

- [ ] All ad hoc types with OpenAPI equivalents replaced with generated types
- [ ] Domain-specific types documented and justified
- [ ] Type compatibility tests pass
- [ ] Contract tests pass
- [ ] Developer documentation updated
- [ ] ARCHITECTURE.md updated
- [ ] No breaking changes in API contracts

---

**Report Version:** 1.0
**Last Updated:** 2026-05-09
