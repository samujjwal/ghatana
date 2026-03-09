import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import { getPersonaConfig, type UserRole } from '@/config/personaConfig';
import type { Feature } from '@/shared/components/FeatureGrid';
import type {
    Persona,
    PersonaAvailabilityStatus,
    ExecutionContext,
    GrowthGoal,
    PlannedAbsence,
    DevSecOpsPhaseId,
} from '@/shared/types/org';

/**
 * Jotai atom definitions for Software-Org frontend state management.
 *
 * <p><b>Purpose</b><br>
 * Centralized atom store for app-scoped state across all 28 components (Days 4-10).
 * Organizes state into: global, feature-specific, persona, and persistence layers.
 * All atoms with localStorage persistence where appropriate.
 *
 * <p><b>Atom Types</b><br>
 * - Global Atoms: tenant, timeRange, theme, sidebarCollapsed, userRole
 * - Persona Atoms: userProfile, personaConfig, recentActivities, pinnedFeatures, pendingTasks
 * - Workflow Atoms: selectedEventIds, playbackSpeed, flowFilter, inspectorEventId
 * - HITL Atoms: selectedActionId, hitlQueueFilter, deferralReason, slaBreachHighlight
 * - Simulator Atoms: selectedEventType, simulatorPayload, recentTemplates
 * - Reporting Atoms: reportTimeRange, reportFilters, exportFormat
 * - Security Atoms: auditLogFilter, complianceFilter, selectedUser
 * - Model Atoms: selectedModels, compareEnabled, testFilter, modelSortBy
 * - Feature Atoms: activeTab, editingSettings, notificationFilter, helpSearchQuery
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useAtom } from 'jotai';
 * import { tenantAtom, userProfileAtom } from '@/state/jotai/atoms';
 *
 * function MyComponent() {
 *   const [tenant, setTenant] = useAtom(tenantAtom);
 *   const [userProfile] = useAtom(userProfileAtom);
 *   // Component logic
 * }
 * ```
 *
 * @doc.type configuration
 * @doc.purpose Jotai atom definitions for app state
 * @doc.layer product
 * @doc.pattern State Management
 */

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface UserProfile {
    userId: string;
    name: string;
    email: string;
    role: UserRole;
    permissions: string[];
    preferences?: {
        favoriteFeatures?: string[];
        quickActions?: string[];
        dashboardLayout?: string;
    };
}

export interface Activity {
    id: string;
    type: 'workflow_created' | 'simulation_run' | 'approval_completed' | 'report_generated' | 'model_deployed';
    title: string;
    description: string;
    timestamp: Date;
    status: 'success' | 'failed' | 'pending';
    href?: string;
}

export interface PendingTasks {
    hitlApprovals: number;
    securityAlerts: number;
    failedWorkflows: number;
    modelAlerts: number;
}

// ============================================================================
// GLOBAL ATOMS (Shared across all features)
// ============================================================================

/**
 * Selected tenant - persists to localStorage
 */
export const tenantAtom = atomWithStorage<string>('software-org:tenant', 'all-tenants');

/**
 * Global time range for filtering - persists to localStorage
 */
export const timeRangeAtom = atomWithStorage<'7d' | '30d' | '90d' | 'custom'>('software-org:time-range', '7d');

/**
 * Custom date range (used when timeRange is 'custom')
 */
export const customDateRangeAtom = atomWithStorage<{ start: string; end: string }>(
    'software-org:custom-date-range',
    { start: '', end: '' }
);

/**
 * Application theme - persists to localStorage
 */
type ThemeMode = 'light' | 'dark' | 'system';

const getInitialTheme = (): ThemeMode => {
    if (typeof window === 'undefined') {
        // On the server, fall back to system so CSS media queries can decide.
        return 'system';
    }

    // Prefer the theme that was already applied to <html> by the inline script
    const attr = document.documentElement.getAttribute('data-theme') as ThemeMode | null;
    if (attr === 'light' || attr === 'dark' || attr === 'system') {
        return attr;
    }

    // Fallback to localStorage if the attribute is missing for some reason
    const stored = window.localStorage.getItem('software-org:theme') as ThemeMode | null;
    if (stored === 'light' || stored === 'dark' || stored === 'system') {
        return stored;
    }

    return 'system';
};

