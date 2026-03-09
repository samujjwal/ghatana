# Persona Components Quick Reference

**Quick start guide for using persona-driven landing page components**

---

## 📦 Available Components (BATCH 4)

All components exported from `@/shared/components`:

```typescript
import {
    PersonaHero,
    QuickActionCard,
    QuickActionsGrid,
    PersonaMetricsGrid,
    RecentActivitiesTimeline,
    PinnedFeaturesGrid,
} from '@/shared/components';
```

---

## 🎨 Component Usage

### 1. PersonaHero

**Purpose**: Personalized greeting with role badge and pending tasks

```tsx
import { PersonaHero } from '@/shared/components';
import { useAtom } from 'jotai';
import { userProfileAtom, pendingTasksAtom } from '@/state/jotai/atoms';

function Dashboard() {
    const [user] = useAtom(userProfileAtom);
    const [pendingTasks] = useAtom(pendingTasksAtom);

    return <PersonaHero user={user} pendingTasks={pendingTasks} />;
}
```

**Props**:
- `user: UserProfile` (required) - User profile with role, name, email
- `pendingTasks?: PendingTasks` (optional) - Task counts for badges
- `greeting?: string` (optional) - Custom greeting (default: "Welcome, {name}")
- `className?: string` (optional) - Additional CSS classes

**Features**:
- ✅ Time-based greeting
- ✅ Role badge with 4 colors (admin=red, lead=blue, engineer=green, viewer=purple)
- ✅ Pending task notification with pulse animation
- ✅ Task breakdown (4 approvals, 2 security alerts, etc.)

---

### 2. QuickActionCard

**Purpose**: Single action card with badge and CTA

```tsx
import { QuickActionCard } from '@/shared/components';
import { getPersonaConfig } from '@/config/personaConfig';

function ActionCard() {
    const config = getPersonaConfig('engineer');
    const action = config.quickActions[0]; // "Create Workflow"

    return (
        <QuickActionCard
            action={action}
            pendingTasks={pendingTasks}
            variant="primary"
            onClick={() => navigate(action.href)}
        />
    );
}
```

**Props**:
- `action: QuickAction` (required) - Action config from personaConfig
- `pendingTasks?: PendingTasks` (optional) - For badge count resolution
- `variant?: 'primary' | 'secondary' | 'warning' | 'success'` (optional, default: primary)
- `onClick?: () => void` (optional) - Click handler
- `className?: string` (optional)

**Features**:
- ✅ Dynamic badge count (resolves from pendingTasks)
- ✅ Keyboard shortcut display
- ✅ Hover scale animation
- ✅ 4 visual variants

---

### 3. QuickActionsGrid

**Purpose**: Grid layout for 4-6 quick action cards

```tsx
import { QuickActionsGrid } from '@/shared/components';
import { useAtom } from 'jotai';
import { personaConfigAtom, pendingTasksAtom } from '@/state/jotai/atoms';

function Dashboard() {
    const [config] = useAtom(personaConfigAtom);
    const [pendingTasks] = useAtom(pendingTasksAtom);

    return (
        <QuickActionsGrid
            actions={config.quickActions}
            pendingTasks={pendingTasks}
            columns={3}
            onActionClick={(action) => navigate(action.href)}
        />
    );
}
```

**Props**:
- `actions: QuickAction[]` (required) - Array of actions
- `pendingTasks?: PendingTasks` (optional)
- `columns?: 2 | 3 | 4` (optional, default: 3)
- `onActionClick?: (action: QuickAction) => void` (optional)
- `className?: string` (optional)

**Features**:
- ✅ Responsive grid (1 col mobile, 2 col tablet, 3+ col desktop)
- ✅ Empty state
- ✅ Section title "Quick Actions"

---

### 4. PersonaMetricsGrid

**Purpose**: Metric cards with threshold indicators

```tsx
import { PersonaMetricsGrid } from '@/shared/components';
import { getMockMetricData } from '@/config/mockPersonaData';

function Dashboard() {
    const config = getPersonaConfig('lead');
    const metricData = getMockMetricData('lead');

    return <PersonaMetricsGrid metrics={config.metrics} data={metricData} />;
}
```

