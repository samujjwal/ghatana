/**
 * Canvas Deep Linking Utilities
 *
 * Provides URL-based navigation and state management for Canvas views.
 * Supports linking to specific tasks, personas, and canvas states via query parameters.
 *
 * @doc.type utility
 * @doc.purpose Deep linking and URL state management for Canvas
 * @doc.layer product
 * @doc.pattern Utility Functions
 */

import type { PersonaType } from '../context/PersonaContext';

/**
 * Canvas URL state interface
 */
export interface CanvasURLState {
  taskId?: string;
  persona?: PersonaType;
  nodeId?: string;
  viewMode?: 'default' | 'focus' | 'overview';
  zoom?: number;
  panX?: number;
  panY?: number;
  leftPanel?: string;
  rightPanel?: string;
  calmMode?: boolean;
}

/**
 * Parse persona from URL string (handles various formats)
 */
export function parsePersonaFromURL(
  personaStr: string
): PersonaType | undefined {
  if (!personaStr) return undefined;

  // Normalize the string: lowercase, replace spaces/dashes with hyphens
  const normalized = personaStr
    .toLowerCase()
    .trim()
    .replace(/\s+/g, '-')
    .replace(/_/g, '-');

  // Map common variations to canonical persona types
  const personaMap: Record<string, PersonaType> = {
    'product-owner': 'product-owner',
    'product-manager': 'product-owner',
    po: 'product-owner',
    pm: 'product-owner',
    developer: 'developer',
    dev: 'developer',
    engineer: 'developer',
    designer: 'designer',
    ux: 'designer',
    ui: 'designer',
    devops: 'devops',
    ops: 'devops',
    sre: 'devops',
    qa: 'qa',
    tester: 'qa',
    quality: 'qa',
    security: 'security',
    sec: 'security',
    infosec: 'security',
  };

  return personaMap[normalized];
}

/**
 * Parse canvas state from URL search params
 *
 * @doc.param searchParams - URLSearchParams from current location
 * @doc.returns Parsed canvas state object
 */
export function parseCanvasURL(searchParams: URLSearchParams): CanvasURLState {
  const taskId = searchParams.get('taskId') || searchParams.get('task');
  const personaStr = searchParams.get('persona') || searchParams.get('role');
  const nodeId = searchParams.get('nodeId') || searchParams.get('node');
  const viewMode = searchParams.get('view') as CanvasURLState['viewMode'];
  const zoom = searchParams.get('zoom');
  const panX = searchParams.get('panX');
  const panY = searchParams.get('panY');
  const leftPanel = searchParams.get('leftPanel');
  const rightPanel = searchParams.get('rightPanel');
  const calmMode = searchParams.get('calmMode') || searchParams.get('calm');

  return {
    taskId: taskId || undefined,
    persona: personaStr ? parsePersonaFromURL(personaStr) : undefined,
    nodeId: nodeId || undefined,
    viewMode: viewMode || undefined,
    zoom: zoom ? parseFloat(zoom) : undefined,
    panX: panX ? parseFloat(panX) : undefined,
    panY: panY ? parseFloat(panY) : undefined,
    leftPanel: leftPanel || undefined,
    rightPanel: rightPanel || undefined,
    calmMode: calmMode === 'true' || calmMode === '1',
  };
}

/**
 * Build URL search params from canvas state
 *
 * @doc.param state - Canvas state to encode
 * @doc.returns URLSearchParams object
 */
export function buildCanvasURL(state: CanvasURLState): URLSearchParams {
  const params = new URLSearchParams();

  if (state.taskId) {
    params.set('taskId', state.taskId);
  }
  if (state.persona) {
    params.set('persona', state.persona);
  }
  if (state.nodeId) {
    params.set('nodeId', state.nodeId);
  }
  if (state.viewMode && state.viewMode !== 'default') {
    params.set('view', state.viewMode);
  }
  if (state.zoom !== undefined) {
    params.set('zoom', state.zoom.toFixed(2));
  }
  if (state.panX !== undefined) {
    params.set('panX', state.panX.toFixed(0));
  }
  if (state.panY !== undefined) {
    params.set('panY', state.panY.toFixed(0));
  }
  if (state.leftPanel) {
    params.set('leftPanel', state.leftPanel);
  }
  if (state.rightPanel) {
    params.set('rightPanel', state.rightPanel);
  }
  if (state.calmMode) {
    params.set('calmMode', 'true');
  }

  return params;
}