export const themeAtom = atom<ThemeMode>(getInitialTheme());

/**
 * Sidebar collapsed state
 */
export const sidebarCollapsedAtom = atomWithStorage<boolean>('software-org:sidebar-collapsed', false);

/**
 * Current user role
 */
export const userRoleAtom = atom<'admin' | 'lead' | 'engineer' | 'viewer'>('engineer');

// ============================================================================
// PERSONA ATOMS (User-specific personalization)
// ============================================================================

/**
 * Current user profile with role, permissions, and preferences
 * Populated on login from JWT token or API endpoint
 */
export const userProfileAtom = atom<UserProfile | null>(null);

/**
 * Persona override atom - allows explicit persona context from /workspace/:personaId route
 * When set, this takes precedence over the user's profile role
 */
export const personaOverrideAtom = atom<UserRole | null>(null);

/**
 * Persona-specific configuration (derived from userProfileAtom or personaOverrideAtom)
 * Contains quick actions, metrics, and features for current role
 */
export const personaConfigAtom = atom((get) => {
    // Check for explicit persona override first (from /workspace/:personaId route)
    const override = get(personaOverrideAtom);
    if (override) {
        return getPersonaConfig(override);
    }
    // Prefer the user's profile role if available; otherwise fallback to the global userRoleAtom
    const profile = get(userProfileAtom);
    const fallbackRole = get(userRoleAtom);
    const role = profile?.role || fallbackRole;
    return getPersonaConfig(role);
});

/**
 * Recent user activities (last 10 actions)
 * Persisted to localStorage per user
 */
export const recentActivitiesAtom = atomWithStorage<Activity[]>(
    'software-org:recent-activities',
    []
);

/**
 * Pinned/favorite features per user
 * Persisted to localStorage per user
 */
export const pinnedFeaturesAtom = atomWithStorage<Feature[]>(
    'software-org:pinned-features',
    []
);

/**
 * User's pending tasks/notifications count
 * Updated periodically from API
 */
export const pendingTasksAtom = atom<PendingTasks>({
    hitlApprovals: 0,
    securityAlerts: 0,
    failedWorkflows: 0,
    modelAlerts: 0,
});

/**
 * Derived: Is user authenticated
 */
export const isAuthenticatedAtom = atom((get) => {
    const profile = get(userProfileAtom);
    return profile !== null;
});

/**
 * Derived: Current user's display name
 */
export const userDisplayNameAtom = atom((get) => {
    const profile = get(userProfileAtom);
    return profile?.name || 'Guest';
});

/**
 * Derived: Time-based greeting (Good morning/afternoon/evening)
 */
export const userGreetingAtom = atom((get) => {
    const name = get(userDisplayNameAtom);
    const hour = new Date().getHours();

    let greeting = 'Good evening';
    if (hour >= 5 && hour < 12) {
        greeting = 'Good morning';
    } else if (hour >= 12 && hour < 18) {
        greeting = 'Good afternoon';
    }

    return `${greeting}, ${name}`;
});

// ============================================================================
// DAY 4: WORKFLOW ATOMS
// ============================================================================

/**
 * Selected event IDs for workflow visualization (multiple selection)
 */
export const selectedEventIdsAtom = atom<Set<string>>(new Set<string>());

/**
 * Playback speed for timeline (0.5x, 1x, 2x)
 */
export const playbackSpeedAtom = atom<0.5 | 1 | 2>(1);

/**
 * Filter for workflow display (all, critical, completed, pending)
 */
export const flowFilterAtom = atom<'all' | 'critical' | 'completed' | 'pending'>('all');

/**
 * Currently inspected event ID (for FlowInspectorDrawer)
 */
export const inspectorEventIdAtom = atom<string | null>(null);

/**
 * Workflow timeline is playing
 */
export const flowPlayingAtom = atom<boolean>(false);

/**
 * Canvas transform state for WorkflowCanvas (zoom/pan)
 */
export const canvasTransformAtom = atom<{ x: number; y: number; k: number }>({ x: 0, y: 0, k: 1 });

/**
 * Hovered node in workflow canvas
 */
