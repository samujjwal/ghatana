# Persona Dashboard - Quick Reference Guide

## 🚀 Quick Start

### Run in Development (Mock API)
```bash
cd products/software-org/apps/web
pnpm dev
```
**Result**: Dashboard uses mock data, no backend required

### Run with Real API
```bash
# Create .env.local
echo "VITE_API_BASE_URL=http://localhost:3000/api/v1" > .env.local
echo "VITE_USE_REAL_API=true" >> .env.local

# Start backend (separate terminal)
cd backend && pnpm start

# Start frontend
pnpm dev
```

---

## 📁 File Structure

```
src/
├── pages/
│   └── HomePage.tsx              # Main persona dashboard (370 lines)
├── shared/components/
│   ├── PersonaHero.tsx           # Greeting + role badge (156 lines)
│   ├── QuickActionsGrid.tsx     # Action cards (94 lines)
│   ├── PersonaMetricsGrid.tsx   # Key metrics (217 lines)
│   ├── RecentActivitiesTimeline.tsx  # Activity history (231 lines)
│   └── PinnedFeaturesGrid.tsx   # Pinned features (141 lines)
├── hooks/
│   ├── usePendingTasks.ts       # Pending tasks hook (126 lines)
│   ├── useRecentActivities.ts   # Activities hook (131 lines)
│   ├── useMetrics.ts            # Metrics hook (121 lines)
│   ├── usePinnedFeatures.ts     # Pinned features hook (205 lines)
│   └── index.ts                 # Hook exports (21 lines)
├── services/
│   └── personaApi.ts            # API client (261 lines)
├── config/
│   ├── personaConfig.ts         # Persona configurations (690 lines)
│   ├── mockPersonaData.ts       # Mock data (268 lines)
│   └── initializeDemoData.ts    # Demo data initializer (108 lines)
├── state/jotai/
│   └── atoms.ts                 # Jotai atoms (478 lines)
└── vite-env.d.ts                # Vite env types (11 lines)
```

---

## 🎨 Component Usage

### HomePage (Adaptive)
```tsx
import HomePage from '@/pages/HomePage';

// Automatically shows:
// - Persona dashboard (if authenticated)
// - Generic landing page (if not authenticated)
```

### PersonaHero
```tsx
<PersonaHero
  user={userProfile}
  pendingTasks={pendingTasks}
  greeting="Good morning"
  className="mb-6"
/>
```

### QuickActionsGrid
```tsx
<QuickActionsGrid
  actions={personaConfig.quickActions}
  pendingTasks={pendingTasks}
  columns={3}
  onActionClick={(action) => navigate(action.href)}
/>
```

### PersonaMetricsGrid
```tsx
<PersonaMetricsGrid
  metrics={personaConfig.metrics}
  data={{ activeWorkflows: 24, avgExecutionTime: 1.8 }}
/>
```

### RecentActivitiesTimeline
```tsx
<RecentActivitiesTimeline
  activities={recentActivities}
  maxItems={5}
  onActivityClick={(activity) => navigate(activity.href)}
/>
```

### PinnedFeaturesGrid
```tsx
<PinnedFeaturesGrid
  features={pinnedFeatures}
  onFeatureClick={(feature) => navigate(feature.href)}
  onUnpin={(title) => unpinFeature(title)}
/>
```

---

## 🪝 Hook Usage

### usePendingTasks
```tsx
const {
  tasks,          // { hitlApprovals: 6, securityAlerts: 4, ... }
  isLoading,      // true on first fetch
  error,          // ApiError | null
  refetch,        // () => void (manual refresh)
  isFetching,     // true during background refresh
} = usePendingTasks({
  refetchInterval: 30000,  // Poll every 30s
  enabled: true,
  retry: 3,
});

// Optimistic update
const updateTask = useOptimisticTaskUpdate();
updateTask('hitlApprovals'); // Decrements by 1
```

### useRecentActivities
```tsx
const {
  activities,  // Activity[]
  isLoading,
  error,
  refetch,
} = useRecentActivities({
  maxItems: 5,
  refetchInterval: 60000,  // Poll every 60s
});

// Filter by status
const failed = filterActivitiesByStatus(activities, 'failed');
```

