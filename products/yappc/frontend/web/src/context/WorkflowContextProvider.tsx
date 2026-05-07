/**
 * Workflow Context Provider
 * 
 * Central context management for the unified workflow experience.
 * Provides phase awareness, action context, and guidance state.
 * 
 * @doc.type provider
 * @doc.purpose Central workflow state management
 * @doc.layer product
 * @doc.pattern Context Provider
 */

import React, { createContext, useContext, useMemo, useCallback, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useAtom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

import { LifecyclePhase, PHASE_LABELS, getOperationsForPhase } from '../types/lifecycle';
import { useWorkspaceContext } from '../hooks/useWorkspaceData';
import { rankNextActions } from '../services/phase';
import { projectCanEdit, projectGuidanceRole } from '../services/workspace/accessControl';

// ============================================================================
// Types
// ============================================================================

/**
 * Route context - where the user is
 */
export interface RouteContext {
    path: string;
    phase: LifecyclePhase | null;
    section: 'intent' | 'workspaces' | 'canvas' | 'preview' | 'deploy' | 'settings' | 'observe' | 'other';
    projectId: string | null;
    isProjectRoute: boolean;
}

/**
 * Project context - what the user is working on
 */
export interface ProjectContext {
    id: string | null;
    name: string | null;
    phase: LifecyclePhase | null;
    status: 'draft' | 'active' | 'deployed' | 'archived';
    hasUnsavedChanges: boolean;
}

/**
 * Selection context - what's currently selected
 */
export interface SelectionContext {
    elements: string[];
    type: 'node' | 'edge' | 'group' | 'mixed' | 'none';
    count: number;
}

/**
 * Capabilities context - what the user can do
 */
export interface CapabilitiesContext {
    canEdit: boolean;
    canValidate: boolean;
    canGenerate: boolean;
    canDeploy: boolean;
    canObserve: boolean;
    aiActive: boolean;
}

/**
 * Guidance step definition
 */
export interface GuidanceStep {
    id: string;
    title: string;
    description: string;
    action?: string;
    completed: boolean;
    current: boolean;
}

/**
 * Guidance context - contextual help and tips
 */
export interface GuidanceContext {
    currentPhaseSteps: GuidanceStep[];
    tips: string[];
    nextActions: string[];
    completedSteps: string[];
    showGuidancePanel: boolean;
}

/**
 * Complete workflow context
 */
export interface WorkflowContextValue {
    // Contexts
    route: RouteContext;
    project: ProjectContext;
    selection: SelectionContext;
    capabilities: CapabilitiesContext;
    guidance: GuidanceContext;

    // Phase navigation
    currentPhase: LifecyclePhase | null;
    availablePhases: LifecyclePhase[];
    canTransitionTo: (phase: LifecyclePhase) => boolean;
    navigateToPhase: (phase: LifecyclePhase) => void;

    // Guidance actions
    toggleGuidancePanel: () => void;
    completeStep: (stepId: string) => void;
    dismissTip: (tipId: string) => void;
    resetGuidance: () => void;

    // Selection actions
    setSelection: (elements: string[], type: SelectionContext['type']) => void;
    clearSelection: () => void;

    // State
    isLoading: boolean;
    error: string | null;
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Phase order for navigation
 */
const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.CONTEXT,
    LifecyclePhase.PLAN,
    LifecyclePhase.EXECUTE,
    LifecyclePhase.VERIFY,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.LEARN,
    LifecyclePhase.INSTITUTIONALIZE,
];

/**
 * Phase to route mapping
 */
const PHASE_ROUTES: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: '',  // /p/:id
    [LifecyclePhase.CONTEXT]: 'canvas',
    [LifecyclePhase.PLAN]: 'canvas',  // Opens validation panel
    [LifecyclePhase.EXECUTE]: 'canvas',  // Opens generation panel
    [LifecyclePhase.VERIFY]: 'deploy',
    [LifecyclePhase.OBSERVE]: 'preview',  // With monitoring
    [LifecyclePhase.LEARN]: 'canvas',  // With improvement suggestions
    [LifecyclePhase.INSTITUTIONALIZE]: 'settings',
};

/**
 * Guidance steps per phase
 */