export const canvasHoveredNodeAtom = atom<string | null>(null);

/**
 * Selected edge in workflow canvas
 */
export const canvasSelectedEdgeAtom = atom<string | null>(null);

// ============================================================================
// DAY 5: HITL CONSOLE ATOMS
// ============================================================================

/**
 * Currently selected action ID in HITL queue
 */
export const selectedActionIdAtom = atom<string | null>(null);

/**
 * HITL queue filter (priority, type, status)
 */
export const hitlQueueFilterAtom = atom<'all' | 'p0' | 'p1' | 'p2'>('all');

/**
 * Current deferral reason for deferred actions
 */
export const deferralReasonAtom = atom<string>('');

/**
 * Highlight SLA breaches
 */
export const slaBreachHighlightAtom = atom<boolean>(true);

/**
 * Sort HITL queue by (priority, time-received, sla-remaining)
 */
export const hitlSortByAtom = atom<'priority' | 'time' | 'sla'>('priority');

// ============================================================================
// DAY 6: EVENT SIMULATOR ATOMS
// ============================================================================

/**
 * Selected event type in simulator
 */
export const selectedEventTypeAtom = atom<'transaction' | 'error' | 'anomaly'>('transaction');

/**
 * Current simulator payload (JSON string)
 */
export const simulatorPayloadAtom = atom<string>('{}');

/**
 * Recent event templates (for quick reuse)
 */
export const recentTemplatesAtom = atom<Array<{ id: string; name: string; payload: string }>>([]);

/**
 * Simulator validation errors
 */
export const simulatorErrorsAtom = atom<string[]>([]);

/**
 * Show simulator dry-run results
 */
export const showSimulatorResultsAtom = atom<boolean>(false);

// ============================================================================
// DAY 7: REPORTING ATOMS
// ============================================================================

/**
 * Time range specific to reporting (may differ from global)
 */
export const reportTimeRangeAtom = atom<'today' | '7d' | '30d' | '90d' | 'custom'>('30d');

/**
 * Reporting page filters
 */
export const reportFiltersAtom = atom<{
    severity?: 'all' | 'low' | 'medium' | 'high' | 'critical';
    service?: string;
    department?: string;
}>({});

/**
 * Export format for reports
 */
export const exportFormatAtom = atom<'pdf' | 'csv' | 'json'>('pdf');

/**
 * Report comparisons (scenario A vs scenario B)
 */
export const reportComparisonAtom = atom<{ scenarioA: string; scenarioB: string }>({ scenarioA: '', scenarioB: '' });

// ============================================================================
// DAY 8: SECURITY ATOMS
// ============================================================================

/**
 * Audit log filter settings
 */
export const auditLogFilterAtom = atom<{
    severity?: 'all' | 'info' | 'warning' | 'critical';
    action?: string;
    user?: string;
}>({});

/**
 * Compliance checklist filter
 */
export const complianceFilterAtom = atom<'all' | 'passed' | 'failed' | 'pending'>('all');

/**
 * Selected user for access review
 */
export const selectedUserAtom = atom<string | null>(null);

/**
 * Vulnerability search query
 */
export const vulnerabilitySearchAtom = atom<string>('');

// ============================================================================
// DAY 9: MODEL ATOMS
// ============================================================================

/**
 * Selected models for comparison (can select multiple)
 */
export const selectedModelsAtom = atom<string[]>([]);

/**
 * Enable/disable comparison mode
 */
export const compareEnabledAtom = atom<boolean>(false);

/**
 * Test suite filter (unit, integration, e2e)
 */
export const testFilterAtom = atom<'all' | 'unit' | 'integration' | 'e2e'>('all');

/**
 * Sort model list by (name, accuracy, latency, throughput)
 */
export const modelSortByAtom = atom<'name' | 'accuracy' | 'latency' | 'throughput'>('name');

/**
 * Current model detail view state
 */
export const modelDetailViewAtom = atom<'catalog' | 'details' | 'compare' | 'test'>('catalog');

// ============================================================================
// DAY 10: ADVANCED FEATURE ATOMS
// ============================================================================

/**
 * Active settings tab
 */
export const activeSettingsTabAtom = atom<'general' | 'notifications' | 'integrations' | 'account'>('general');

