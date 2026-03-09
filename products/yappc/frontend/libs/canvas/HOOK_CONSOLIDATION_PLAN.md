# Canvas Hook Consolidation Plan

**Status:** Ready for Implementation  
**Priority:** High  
**Estimated Effort:** 3-5 days  
**Current Hook Count:** 37 hooks  
**Target Hook Count:** 8-10 consolidated hooks

---

## Problem Statement

The canvas library currently has **37 individual hooks**, which creates:
- **Maintenance burden** - Too many files to update
- **Inconsistent patterns** - Each hook implements similar logic differently
- **Poor discoverability** - Hard to find the right hook
- **Code duplication** - Similar logic repeated across hooks
- **Testing complexity** - 37 separate test files

---

## Proposed Consolidation

### Current Hook Categories (37 hooks)

**AI & Generation (7 hooks):**
- `useAIBrainstorming`
- `useComponentGeneration`
- `useCodeScaffold`
- `useRequirementWireframer`
- `useMicroservicesExtractor`
- `useServiceBlueprint`
- `useTemplateActions`

**Infrastructure & DevOps (6 hooks):**
- `useCICDPipeline`
- `useCloudInfrastructure`
- `useDataPipeline`
- `useReleaseTrain`
- `useServiceHealth`
- `usePerformanceAnalysis`

**Security & Compliance (4 hooks):**
- `useCISODashboard`
- `useCompliance`
- `useThreatModeling`
- `useZeroTrustArchitecture`

**Collaboration (4 hooks):**
- `useCollaboration`
- `useAdvancedCollaboration`
- `useCanvasCollaborationBackend`
- `useBidirectionalSync`

**Canvas Core (8 hooks):**
- `useCanvasApi`
- `useCanvasPortal`
- `useMobileCanvas`
- `useFullStackMode`
- `useUserJourney`
- `useAuth`
- (+ 2 more)

**Specialized (8 hooks):**
- Various domain-specific hooks

---

## Consolidated Hook Structure (10 hooks)

### 1. `useCanvasCore` - Core Canvas Functionality
**Consolidates:** `useCanvasApi`, `useCanvasPortal`, `useAuth`

```typescript
interface UseCanvasCoreOptions {
  canvasId: string;
  tenantId: string;
  userId: string;
  mode?: 'view' | 'edit' | 'fullstack';
}

interface UseCanvasCoreReturn {
  // Node/Edge operations
  nodes: Node[];
  edges: Edge[];
  addNode: (node: Node) => void;
  updateNode: (id: string, data: Partial<Node>) => void;
  deleteNode: (id: string) => void;
  
  // Canvas state
  viewport: Viewport;
  setViewport: (viewport: Viewport) => void;
  
  // Auth
  isAuthenticated: boolean;
  permissions: Permission[];
}

export function useCanvasCore(options: UseCanvasCoreOptions): UseCanvasCoreReturn;
```

### 2. `useCanvasCollaboration` - Real-time Collaboration
**Consolidates:** `useCollaboration`, `useAdvancedCollaboration`, `useCanvasCollaborationBackend`, `useBidirectionalSync`

```typescript
interface UseCanvasCollaborationOptions {
  canvasId: string;
  userId: string;
  enablePresence?: boolean;
  enableCursors?: boolean;
  enableComments?: boolean;
}

interface UseCanvasCollaborationReturn {
  // Presence
  activeUsers: User[];
  userCursors: Map<string, Cursor>;
  
  // Sync
  syncStatus: 'connected' | 'disconnected' | 'syncing';
  conflicts: Conflict[];
  resolveConflict: (conflictId: string, resolution: Resolution) => void;
  
  // Comments
  comments: Comment[];
  addComment: (nodeId: string, text: string) => void;
}

export function useCanvasCollaboration(options: UseCanvasCollaborationOptions): UseCanvasCollaborationReturn;
```

### 3. `useCanvasAI` - AI-Powered Features
**Consolidates:** `useAIBrainstorming`, `useComponentGeneration`, `useCodeScaffold`, `useRequirementWireframer`, `useMicroservicesExtractor`, `useServiceBlueprint`

```typescript
interface UseCanvasAIOptions {
  canvasId: string;
  tenantId: string;
  enabledFeatures?: AIFeature[];
}

type AIFeature = 
  | 'brainstorming'
  | 'component-generation'
  | 'code-scaffold'
  | 'wireframing'
  | 'microservices-extraction'
  | 'service-blueprint';

interface UseCanvasAIReturn {
  // Brainstorming
  generateIdeas: (prompt: string) => Promise<Idea[]>;
  
  // Component Generation
  generateComponent: (spec: ComponentSpec) => Promise<Component>;
  
  // Code Scaffolding
  generateScaffold: (template: string, config: Config) => Promise<Scaffold>;
  
  // Wireframing
  generateWireframe: (requirement: Requirement) => Promise<Wireframe>;
  
  // Microservices
  extractMicroservices: (monolith: Architecture) => Promise<Microservice[]>;
  
  // Service Blueprint
  generateBlueprint: (service: ServiceSpec) => Promise<Blueprint>;
  
  // Common
  isGenerating: boolean;
  error: Error | null;
}

export function useCanvasAI(options: UseCanvasAIOptions): UseCanvasAIReturn;
```