const PHASE_GUIDANCE: Record<LifecyclePhase, Omit<GuidanceStep, 'completed' | 'current'>[]> = {
    [LifecyclePhase.INTENT]: [
        { id: 'intent-1', title: 'Describe your idea', description: 'Use natural language to describe what you want to build', action: 'Type in the command input' },
        { id: 'intent-2', title: 'Review AI suggestions', description: 'AI will suggest a project structure based on your description' },
        { id: 'intent-3', title: 'Confirm or customize', description: 'Accept the suggestion or modify it to match your vision' },
    ],
    [LifecyclePhase.CONTEXT]: [
        { id: 'shape-1', title: 'Add components', description: 'Drag components from the palette to the canvas', action: 'Open component palette' },
        { id: 'shape-2', title: 'Connect nodes', description: 'Draw edges between components to define relationships' },
        { id: 'shape-3', title: 'Configure properties', description: 'Select a node and edit its properties in the panel' },
        { id: 'shape-4', title: 'Use assistance', description: 'Get help to add features or modify your design' },
    ],
    [LifecyclePhase.PLAN]: [
        { id: 'validate-1', title: 'Run validation', description: 'AI will check your design for issues and gaps' },
        { id: 'validate-2', title: 'Review findings', description: 'Address errors, warnings, and suggestions' },
        { id: 'validate-3', title: 'Fix issues', description: 'Click on issues to navigate to the problem area' },
    ],
    [LifecyclePhase.EXECUTE]: [
        { id: 'generate-1', title: 'Configure generation', description: 'Choose language, framework, and options' },
        { id: 'generate-2', title: 'Prepare implementation', description: 'System will generate production-ready code' },
        { id: 'generate-3', title: 'Review output', description: 'Preview generated files and make adjustments' },
    ],
    [LifecyclePhase.VERIFY]: [
        { id: 'run-1', title: 'Configure deployment', description: 'Choose environment and settings' },
        { id: 'run-2', title: 'Deploy', description: 'One-click deployment to your target environment' },
        { id: 'run-3', title: 'Verify', description: 'Check that everything is working correctly' },
    ],
    [LifecyclePhase.OBSERVE]: [
        { id: 'observe-1', title: 'Monitor metrics', description: 'View real-time performance and usage data' },
        { id: 'observe-2', title: 'Check logs', description: 'Review application logs for issues' },
        { id: 'observe-3', title: 'Set alerts', description: 'Configure notifications for important events' },
    ],
    [LifecyclePhase.LEARN]: [
        { id: 'improve-1', title: 'Review insights', description: 'AI analyzes usage and suggests improvements' },
        { id: 'improve-2', title: 'Apply changes', description: 'Make improvements based on observations' },
        { id: 'improve-3', title: 'Iterate', description: 'Continue the cycle to enhance your app' },
    ],
    [LifecyclePhase.INSTITUTIONALIZE]: [
        { id: 'institutionalize-1', title: 'Capture standards', description: 'Turn validated decisions into reusable patterns and standards' },
        { id: 'institutionalize-2', title: 'Publish guidance', description: 'Share the runbook, checklist, or template with the team' },
        { id: 'institutionalize-3', title: 'Enforce adoption', description: 'Add guardrails so the improvement becomes the new default' },
    ],
};

/**
 * Context tips per phase
 */
const PHASE_TIPS: Record<LifecyclePhase, string[]> = {
    [LifecyclePhase.INTENT]: [
        '💡 Be specific about features you need',
        '💡 Mention your tech stack preferences',
        '💡 Describe your target users',
    ],
    [LifecyclePhase.CONTEXT]: [
        '💡 Use Cmd/Ctrl+K to open command palette',
        '💡 Hold Shift to select multiple nodes',
        '💡 Right-click for context menu',
    ],
    [LifecyclePhase.PLAN]: [
        '💡 Fix errors before generating code',
        '💡 Warnings can be addressed later',
        '💡 AI can auto-fix some issues',
    ],
    [LifecyclePhase.EXECUTE]: [
        '💡 Generated code is fully editable',
        '💡 Include tests for better quality',
        '💡 Review before deploying',
    ],
    [LifecyclePhase.VERIFY]: [
        '💡 Start with a preview deployment',
        '💡 Check all environment variables',
        '💡 Monitor the deployment progress',
    ],
    [LifecyclePhase.OBSERVE]: [
        '💡 Set up alerts for critical metrics',
        '💡 Review logs regularly',
        '💡 Track user behavior patterns',
    ],
    [LifecyclePhase.LEARN]: [
        '💡 Small iterations are better than big changes',
        '💡 Use A/B testing for major changes',
        '💡 Listen to user feedback',
    ],
    [LifecyclePhase.INSTITUTIONALIZE]: [
        '💡 Document the pattern while the context is still fresh',
        '💡 Prefer templates and checklists over tribal knowledge',
        '💡 Add guardrails where the same issue can recur',
    ],
};