/**
 * Settings form editing state
 */
export const editingSettingsAtom = atom<boolean>(false);

/**
 * Notification center filter (all, unread)
 */
export const notificationFilterAtom = atom<'all' | 'unread'>('all');

/**
 * Help center search query
 */
export const helpSearchQueryAtom = atom<string>('');

/**
 * Help center selected category
 */
export const helpSelectedCategoryAtom = atom<string>('all');

/**
 * Export utility - selected format
 */
export const exportUtilFormatAtom = atom<'pdf' | 'csv' | 'json' | 'excel'>('csv');

/**
 * Export utility - selected data type
 */
export const exportUtilDataTypeAtom = atom<'incidents' | 'metrics' | 'audit' | 'models' | 'alerts'>('metrics');

/**
 * Export utility - selected columns
 */
export const exportUtilColumnsAtom = atom<Set<string>>(new Set<string>());

/**
 * Export utility - date range for export
 */
export const exportUtilDateRangeAtom = atom<'today' | '7d' | '30d' | 'custom'>('7d');

// ============================================================================
// DERIVED/COMPUTED ATOMS (Read-only, computed from base atoms)
// ============================================================================

/**
 * Whether dark mode is active (computed from themeAtom)
 */
export const isDarkModeAtom = atom((get) => {
    const theme = get(themeAtom);
    if (theme === 'system') {
        return typeof window !== 'undefined'
            ? window.matchMedia('(prefers-color-scheme: dark)').matches
            : true;
    }
    return theme === 'dark';
});

/**
 * Display name for current time range (derived)
 */
export const timeRangeDisplayAtom = atom((get) => {
    const range = get(timeRangeAtom);
    const labels: Record<string, string> = {
        '7d': 'Last 7 days',
        '30d': 'Last 30 days',
        '90d': 'Last 90 days',
        'custom': 'Custom range',
    };
    return labels[range] || range;
});

/**
 * Model comparison enabled indicator (derived from selectedModelsAtom)
 */
export const modelComparisonAvailableAtom = atom((get) => {
    const selected = get(selectedModelsAtom);
    return selected.length >= 2;
});

/**
 * Is HITL queue showing urgent items (derived)
 */
export const hitlHasUrgentAtom = atom((get) => {
    const filter = get(hitlQueueFilterAtom);
    return filter === 'p0';
});

// ============================================================================
// UNIFIED PERSONA ATOMS (Human/Agent Agnostic)
// ============================================================================

/**
 * Current persona entity (unified human/agent model)
 * Contains capacity, availability, growth, and tool configurations
 */
export const currentPersonaAtom = atom<Persona | null>(null);

/**
 * Current persona's availability status
 * Derived from currentPersonaAtom for quick access
 */
export const personaAvailabilityStatusAtom = atom<PersonaAvailabilityStatus>('available');

/**
 * Current persona's active work session
 * Tracks focus time, active task, and session metrics
 */
export interface WorkSession {
    /** Session ID */
    id: string;
    /** Session start time */
    startedAt: string;
    /** Active work item ID (if any) */
    activeWorkItemId: string | null;
    /** Current DevSecOps phase */
    currentPhase: DevSecOpsPhaseId | null;
    /** Focus mode enabled */
    focusMode: boolean;
    /** Break time accumulated (minutes) */
    breakTimeMinutes: number;
    /** Productive time accumulated (minutes) */
    productiveTimeMinutes: number;
}

export const workSessionAtom = atom<WorkSession | null>(null);

/**
 * Derived: Is work session active
 */
export const isWorkSessionActiveAtom = atom((get) => {
    return get(workSessionAtom) !== null;
});

/**
 * Current persona's growth goals
 */
export const personaGrowthGoalsAtom = atom<GrowthGoal[]>([]);

/**
 * Current persona's planned absences (PTO for humans, maintenance for agents)
 */
export const personaPlannedAbsencesAtom = atom<PlannedAbsence[]>([]);

/**
 * Active execution context for current work item
 * Contains all tools and integrations needed to complete the work
 */
export const activeExecutionContextAtom = atom<ExecutionContext | null>(null);

/**
 * Selected tool tab in execution panel
 */
