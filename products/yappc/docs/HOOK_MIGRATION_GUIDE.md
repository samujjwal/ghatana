# Canvas Hook Migration Guide

**Version:** 1.0  
**Date:** 2026-03-07  
**Status:** Ready for Implementation

---

## 🎯 Overview

This guide provides step-by-step instructions for migrating from **37 individual canvas hooks** to **10 consolidated hooks**, achieving a **73% reduction** in hook count while improving maintainability and developer experience.

---

## 📊 Migration Summary

### Before (37 hooks)
- `useCanvasApi`, `useCanvasPortal`, `useAuth`
- `useCollaboration`, `useAdvancedCollaboration`, `useCanvasCollaborationBackend`, `useBidirectionalSync`
- `useAIBrainstorming`, `useComponentGeneration`, `useCodeScaffold`, `useRequirementWireframer`, `useMicroservicesExtractor`, `useServiceBlueprint`
- `useCICDPipeline`, `useCloudInfrastructure`, `useDataPipeline`, `useReleaseTrain`, `useServiceHealth`, `usePerformanceAnalysis`
- `useCISODashboard`, `useCompliance`, `useThreatModeling`, `useZeroTrustArchitecture`
- `useTemplateActions`
- `useMobileCanvas`
- `useUserJourney`
- `useFullStackMode`
- Plus 10+ other specialized hooks

### After (10 hooks)
1. `useCanvasCore` - Core functionality
2. `useCanvasCollaboration` - Real-time features
3. `useCanvasAI` - AI-powered features
4. `useCanvasInfrastructure` - DevOps & infrastructure
5. `useCanvasSecurity` - Security & compliance
6. `useCanvasTemplates` - Template management
7. `useCanvasMobile` - Mobile-specific features
8. `useCanvasUserJourney` - Journey mapping
9. `useCanvasFullStack` - Full-stack mode
10. `useCanvasAnalytics` - Analytics & insights

---

## 🔄 Migration Steps

### Step 1: Update Imports

**Before:**
```typescript
import { useCanvasApi } from '@ghatana/canvas';
import { useAuth } from '@ghatana/canvas';
import { useCollaboration } from '@ghatana/canvas';
```

**After:**
```typescript
import { useCanvasCore, useCanvasCollaboration } from '@ghatana/canvas';
```

### Step 2: Update Hook Initialization

**Before:**
```typescript
const { nodes, edges, addNode } = useCanvasApi({ canvasId });
const { user, permissions } = useAuth();
const { activeUsers, comments } = useCollaboration({ canvasId });
```

**After:**
```typescript
const canvas = useCanvasCore({
  canvasId: 'canvas-1',
  tenantId: 'tenant-1',
  userId: 'user-1',
  mode: 'edit'
});

const collab = useCanvasCollaboration({
  canvasId: 'canvas-1',
  userId: 'user-1',
  enablePresence: true,
  enableCursors: true
});
```

### Step 3: Update Method Calls

**Before:**
```typescript
addNode({ type: 'default', position: { x: 0, y: 0 } });
```

**After:**
```typescript
canvas.addNode({ type: 'default', position: { x: 0, y: 0 } });
```

---

## 📋 Hook-by-Hook Migration

### 1. useCanvasCore

**Replaces:** `useCanvasApi`, `useCanvasPortal`, `useAuth`

**Migration:**
```typescript
// Before
import { useCanvasApi, useAuth } from '@ghatana/canvas';

const { nodes, edges, addNode, updateNode, deleteNode } = useCanvasApi({ canvasId });
const { user, canEdit } = useAuth();

// After
import { useCanvasCore } from '@ghatana/canvas';

const {
  nodes,
  edges,
  addNode,
  updateNode,
  deleteNode,
  canEdit,
  save
} = useCanvasCore({
  canvasId: 'canvas-1',
  tenantId: 'tenant-1',
  userId: 'user-1',
  mode: 'edit'
});
```

**Breaking Changes:**
- Options object is now required
- `tenantId` and `userId` are required parameters
- Auth is integrated (no separate `useAuth` needed)

---

### 2. useCanvasCollaboration

**Replaces:** `useCollaboration`, `useAdvancedCollaboration`, `useCanvasCollaborationBackend`, `useBidirectionalSync`

**Migration:**
```typescript
// Before
import { useCollaboration } from '@ghatana/canvas';

const { activeUsers, updateCursor, addComment } = useCollaboration({ canvasId });

// After
import { useCanvasCollaboration } from '@ghatana/canvas';

const {
  activeUsers,
  updateCursor,
  addComment,
  syncStatus,
  conflicts,
  resolveConflict
} = useCanvasCollaboration({
  canvasId: 'canvas-1',
  userId: 'user-1',
  enablePresence: true,
  enableCursors: true,
  enableComments: true
});
```

**New Features:**
- Built-in conflict resolution
- WebSocket auto-reconnect
- Sync status tracking

---

### 3. useCanvasAI