// ============================================================================
// Atoms
// ============================================================================

/**
 * Completed guidance steps (persisted)
 */
export const completedStepsAtom = atomWithStorage<string[]>('workflow-completed-steps', []);

/**
 * Dismissed tips (persisted)
 */
export const dismissedTipsAtom = atomWithStorage<string[]>('workflow-dismissed-tips', []);

/**
 * Guidance panel visibility
 */
export const showGuidancePanelAtom = atomWithStorage<boolean>('workflow-guidance-panel', true);

// ============================================================================
// Context
// ============================================================================

const WorkflowContext = createContext<WorkflowContextValue | null>(null);

// ============================================================================
// Provider Component
// ============================================================================

export interface WorkflowContextProviderProps {
    children: React.ReactNode;
}

export function WorkflowContextProvider({ children }: WorkflowContextProviderProps) {
    const location = useLocation();
    const navigate = useNavigate();
    const { projectId } = useParams<{ projectId: string }>();

    // Workspace context
    const { ownedProjects, includedProjects, isLoading: workspaceLoading } = useWorkspaceContext();

    // Persisted state
    const [completedSteps, setCompletedSteps] = useAtom(completedStepsAtom);
    const [dismissedTips, setDismissedTips] = useAtom(dismissedTipsAtom);
    const [showGuidancePanel, setShowGuidancePanel] = useAtom(showGuidancePanelAtom);

    // Local state
    const [selection, setSelectionState] = useState<SelectionContext>({
        elements: [],
        type: 'none',
        count: 0,
    });
    const [error, setError] = useState<string | null>(null);

    // ========================================================================
    // Derived: Route Context
    // ========================================================================

    const routeContext = useMemo<RouteContext>(() => {
        const path = location.pathname;
        const isProjectRoute = path.includes('/p/');

        // Determine section from path
        let section: RouteContext['section'] = 'other';
        if (path.includes('/canvas')) section = 'canvas';
        else if (path.includes('/preview')) section = 'preview';
        else if (path.includes('/deploy')) section = 'deploy';
        else if (path.includes('/settings')) section = 'settings';
        else if (path.includes('/observe')) section = 'observe';
        else if (path === '/' || path === '/projects') section = 'intent';
        else if (path === '/workspaces') section = 'workspaces';
        else if (isProjectRoute && !path.includes('/')) section = 'canvas'; // Default

        // Determine phase from section
        let phase: LifecyclePhase | null = null;
        switch (section) {
            case 'intent':
            case 'workspaces':
                phase = LifecyclePhase.INTENT;
                break;
            case 'canvas': phase = LifecyclePhase.SHAPE; break;
            case 'preview': phase = LifecyclePhase.OBSERVE; break;
            case 'deploy': phase = LifecyclePhase.RUN; break;
            case 'settings': phase = null; break;
            default: phase = null;
        }

        return {
            path,
            phase,
            section,
            projectId: projectId || null,
            isProjectRoute,
        };
    }, [location.pathname, projectId]);

    // ========================================================================
    // Derived: Project Context
    // ========================================================================

    const normalizeStatus = (status: string | undefined): ProjectContext['status'] => {
        if (!status) return 'active';
        const lower = status.toLowerCase();
        if (lower === 'draft' || lower === 'active' || lower === 'deployed' || lower === 'archived') {
            return lower as ProjectContext['status'];
        }
        if (lower === 'completed') return 'deployed';
        return 'active';
    };

    const normalizePhase = (phase: string | null | undefined): LifecyclePhase | null => {
        switch (phase) {
            case LifecyclePhase.INTENT:
                return LifecyclePhase.INTENT;
            case LifecyclePhase.SHAPE:
            case LifecyclePhase.CONTEXT:
                return LifecyclePhase.CONTEXT;
            case LifecyclePhase.VALIDATE:
            case LifecyclePhase.PLAN:
                return LifecyclePhase.PLAN;
            case LifecyclePhase.GENERATE:
            case LifecyclePhase.EXECUTE:
                return LifecyclePhase.EXECUTE;
            case LifecyclePhase.RUN:
            case LifecyclePhase.VERIFY:
                return LifecyclePhase.VERIFY;
            case LifecyclePhase.OBSERVE:
                return LifecyclePhase.OBSERVE;
            case LifecyclePhase.IMPROVE:
            case LifecyclePhase.LEARN:
                return LifecyclePhase.LEARN;
            case LifecyclePhase.INSTITUTIONALIZE:
                return LifecyclePhase.INSTITUTIONALIZE;
            default:
                return null;
        }
    };

    const projectContext = useMemo<ProjectContext>(() => {
        if (!projectId) {
            return {
                id: null,
                name: null,
                phase: null,
                status: 'draft',
                hasUnsavedChanges: false,
            };
        }

        const allProjects = [...ownedProjects, ...includedProjects];
        const project = allProjects.find(p => p.id === projectId);

        return {
            id: projectId,
            name: project?.name || null,
            phase: normalizePhase(project?.lifecyclePhase) ?? LifecyclePhase.CONTEXT,
            status: normalizeStatus(project?.status),
            hasUnsavedChanges: false, // NOTE: Connect to canvas state
        };
    }, [projectId, ownedProjects, includedProjects]);

    // ========================================================================
    // Derived: Capabilities Context
    // ========================================================================

    const capabilitiesContext = useMemo<CapabilitiesContext>(() => {
        const phase = routeContext.phase || projectContext.phase;
        const activeProject = [...ownedProjects, ...includedProjects].find((project) => project.id === projectContext.id);
        const canEditProject = activeProject ? projectCanEdit(activeProject) : true;
        if (!phase) {
            return {
                canEdit: canEditProject,
                canValidate: false,
                canGenerate: false,
                canDeploy: false,
                canObserve: false,
                aiActive: true,
            };
        }

        const phaseOperations = getOperationsForPhase(phase);
        return {
            ...phaseOperations,
            canEdit: phaseOperations.canEdit && canEditProject,
            canValidate: phaseOperations.canValidate && canEditProject,
            canGenerate: phaseOperations.canGenerate && canEditProject,
            canDeploy: phaseOperations.canDeploy && canEditProject,
        };
    }, [routeContext.phase, projectContext.phase, projectContext.id, ownedProjects, includedProjects]);

    // ========================================================================
    // Derived: Guidance Context
    // ========================================================================

    const guidanceContext = useMemo<GuidanceContext>(() => {
        const phase = routeContext.phase || projectContext.phase || LifecyclePhase.INTENT;
        const phaseSteps = PHASE_GUIDANCE[phase] || [];
        const phaseTips = PHASE_TIPS[phase] || [];

        // Build guidance steps with completion status
        const currentPhaseSteps = phaseSteps.map((step, index) => {
            const completed = completedSteps.includes(step.id);
            const firstIncomplete = phaseSteps.findIndex(s => !completedSteps.includes(s.id));
            return {
                ...step,
                completed,
                current: index === firstIncomplete,
            };
        });

        // Filter out dismissed tips
        const activeTips = phaseTips.filter((_, i) => !dismissedTips.includes(`${phase}-tip-${i}`));

        const activeProject = [...ownedProjects, ...includedProjects].find((project) => project.id === projectContext.id);
        const role = projectGuidanceRole(activeProject);
        const phaseIndex = PHASE_ORDER.indexOf(phase);
        const nextActions = rankNextActions({
            phase,
            phaseSteps: currentPhaseSteps,
            completedSteps,
            project: projectContext,
            projectSignals: {
                aiNextActions: activeProject?.aiNextActions ?? [],
                aiHealthScore: activeProject?.aiHealthScore,
            },
            role,
            canTransitionForward: phaseIndex < PHASE_ORDER.length - 1,
        });

        return {
            currentPhaseSteps,
            tips: activeTips,
            nextActions,
            completedSteps,
            showGuidancePanel,
        };
    }, [
        routeContext.phase,
        projectContext,
        completedSteps,
        dismissedTips,
        showGuidancePanel,
        ownedProjects,
        includedProjects,
    ]);

    // ========================================================================
    // Phase Navigation
    // ========================================================================

    const currentPhase = routeContext.phase || projectContext.phase;

    const availablePhases = useMemo(() => {
        // All phases are theoretically available, but some may be locked
        return PHASE_ORDER;
    }, []);

    const canTransitionTo = useCallback((phase: LifecyclePhase): boolean => {
        if (!currentPhase) return true;

        // Allow going back freely
        const currentIndex = PHASE_ORDER.indexOf(currentPhase);
        const targetIndex = PHASE_ORDER.indexOf(phase);
        if (targetIndex <= currentIndex) return true;

        // Allow going forward one step
        if (targetIndex === currentIndex + 1) return true;

        // Allow skipping ahead if previous phases are complete
        // (simplified logic - could be enhanced with more validation)
        return true;
    }, [currentPhase]);

    const navigateToPhase = useCallback((phase: LifecyclePhase) => {
        if (!projectId && phase !== LifecyclePhase.INTENT) {
            setError('Please create or select a project first');
            return;
        }

        if (!canTransitionTo(phase)) {
            setError(`Cannot transition to ${PHASE_LABELS[phase]} from current phase`);
            return;
        }

        const route = PHASE_ROUTES[phase];
        if (phase === LifecyclePhase.INTENT) {
            navigate('/projects');
        } else {
            navigate(`/p/${projectId}/${route}`);
        }
    }, [projectId, canTransitionTo, navigate]);

    // ========================================================================
    // Guidance Actions
    // ========================================================================

    const toggleGuidancePanel = useCallback(() => {
        setShowGuidancePanel(prev => !prev);
    }, [setShowGuidancePanel]);

    const completeStep = useCallback((stepId: string) => {
        setCompletedSteps(prev => {
            if (prev.includes(stepId)) return prev;
            return [...prev, stepId];
        });
    }, [setCompletedSteps]);

    const dismissTip = useCallback((tipId: string) => {
        setDismissedTips(prev => {
            if (prev.includes(tipId)) return prev;
            return [...prev, tipId];
        });
    }, [setDismissedTips]);

    const resetGuidance = useCallback(() => {
        setCompletedSteps([]);
        setDismissedTips([]);
    }, [setCompletedSteps, setDismissedTips]);

    // ========================================================================
    // Selection Actions
    // ========================================================================

    const setSelection = useCallback((elements: string[], type: SelectionContext['type']) => {
        setSelectionState({
            elements,
            type,
            count: elements.length,
        });
    }, []);

    const clearSelection = useCallback(() => {
        setSelectionState({
            elements: [],
            type: 'none',
            count: 0,
        });
    }, []);

    // ========================================================================
    // Context Value
    // ========================================================================

    const value = useMemo<WorkflowContextValue>(() => ({
        route: routeContext,
        project: projectContext,
        selection,
        capabilities: capabilitiesContext,
        guidance: guidanceContext,

        currentPhase,
        availablePhases,
        canTransitionTo,
        navigateToPhase,

        toggleGuidancePanel,
        completeStep,
        dismissTip,
        resetGuidance,

        setSelection,
        clearSelection,

        isLoading: workspaceLoading,
        error,
    }), [
        routeContext,
        projectContext,
        selection,
        capabilitiesContext,
        guidanceContext,
        currentPhase,
        availablePhases,
        canTransitionTo,
        navigateToPhase,
        toggleGuidancePanel,
        completeStep,
        dismissTip,
        resetGuidance,
        setSelection,
        clearSelection,
        workspaceLoading,
        error,
    ]);

    return (
        <WorkflowContext.Provider value={value}>
            {children}
        </WorkflowContext.Provider>
    );
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook to access workflow context.
 * Must be used within WorkflowContextProvider.
 */
export function useWorkflowContext(): WorkflowContextValue {
    const context = useContext(WorkflowContext);
    if (!context) {
        throw new Error('useWorkflowContext must be used within WorkflowContextProvider');
    }
    return context;
}

/**
 * Hook to access specific parts of workflow context.
 */
export function usePhaseContext() {
    const { currentPhase, availablePhases, canTransitionTo, navigateToPhase } = useWorkflowContext();
    return { currentPhase, availablePhases, canTransitionTo, navigateToPhase };
}

export function useGuidanceContext() {
    const { guidance, toggleGuidancePanel, completeStep, dismissTip, resetGuidance } = useWorkflowContext();
    return { ...guidance, toggleGuidancePanel, completeStep, dismissTip, resetGuidance };
}

export function useSelectionContext() {
    const { selection, setSelection, clearSelection } = useWorkflowContext();
    return { ...selection, setSelection, clearSelection };
}

export function useCapabilitiesContext() {
    const { capabilities } = useWorkflowContext();
    return capabilities;
}