### useMetrics
```tsx
const {
  metrics,     // { activeWorkflows: 24, ... }
  isLoading,
  error,
  refetch,
} = useMetrics('engineer', {
  refetchInterval: 30000,
});

// Calculate change
const change = calculateMetricChange(100, 85); // 17.6%
```

### usePinnedFeatures
```tsx
const {
  features,    // Feature[]
  isLoading,
  pin,         // (title: string) => void
  unpin,       // (title: string) => void
  updateAll,   // (features: Feature[]) => void (for reorder)
  isMutating,  // true during pin/unpin
} = usePinnedFeatures();

// Optimistic unpin
unpin('Control Tower'); // Instant UI update
```

---

## 🔌 API Client

### Import
```tsx
import { api } from '@/services/personaApi';
```

### Methods
```tsx
// Pending tasks
const tasks = await api.getPendingTasks();

// Recent activities
const activities = await api.getRecentActivities(5);

// Metrics
const metrics = await api.getMetrics('engineer');

// Pinned features
const features = await api.getPinnedFeatures();
await api.updatePinnedFeatures(newFeatures);
await api.pinFeature('Control Tower');
await api.unpinFeature('Reports');

// User profile
const profile = await api.getUserProfile();

// Complete task
await api.completeTask('hitl', 'task-123');
```

### Error Handling
```tsx
try {
  const tasks = await api.getPendingTasks();
} catch (error) {
  if (error instanceof ApiError) {
    console.error(`API Error: ${error.message} (${error.status})`);
    console.error(`Code: ${error.code}`);
  }
}
```

---

## 🎯 Persona Roles

### Admin (Security & Compliance)
**Quick Actions**:
- Review Security Alerts → `/security/alerts`
- Manage Users & Roles → `/users`
- View Audit Logs → `/audit`
- System Configuration → `/settings/system`
- Backup & Recovery → `/settings/backup`
- License Management → `/settings/license`

**Metrics**:
- Open Security Cases (threshold: <10 green, <20 amber, ≥20 red)
- Avg Resolution Time (threshold: <4h green, <8h amber, ≥8h red)
- Compliance Score (threshold: ≥95% green, ≥90% amber, <90% red)
- Active Users Today (threshold: ≥50 green, ≥30 amber, <30 amber)

### Lead (Team Management)
**Quick Actions**:
- Approve HITL Tasks → `/hitl`
- Monitor Team Dashboard → `/dashboards/team`
- Review Failed Workflows → `/workflows/failures`
- Team Performance → `/reports/team`
- Sprint Planning → `/sprints`
- Resource Allocation → `/resources`

**Metrics**:
- Team Velocity (threshold: ≥90% green, ≥75% amber, <75% red)
- HITL Queue Size (threshold: <20 green, <50 amber, ≥50 red)
- Avg Approval Time (threshold: <1h green, <2h amber, ≥2h red)
- Sprint Progress (threshold: ≥80% green, ≥60% amber, <60% amber)

### Engineer (Development)
**Quick Actions**:
- Create New Workflow → `/workflows/new`
- Test Workflows → `/workflows/test`
- View Deployments → `/workflows/deployments`
- Debug Failed Runs → `/workflows/failures`
- Model Observatory → `/ml/observatory`
- API Documentation → `/docs/api`

**Metrics**:
- Active Workflows (threshold: ≥20 green, ≥10 amber, <10 amber)
- Avg Execution Time (threshold: <2s green, <5s amber, ≥5s red)
- Success Rate (threshold: ≥95% green, ≥90% amber, <90% red)
- Deployments Today (threshold: ≥5 green, ≥2 amber, <2 amber)

### Viewer (Analytics)
**Quick Actions**:
- View Dashboards → `/dashboards`
- System Reports → `/reports`
- Live Monitoring → `/monitor/live`
- Export Data → `/export`
- Help & Support → `/help`

**Metrics**:
- Total Events (24h) (threshold: ≥100k green, ≥50k amber, <50k amber)
- Avg Latency (threshold: <50ms green, <100ms amber, ≥100ms red)
- System Uptime (threshold: ≥99.9% green, ≥99.5% amber, <99.5% red)
- Active Workflows (threshold: ≥20 green, ≥10 amber, <10 amber)

---