/**
 * Generate shareable link for a specific canvas view
 *
 * @doc.param projectId - Project identifier
 * @doc.param state - Canvas state to encode
 * @doc.param baseURL - Base URL (defaults to current origin)
 * @doc.returns Complete shareable URL
 */
export function generateCanvasLink(
  projectId: string,
  state: CanvasURLState,
  baseURL: string = typeof window !== 'undefined' ? window.location.origin : ''
): string {
  const params = buildCanvasURL(state);
  const query = params.toString();
  return `${baseURL}/p/${projectId}/canvas${query ? `?${query}` : ''}`;
}

/**
 * Copy canvas link to clipboard
 *
 * @doc.param projectId - Project identifier
 * @doc.param state - Canvas state to encode
 * @doc.returns Promise resolving to success status
 */
export async function copyCanvasLink(
  projectId: string,
  state: CanvasURLState
): Promise<boolean> {
  try {
    const link = generateCanvasLink(projectId, state);
    await navigator.clipboard.writeText(link);
    return true;
  } catch (err) {
    console.error('Failed to copy canvas link:', err);
    return false;
  }
}

/**
 * Update browser URL without full navigation
 *
 * @doc.param state - Canvas state to encode
 * @doc.param replace - Whether to replace current history entry
 */
export function updateCanvasURL(
  state: CanvasURLState,
  replace: boolean = false
): void {
  if (typeof window === 'undefined') return;

  const params = buildCanvasURL(state);
  const url = `${window.location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;

  if (replace) {
    window.history.replaceState({}, '', url);
  } else {
    window.history.pushState({}, '', url);
  }
}

/**
 * Scroll to and highlight a specific node on the canvas
 *
 * @doc.param nodeId - Node identifier to focus
 * @doc.param behavior - Scroll behavior (smooth or auto)
 */
export function scrollToNode(
  nodeId: string,
  behavior: ScrollBehavior = 'smooth'
): void {
  const nodeElement = document.querySelector(`[data-id="${nodeId}"]`);
  if (nodeElement) {
    nodeElement.scrollIntoView({ behavior, block: 'center' });

    // Add highlight effect
    nodeElement.classList.add('ring-4', 'ring-primary-500', 'ring-offset-2');
    setTimeout(() => {
      nodeElement.classList.remove(
        'ring-4',
        'ring-primary-500',
        'ring-offset-2'
      );
    }, 2000);
  }
}

/**
 * Validate canvas URL state
 *
 * @doc.param state - Canvas state to validate
 * @doc.returns Validation result with errors
 */
export function validateCanvasURLState(state: CanvasURLState): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  // Validate zoom level
  if (state.zoom !== undefined && (state.zoom < 0.1 || state.zoom > 10)) {
    errors.push('Zoom level must be between 0.1 and 10');
  }

  // Validate view mode
  if (
    state.viewMode &&
    !['default', 'focus', 'overview'].includes(state.viewMode)
  ) {
    errors.push('Invalid view mode');
  }

  // Validate persona
  if (state.persona) {
    const validPersonas: PersonaType[] = [
      'product-owner',
      'developer',
      'designer',
      'devops',
      'qa',
      'security',
    ];
    if (!validPersonas.includes(state.persona)) {
      errors.push('Invalid persona type');
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Merge partial canvas state with current state
 *
 * @doc.param currentState - Current canvas state
 * @doc.param updates - Partial updates to apply
 * @doc.returns Merged canvas state
 */
export function mergeCanvasURLState(
  currentState: CanvasURLState,
  updates: Partial<CanvasURLState>
): CanvasURLState {
  return {
    ...currentState,
    ...updates,
  };
}

/**
 * Extract canvas state from current window location
 *
 * @doc.returns Current canvas state from URL
 */
export function getCurrentCanvasURLState(): CanvasURLState {
  if (typeof window === 'undefined') {
    return {};
  }

  const searchParams = new URLSearchParams(window.location.search);
  return parseCanvasURL(searchParams);
}

/**
 * Check if canvas URL has specific parameter
 *
 * @doc.param param - Parameter name to check
 * @doc.returns Whether parameter exists in URL
 */
export function hasCanvasURLParam(param: keyof CanvasURLState): boolean {
  if (typeof window === 'undefined') return false;

  const searchParams = new URLSearchParams(window.location.search);
  const state = parseCanvasURL(searchParams);
  return state[param] !== undefined;
}
