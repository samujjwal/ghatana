# Backend Integration Status

## Overview

**Current State**: 10 new pages created in Phase 1 render successfully but display **mock/static data only**. Users perceive these pages as "under construction" because they lack backend API integration.

**Root Cause**: Previous session focused on UI structure creation. Backend integration (Phase 2) has not been started.

## Page Status

### ✅ Fully Working Pages (Pre-existing)

| Page      | Route                | Backend Integration               | Feature Component   | API Hook        |
| --------- | -------------------- | --------------------------------- | ------------------- | --------------- |
| Dashboard | `/dashboard`         | ⚠️ Partial (uses mockStageHealth) | OperationsDashboard | N/A             |
| Incidents | `/operate/incidents` | ✅ Full                           | IncidentsExplorer   | useIncidents()  |
| Queue     | `/operate/queue`     | ✅ Full                           | QueueExplorer       | useQueueItems() |

### ❌ Mock-Only Pages (Need Backend Integration)

| Page          | Route                | Current State           | Backend Endpoint Needed         | Priority  |
| ------------- | -------------------- | ----------------------- | ------------------------------- | --------- |
| **Genesis**   | `/genesis`           | Static wizard steps     | POST /api/genesis/generate      | 🔴 High   |
| **Org Chart** | `/manage/org-chart`  | Mock departments/agents | GET /api/organization/structure | 🟡 Medium |
| **Norms**     | `/manage/norms`      | Mock rules array        | GET/POST /api/norms             | 🟡 Medium |
| **Agents**    | `/manage/agents`     | AGENT_TEMPLATES array   | GET /api/agents/marketplace     | 🟢 Low    |
| **Budget**    | `/manage/budget`     | Mock categories/teams   | GET /api/budget                 | 🟡 Medium |
| **Live Feed** | `/operate/live-feed` | MOCK_ACTIVITIES array   | WS /api/activity/stream         | 🔴 High   |
| **Tasks**     | `/operate/tasks`     | MOCK_TASKS array        | GET /api/tasks                  | 🟡 Medium |
| **Insights**  | `/operate/insights`  | Chat UI only            | POST /api/insights/query (RAG)  | 🔴 High   |
| **Reviews**   | `/people/reviews`    | MOCK_REVIEWS array      | GET /api/performance/reviews    | 🟡 Medium |
| **Growth**    | `/people/growth`     | MOCK_AGENTS growth data | GET /api/growth/paths           | 🟢 Low    |

## Integration Pattern (from Working Pages)

```
┌─────────────┐
│ Route File  │  e.g., /routes/operate/incidents.tsx
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│ Feature Component   │  e.g., /features/operate/IncidentsExplorer.tsx
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│ Custom Hook         │  e.g., useIncidents(tenantId, status, severity)
│ (React Query)       │  Location: /hooks/useOperateApi.ts
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│ API Client          │  /services/api.service.ts (APIClient class)
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│ Backend API         │  http://localhost:8080/api/...
└─────────────────────┘
```

### Example: Incidents Page Integration

**Route File** (`/routes/operate/incidents.tsx`):

```typescript
export default function IncidentsPage() {
  return <IncidentsExplorer />;
}
```

**Feature Component** (`/features/operate/IncidentsExplorer.tsx`):

```typescript
export function IncidentsExplorer() {
  const [selectedTenant] = useAtom(selectedTenantAtom);
  const tenantId = selectedTenant?.id || "";

  const { data: incidents, isLoading } = useIncidents(tenantId, status, severity);

  return <IncidentsTable incidents={incidents} />;
}
```

**Custom Hook** (`/hooks/useOperateApi.ts`):

```typescript
export function useIncidents(
  tenantId: string,
  status?: string,
  severity?: string
) {
  return useQuery({
    queryKey: ["incidents", tenantId, status, severity],
    queryFn: () => apiClient.get<Incident[]>(`/incidents?tenantId=${tenantId}`),
    enabled: !!tenantId,
  });
}
```

**API Client** (`/services/api.service.ts`):

```typescript
class APIClient {
  private baseURL =
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

  async get<T>(endpoint: string): Promise<T> {
    const response = await fetch(`${this.baseURL}${endpoint}`, {
      headers: { Authorization: `Bearer ${this.token}` },
    });
    return response.json();
  }
}
```

## Required Work for Phase 2

### 1. Create API Hooks (Priority Order)

#### High Priority (Complex Features)

- [ ] **useGenesisApi.ts** - POST /api/genesis/generate (AI-powered org creation)
- [ ] **useInsightsApi.ts** - POST /api/insights/query (RAG over agent logs)
- [ ] **useLiveFeedApi.ts** - WebSocket connection to /api/activity/stream

#### Medium Priority (CRUD Operations)

- [ ] **useOrgChartApi.ts** - GET/POST /api/organization/structure
- [ ] **useNormsApi.ts** - GET/POST/PUT/DELETE /api/norms
- [ ] **useBudgetApi.ts** - GET/PUT /api/budget
- [ ] **useTasksApi.ts** - GET/PUT /api/tasks (integrate with existing Queue API?)
- [ ] **useReviewsApi.ts** - GET/POST /api/performance/reviews

#### Low Priority (Read-Only or Simple)

- [ ] **useAgentsMarketplaceApi.ts** - GET /api/agents/marketplace
- [ ] **useGrowthPathsApi.ts** - GET /api/growth/paths (might exist already)

### 2. Backend Endpoint Verification

**Action Required**: Check which endpoints already exist vs. need to be created.