**Props**:
- `metrics: MetricDefinition[]` (required) - Metric definitions from personaConfig
- `data: Record<string, number>` (required) - Metric values (keyed by dataKey)
- `className?: string` (optional)

**Features**:
- ✅ Threshold indicators: green (ok), amber (warning), red (critical)
- ✅ Format support: number, percentage, duration
- ✅ Auto-converts seconds to human-readable (3600 → "1h")
- ✅ Responsive grid (1-4 columns)

**Metric Format Examples**:
```typescript
// Number: 42 → "42"
// Percentage: 87.5 → "87.5%"
// Duration: 3600 → "1h", 90 → "1m", 45 → "45s"
```

---

### 5. RecentActivitiesTimeline

**Purpose**: Activity history timeline

```tsx
import { RecentActivitiesTimeline } from '@/shared/components';
import { useAtom } from 'jotai';
import { recentActivitiesAtom } from '@/state/jotai/atoms';

function Dashboard() {
    const [activities] = useAtom(recentActivitiesAtom);

    return (
        <RecentActivitiesTimeline
            activities={activities}
            maxItems={5}
            onActivityClick={(activity) => navigate(activity.href)}
        />
    );
}
```

**Props**:
- `activities: Activity[]` (required) - Array of recent activities
- `maxItems?: number` (optional, default: 5)
- `onActivityClick?: (activity: Activity) => void` (optional)
- `className?: string` (optional)

**Features**:
- ✅ Timeline visualization with connecting lines
- ✅ Status badges: success (green), failed (red), pending (amber)
- ✅ Relative timestamps ("2 hours ago", "just now")
- ✅ Quick navigation to related features
- ✅ "View all" button when activities > maxItems

---

### 6. PinnedFeaturesGrid

**Purpose**: User's pinned features for quick access

```tsx
import { PinnedFeaturesGrid } from '@/shared/components';
import { useAtom } from 'jotai';
import { pinnedFeaturesAtom } from '@/state/jotai/atoms';

function Dashboard() {
    const [pinnedFeatures, setPinnedFeatures] = useAtom(pinnedFeaturesAtom);

    const handleUnpin = (featureTitle: string) => {
        setPinnedFeatures((prev) => prev.filter((f) => f.title !== featureTitle));
    };

    return (
        <PinnedFeaturesGrid
            features={pinnedFeatures}
            onFeatureClick={(feature) => navigate(feature.href)}
            onUnpin={handleUnpin}
        />
    );
}
```

**Props**:
- `features: Feature[]` (required) - Array of pinned features
- `onFeatureClick?: (feature: Feature) => void` (optional)
- `onUnpin?: (featureTitle: string) => void` (optional)
- `className?: string` (optional)

**Features**:
- ✅ Grid of feature cards
- ✅ Pin/unpin toggle button (📌)
- ✅ Empty state with CTA
- ✅ Responsive grid (1-3 columns)

---

## 🚀 Complete Dashboard Example

```tsx
import {
    PersonaHero,
    QuickActionsGrid,
    PersonaMetricsGrid,
    RecentActivitiesTimeline,
    PinnedFeaturesGrid,
} from '@/shared/components';
import { useAtom } from 'jotai';
import {
    userProfileAtom,
    personaConfigAtom,
    pendingTasksAtom,
    recentActivitiesAtom,
    pinnedFeaturesAtom,
} from '@/state/jotai/atoms';
import { getMockMetricData } from '@/config/mockPersonaData';
import { useNavigate } from 'react-router-dom';

export default function PersonaDashboard() {
    const navigate = useNavigate();
    const [userProfile] = useAtom(userProfileAtom);
    const [personaConfig] = useAtom(personaConfigAtom);
    const [pendingTasks] = useAtom(pendingTasksAtom);
    const [recentActivities] = useAtom(recentActivitiesAtom);
    const [pinnedFeatures, setPinnedFeatures] = useAtom(pinnedFeaturesAtom);

    if (!userProfile) {
        return <GenericLandingPage />;
    }

    const metricData = getMockMetricData(userProfile.role);

    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-6">
            <PersonaHero user={userProfile} pendingTasks={pendingTasks} />

            <QuickActionsGrid
                actions={personaConfig.quickActions}
                pendingTasks={pendingTasks}
                columns={3}
                onActionClick={(action) => navigate(action.href)}
            />

            <PersonaMetricsGrid metrics={personaConfig.metrics} data={metricData} />

            <RecentActivitiesTimeline
                activities={recentActivities}
                maxItems={5}
                onActivityClick={(activity) => activity.href && navigate(activity.href)}
            />

            <PinnedFeaturesGrid
                features={pinnedFeatures}
                onFeatureClick={(feature) => navigate(feature.href)}
                onUnpin={(title) => setPinnedFeatures((prev) => prev.filter((f) => f.title !== title))}
            />
        </div>
    );
}
```

