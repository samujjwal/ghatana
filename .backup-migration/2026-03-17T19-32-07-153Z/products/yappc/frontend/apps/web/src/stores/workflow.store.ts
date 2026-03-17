/**
 * Workflow State Management with Jotai
 *
 * @doc.type module
 * @doc.purpose Client-side state management for workflows
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

import type {
    Workflow,
    WorkflowStep,
    WorkflowStatus,
    WorkflowType,
    AIMode,
    WorkflowSteps,
    WorkflowAuditEntry,
    WORKFLOW_STEPS,
} from '@ghatana/yappc-types';

// Removed: import { sampleWorkflows } from './workflow.sample-data';
// Workflows are now loaded from the API via TanStack Query

// ============================================================================
// WORKFLOW LIST STATE
// ============================================================================

/**
 * List of workflows for the current user/view.
 * Initially empty - data is loaded from the API.
 */
export const workflowListAtom = atom<Workflow[]>([]);

/**
 * Loading state for workflow list.
 */
export const workflowListLoadingAtom = atom<boolean>(false);

/**
 * Error state for workflow list.
 */
export const workflowListErrorAtom = atom<string | null>(null);

/**
 * Filter state for workflow list.
 */
export interface WorkflowListFilter {
    status?: WorkflowStatus;
    workflowType?: WorkflowType;
    search?: string;
}

export const workflowListFilterAtom = atomWithStorage<WorkflowListFilter>(
    'workflow-list-filter',
    {}
);

// ============================================================================
// CURRENT WORKFLOW STATE
// ============================================================================

/**
 * Currently selected/active workflow.
 */
export const currentWorkflowAtom = atom<Workflow | null>(null);

/**
 * Loading state for current workflow.
 */
export const currentWorkflowLoadingAtom = atom<boolean>(false);

/**
 * Error state for current workflow.
 */
export const currentWorkflowErrorAtom = atom<string | null>(null);

/**
 * Derived atom for current step from workflow.
 */
export const currentStepAtom = atom<WorkflowStep | null>((get) => {
    const workflow = get(currentWorkflowAtom);
    return workflow?.currentStep ?? null;
});

/**
 * Derived atom for workflow status.
 */
export const workflowStatusAtom = atom<WorkflowStatus | null>((get) => {
    const workflow = get(currentWorkflowAtom);
    return workflow?.status ?? null;
});

/**
 * Derived atom for AI mode.
 */
export const aiModeAtom = atom<AIMode | null>((get) => {
    const workflow = get(currentWorkflowAtom);
    return workflow?.aiMode ?? null;
});

// ============================================================================
// STEP DATA STATE
// ============================================================================

/**
 * Current step's data (derived from workflow).
 */
export const currentStepDataAtom = atom((get) => {
    const workflow = get(currentWorkflowAtom);
    const currentStep = get(currentStepAtom);

    if (!workflow || !currentStep) return null;

    const stepKey = currentStep.toLowerCase() as keyof WorkflowSteps;
    return workflow.steps[stepKey];
});

/**
 * Unsaved changes tracking.
 */
export const hasUnsavedChangesAtom = atom<boolean>(false);

/**
 * Draft step data (for editing before save).
 */
export const draftStepDataAtom = atom<unknown | null>(null);

// ============================================================================
// NAVIGATION STATE
// ============================================================================

/**
 * Step navigation state.
 */
export interface StepNavigationState {
    canAdvance: boolean;
    canGoBack: boolean;
    nextStep: WorkflowStep | null;
    previousStep: WorkflowStep | null;
}

const STEP_ORDER: WorkflowStep[] = [
    'INTENT',
    'CONTEXT',
    'PLAN',
    'EXECUTE',
    'VERIFY',
    'OBSERVE',
    'LEARN',
    'INSTITUTIONALIZE',
];

export const stepNavigationAtom = atom<StepNavigationState>((get) => {
    const currentStep = get(currentStepAtom);
    const workflow = get(currentWorkflowAtom);

    if (!currentStep || !workflow) {
        return {
            canAdvance: false,
            canGoBack: false,
            nextStep: null,
            previousStep: null,
        };
    }

    const currentIndex = STEP_ORDER.indexOf(currentStep);
    const stepKey = currentStep.toLowerCase() as keyof WorkflowSteps;
    const stepState = workflow.steps[stepKey];

    // Can advance if current step has data and is not blocked
    const canAdvance =
        currentIndex < STEP_ORDER.length - 1 &&
        stepState.status !== 'BLOCKED' &&
        workflow.status !== 'BLOCKED' &&
        workflow.status !== 'COMPLETED';

    const canGoBack = currentIndex > 0;

    return {
        canAdvance,
        canGoBack,
        nextStep: canAdvance ? STEP_ORDER[currentIndex + 1] : null,
        previousStep: canGoBack ? STEP_ORDER[currentIndex - 1] : null,
    };
});

// ============================================================================
// AUDIT TRAIL STATE
// ============================================================================

/**
 * Audit entries for current workflow (derived).
 */
export const workflowAuditAtom = atom<WorkflowAuditEntry[]>((get) => {
    const workflow = get(currentWorkflowAtom);
    return workflow?.audit ?? [];
});

/**
 * Show audit panel state.
 */
export const showAuditPanelAtom = atom<boolean>(false);

// ============================================================================
// UI STATE
// ============================================================================

/**
 * Evidence panel visibility.
 */
export const showEvidencePanelAtom = atom<boolean>(true);

/**
 * Evidence panel width (for resizable panel).
 */
export const evidencePanelWidthAtom = atomWithStorage<number>(
    'workflow-evidence-panel-width',
    350
);