**Replaces:** `useAIBrainstorming`, `useComponentGeneration`, `useCodeScaffold`, `useRequirementWireframer`, `useMicroservicesExtractor`, `useServiceBlueprint`

**Migration:**
```typescript
// Before
import { useAIBrainstorming, useComponentGeneration } from '@ghatana/canvas';

const { generateIdeas } = useAIBrainstorming({ canvasId });
const { generateComponent } = useComponentGeneration({ canvasId });

// After
import { useCanvasAI } from '@ghatana/canvas';

const {
  generateIdeas,
  generateComponent,
  generateScaffold,
  generateWireframe,
  extractMicroservices,
  generateBlueprint,
  isGenerating,
  error
} = useCanvasAI({
  canvasId: 'canvas-1',
  tenantId: 'tenant-1',
  enabledFeatures: ['brainstorming', 'component-generation']
});

// Usage
const ideas = await generateIdeas('Create a login page');
const component = await generateComponent({
  name: 'LoginForm',
  framework: 'react',
  styling: 'tailwind'
});
```

**Benefits:**
- Single loading state for all AI operations
- Unified error handling
- Feature flags for selective enablement

---

### 4. useCanvasInfrastructure

**Replaces:** `useCICDPipeline`, `useCloudInfrastructure`, `useDataPipeline`, `useReleaseTrain`, `useServiceHealth`, `usePerformanceAnalysis`

**Migration:**
```typescript
// Before
import { useCICDPipeline, useCloudInfrastructure } from '@ghatana/canvas';

const { pipelines, createPipeline } = useCICDPipeline({ canvasId });
const { resources, deployResource } = useCloudInfrastructure({ canvasId });

// After
import { useCanvasInfrastructure } from '@ghatana/canvas';

const {
  pipelines,
  createPipeline,
  resources,
  deployResource,
  dataPipelines,
  releases,
  healthStatus,
  performanceMetrics
} = useCanvasInfrastructure({
  canvasId: 'canvas-1',
  provider: 'aws'
});
```

---

### 5. useCanvasSecurity

**Replaces:** `useCISODashboard`, `useCompliance`, `useThreatModeling`, `useZeroTrustArchitecture`

**Migration:**
```typescript
// Before
import { useCompliance, useThreatModeling } from '@ghatana/canvas';

const { complianceStatus } = useCompliance({ canvasId });
const { threats, generateThreatModel } = useThreatModeling({ canvasId });

// After
import { useCanvasSecurity } from '@ghatana/canvas';

const {
  securityPosture,
  vulnerabilities,
  complianceStatus,
  threats,
  generateThreatModel,
  zeroTrustPolicies,
  validateAccess
} = useCanvasSecurity({
  canvasId: 'canvas-1',
  tenantId: 'tenant-1',
  complianceFrameworks: ['SOC2', 'HIPAA']
});
```

---

### 6. useCanvasTemplates

**Replaces:** `useTemplateActions`

**Migration:**
```typescript
// Before
import { useTemplateActions } from '@ghatana/canvas';

const { applyTemplate, saveAsTemplate } = useTemplateActions({ canvasId });

// After
import { useCanvasTemplates } from '@ghatana/canvas';

const {
  templates,
  applyTemplate,
  saveAsTemplate,
  deleteTemplate,
  updateTemplate
} = useCanvasTemplates({
  canvasId: 'canvas-1',
  category: 'workflow'
});
```

---

### 7. useCanvasMobile

**Replaces:** `useMobileCanvas`

**Migration:**
```typescript
// Before
import { useMobileCanvas } from '@ghatana/canvas';

const { isMobile, handleTouch } = useMobileCanvas({ canvasId });

// After
import { useCanvasMobile } from '@ghatana/canvas';

const {
  onPinchZoom,
  onPan,
  onDoubleTap,
  isMobileView,
  showMobileToolbar,
  viewportSize,
  orientation
} = useCanvasMobile({
  canvasId: 'canvas-1',
  enableGestures: true
});
```

---

### 8. useCanvasUserJourney

**Replaces:** `useUserJourney`

**Migration:**
```typescript
// Before
import { useUserJourney } from '@ghatana/canvas';

const { journeys, createJourney } = useUserJourney({ canvasId });

// After
import { useCanvasUserJourney } from '@ghatana/canvas';

const {
  journeys,
  createJourney,
  updateJourney,
  deleteJourney,
  analyzeJourney
} = useCanvasUserJourney({
  canvasId: 'canvas-1',
  persona: myPersona
});
```

---

### 9. useCanvasFullStack

**Replaces:** `useFullStackMode`

**Migration:**
```typescript
// Before
import { useFullStackMode } from '@ghatana/canvas';

const { mode, setMode } = useFullStackMode({ canvasId });

// After
import { useCanvasFullStack } from '@ghatana/canvas';

const {
  mode,
  setMode,
  layers,
  activeLayer,
  setActiveLayer,
  isSplitView,
  splitRatio,
  setSplitRatio
} = useCanvasFullStack({
  canvasId: 'canvas-1',
  stack: 'all'
});
```

---