export type ExecutionToolTab = 'canvas' | 'terminal' | 'editor' | 'vcs' | 'ci' | 'observability' | 'security' | 'ai';
export const selectedExecutionToolAtom = atom<ExecutionToolTab>('canvas');

/**
 * Current DevSecOps phase for the active persona
 */
export const currentDevSecOpsPhaseAtom = atom<DevSecOpsPhaseId | null>(null);

/**
 * Persona work items - tasks assigned to current persona
 */
export interface PersonaWorkItem {
    id: string;
    title: string;
    description: string;
    status: 'backlog' | 'ready' | 'in-progress' | 'in-review' | 'staging' | 'deployed' | 'done' | 'blocked';
    priority: 'p0' | 'p1' | 'p2' | 'p3';
    phase: DevSecOpsPhaseId;
    dueDate?: string;
    estimatedHours?: number;
    loggedHours?: number;
    tags: string[];
}

export const personaWorkItemsAtom = atom<PersonaWorkItem[]>([]);

/**
 * Selected work item ID for detail view
 */
export const selectedWorkItemIdAtom = atom<string | null>(null);

/**
 * Derived: Selected work item entity
 */
export const selectedWorkItemAtom = atom((get) => {
    const workItems = get(personaWorkItemsAtom);
    const selectedId = get(selectedWorkItemIdAtom);
    if (!selectedId) return null;
    return workItems.find((item) => item.id === selectedId) || null;
});

/**
 * Work item filters for persona dashboard
 */
export interface WorkItemFilters {
    status?: string[];
    priority?: string[];
    phase?: DevSecOpsPhaseId[];
    search?: string;
    dueDateRange?: { start: string; end: string };
}

export const workItemFiltersAtom = atom<WorkItemFilters>({});

/**
 * Derived: Filtered work items based on current filters
 */
export const filteredWorkItemsAtom = atom((get) => {
    const workItems = get(personaWorkItemsAtom);
    const filters = get(workItemFiltersAtom);

    return workItems.filter((item) => {
        if (filters.status?.length && !filters.status.includes(item.status)) {
            return false;
        }
        if (filters.priority?.length && !filters.priority.includes(item.priority)) {
            return false;
        }
        if (filters.phase?.length && !filters.phase.includes(item.phase)) {
            return false;
        }
        if (filters.search) {
            const searchLower = filters.search.toLowerCase();
            if (
                !item.title.toLowerCase().includes(searchLower) &&
                !item.description.toLowerCase().includes(searchLower)
            ) {
                return false;
            }
        }
        return true;
    });
});

/**
 * Derived: Work items grouped by DevSecOps phase (for Kanban view)
 */
export const workItemsByPhaseAtom = atom((get) => {
    const workItems = get(filteredWorkItemsAtom);
    const phases: DevSecOpsPhaseId[] = [
        'intake',
        'plan',
        'build',
        'verify',
        'review',
        'staging',
        'deploy',
        'operate',
        'learn',
    ];

    const grouped: Record<DevSecOpsPhaseId, PersonaWorkItem[]> = {} as Record<DevSecOpsPhaseId, PersonaWorkItem[]>;
    for (const phase of phases) {
        grouped[phase] = workItems.filter((item) => item.phase === phase);
    }
    return grouped;
});

/**
 * Derived: Work item counts by status
 */
export const workItemCountsAtom = atom((get) => {
    const workItems = get(personaWorkItemsAtom);
    return {
        total: workItems.length,
        backlog: workItems.filter((i) => i.status === 'backlog').length,
        inProgress: workItems.filter((i) => i.status === 'in-progress').length,
        inReview: workItems.filter((i) => i.status === 'in-review').length,
        done: workItems.filter((i) => i.status === 'done').length,
        blocked: workItems.filter((i) => i.status === 'blocked').length,
    };
});

/**
 * Persona capacity utilization (derived)
 */
export const personaCapacityUtilizationAtom = atom((get) => {
    const persona = get(currentPersonaAtom);
    if (!persona) return { available: 100, allocated: 0, utilization: 0 };
    return {
        available: persona.capacity.available,
        allocated: persona.capacity.allocated,
        utilization: persona.capacity.currentUtilization,
    };
});
