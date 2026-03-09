# SOFTWARE-ORG Frontend - Usage Guide & Component Library

**Version:** 2.0  
**Date:** November 2025  
**Status:** Production Ready ✅

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Component Catalog](#component-catalog)
3. [Feature Modules](#feature-modules)
4. [Integration Examples](#integration-examples)
5. [Accessibility Guide](#accessibility-guide)
6. [Performance Tips](#performance-tips)
7. [Troubleshooting](#troubleshooting)
8. [Contributing](#contributing)

---

## Quick Start

### Installation

```bash
cd products/software-org/apps/web
pnpm install
pnpm dev
```

Navigate to `http://localhost:5173` to see the application.

### Environment Setup

Create `.env.local`:

```env
VITE_API_URL=http://localhost:3000
VITE_WS_URL=ws://localhost:3000
```

### Project Structure

```
src/
├── features/           # Feature modules by domain
│   ├── dashboard/     # KPIs, insights, what-if analysis
│   ├── security/      # Vulnerabilities, compliance
│   ├── models/        # ML performance, drift, registry
│   ├── departments/   # Organization structure
│   ├── workflows/     # Real-time flow visualization
│   ├── agents/        # HITL console, actions
│   ├── incidents/     # Incident management
│   ├── simulator/     # Event generation
│   └── reports/       # Reports & exports
├── shared/            # Shared UI & hooks
│   ├── components/    # Reusable components
│   ├── hooks/         # Custom React hooks
│   └── lib/          # Utilities
└── state/            # Global state (Jotai, React Query)
```

---

## Component Catalog

### Day 8 - AI Intelligence Components

#### **NaturalLanguageQuery**

Natural language interface for metric exploration.

**Import:**
```tsx
import { NaturalLanguageQuery } from '@/features/dashboard/components/NaturalLanguageQuery';
```

**Usage:**
```tsx
<NaturalLanguageQuery
  onQuerySelect={(query) => console.log(query)}
  onMetricsSelect={(metrics) => setSelectedMetrics(metrics)}
/>
```

**Props:**
- `onQuerySelect?`: (query: string) => void
- `onMetricsSelect?`: (metrics: string[]) => void

**Features:**
- Query suggestions dropdown
- Confidence score display (0-1)
- Related queries for exploration
- Query history persistence
- Keyboard shortcuts (Enter to submit)

---

#### **WhatIfAnalysisPanel**

Scenario simulation with real-time impact projection.

**Import:**
```tsx
import { WhatIfAnalysisPanel } from '@/features/dashboard/components/WhatIfAnalysisPanel';
```

**Usage:**
```tsx
<WhatIfAnalysisPanel
  defaultExpanded={false}
/>
```

**Features:**
- 4 adjustable sliders:
  - Deployment Frequency (50-300)
  - MTTR (10-120 min)
  - Change Failure Rate (0-50%)
  - Lead Time (4-168 hr)
- Real-time simulation (<500ms)
- Baseline vs Projected comparison
- Confidence indicator

---

#### **AIExplainabilityDrawer**

Modal showing AI decision reasoning and feature importance.

**Import:**
```tsx
import { AIExplainabilityDrawer } from '@/features/dashboard/components/AIExplainabilityDrawer';
```

**Usage:**
```tsx
<AIExplainabilityDrawer
  isOpen={true}
  onClose={() => setOpen(false)}
  prediction="High Risk"
  confidence={0.87}
  modelInfo={{
    name: "Risk Predictor v2.1",
    version: "2.1.5",
    trainedAt: new Date("2024-11-01"),
    accuracy: 0.924
  }}
/>
```

**Features:**
- Feature importance visualization (bar chart)
- Model metadata display
- Alternative predictions with probabilities
- Expandable sections for organization
- Modal with fixed positioning (z-50)

---

#### **ModelLineage**

SVG-based data flow and lineage visualization.

**Import:**
```tsx
import { ModelLineage } from '@/shared/components/ModelLineage';
```

**Usage:**
```tsx
<ModelLineage
  nodes={[
    { id: "1", name: "Raw Events", type: "source", status: "healthy" },
    { id: "2", name: "Feature Store", type: "feature", status: "warning" },
    { id: "3", name: "ML Model", type: "model", status: "healthy" },
  ]}
  edges={[
    { from: "1", to: "2" },
    { from: "2", to: "3" },
  ]}
/>
```

**Node Types:**
- `source`: Data source
- `feature`: Feature engineering
- `model`: ML model
- `output`: Final output

---

### Day 9 - Security & ML Observatory

#### **VulnerabilityDashboard**

Vulnerability tracking and management dashboard.

**Import:**
```tsx
import { VulnerabilityDashboard } from '@/features/security/components/VulnerabilityDashboard';
```

**Usage:**
```tsx
<VulnerabilityDashboard
  onSelectVulnerability={(id) => console.log(id)}
/>
```

**Features:**
- Severity grouping (critical/high/medium/low)
- Status tracking (open/in-progress/remediated)
- CVSS scores with color coding
- Due date tracking
- Affected components list

---

#### **CompliancePosture**

Multi-framework compliance tracking dashboard.

**Import:**
```tsx
import { CompliancePosture } from '@/features/security/components/CompliancePosture';
```

**Usage:**
```tsx
<CompliancePosture />
```

**Supported Frameworks:**
- SOC2
- ISO27001
- GDPR
- HIPAA

**Features:**
- Per-framework compliance %
- Control implementation tracking
- Audit timeline
- Overall compliance score
- Gap analysis buttons

---

#### **ModelPerformanceDashboard**

ML model performance metrics tracking.

**Import:**
```tsx
import { ModelPerformanceDashboard } from '@/features/models/components/ModelPerformanceDashboard';
```

**Usage:**
```tsx
<ModelPerformanceDashboard modelName="Risk Predictor" />
```

**Metrics Tracked:**
- Accuracy
- Precision
- Recall
- F1 Score
- AUC

**Features:**
- 7-day trend sparklines
- % change indicators (↑/↓)
- Status indicators
- Last updated timestamp

---

#### **ABTestResults**

A/B test results and champion/challenger comparison.

**Import:**
```tsx
import { ABTestResults } from '@/features/models/components/ABTestResults';
```

**Usage:**
```tsx
<ABTestResults />
```

**Features:**
- Test status (running/completed/paused)
- Champion vs Challenger comparison
- Statistical significance (p-value notation)
- Confidence intervals
- Winner recommendation

**P-Value Notation:**
- `***` = p < 0.001
- `**` = p < 0.01
- `*` = p < 0.05
- `ns` = not significant

---

#### **DriftMonitor**

Data drift detection and monitoring.

**Import:**
```tsx
import { DriftMonitor } from '@/features/models/components/DriftMonitor';
```

**Usage:**
```tsx
<DriftMonitor />
```

**Features:**
- Overall drift score (%)
- Feature drift heatmap
- Status indicators (healthy/warning/critical)
- Expandable detailed metrics
- Retraining recommendations

---

#### **ModelRegistry**

Model version management and registry.

**Import:**
```tsx
import { ModelRegistry } from '@/features/models/components/ModelRegistry';
```

**Usage:**
```tsx
<ModelRegistry
  onSetChampion={(id) => console.log(id)}
  onPromoteChallenger={(id) => console.log(id)}
/>
```

**Features:**
- Model version listing
- Champion/Challenger tracking
- Comparison mode
- Performance metrics per version
- Promotion and archival actions
- Role indicators (👑/⚔️/📦)

---

## Feature Modules

### Dashboard Module

**Path:** `src/features/dashboard/`

**Components:**
- `NaturalLanguageQuery.tsx` - NL query interface
- `WhatIfAnalysisPanel.tsx` - Scenario simulation
- `AIExplainabilityDrawer.tsx` - Decision reasoning
- `KpiGrid.tsx` - KPI card layout
- `KpiCard.tsx` - Individual metric card
- `TimelineChart.tsx` - Trend visualization

**Hooks:**
- `useNLQuery()` - Natural language query with React Query
- `useOrgKpis()` - Organization KPI data
- `useThemeStore()` - Theme preferences

---

### Security Module

**Path:** `src/features/security/`

**Components:**
- `VulnerabilityDashboard.tsx` - Vulnerability tracking
- `CompliancePosture.tsx` - Compliance framework status

**Hooks:**
- `useVulnerabilities()` - Fetch vulnerability data
- `useComplianceFrameworks()` - Compliance data

---

### Models Module

**Path:** `src/features/models/`

**Components:**
- `ModelPerformanceDashboard.tsx` - Metrics tracking
- `ABTestResults.tsx` - A/B test comparison
- `DriftMonitor.tsx` - Drift detection
- `ModelRegistry.tsx` - Model management

**Hooks:**
- `useModelPerformance()` - Performance metrics
- `useABTests()` - A/B test data
- `useDriftMonitor()` - Drift detection data
- `useModelRegistry()` - Model versions

---

## Integration Examples

### Connecting to Real API

#### Step 1: Create API Service

```typescript
// lib/services/modelsApi.ts
import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
});

export const modelsApi = {
  getPerformance: (modelId: string) =>
    client.get(`/api/v1/models/${modelId}/performance`),

  getDrift: (modelId: string) =>
    client.get(`/api/v1/models/${modelId}/drift`),

  getABTests: () =>
    client.get('/api/v1/ab-tests'),
};
```

#### Step 2: Create React Query Hook

```typescript
// features/models/hooks/useModelPerformance.ts
import { useQuery } from '@tanstack/react-query';
import { modelsApi } from '@/lib/services/modelsApi';

export const useModelPerformance = (modelId: string) => {
  return useQuery({
    queryKey: ['modelPerformance', modelId],
    queryFn: () => modelsApi.getPerformance(modelId),
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 10 * 60 * 1000, // 10 minutes
  });
};
```

#### Step 3: Use in Component

```typescript
// features/models/components/ModelPerformanceDashboard.tsx
export const ModelPerformanceDashboard = memo(
  function ModelPerformanceDashboard({ modelId }: Props) {
    const { data, isLoading, error } = useModelPerformance(modelId);

    if (isLoading) return <PerformanceSkeleton />;
    if (error) return <ErrorState />;

    return <Dashboard metrics={data} />;
  }
);
```

### Real-Time Updates with WebSocket

```typescript
// lib/hooks/useWebSocket.ts
export const useWebSocket = (url: string) => {
  const [data, setData] = useState(null);

  useEffect(() => {
    const ws = new WebSocket(url);
    ws.onmessage = (event) => setData(JSON.parse(event.data));
    return () => ws.close();
  }, [url]);

  return data;
};
```

---

## Accessibility Guide

### WCAG AA Compliance

All components are built with WCAG AA compliance:

✅ **Color Contrast**
- Normal text: 4.5:1 minimum
- Large text: 3:1 minimum
- Dark mode: Same ratios maintained

✅ **Keyboard Navigation**
- Tab to navigate
- Enter to activate
- Escape to close modals
- Arrow keys in lists
- Shortcuts (⌘+K, ⌘+1-9)

✅ **Screen Reader Support**
- ARIA labels on all interactive elements
- Live regions for status updates
- Semantic HTML structure
- Focus management

✅ **Focus Indicators**
- Always visible (minimum 2px outline)
- High contrast ratio
- Logical tab order

### Testing Accessibility

```bash
# Run accessibility tests
pnpm test:a11y

# Manual testing with tools
# - NVDA (Windows screen reader)
# - JAWS (Windows screen reader)
# - VoiceOver (macOS/iOS screen reader)
# - WebAIM WAVE (browser extension)
```

---

## Performance Tips

### Code Splitting

Routes are automatically code-split:

```typescript
// React Router automatically lazy loads routes
const routes = [
  { path: '/dashboard', Component: DashboardPage },
  { path: '/security', Component: SecurityPage },
  { path: '/models', Component: ModelsPage },
];
```

### Component Optimization

All components use `React.memo` for performance:

```typescript
export const ComponentName = memo(function ComponentName(props) {
  // Prevents unnecessary re-renders
  return <div>...</div>;
});
```

### Query Performance

Use React Query patterns:

```typescript
// Stale while revalidate
useQuery({
  queryKey: ['data'],
  queryFn: fetchData,
  staleTime: 5 * 60 * 1000, // Fresh for 5 minutes
  gcTime: 10 * 60 * 1000,   // Cache for 10 minutes
});
```

### Bundle Size Monitoring

```bash
# Check bundle size
pnpm build
pnpm analyse

# Target: <500KB gzipped
```

---

## Troubleshooting

### Common Issues

**Q: Components not rendering?**
```
A: Ensure QueryClientProvider wraps your app:
   <QueryClientProvider client={queryClient}>
     <App />
   </QueryClientProvider>
```

**Q: TypeScript errors on props?**
```
A: Import component types:
   import type { ComponentNameProps } from '@/features/...';
```

**Q: Styles not applying?**
```
A: Ensure Tailwind CSS is configured:
   - tailwind.config.js exists
   - content paths include src/
   - PostCSS configured
```

**Q: WebSocket connection issues?**
```
A: Check environment variables:
   VITE_WS_URL=ws://localhost:3000
   (Note: Use ws: for dev, wss: for production)
```

### Debug Mode

Enable detailed logging:

```typescript
// In App.tsx or main.tsx
if (import.meta.env.DEV) {
  window.__DEBUG__ = true;
}

// In components
if (window.__DEBUG__) {
  console.log('Debug:', data);
}
```

---

## Contributing

### Code Style

All code follows `copilot-instructions.md` patterns:

- React.memo for all components
- Comprehensive JSDoc documentation
- TypeScript interfaces for props
- Dark mode support (dark: prefixes)
- Responsive design with Tailwind

### Before Submitting PR

```bash
# Format code
pnpm format

# Run linter
pnpm lint

# Run type checker
pnpm type-check

# Run tests
pnpm test

# Check bundle size
pnpm build
```

### File Structure for New Features

```
src/features/new-feature/
├── components/
│   ├── Component1.tsx
│   └── Component2.tsx
├── pages/
│   └── FeaturePage.tsx
├── hooks/
│   └── useFeatureData.ts
├── types/
│   └── feature.types.ts
└── index.ts
```

---

## Testing Commands

```bash
# Run all tests
pnpm test

# Run specific test file
pnpm test NaturalLanguageQuery

# Run with coverage
pnpm test:coverage

# Run E2E tests
pnpm test:e2e

# Run accessibility tests
pnpm test:a11y
```

---

## Production Deployment

### Build

```bash
pnpm build
# Output: dist/ directory
```

### Environment

```env
# Production .env
VITE_API_URL=https://api.production.com
VITE_WS_URL=wss://api.production.com
```

### Deployment Checklist

- [ ] All tests passing
- [ ] No TypeScript errors
- [ ] Bundle size <500KB
- [ ] Lighthouse >90
- [ ] Performance metrics tracked
- [ ] Error tracking configured
- [ ] Analytics enabled
- [ ] Monitoring set up

---

## Support

For issues, questions, or feature requests:
1. Check this guide and troubleshooting section
2. Review component props and usage examples
3. Check related test files for usage patterns
4. Consult copilot-instructions.md for code standards
5. File an issue on GitHub with detailed reproduction steps

---

**Last Updated:** November 2025  
**Version:** 2.0  
**Status:** ✅ Production Ready