### 10. useCanvasAnalytics (NEW)

**New Hook - No Migration Needed**

```typescript
import { useCanvasAnalytics } from '@ghatana/canvas';

const {
  canvasMetrics,
  userActivity,
  recommendations,
  trackEvent,
  getInsights
} = useCanvasAnalytics({
  canvasId: 'canvas-1',
  tenantId: 'tenant-1',
  enableTracking: true
});

// Track events
trackEvent('node-created', { nodeType: 'component' });
```

---

## ⚠️ Breaking Changes

### 1. Required Options

All new hooks require an options object:

```typescript
// ❌ Old (no options)
const { nodes } = useCanvasApi();

// ✅ New (options required)
const { nodes } = useCanvasCore({
  canvasId: 'canvas-1',
  tenantId: 'tenant-1',
  userId: 'user-1'
});
```

### 2. Return Value Structure

Hooks now return objects instead of arrays:

```typescript
// ❌ Old (array destructuring)
const [nodes, setNodes] = useCanvasApi();

// ✅ New (object destructuring)
const { nodes, addNode, updateNode } = useCanvasCore({ ... });
```

### 3. Async Operations

All mutations are now async:

```typescript
// ❌ Old (synchronous)
addNode(newNode);

// ✅ New (async)
await canvas.addNode(newNode);
```

---

## 🧪 Testing Migration

### Update Test Imports

```typescript
// Before
import { useCanvasApi } from '@ghatana/canvas';
import { renderHook } from '@testing-library/react';

const { result } = renderHook(() => useCanvasApi({ canvasId: 'test' }));

// After
import { useCanvasCore } from '@ghatana/canvas';
import { renderHook } from '@testing-library/react';

const { result } = renderHook(() =>
  useCanvasCore({
    canvasId: 'test',
    tenantId: 'test-tenant',
    userId: 'test-user'
  })
);
```

### Mock Updates

```typescript
// Before
jest.mock('@ghatana/canvas', () => ({
  useCanvasApi: () => ({
    nodes: [],
    addNode: jest.fn()
  })
}));

// After
jest.mock('@ghatana/canvas', () => ({
  useCanvasCore: () => ({
    nodes: [],
    edges: [],
    addNode: jest.fn(),
    canEdit: true,
    save: jest.fn()
  })
}));
```

---

## 📅 Migration Timeline

### Phase 1: Preparation (Week 1)
- [ ] Review this migration guide
- [ ] Identify all components using old hooks
- [ ] Create migration checklist
- [ ] Set up feature flags for gradual rollout

### Phase 2: Core Migration (Week 2)
- [ ] Migrate `useCanvasCore` consumers
- [ ] Migrate `useCanvasCollaboration` consumers
- [ ] Migrate `useCanvasAI` consumers
- [ ] Run integration tests

### Phase 3: Specialized Migration (Week 3)
- [ ] Migrate infrastructure hooks
- [ ] Migrate security hooks
- [ ] Migrate templates/mobile/journey hooks
- [ ] Update all tests

### Phase 4: Cleanup (Week 4)
- [ ] Deprecate old hooks
- [ ] Remove old hook imports
- [ ] Update documentation
- [ ] Monitor for issues

---

## 🔍 Verification Checklist

After migration, verify:

- [ ] All imports updated
- [ ] All hook calls use new API
- [ ] All tests passing
- [ ] No TypeScript errors
- [ ] No runtime errors
- [ ] Performance unchanged or improved
- [ ] Bundle size reduced
- [ ] No deprecated warnings

---

## 🆘 Troubleshooting

### Issue: TypeScript errors after migration

**Solution:** Ensure you're importing from the correct path and using the new type definitions:

```typescript
import type { UseCanvasCoreOptions, UseCanvasCoreReturn } from '@ghatana/canvas';
```

### Issue: Missing methods

**Solution:** Check which consolidated hook provides the method you need. Use the mapping table above.

### Issue: Async/await errors

**Solution:** All mutations are now async. Add `await` or `.then()`:

```typescript
// Before
addNode(node);

// After
await canvas.addNode(node);
// or
canvas.addNode(node).then(() => { ... });
```

### Issue: Options not recognized

**Solution:** Ensure all required options are provided:

```typescript
useCanvasCore({
  canvasId: 'required',
  tenantId: 'required',
  userId: 'required',
  mode: 'edit' // optional
});
```

---

## 📚 Additional Resources

- [Hook Consolidation Plan](./HOOK_CONSOLIDATION_PLAN.md)
- [Hook Consolidation Progress](./HOOK_CONSOLIDATION_PROGRESS.md)
- [API Reference](./API_REFERENCE.md) (coming soon)
- [Migration Examples](./examples/migration/) (coming soon)

---

## 💬 Support

For migration assistance:
- Check the [FAQ](./FAQ.md)
- Review [examples](./examples/)
- Ask in #canvas-migration Slack channel
- File an issue on GitHub

---

**Last Updated:** 2026-03-07  
**Version:** 1.0  
**Status:** Ready for Implementation
