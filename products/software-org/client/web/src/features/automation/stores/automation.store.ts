/**
 * Automation Feature Store
 *
 * <p><b>Purpose</b><br>
 * Jotai state management for Automation Engine feature, handling workflow selection,
 * execution tracking, and trigger management.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const [automationState, setAutomationState] = useAtom(automationStateAtom);
 * setAutomationState(prev => ({ ...prev, selectedWorkflowId: workflowId }));
 * ```
 *
 * @doc.type service
 * @doc.purpose Automation Engine state management
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';

export interface AutomationState {
    selectedWorkflowId: string | null;
    activeExecutionId: string | null;
    builderOpen: boolean;
    editingWorkflowId: string | null;
    notification: {
        type: 'success' | 'error' | 'info';
        message: string;
    } | null;
    executionFilter: 'all' | 'running' | 'completed' | 'failed';
    lastRefresh: Date | null;
}

const initialAutomationState: AutomationState = {
    selectedWorkflowId: null,
    activeExecutionId: null,
    builderOpen: false,
    editingWorkflowId: null,
    notification: null,
    executionFilter: 'all',
    lastRefresh: null,
};

/**
 * Base automation state atom.
 * Provides core state for workflow and execution management.
 */
export const automationStateAtom = atom<AutomationState>(initialAutomationState);

/**
 * Derived atom - Get selected workflow ID.
 */
export const selectedWorkflowIdAtom = atom(
    (get) => get(automationStateAtom).selectedWorkflowId
);

/**
 * Derived atom - Get active execution ID.
 */
export const activeExecutionIdAtom = atom((get) => get(automationStateAtom).activeExecutionId);

/**
 * Derived atom - Get builder open status.
 */
export const isBuilderOpenAtom = atom((get) => get(automationStateAtom).builderOpen);

/**
 * Derived atom - Get editing workflow ID.
 */
export const editingWorkflowIdAtom = atom(
    (get) => get(automationStateAtom).editingWorkflowId
);

/**
 * Derived atom - Get current notification.
 */
export const automationNotificationAtom = atom(
    (get) => get(automationStateAtom).notification
);

/**
 * Derived atom - Get execution filter.
 */
export const executionFilterAtom = atom(
    (get) => get(automationStateAtom).executionFilter
);

/**
 * Action atom - Select workflow.
 */
export const selectWorkflowAtom = atom(null, (_, set, workflowId: string) => {
    set(automationStateAtom, (prev) => ({
        ...prev,
        selectedWorkflowId: workflowId,
    }));
});

/**
 * Action atom - Set active execution.
 */
export const setActiveExecutionAtom = atom(null, (_, set, executionId: string | null) => {
    set(automationStateAtom, (prev) => ({
        ...prev,
        activeExecutionId: executionId,
    }));
});

/**
 * Action atom - Open workflow builder.
 */
export const openBuilderAtom = atom(null, (_, set, workflowId?: string) => {
    set(automationStateAtom, (prev) => ({
        ...prev,
        builderOpen: true,
        editingWorkflowId: workflowId || null,
    }));
});

/**
 * Action atom - Close workflow builder.
 */
export const closeBuilderAtom = atom(null, (_, set) => {
    set(automationStateAtom, (prev) => ({
        ...prev,
        builderOpen: false,
        editingWorkflowId: null,
    }));
});

/**
 * Action atom - Set execution filter.
 */
export const setExecutionFilterAtom = atom(
    null,
    (_, set, filter: 'all' | 'running' | 'completed' | 'failed') => {
        set(automationStateAtom, (prev) => ({
            ...prev,
            executionFilter: filter,
        }));
    }
);

/**
 * Action atom - Update last refresh timestamp.
 */
export const updateLastRefreshAtom = atom(null, (_, set, timestamp: Date) => {
    set(automationStateAtom, (prev) => ({
        ...prev,
        lastRefresh: timestamp,
    }));
});

/**
 * Action atom - Show notification.
 */
export const showAutomationNotificationAtom = atom(
    null,
    (
        _,
        set,
        notification: { type: 'success' | 'error' | 'info'; message: string }
    ) => {
        set(automationStateAtom, (prev) => ({
            ...prev,
            notification,
        }));
    }
);

/**
 * Action atom - Clear notification.
 */
export const clearAutomationNotificationAtom = atom(null, (_, set) => {
    set(automationStateAtom, (prev) => ({
        ...prev,
        notification: null,
    }));
});

/**
 * Action atom - Reset all automation state.
 */
export const resetAutomationStateAtom = atom(null, (_, set) => {
    set(automationStateAtom, initialAutomationState);
});

export default {
    automationStateAtom,
    selectedWorkflowIdAtom,
    activeExecutionIdAtom,
    isBuilderOpenAtom,
    editingWorkflowIdAtom,
    automationNotificationAtom,
    executionFilterAtom,
    selectWorkflowAtom,
    setActiveExecutionAtom,
    openBuilderAtom,
    closeBuilderAtom,
    setExecutionFilterAtom,
    updateLastRefreshAtom,
    showAutomationNotificationAtom,
    clearAutomationNotificationAtom,
    resetAutomationStateAtom,
};