### 4. `useCanvasInfrastructure` - Infrastructure & DevOps
**Consolidates:** `useCICDPipeline`, `useCloudInfrastructure`, `useDataPipeline`, `useReleaseTrain`, `useServiceHealth`, `usePerformanceAnalysis`

```typescript
interface UseCanvasInfrastructureOptions {
  canvasId: string;
  provider?: 'aws' | 'azure' | 'gcp';
}

interface UseCanvasInfrastructureReturn {
  // CI/CD
  pipelines: Pipeline[];
  createPipeline: (config: PipelineConfig) => Promise<Pipeline>;
  
  // Cloud Infrastructure
  resources: CloudResource[];
  deployResource: (resource: ResourceSpec) => Promise<Deployment>;
  
  // Data Pipelines
  dataPipelines: DataPipeline[];
  createDataPipeline: (config: DataPipelineConfig) => Promise<DataPipeline>;
  
  // Release Management
  releases: Release[];
  scheduleRelease: (release: ReleaseSpec) => Promise<Release>;
  
  // Health & Performance
  healthStatus: HealthStatus;
  performanceMetrics: Metrics;
}

export function useCanvasInfrastructure(options: UseCanvasInfrastructureOptions): UseCanvasInfrastructureReturn;
```

### 5. `useCanvasSecurity` - Security & Compliance
**Consolidates:** `useCISODashboard`, `useCompliance`, `useThreatModeling`, `useZeroTrustArchitecture`

```typescript
interface UseCanvasSecurityOptions {
  canvasId: string;
  tenantId: string;
  complianceFrameworks?: ComplianceFramework[];
}

interface UseCanvasSecurityReturn {
  // CISO Dashboard
  securityPosture: SecurityPosture;
  vulnerabilities: Vulnerability[];
  
  // Compliance
  complianceStatus: ComplianceStatus;
  auditTrail: AuditEvent[];
  
  // Threat Modeling
  threats: Threat[];
  generateThreatModel: (architecture: Architecture) => Promise<ThreatModel>;
  
  // Zero Trust
  zeroTrustPolicies: Policy[];
  validateAccess: (request: AccessRequest) => Promise<AccessDecision>;
}

export function useCanvasSecurity(options: UseCanvasSecurityOptions): UseCanvasSecurityReturn;
```

### 6. `useCanvasTemplates` - Template Management
**Consolidates:** `useTemplateActions` + template-related functionality

```typescript
interface UseCanvasTemplatesOptions {
  canvasId: string;
  category?: TemplateCategory;
}

interface UseCanvasTemplatesReturn {
  templates: Template[];
  applyTemplate: (templateId: string) => Promise<void>;
  saveAsTemplate: (name: string, description: string) => Promise<Template>;
  deleteTemplate: (templateId: string) => Promise<void>;
}

export function useCanvasTemplates(options: UseCanvasTemplatesOptions): UseCanvasTemplatesReturn;
```

### 7. `useCanvasMobile` - Mobile-Specific Features
**Consolidates:** `useMobileCanvas` + touch interactions

```typescript
interface UseCanvasMobileOptions {
  canvasId: string;
  enableGestures?: boolean;
}

interface UseCanvasMobileReturn {
  // Touch gestures
  onPinchZoom: (scale: number) => void;
  onPan: (delta: { x: number; y: number }) => void;
  onDoubleTap: (position: { x: number; y: number }) => void;
  
  // Mobile-specific UI
  isMobileView: boolean;
  showMobileToolbar: boolean;
  
  // Responsive
  viewportSize: { width: number; height: number };
}

export function useCanvasMobile(options: UseCanvasMobileOptions): UseCanvasMobileReturn;
```

### 8. `useCanvasUserJourney` - User Journey Mapping
**Consolidates:** `useUserJourney` + journey-specific features

```typescript
interface UseCanvasUserJourneyOptions {
  canvasId: string;
  persona?: Persona;
}

interface UseCanvasUserJourneyReturn {
  journeys: UserJourney[];
  createJourney: (journey: UserJourneySpec) => Promise<UserJourney>;
  updateJourney: (id: string, updates: Partial<UserJourney>) => Promise<void>;
  analyzeJourney: (journeyId: string) => Promise<JourneyAnalysis>;
}

export function useCanvasUserJourney(options: UseCanvasUserJourneyOptions): UseCanvasUserJourneyReturn;
```

### 9. `useCanvasFullStack` - Full-Stack Mode
**Consolidates:** `useFullStackMode` + full-stack features

```typescript
interface UseCanvasFullStackOptions {
  canvasId: string;
  stack?: 'frontend' | 'backend' | 'database' | 'all';
}

interface UseCanvasFullStackReturn {
  mode: FullStackMode;
  setMode: (mode: FullStackMode) => void;
  layers: Layer[];
  activeLayer: Layer;
  setActiveLayer: (layerId: string) => void;
}

export function useCanvasFullStack(options: UseCanvasFullStackOptions): UseCanvasFullStackReturn;
```