## 🧪 Testing Scenarios

### Test 1: Loading State
1. Clear React Query cache (DevTools)
2. Refresh page
3. **Expected**: Spinner with "Loading your dashboard..."
4. **Expected**: Data appears after 1-2s

### Test 2: Error Handling
1. Stop backend server
2. Refresh page
3. **Expected**: Error banners appear after retry attempts
4. Click "Retry" button
5. **Expected**: Attempts to refetch

### Test 3: Polling
1. Open DevTools → Network
2. Filter by XHR/Fetch
3. Wait 30-60 seconds
4. **Expected**: Periodic API calls (30s for tasks/metrics, 60s for activities)

### Test 4: Optimistic Updates
1. See 4 pinned features
2. Click "Unpin" on any feature
3. **Expected**: Feature disappears instantly
4. Check Network: API call happens after UI update
5. If API fails: Feature reappears (rollback)

### Test 5: Role Switching
```tsx
// In demo mode, use initializeDemoData
initializeDemoData('admin', setUserProfile, setPendingTasks, setRecentActivities, setPinnedFeatures);
```
**Expected**: Dashboard adapts to admin persona (6 quick actions, different metrics)

---

## 🐛 Troubleshooting

### Issue: "Property 'env' does not exist on type 'ImportMeta'"
**Solution**: Ensure `vite-env.d.ts` exists in `src/` directory

### Issue: API calls fail with 404
**Check**:
1. Backend server running on correct port?
2. `VITE_API_BASE_URL` correct in `.env.local`?
3. API endpoints implemented?

**Fallback**: Set `VITE_USE_REAL_API=false` to use mock data

### Issue: Data not polling
**Check**:
1. React Query DevTools open (see query status)
2. `refetchInterval` set correctly in hook options?
3. Component still mounted?

### Issue: Optimistic update not rolling back on error
**Check**:
1. API returning error response?
2. Error caught in `onError` handler?
3. React Query DevTools showing mutation error?

---

## 📦 Dependencies

**Required**:
- `jotai` (state management)
- `@tanstack/react-query` (server state)
- `react-router-dom` (navigation)

**Optional** (Phase 5):
- `@dnd-kit/core` (drag-drop)
- `framer-motion` (animations)
- `react-hot-toast` (notifications)

---

## 🎓 Code Patterns

### Pattern 1: Conditional Rendering with Loading
```tsx
if (isLoading) {
  return <Spinner />;
}

if (error) {
  return <ErrorBanner error={error} />;
}

return <DataComponent data={data} />;
```

### Pattern 2: Optimistic Update with Rollback
```tsx
const mutation = useMutation({
  mutationFn: api.unpinFeature,
  onMutate: async (title) => {
    // Cancel refetch, snapshot previous, update optimistically
    await queryClient.cancelQueries(['pinnedFeatures']);
    const previous = queryClient.getQueryData(['pinnedFeatures']);
    queryClient.setQueryData(['pinnedFeatures'], (old) => old.filter(f => f.title !== title));
    return { previous };
  },
  onError: (err, title, context) => {
    // Rollback on error
    queryClient.setQueryData(['pinnedFeatures'], context.previous);
  },
  onSettled: () => {
    // Refetch to ensure sync
    queryClient.invalidateQueries(['pinnedFeatures']);
  },
});
```

### Pattern 3: Hook with Jotai Sync
```tsx
export function useMyData() {
  const [data, setData] = useAtom(myDataAtom);
  
  const { data: apiData } = useQuery({
    queryKey: ['myData'],
    queryFn: api.getMyData,
  });
  
  // Sync API data to Jotai atom
  useEffect(() => {
    if (apiData) setData(apiData);
  }, [apiData, setData]);
  
  return { data };
}
```

---

## 📚 References

- **React Query Docs**: https://tanstack.com/query/latest
- **Jotai Docs**: https://jotai.org
- **Phase 3 Docs**: `PERSONA_PHASE3_COMPLETE.md`
- **Phase 4 Docs**: `PERSONA_PHASE4_COMPLETE.md`
- **Plan Document**: `PERSONA_DRIVEN_LANDING_PAGE_PLAN.md`

---

**Last Updated**: November 23, 2025  
**Version**: Phase 4 Complete
