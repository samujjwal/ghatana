/**
 * Deep Linking Utilities for Lifecycle
 * 
 * Provides URL-based navigation and state management for lifecycle views.
 * Supports linking to specific phases and artifacts via query parameters.
 * 
 * @doc.type utility
 * @doc.purpose Deep linking and URL state management
 * @doc.layer product
 * @doc.pattern Utility Functions
 */

import type { LifecyclePhase } from '../../../shared/types/lifecycle';
import type { LifecycleArtifactKind } from '../../../shared/types/lifecycle-artifacts';

export interface LifecycleURLState {
    phase?: LifecyclePhase;
    artifactId?: string;
    artifactKind?: LifecycleArtifactKind;
}

/**
 * Parse lifecycle state from URL search params
 */
export function parseLifecycleURL(searchParams: URLSearchParams): LifecycleURLState {
    const phase = searchParams.get('phase') as LifecyclePhase | null;
    const artifactId = searchParams.get('artifact');
    const artifactKind = searchParams.get('kind') as LifecycleArtifactKind | null;

    return {
        phase: phase || undefined,
        artifactId: artifactId || undefined,
        artifactKind: artifactKind || undefined,
    };
}

/**
 * Build URL search params from lifecycle state
 */
export function buildLifecycleURL(state: LifecycleURLState): URLSearchParams {
    const params = new URLSearchParams();

    if (state.phase) {
        params.set('phase', state.phase);
    }
    if (state.artifactId) {
        params.set('artifact', state.artifactId);
    }
    if (state.artifactKind) {
        params.set('kind', state.artifactKind);
    }

    return params;
}

/**
 * Generate shareable link for a specific lifecycle view
 */
export function generateLifecycleLink(
    projectId: string,
    state: LifecycleURLState,
    baseURL: string = window.location.origin
): string {
    const params = buildLifecycleURL(state);
    const query = params.toString();
    return `${baseURL}/p/${projectId}/lifecycle${query ? `?${query}` : ''}`;
}

/**
 * Copy lifecycle link to clipboard
 */
export async function copyLifecycleLink(
    projectId: string,
    state: LifecycleURLState
): Promise<boolean> {
    try {
        const link = generateLifecycleLink(projectId, state);
        await navigator.clipboard.writeText(link);
        return true;
    } catch (err) {
        console.error('Failed to copy link:', err);
        return false;
    }
}

/**
 * Update browser URL without full navigation
 */
export function updateLifecycleURL(state: LifecycleURLState, replace: boolean = false) {
    const params = buildLifecycleURL(state);
    const url = `${window.location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;

    if (replace) {
        window.history.replaceState({}, '', url);
    } else {
        window.history.pushState({}, '', url);
    }
}

/**
 * Navigate to a specific phase (scrolls into view)
 */
export function scrollToPhase(phase: LifecyclePhase) {
    const phaseElement = document.getElementById(`phase-${phase}`);
    if (phaseElement) {
        phaseElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

/**
 * Navigate to specific artifact within a phase
 */
export function scrollToArtifact(artifactId: string) {
    const artifactElement = document.getElementById(`artifact-${artifactId}`);
    if (artifactElement) {
        artifactElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
        // Add highlight effect
        artifactElement.classList.add('ring-2', 'ring-primary-500', 'ring-offset-2');
        setTimeout(() => {
            artifactElement.classList.remove('ring-2', 'ring-primary-500', 'ring-offset-2');
        }, 2000);
    }
}