### 10. `useCanvasAnalytics` - Analytics & Insights
**New consolidated hook for analytics**

```typescript
interface UseCanvasAnalyticsOptions {
  canvasId: string;
  tenantId: string;
}

interface UseCanvasAnalyticsReturn {
  // Usage analytics
  canvasMetrics: CanvasMetrics;
  userActivity: Activity[];
  
  // Performance
  renderTime: number;
  nodeCount: number;
  edgeCount: number;
  
  // Insights
  recommendations: Recommendation[];
}

export function useCanvasAnalytics(options: UseCanvasAnalyticsOptions): UseCanvasAnalyticsReturn;
```

---

## Migration Strategy

### Phase 1: Create Consolidated Hooks (Week 1)
1. Create new consolidated hook files
2. Implement core functionality
3. Add comprehensive tests
4. Document APIs

### Phase 2: Update Consumers (Week 2)
1. Update canvas components to use new hooks
2. Update YAPPC app components
3. Run integration tests
4. Fix any breaking changes

### Phase 3: Deprecate Old Hooks (Week 3)
1. Mark old hooks as `@deprecated`
2. Add migration guides
3. Update documentation
4. Monitor usage

### Phase 4: Remove Old Hooks (Week 4)
1. Verify zero usage of old hooks
2. Delete deprecated hooks
3. Update exports
4. Final testing

---

## Benefits

### Reduced Complexity
- **37 hooks → 10 hooks** (73% reduction)
- **37 test files → 10 test files**
- **Easier to find** the right hook

### Better Patterns
- **Consistent API** across all hooks
- **Shared utilities** reduce duplication
- **Type safety** with TypeScript

### Improved Performance
- **Lazy loading** of feature-specific code
- **Shared state** reduces re-renders
- **Optimized** hook dependencies

### Easier Maintenance
- **Single source** of truth per domain
- **Easier refactoring** with fewer files
- **Better testing** with consolidated tests

---

## Implementation Checklist

- [ ] Create `useCanvasCore` hook
- [ ] Create `useCanvasCollaboration` hook
- [ ] Create `useCanvasAI` hook
- [ ] Create `useCanvasInfrastructure` hook
- [ ] Create `useCanvasSecurity` hook
- [ ] Create `useCanvasTemplates` hook
- [ ] Create `useCanvasMobile` hook
- [ ] Create `useCanvasUserJourney` hook
- [ ] Create `useCanvasFullStack` hook
- [ ] Create `useCanvasAnalytics` hook
- [ ] Write comprehensive tests for each
- [ ] Update component consumers
- [ ] Deprecate old hooks
- [ ] Remove old hooks after migration
- [ ] Update documentation

---

## Testing Strategy

### Unit Tests
```typescript
describe('useCanvasCore', () => {
  it('should manage nodes and edges', () => {
    const { result } = renderHook(() => useCanvasCore({
      canvasId: 'test',
      tenantId: 'tenant-1',
      userId: 'user-1'
    }));
    
    act(() => {
      result.current.addNode({ id: 'node-1', type: 'default', position: { x: 0, y: 0 } });
    });
    
    expect(result.current.nodes).toHaveLength(1);
  });
});
```

### Integration Tests
```typescript
describe('Canvas Hook Integration', () => {
  it('should work together seamlessly', () => {
    const core = useCanvasCore({ ... });
    const ai = useCanvasAI({ ... });
    const collab = useCanvasCollaboration({ ... });
    
    // Test cross-hook functionality
    act(() => {
      const ideas = await ai.generateIdeas('Create a login page');
      ideas.forEach(idea => core.addNode(idea.node));
    });
    
    expect(collab.activeUsers).toContain(currentUser);
  });
});
```

---

## Breaking Changes

### Migration Guide

**Before:**
```typescript
import { useAIBrainstorming } from '@ghatana/canvas';

const { generateIdeas } = useAIBrainstorming({ canvasId });
```

**After:**
```typescript
import { useCanvasAI } from '@ghatana/canvas';

const { generateIdeas } = useCanvasAI({ 
  canvasId,
  tenantId,
  enabledFeatures: ['brainstorming']
});
```

---

## Timeline

| Week | Tasks | Deliverables |
|------|-------|--------------|
| 1 | Create consolidated hooks | 10 new hook files + tests |
| 2 | Update consumers | Migrated components |
| 3 | Deprecate old hooks | Migration guides |
| 4 | Remove old hooks | Clean codebase |

**Total Effort:** 4 weeks (1 developer)

---

## Success Metrics

- ✅ Hook count reduced from 37 to 10
- ✅ Test coverage maintained at 80%+
- ✅ No breaking changes for consumers
- ✅ Documentation updated
- ✅ Performance improved or maintained
- ✅ Developer satisfaction improved

---

## Next Steps

1. **Review this plan** with team
2. **Get approval** for breaking changes
3. **Create Jira tickets** for each phase
4. **Assign developers** to implementation
5. **Start Phase 1** - Create consolidated hooks