---

## 🧪 Testing with Mock Data

```typescript
import {
    getMockUserProfile,
    getMockPendingTasks,
    getMockRecentActivities,
    getMockPinnedFeatures,
    getMockMetricData,
} from '@/config/mockPersonaData';

// Get mock data for testing
const adminUser = getMockUserProfile('admin');
const pendingTasks = getMockPendingTasks(4, 2, 1, 3); // Custom counts
const activities = getMockRecentActivities(5);
const pinnedFeatures = getMockPinnedFeatures(4);
const metricData = getMockMetricData('admin');

// Use in components
<PersonaHero user={adminUser} pendingTasks={pendingTasks} />
```

---

## 🎯 Persona-Specific Examples

### Admin Dashboard
```tsx
const config = getPersonaConfig('admin');
// Quick Actions: Review Security Alerts, Approve HITL Requests, Check Compliance Status, ...
// Metrics: Security Score, Compliance Rate, Active Users, Audit Events
```

### Lead Dashboard
```tsx
const config = getPersonaConfig('lead');
// Quick Actions: Approve HITL Requests, Review Workflows, Check Team Metrics, ...
// Metrics: Workflows Active, Avg Approval Time, HITL Backlog, Team Velocity
```

### Engineer Dashboard
```tsx
const config = getPersonaConfig('engineer');
// Quick Actions: Create Workflow, Run Simulation, Check Logs, ...
// Metrics: Deployments Today, Simulations Passed, Failed Workflows, Test Coverage
```

### Viewer Dashboard
```tsx
const config = getPersonaConfig('viewer');
// Quick Actions: View Reports, Explore Data, Export Analytics, ...
// Metrics: Reports Generated, Dashboard Views, Data Exports, Insights Discovered
```

---

## 🔧 Customization

### Custom Greeting
```tsx
<PersonaHero user={user} greeting="Good morning, Alice!" />
```

### Custom Columns
```tsx
<QuickActionsGrid actions={actions} columns={4} />
```

### Custom Max Items
```tsx
<RecentActivitiesTimeline activities={activities} maxItems={10} />
```

### Custom Metric Data
```tsx
const customMetrics = {
    workflowsActive: 50,
    avgApprovalTime: 1800, // 30 minutes
    hitlBacklog: 12,
    teamVelocity: 95,
};

<PersonaMetricsGrid metrics={config.metrics} data={customMetrics} />
```

---

## 📚 Related Files

- **Component Source**: `src/shared/components/Persona*.tsx`
- **Component Exports**: `src/shared/components/index.ts`
- **Persona Config**: `src/config/personaConfig.ts`
- **Mock Data**: `src/config/mockPersonaData.ts`
- **State Atoms**: `src/state/jotai/atoms.ts`
- **Plan Document**: `PERSONA_DRIVEN_LANDING_PAGE_PLAN.md`
- **Phase 2 Complete**: `PERSONA_PHASE2_COMPLETE.md`

---

## ✅ Best Practices

1. **Always provide user profile** - PersonaHero requires it
2. **Use personaConfig for actions/metrics** - Don't hardcode
3. **Handle navigation clicks** - Provide onActionClick, onActivityClick, onFeatureClick
4. **Use mock data for testing** - Available in mockPersonaData.ts
5. **Respect empty states** - Components handle gracefully
6. **Test all personas** - Verify admin, lead, engineer, viewer
7. **Test dark mode** - All components support it
8. **Test responsive breakpoints** - Mobile, tablet, desktop

---

**Status**: Phase 2 Complete ✅
**Next**: Integrate into HomePage.tsx (Phase 3)
