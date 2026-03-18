/**
 * Workspace State Atom
 * 
 * Manages workspace context with AI-enhanced features.
 * Workspaces are containers - project ownership determines permissions.
 * 
 * @doc.type atom
 * @doc.purpose Workspace context management with AI
 * @doc.layer product
 * @doc.pattern State Management
 */
import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import { LifecyclePhase } from '@ghatana/yappc-shared';

// ============================================================================
// Types
// ============================================================================

/**
 * Workspace entity from the database
 */
export interface Workspace {
    id: string;
    name: string;
    description?: string;
    ownerId: string;
    isDefault: boolean;
    aiSummary?: string;
    aiTags: string[];
    createdAt: string;
    updatedAt: string;
}

/**
 * Base project type (from API)
 */
export interface Project {
    id: string;
    name: string;
    description?: string;
    type: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
    status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
    lifecyclePhase: LifecyclePhase;
    ownerWorkspaceId: string;
    isDefault: boolean;
    aiSummary?: string;
    aiNextActions: string[];
    aiHealthScore?: number;
    createdAt: string;
    updatedAt: string;
}

/**
 * Project with ownership context (for client display)
 */
export interface ProjectWithOwnership extends Project {
    isOwned: boolean; // true if current workspace owns this project
}

/**
 * Workspace state
 */
export interface WorkspaceState {
    currentWorkspace: Workspace | null;
    availableWorkspaces: Workspace[];

    // Projects in current workspace (owned + included)
    ownedProjects: ProjectWithOwnership[];
    includedProjects: ProjectWithOwnership[];

    // Loading states
    isLoading: boolean;
    isCreating: boolean;
    isSwitching: boolean;

    // AI features
    aiSuggestedWorkspaceName?: string;
    aiSuggestedProjectName?: string;

    error?: string;
}

// ============================================================================
// Atoms
// ============================================================================

/**
 * Main workspace state atom
 */
export const workspaceAtom = atom<WorkspaceState>({
    currentWorkspace: null,
    availableWorkspaces: [],
    ownedProjects: [],
    includedProjects: [],
    isLoading: false,
    isCreating: false,
    isSwitching: false,
});

/**
 * Persisted current workspace ID (survives page refresh)
 */
export const currentWorkspaceIdAtom = atomWithStorage<string | null>(
    'yappc:currentWorkspaceId',
    null
);

// ============================================================================
// Derived Atoms (Read-Only)
// ============================================================================

/**
 * Current workspace (convenience)
 */
export const currentWorkspaceAtom = atom((get) =>
    get(workspaceAtom).currentWorkspace
);

/**
 * All projects in current workspace (owned + included)
 */
export const allProjectsAtom = atom((get) => {
    const state = get(workspaceAtom);
    return [
        ...state.ownedProjects.map(p => ({ ...p, isOwned: true })),
        ...state.includedProjects.map(p => ({ ...p, isOwned: false })),
    ];
});

/**
 * Count of projects by ownership
 */
export const projectCountsAtom = atom((get) => {
    const state = get(workspaceAtom);
    return {
        owned: state.ownedProjects.length,
        included: state.includedProjects.length,
        total: state.ownedProjects.length + state.includedProjects.length,
    };
});

/**
 * Is current workspace the default one?
 */
export const isDefaultWorkspaceAtom = atom((get) =>
    get(workspaceAtom).currentWorkspace?.isDefault ?? false
);

/**
 * Check if a project is owned by current workspace
 */
export const isProjectOwnedAtom = atom((get) => {
    return (projectId: string): boolean => {
        const state = get(workspaceAtom);
        return state.ownedProjects.some(p => p.id === projectId);
    };
});

/**
 * Can user edit projects in current context?
 */
export const canEditProjectAtom = atom((get) => {
    return (projectId: string): boolean => {
        const isOwned = get(isProjectOwnedAtom);
        return isOwned(projectId);
    };
});

// ============================================================================
// Action Atoms (Write)
// ============================================================================

/**
 * Set current workspace
 */
export const setCurrentWorkspaceAtom = atom(
    null,
    (get, set, workspace: Workspace) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            currentWorkspace: workspace,
            isSwitching: false,
        });
        set(currentWorkspaceIdAtom, workspace.id);
    }
);

/**
 * Set available workspaces
 */
export const setWorkspacesAtom = atom(
    null,
    (get, set, workspaces: Workspace[]) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            availableWorkspaces: workspaces,
        });
    }
);

/**
 * Set projects for current workspace
 */
export const setProjectsAtom = atom(
    null,
    (get, set, payload: { owned: ProjectWithOwnership[]; included: ProjectWithOwnership[] }) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            ownedProjects: payload.owned,
            includedProjects: payload.included,
            isLoading: false,
        });
    }
);

/**
 * Set loading state
 */
export const setWorkspaceLoadingAtom = atom(
    null,
    (get, set, isLoading: boolean) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            isLoading,
        });
    }
);

/**
 * Set error state
 */
export const setWorkspaceErrorAtom = atom(
    null,
    (get, set, error: string | undefined) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            error,
            isLoading: false,
        });
    }
);

/**
 * Add new workspace to the list
 */
export const addWorkspaceAtom = atom(
    null,
    (get, set, workspace: Workspace) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            availableWorkspaces: [...state.availableWorkspaces, workspace],
            isCreating: false,
        });
    }
);

/**
 * Add new owned project
 */
export const addOwnedProjectAtom = atom(
    null,
    (get, set, project: ProjectWithOwnership) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            ownedProjects: [...state.ownedProjects, { ...project, isOwned: true }],
        });
    }
);

/**
 * Add included project (from another workspace)
 */
export const addIncludedProjectAtom = atom(
    null,
    (get, set, project: ProjectWithOwnership) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            includedProjects: [...state.includedProjects, { ...project, isOwned: false }],
        });
    }
);

/**
 * Set AI suggestions
 */
export const setAiSuggestionsAtom = atom(
    null,
    (get, set, suggestions: { workspaceName?: string; projectName?: string }) => {
        const state = get(workspaceAtom);
        set(workspaceAtom, {
            ...state,
            aiSuggestedWorkspaceName: suggestions.workspaceName,
            aiSuggestedProjectName: suggestions.projectName,
        });
    }
);