```bash
# Check existing backend APIs
curl http://localhost:8080/api/organization/structure
curl http://localhost:8080/api/norms
curl http://localhost:8080/api/agents/marketplace
curl http://localhost:8080/api/budget
curl http://localhost:8080/api/tasks
curl http://localhost:8080/api/performance/reviews
curl http://localhost:8080/api/growth/paths
```

### 3. Refactor Route Files

For each of the 10 pages:

1. Extract UI logic into Feature Component (in `/features/`)
2. Replace mock data arrays with custom hooks
3. Add loading states, error handling
4. Implement action handlers (modals, forms, navigation)
5. Remove TODO comments

**Example Refactor** (Genesis page):

**Before** (Current):

```typescript
// /routes/genesis.tsx
const MOCK_STEPS = [...];
export default function GenesisPage() {
  const [currentStep, setCurrentStep] = useState(0);
  // TODO: Connect to backend
  return <WizardUI steps={MOCK_STEPS} />;
}
```

**After** (Target):

```typescript
// /routes/genesis.tsx
export default function GenesisPage() {
  return <GenesisWizard />;
}

// /features/genesis/GenesisWizard.tsx
export function GenesisWizard() {
  const [selectedTenant] = useAtom(selectedTenantAtom);
  const { mutate: generateOrg, isLoading } = useGenerateOrganization();

  const handleGenerate = (data: GenesisFormData) => {
    generateOrg({ tenantId: selectedTenant.id, ...data });
  };

  return <WizardUI onSubmit={handleGenerate} isLoading={isLoading} />;
}

// /hooks/useGenesisApi.ts
export function useGenerateOrganization() {
  return useMutation({
    mutationFn: (data: GenesisRequest) =>
      apiClient.post<OrganizationConfig>("/genesis/generate", data),
    onSuccess: (org) => {
      toast.success("Organization created!");
      navigate("/manage/org-chart");
    },
  });
}
```

### 4. Feature Components (Optional but Recommended)

Create these in `/features/` directory:

- [ ] `/features/genesis/GenesisWizard.tsx`
- [ ] `/features/manage/OrgChartView.tsx`
- [ ] `/features/manage/NormsEditor.tsx`
- [ ] `/features/manage/AgentMarketplace.tsx`
- [ ] `/features/manage/BudgetView.tsx`
- [ ] `/features/operate/LiveFeed.tsx`
- [ ] `/features/operate/TaskQueue.tsx`
- [ ] `/features/operate/InsightsChat.tsx`
- [ ] `/features/people/ReviewsDashboard.tsx`
- [ ] `/features/people/GrowthPathsView.tsx`

## Dependencies & Blockers

### Frontend Dependencies (Already Available)

- ✅ React Query (@tanstack/react-query) - installed
- ✅ API Client (/services/api.service.ts) - working
- ✅ Jotai (state management) - working
- ✅ React Router v7 - configured

### Backend Dependencies (Need Verification)

- ❓ Java Backend at http://localhost:8080 - **verify running**
- ❓ `/api/genesis/generate` endpoint - **check exists**
- ❓ `/api/organization/structure` endpoint - **check exists**
- ❓ `/api/norms` CRUD endpoints - **check exists**
- ❓ `/api/agents/marketplace` endpoint - **check exists**
- ❓ `/api/budget` endpoints - **check exists**
- ❓ `/api/activity/stream` WebSocket - **check exists**
- ❓ `/api/tasks` endpoints - **check exists**
- ❓ `/api/insights/query` RAG endpoint - **check exists**
- ❓ `/api/performance/reviews` endpoints - **check exists**
- ❓ `/api/growth/paths` endpoint - **check exists**

### AI Service Dependencies (Need Verification)

- ❓ `virtual-org` AI service for Genesis wizard
- ❓ RAG service for Insights chat
- ❓ NLP service for Norm translation

## Recommended Approach

### Step 1: Verify Backend Availability (1 hour)

1. Start Java backend: `./gradlew :products:software-org:apps:backend:bootRun`
2. Test existing endpoints with curl/Postman
3. Document which endpoints exist vs. need creation

### Step 2: Start with Simple CRUD Pages (4 hours)

1. **Org Chart** - Read-only view of organization structure
2. **Agents** - Marketplace list view
3. **Reviews** - Performance reviews list

**Why?** These are straightforward GET requests, no complex state or AI.

### Step 3: Add Form/Action Pages (6 hours)

4. **Norms** - CRUD operations
5. **Budget** - Budget allocation editor
6. **Tasks** - Task queue with actions

### Step 4: Implement Complex Features (8+ hours)

7. **Genesis** - AI-powered org generation wizard
8. **Insights** - RAG-based chat interface
9. **Live Feed** - WebSocket real-time activity stream
10. **Growth** - IC growth path recommendations

### Step 5: Polish & Test (4 hours)

- Add loading states
- Add error handling
- Add optimistic updates
- E2E testing

**Total Estimate**: 23+ hours (3 days full-time)

## Success Criteria

Phase 2 is complete when:

- [ ] All 10 pages load real data from backend APIs
- [ ] No mock data arrays remain in route files
- [ ] All TODO comments resolved
- [ ] Loading states implemented
- [ ] Error handling implemented
- [ ] User can perform CRUD operations (where applicable)
- [ ] AI features (Genesis, Insights) functional
- [ ] WebSocket (Live Feed) working

## Notes

- **Hybrid Backend Architecture**: Java (ActiveJ) for domain logic, Node (Fastify) for user API. Check which backend serves which endpoints.
- **Multi-Tenancy**: All API calls include `tenantId` from `selectedTenantAtom`.
- **Authentication**: API client uses bearer tokens from `/services/api.service.ts`.
- **Type Safety**: Define TypeScript types for all API requests/responses.
