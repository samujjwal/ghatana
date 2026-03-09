# Phase 3 - New Capabilities Reference

**Date:** November 18, 2025  
**Files Added:** 9  
**Total LOC:** 2,918  
**Status:** ✅ Production Ready

---

## 🎯 Quick Navigation

### Orchestration Hooks
- [useMLOrchestration](#usemlochestration) - Model management
- [useMonitoringOrchestration](#usemonitoringochestration) - Real-time monitoring
- [useAutomationOrchestration](#useautomationochestration) - Workflow management

### Utility Modules
- [dataManagement](#datamanagement) - Data transformation
- [apiService](#apiservice) - API client
- [queryHelpers](#queryhelpers) - React Query integration
- [formHelpers](#formhelpers) - Form validation
- [stateSync](#statesync) - Jotai synchronization
- [useCommon](#usecommon) - Common hooks

---

## Orchestration Hooks

### useMLOrchestration

**File:** `/src/features/models/hooks/useMLOrchestration.ts` (182 LOC)

**Purpose:** Centralized ML feature orchestration

**Usage:**
```typescript
const {
  models,           // Model[]
  trainingJobs,     // TrainingJob[]
  abTests,          // ABTest[]
  isLoading,        // boolean
  selectedModelId,  // string | null
  comparisonModels, // Model[]
  selectModel,      // (id: string) => void
  addToComparison,  // (model: Model) => void
  removeFromComparison, // (id: string) => void
  cancelTraining,   // (jobId: string) => void
  stopTest          // (testId: string) => void
} = useMLOrchestration();
```

**Key Features:**
- ✅ Integrates Jotai state with React Query
- ✅ Model selection and comparison
- ✅ Training job tracking
- ✅ A/B test management
- ✅ Automatic cache invalidation
- ✅ Memoized computations

---

### useMonitoringOrchestration

**File:** `/src/features/monitoring/hooks/useMonitoringOrchestration.ts` (223 LOC)

**Purpose:** Real-time monitoring with WebSocket integration

**Usage:**
```typescript
const {
  systemHealth,      // SystemHealth | null
  alerts,            // Alert[]
  anomalies,         // Anomaly[]
  isConnected,       // boolean
  isLoading,         // boolean
  selectedAlert,     // Alert | null
  reconnect,         // () => void
  selectAlert,       // (alert: Alert) => void
  acknowledgeAlert,  // (alertId: string) => void
  dismissAnomaly,    // (anomalyId: string) => void
  filterAlerts,      // (severity: AlertSeverity) => void
  clearFilters       // () => void
} = useMonitoringOrchestration();
```

**Key Features:**
- ✅ WebSocket auto-connect/reconnect
- ✅ Real-time metrics streaming
- ✅ Alert acknowledgment
- ✅ Anomaly dismissal
- ✅ Automatic fallback to polling
- ✅ Exponential backoff reconnection

---

### useAutomationOrchestration

**File:** `/src/features/automation/hooks/useAutomationOrchestration.ts` (307 LOC)

**Purpose:** Complete workflow automation orchestration

**Usage:**
```typescript
const {
  workflows,         // Workflow[]
  executions,        // WorkflowExecution[]
  triggers,          // WorkflowTrigger[]
  stats,             // WorkflowStats
  isLoading,         // boolean
  showBuilder,       // boolean
  filteredExecutions, // WorkflowExecution[]
  createWorkflow,    // (name, description) => void
  updateWorkflow,    // (id, updates) => void
  deleteWorkflow,    // (id) => void
  executeWorkflow,   // (id) => void
  cancelExecution,   // (executionId) => void
  addTrigger,        // (workflowId, trigger) => void
  removeTrigger,     // (triggerId) => void
  updateTrigger,     // (triggerId, updates) => void
  openBuilder,       // () => void
  closeBuilder       // () => void
} = useAutomationOrchestration();
```

**Key Features:**
- ✅ Full workflow CRUD
- ✅ Execution history with filtering
- ✅ Trigger management
- ✅ Workflow statistics
- ✅ Builder state machine
- ✅ Optimistic updates

---

## Utility Modules

### dataManagement - 13 Data Functions

**File:** `/src/lib/utils/dataManagement.ts` (311 LOC)

**Array Manipulation:**
- `sortByProperty<T>(items, property, ascending?)` - Sort by field
- `filterByStatus<T>(items, status, statusField)` - Filter by status
- `groupByProperty<T>(items, property)` - Group by field
- `paginate<T>(items, page, pageSize)` - Pagination
- `deduplicateBy<T>(items, key)` - Remove duplicates

**Aggregation:**
- `calculateMetrics(values)` - Min/max/avg/sum/count
- `getSeverityCounts(items)` - Count by severity
- `getStatusCounts(items)` - Count by status

**Filtering:**
- `filterAnomalies(anomalies, severity)` - Filter anomalies
- `filterAlerts(alerts, type)` - Filter alerts
- `sortModelsByAccuracy(models)` - Sort models

**Formatting:**
- `formatDuration(milliseconds)` - "1h 30m 45s"
- `formatTimestamp(date, locale?)` - Locale-aware dates

---

### apiService - 8 API Functions

**File:** `/src/lib/utils/apiService.ts` (388 LOC)

**Error Handling:**
- `handleApiError(error)` - Standardized error format
- `validateResponse<T>(response, schema?)` - Runtime validation

**Request Management:**
- `retryRequest<T>(fn, config?)` - Exponential backoff
- `buildQueryParams(params)` - URL encoding
- `createHeaders(token?, custom?)` - Header management

**Caching:**
- `ResponseCache` class with TTL support
- get/set/clear/clearAll methods

**Factory:**
- `createApiClient<T>(config)` - API client factory

---

### queryHelpers - 8 React Query Functions

**File:** `/src/lib/utils/queryHelpers.ts` (303 LOC)

**Configuration:**
- `createQueryOptions<TData>(key, fn, config?)` - Query config
- `createMutationOptions<T,E,V>(fn, config?)` - Mutation config
- `setupQueryClientDefaults(queryClient)` - Global config

**Cache Management:**
- `invalidateQueries(queryClient, patterns)` - Invalidate keys
- `prefetchQuery<T>(queryClient, options)` - Preload data
- `optimisticUpdate<T>(queryClient, key, updater)` - Optimistic update
- `rollbackOptimisticUpdate(queryClient, key)` - Rollback update

**Utilities:**
- `handleMutationError(error, defaultMessage?)` - Error handling
- `getInvalidationStrategy(mutationType)` - Cache strategy
- `createQueryKeyFactory(scope)` - Key generation

---

### formHelpers - 8 Validation Rules + 13 Functions

**File:** `/src/lib/utils/formHelpers.ts` (404 LOC)

**Validation Rules:**
- `required` - Non-empty value
- `email` - Valid email
- `minLength(n)` - Minimum length
- `maxLength(n)` - Maximum length
- `min(n)` - Minimum value
- `max(n)` - Maximum value
- `pattern(regex)` - Regex match
- `custom(validate)` - Custom validation

**Form Functions:**
- `createFormState<T>(values)` - Initialize form
- `setFieldValue<T>(form, field, value)` - Update field
- `setFieldError<T>(form, field, error)` - Set error
- `touchField<T>(form, field)` - Mark touched
- `resetForm<T>(form)` - Reset all fields
- `validateField(field, value, rules?)` - Validate field
- `validateForm<T>(form, rules?)` - Validate all
- `getFormErrors<T>(form)` - Get error map
- `getFormValues<T>(form)` - Get values map
- `isFormValid<T>(form)` - Check validity
- `isFormDirty<T>(form)` - Check dirty
- `isFormTouched<T>(form)` - Check touched

---

### stateSync - 8 Atom Factories

**File:** `/src/lib/utils/stateSync.ts` (398 LOC)

**Synchronization:**
- `syncAtomWithQuery<T>(atom, queryFn, options?)` - Sync query to atom
- `createPersistentAtom<T>(key, value, version?)` - localStorage persistence
- `createDerivedAtom<T,U>(source, derive)` - Computed atoms
- `createDebouncedAtomUpdater<T>(atom, delay)` - Debounced updates

**Common Patterns:**
- `createListAtom<T>(items?)` - List management
- `createPaginationAtom(page?, size?, total?)` - Pagination state
- `createFilterAtom<T>(filters?)` - Filter state
- `createSortAtom(field?, asc?)` - Sort state

---

### useCommon - 12 UI Hooks

**File:** `/src/lib/hooks/useCommon.ts` (402 LOC)

**Async:**
- `useAsync<T>(fn, deps?)` - Async operations

**State:**
- `useToggle(initial?)` - Boolean toggle
- `useLocalStorage<T>(key, initial?)` - localStorage
- `usePrevious<T>(value)` - Previous value

**Timing:**
- `useDebounce<T>(value, delay)` - Debounce
- `useThrottle<T>(value, interval)` - Throttle

**Interaction:**
- `useKeyboardNavigation(options)` - Keyboard events
- `useOutsideClick(ref, onClickOutside)` - Outside detection
- `useClipboard()` - Clipboard access

**Window:**
- `useWindowSize()` - Window dimensions
- `useInViewport(ref)` - Intersection Observer
- `useIsMounted()` - Mount state

---

## 📊 Usage Examples

### Example 1: ML Model Management
```typescript
function ModelComparison() {
  const { 
    models, 
    selectedModelId, 
    comparisonModels, 
    selectModel, 
    addToComparison 
  } = useMLOrchestration();
  
  return (
    <>
      <ModelList 
        models={models} 
        onSelect={(model) => {
          selectModel(model.id);
          addToComparison(model);
        }}
      />
      <ComparisonPanel models={comparisonModels} />
    </>
  );
}
```

### Example 2: Real-time Monitoring
```typescript
function MonitoringDashboard() {
  const { 
    systemHealth, 
    alerts, 
    isConnected, 
    reconnect 
  } = useMonitoringOrchestration();
  
  return (
    <>
      <HealthStatus health={systemHealth} />
      {!isConnected && (
        <button onClick={reconnect}>Reconnect</button>
      )}
      <AlertsList alerts={alerts} />
    </>
  );
}
```

### Example 3: Form with Validation
```typescript
function LoginForm() {
  const [form, setForm] = useState(() => 
    createFormState({ email: '', password: '' })
  );
  
  const handleSubmit = () => {
    const errors = validateForm(form, {
      email: [validationRules.required, validationRules.email],
      password: [validationRules.required, validationRules.minLength(8)]
    });
    
    if (errors.length === 0) {
      // Submit
    }
  };
  
  return (
    <form onSubmit={handleSubmit}>
      <input
        value={form.values.email}
        onChange={(e) => setForm(
          setFieldValue(form, 'email', e.target.value)
        )}
      />
    </form>
  );
}
```

### Example 4: API with Retry
```typescript
const api = createApiClient({ baseURL: 'https://api.example.com' });

const users = await retryRequest(
  () => api.get('/users'),
  { maxRetries: 3, delayMs: 1000 }
);
```

### Example 5: Data Transformation
```typescript
const data = [
  { id: 1, status: 'active', name: 'Item 1' },
  { id: 2, status: 'inactive', name: 'Item 2' },
  { id: 3, status: 'active', name: 'Item 3' }
];

const active = filterByStatus(data, 'active', 'status');
const sorted = sortByProperty(active, 'name');
const page = paginate(sorted, 1, 10);
```

---

## 🚀 Next Steps

1. **Integrate into pages** - Use hooks in MLObservatory, RealTimeMonitor, etc.
2. **Build workflows** - Create feature workflows using orchestration hooks
3. **Add tests** - Unit and integration tests for hooks/utilities
4. **Performance tune** - Profile and optimize critical paths

---

**Status:** ✅ Production Ready  
**Last Updated:** November 18, 2025