/**
 * Step rail collapsed state.
 */
export const stepRailCollapsedAtom = atomWithStorage<boolean>(
    'workflow-step-rail-collapsed',
    false
);

/**
 * AI suggestions visibility.
 */
export const showAISuggestionsAtom = atom<boolean>(true);

/**
 * Current AI suggestion (if any).
 */
export interface AISuggestion {
    id: string;
    type: 'REFINEMENT' | 'COMPLETION' | 'WARNING' | 'VALIDATION';
    content: string;
    confidence: number;
    actions?: Array<{
        label: string;
        action: 'ACCEPT' | 'REJECT' | 'MODIFY';
    }>;
}

export const currentAISuggestionAtom = atom<AISuggestion | null>(null);

// ============================================================================
// WORKFLOW CREATION STATE
// ============================================================================

/**
 * Workflow creation dialog open state.
 */
export const createWorkflowDialogOpenAtom = atom<boolean>(false);

/**
 * Selected template for new workflow.
 */
export const selectedTemplateIdAtom = atom<string | null>(null);

// ============================================================================
// ACTIONS (Write atoms)
// ============================================================================

/**
 * Action to update current workflow.
 */
export const updateCurrentWorkflowAtom = atom(
    null,
    (get, set, workflow: Workflow | null) => {
        set(currentWorkflowAtom, workflow);
        set(hasUnsavedChangesAtom, false);
        set(draftStepDataAtom, null);
    }
);

/**
 * Action to update draft step data.
 */
export const updateDraftStepDataAtom = atom(
    null,
    (get, set, data: unknown) => {
        set(draftStepDataAtom, data);
        set(hasUnsavedChangesAtom, true);
    }
);

/**
 * Action to discard draft changes.
 */
export const discardDraftChangesAtom = atom(null, (get, set) => {
    set(draftStepDataAtom, null);
    set(hasUnsavedChangesAtom, false);
});

/**
 * Action to toggle evidence panel.
 */
export const toggleEvidencePanelAtom = atom(null, (get, set) => {
    set(showEvidencePanelAtom, !get(showEvidencePanelAtom));
});

/**
 * Action to toggle step rail.
 */
export const toggleStepRailAtom = atom(null, (get, set) => {
    set(stepRailCollapsedAtom, !get(stepRailCollapsedAtom));
});

/**
 * Action to change the current step of the workflow.
 */
export const setCurrentStepAtom = atom(
    null,
    (get, set, step: WorkflowStep) => {
        const workflow = get(currentWorkflowAtom);
        if (!workflow) return;

        set(currentWorkflowAtom, {
            ...workflow,
            currentStep: step,
        });
    }
);

// ============================================================================
// SELECTORS
// ============================================================================

/**
 * Get step completion percentage.
 */
export const workflowCompletionAtom = atom<number>((get) => {
    const workflow = get(currentWorkflowAtom);
    if (!workflow) return 0;

    const steps = workflow.steps;
    const completedCount = Object.values(steps).filter(
        (s) => s.status === 'COMPLETED'
    ).length;

    return Math.round((completedCount / STEP_ORDER.length) * 100);
});

/**
 * Get all step statuses for the rail.
 */
export interface StepRailItem {
    step: WorkflowStep;
    label: string;
    status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'REVISITED' | 'BLOCKED';
    aiConfidence?: number;
    isBlocked: boolean;
    isCurrent: boolean;
}

const STEP_LABELS: Record<WorkflowStep, string> = {
    INTENT: 'Intent',
    CONTEXT: 'Context',
    PLAN: 'Plan',
    EXECUTE: 'Execute',
    VERIFY: 'Verify',
    OBSERVE: 'Observe',
    LEARN: 'Learn',
    INSTITUTIONALIZE: 'Institutionalize',
};

export const stepRailItemsAtom = atom<StepRailItem[]>((get) => {
    const workflow = get(currentWorkflowAtom);
    const currentStep = get(currentStepAtom);

    return STEP_ORDER.map((step) => {
        const stepKey = step.toLowerCase() as keyof WorkflowSteps;
        const stepState = workflow?.steps[stepKey];

        return {
            step,
            label: STEP_LABELS[step],
            status: stepState?.status ?? 'NOT_STARTED',
            aiConfidence: stepState?.aiConfidence,
            isBlocked: stepState?.status === 'BLOCKED',
            isCurrent: step === currentStep,
        };
    });
});

export default {
    // List
    workflowListAtom,
    workflowListLoadingAtom,
    workflowListErrorAtom,
    workflowListFilterAtom,
    // Current
    currentWorkflowAtom,
    currentWorkflowLoadingAtom,
    currentWorkflowErrorAtom,
    currentStepAtom,
    workflowStatusAtom,
    aiModeAtom,
    // Step Data
    currentStepDataAtom,
    hasUnsavedChangesAtom,
    draftStepDataAtom,
    // Navigation
    stepNavigationAtom,
    // Audit
    workflowAuditAtom,
    showAuditPanelAtom,
    // UI
    showEvidencePanelAtom,
    evidencePanelWidthAtom,
    stepRailCollapsedAtom,
    showAISuggestionsAtom,
    currentAISuggestionAtom,
    // Creation
    createWorkflowDialogOpenAtom,
    selectedTemplateIdAtom,
    // Actions
    updateCurrentWorkflowAtom,
    updateDraftStepDataAtom,
    discardDraftChangesAtom,
    toggleEvidencePanelAtom,
    toggleStepRailAtom,
    setCurrentStepAtom,
    // Selectors
    workflowCompletionAtom,
    stepRailItemsAtom,
};
