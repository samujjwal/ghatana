/**
 * Breadcrumb State Atom
 * 
 * Manages breadcrumb navigation state with smart context awareness.
 * Auto-updates based on route changes and project/workspace context.
 * 
 * @doc.type atom
 * @doc.purpose Breadcrumb navigation state
 * @doc.layer product
 * @doc.pattern State Management
 */
import { atom } from 'jotai';

// ============================================================================
// Types
// ============================================================================

/**
 * Breadcrumb item matching the UI component interface
 */
export interface BreadcrumbItem {
    label: string;
    href?: string;
    onClick?: () => void;
    icon?: React.ReactNode;
    disabled?: boolean;

    // AI enhancements
    aiTooltip?: string;
}

/**
 * Navigation context for breadcrumb generation
 */
export interface NavigationContext {
    workspaceId?: string;
    workspaceName?: string;
    projectId?: string;
    projectName?: string;
    projectIsOwned?: boolean;
    currentSection?: string;
    customItems?: BreadcrumbItem[];
}

// ============================================================================
// Atoms
// ============================================================================

/**
 * Current navigation context
 */
export const navigationContextAtom = atom<NavigationContext>({});

/**
 * Computed breadcrumb items based on navigation context
 */
export const breadcrumbItemsAtom = atom((get) => {
    const ctx = get(navigationContextAtom);
    const items: BreadcrumbItem[] = [];

    // Always start with workspace if available
    if (ctx.workspaceName) {
        items.push({
            label: ctx.workspaceName,
            href: '/app',
            aiTooltip: 'Your current workspace',
        });
    }

    // Add project if in project context
    if (ctx.projectName && ctx.projectId) {
        items.push({
            label: ctx.projectName,
            href: `/app/project/${ctx.projectId}`,
            aiTooltip: ctx.projectIsOwned
                ? 'You own this project (full access)'
                : 'Included project (read-only)',
        });
    }

    // Add current section
    if (ctx.currentSection) {
        items.push({
            label: formatSectionName(ctx.currentSection),
        });
    }

    // Append any custom items
    if (ctx.customItems) {
        items.push(...ctx.customItems);
    }

    return items;
});

// ============================================================================
// Action Atoms
// ============================================================================

/**
 * Update navigation context
 */
export const updateNavigationContextAtom = atom(
    null,
    (get, set, update: Partial<NavigationContext>) => {
        const current = get(navigationContextAtom);
        set(navigationContextAtom, {
            ...current,
            ...update,
        });
    }
);

/**
 * Set workspace in breadcrumb
 */
export const setWorkspaceBreadcrumbAtom = atom(
    null,
    (get, set, workspace: { id: string; name: string } | null) => {
        const current = get(navigationContextAtom);
        set(navigationContextAtom, {
            ...current,
            workspaceId: workspace?.id,
            workspaceName: workspace?.name,
        });
    }
);

/**
 * Set project in breadcrumb
 */
export const setProjectBreadcrumbAtom = atom(
    null,
    (get, set, project: { id: string; name: string; isOwned: boolean } | null) => {
        const current = get(navigationContextAtom);
        set(navigationContextAtom, {
            ...current,
            projectId: project?.id,
            projectName: project?.name,
            projectIsOwned: project?.isOwned,
        });
    }
);

/**
 * Set current section
 */
export const setSectionBreadcrumbAtom = atom(
    null,
    (get, set, section: string | undefined) => {
        const current = get(navigationContextAtom);
        set(navigationContextAtom, {
            ...current,
            currentSection: section,
        });
    }
);

/**
 * Clear breadcrumb (reset to workspace level)
 */
export const clearBreadcrumbAtom = atom(
    null,
    (get, set) => {
        const current = get(navigationContextAtom);
        set(navigationContextAtom, {
            workspaceId: current.workspaceId,
            workspaceName: current.workspaceName,
        });
    }
);

// ============================================================================
// Helpers
// ============================================================================

/**
 * Format section name for display
 * canvas -> Canvas, page-builder -> Page Builder
 */
function formatSectionName(section: string): string {
    return section
        .split('-')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}
