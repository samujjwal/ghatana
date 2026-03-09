/**
 * @fileoverview C4 Model Synchronization
 * 
 * Provides integration with Structurizr C4 Model DSL for architecture visualization.
 * Supports context, container, component, and code views with environment overlays
 * and hierarchical navigation.
 * 
 * @module libs/canvas/src/devsecops/c4Sync
 * @see https://structurizr.com/dsl
 * @see https://c4model.com/
 */

import type { CanvasDocument, CanvasNode, CanvasEdge } from '../types/canvas-document';

/**
 * C4 model view types following the C4 architecture model
 */
export type C4ViewType = 'system-context' | 'container' | 'component' | 'code';

/**
 * C4 element types
 */
export type C4ElementType = 
  | 'person'           // External user/actor
  | 'software-system'  // Software system
  | 'container'        // Application/service
  | 'component'        // Code component
  | 'deployment-node'  // Infrastructure node
  | 'infrastructure-node'; // Infrastructure element

/**
 * Environment type for overlays
 */
export type C4Environment = 'dev' | 'staging' | 'production' | 'test';

/**
 * C4 element definition
 */
export interface C4Element {
  id: string;
  name: string;
  type: C4ElementType;
  description?: string;
  technology?: string;
  tags?: string[];
  url?: string;
  properties?: Record<string, string>;
  environment?: C4Environment;
}

/**
 * C4 relationship definition
 */
export interface C4Relationship {
  id: string;
  source: string;
  target: string;
  description?: string;
  technology?: string;
  tags?: string[];
}

/**
 * C4 view configuration
 */
export interface C4View {
  key: string;
  type: C4ViewType;
  title?: string;
  description?: string;
  softwareSystemId?: string;
  containerId?: string;
  include?: string[];
  exclude?: string[];
  autoLayout?: boolean;
}

/**
 * C4 workspace (top-level structure)
 */
export interface C4Workspace {
  name: string;
  description?: string;
  model: {
    elements: C4Element[];
    relationships: C4Relationship[];
  };
  views: C4View[];
}

/**
 * C4 sync configuration
 */
export interface C4SyncConfig {
  autoSync: boolean;
  validateOnSync: boolean;
  defaultEnvironment: C4Environment;
  enableEnvironmentOverlays: boolean;
  autoLayout: boolean;
}

/**
 * C4 sync state
 */
export interface C4SyncState {
  config: C4SyncConfig;
  workspace: C4Workspace | null;
  activeView: string | null;
  activeEnvironment: C4Environment;
  breadcrumbs: C4Breadcrumb[];
  lastSyncTimestamp: string | null;
}

/**
 * Breadcrumb for navigation
 */
export interface C4Breadcrumb {
  viewKey: string;
  viewType: C4ViewType;
  title: string;
  elementId?: string;
}

/**
 * View hierarchy for drill-down
 */
export interface C4ViewHierarchy {
  systemContext: C4View[];
  containers: Map<string, C4View[]>;
  components: Map<string, C4View[]>;
}

/**
 * Environment overlay data
 */
export interface C4EnvironmentOverlay {
  environment: C4Environment;
  elementsToShow: string[];
  elementsToHide: string[];
  styleOverrides: Map<string, Record<string, string>>;
}

/**
 * Create default C4 sync configuration
 * 
 * @param overrides - Optional configuration overrides
 * @returns C4 sync configuration
 * 
 * @example
 * ```typescript
 * const config = createC4SyncConfig({
 *   enableEnvironmentOverlays: true,
 *   defaultEnvironment: 'production'
 * });
 * ```
 */
export function createC4SyncConfig(
  overrides?: Partial<C4SyncConfig>
): C4SyncConfig {
  return {
    autoSync: false,
    validateOnSync: true,
    defaultEnvironment: 'dev',
    enableEnvironmentOverlays: true,
    autoLayout: true,
    ...overrides,
  };
}

/**
 * Create initial C4 sync state
 * 
 * @param config - C4 sync configuration
 * @returns C4 sync state
 * 
 * @example
 * ```typescript
 * const state = createC4SyncState(config);
 * ```
 */
export function createC4SyncState(
  config: C4SyncConfig
): C4SyncState {
  return {
    config,
    workspace: null,
    activeView: null,
    activeEnvironment: config.defaultEnvironment,
    breadcrumbs: [],
    lastSyncTimestamp: null,
  };
}

/**
 * Parse C4 DSL to workspace
 * 
 * @param dsl - C4 DSL string
 * @returns Parsed C4 workspace
 * 
 * @example
 * ```typescript
 * const dsl = `
 *   workspace "My System" {
 *     model {
 *       user = person "User"
 *       system = softwareSystem "My System"
 *       user -> system "Uses"
 *     }
 *     views {
 *       systemContext system {
 *         include *
 *       }
 *     }
 *   }
 * `;
 * const workspace = parseC4DSL(dsl);
 * ```
 */
export function parseC4DSL(dsl: string): C4Workspace {
  const lines = dsl.split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('//'));
  
  const workspace: C4Workspace = {
    name: 'Untitled',
    model: {
      elements: [],
      relationships: [],
    },
    views: [],
  };

  let currentSection: 'workspace' | 'model' | 'views' | null = null;
  let braceLevel = 0;
  let currentView: Partial<C4View> | null = null;

  for (const line of lines) {
    // Track brace level
    braceLevel += (line.match(/{/g) || []).length;
    braceLevel -= (line.match(/}/g) || []).length;

    // Workspace declaration
    if (line.startsWith('workspace ')) {
      const match = line.match(/workspace\s+"([^"]+)"/);
      if (match) workspace.name = match[1];
      currentSection = 'workspace';
    }
    // Model section
    else if (line === 'model {') {
      currentSection = 'model';
    }
    // Views section
    else if (line === 'views {') {
      currentSection = 'views';
    }
    // Parse model elements
    else if (currentSection === 'model' && line.includes('=')) {
      const personMatch = line.match(/(\w+)\s*=\s*person\s+"([^"]+)"(?:\s+"([^"]+)")?/);
      const systemMatch = line.match(/(\w+)\s*=\s*softwareSystem\s+"([^"]+)"(?:\s+"([^"]+)")?/);
      const containerMatch = line.match(/(\w+)\s*=\s*container\s+"([^"]+)"(?:\s+"([^"]+)")?(?:\s+"([^"]+)")?/);
      
      if (personMatch) {
        workspace.model.elements.push({
          id: personMatch[1],
          name: personMatch[2],
          type: 'person',
          description: personMatch[3],
        });
      } else if (systemMatch) {
        workspace.model.elements.push({
          id: systemMatch[1],
          name: systemMatch[2],
          type: 'software-system',
          description: systemMatch[3],
        });
      } else if (containerMatch) {
        workspace.model.elements.push({
          id: containerMatch[1],
          name: containerMatch[2],
          type: 'container',
          description: containerMatch[3],
          technology: containerMatch[4],
        });
      }
    }
    // Parse relationships
    else if (currentSection === 'model' && line.includes('->')) {
      const relMatch = line.match(/(\w+)\s*->\s*(\w+)(?:\s+"([^"]+)")?(?:\s+"([^"]+)")?/);
      if (relMatch) {
        workspace.model.relationships.push({
          id: `rel-${workspace.model.relationships.length}`,
          source: relMatch[1],
          target: relMatch[2],
          description: relMatch[3],
          technology: relMatch[4],
        });
      }
    }
    // Parse views
    else if (currentSection === 'views' && line.includes('systemContext')) {
      const viewMatch = line.match(/systemContext\s+(\w+)(?:\s+"([^"]+)")?/);
      if (viewMatch) {
        currentView = {
          key: `system-context-${viewMatch[1]}`,
          type: 'system-context',
          softwareSystemId: viewMatch[1],
          title: viewMatch[2] || `System Context for ${viewMatch[1]}`,
          include: [],
        };
      }
    } else if (currentSection === 'views' && line.includes('container')) {
      const viewMatch = line.match(/container\s+(\w+)(?:\s+"([^"]+)")?/);
      if (viewMatch) {
        currentView = {
          key: `container-${viewMatch[1]}`,
          type: 'container',
          softwareSystemId: viewMatch[1],
          title: viewMatch[2] || `Container View for ${viewMatch[1]}`,
          include: [],
        };
      }
    } else if (currentSection === 'views' && line.includes('component')) {
      const viewMatch = line.match(/component\s+(\w+)(?:\s+"([^"]+)")?/);
      if (viewMatch) {
        currentView = {
          key: `component-${viewMatch[1]}`,
          type: 'component',
          containerId: viewMatch[1],
          title: viewMatch[2] || `Component View for ${viewMatch[1]}`,
          include: [],
        };
      }
    }
    // View includes
    else if (currentView && line.startsWith('include ')) {
      const includeMatch = line.match(/include\s+(.+)/);
      if (includeMatch) {
        const includes = includeMatch[1].split(/\s+/);
        currentView.include = includes;
      }
    }
    // View closing
    else if (currentView && line === '}' && braceLevel === 2) {
      workspace.views.push(currentView as C4View);
      currentView = null;
    }
  }

  return workspace;
}

/**
 * Convert C4 workspace to canvas document
 * 
 * @param workspace - C4 workspace
 * @param viewKey - Specific view to render
 * @returns Canvas document
 * 
 * @example
 * ```typescript
 * const canvas = c4WorkspaceToCanvas(workspace, 'system-context-mySystem');
 * ```
 */
export function c4WorkspaceToCanvas(
  workspace: C4Workspace,
  viewKey: string
): CanvasDocument {
  const view = workspace.views.find(v => v.key === viewKey);
  if (!view) {
    throw new Error(`View not found: ${viewKey}`);
  }

  const elements: Record<string, CanvasNode | CanvasEdge> = {};
  const elementOrder: string[] = [];

  // Determine which elements to include
  const includeAll = view.include?.includes('*');
  const includedIds = new Set(includeAll ? workspace.model.elements.map(e => e.id) : view.include || []);
  const excludedIds = new Set(view.exclude || []);

  // Filter elements for this view
  const viewElements = workspace.model.elements.filter(
    e => includedIds.has(e.id) && !excludedIds.has(e.id)
  );

  // Convert elements to nodes
  viewElements.forEach((element, index) => {
    const nodeId = element.id;
    const x = index * 200;
    const y = 100;
    const width = 150;
    const height = 100;
    
    elements[nodeId] = {
      id: nodeId,
      type: 'node' as const,
      nodeType: elementTypeToNodeType(element.type),
      transform: {
        position: { x, y },
        scale: 1,
        rotation: 0,
      },
      bounds: { x, y, width, height },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 1,
      metadata: {
        c4Type: element.type,
        c4Tags: element.tags,
      },
      version: '1.0.0',
      createdAt: new Date(),
      updatedAt: new Date(),
      data: {
        label: element.name,
        description: element.description,
        technology: element.technology,
      },
      inputs: [],
      outputs: [],
      style: getC4ElementStyle(element.type),
    };
    elementOrder.push(nodeId);
  });

  // Convert relationships to edges
  const elementIds = new Set(viewElements.map(e => e.id));
  workspace.model.relationships
    .filter(r => elementIds.has(r.source) && elementIds.has(r.target))
    .forEach(rel => {
      const edgeId = rel.id;
      elements[edgeId] = {
        id: edgeId,
        type: 'edge' as const,
        sourceId: rel.source,
        targetId: rel.target,
        transform: {
          position: { x: 0, y: 0 },
          scale: 1,
          rotation: 0,
        },
        bounds: { x: 0, y: 0, width: 0, height: 0 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {
          label: rel.description,
          technology: rel.technology,
          c4Tags: rel.tags,
        },
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
        path: [],
        style: {
          strokeWidth: 2,
          stroke: '#666',
        },
      };
      elementOrder.push(edgeId);
    });

  return {
    id: `c4-view-${viewKey}`,
    version: '1.0.0',
    title: view.title || viewKey,
    description: workspace.description,
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1,
    },
    elements,
    elementOrder,
    metadata: {
      c4View: view,
      c4Workspace: workspace.name,
    },
    capabilities: {
      canEdit: true,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: true,
      canRedo: true,
      canExport: true,
      canImport: true,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

/**
 * Convert C4 element type to canvas node type
 */
function elementTypeToNodeType(c4Type: C4ElementType): string {
  const mapping: Record<C4ElementType, string> = {
    'person': 'actor',
    'software-system': 'system',
    'container': 'service',
    'component': 'component',
    'deployment-node': 'server',
    'infrastructure-node': 'infrastructure',
  };
  return mapping[c4Type] || 'default';
}

/**
 * Get default style for C4 element type
 */
function getC4ElementStyle(c4Type: C4ElementType): Record<string, unknown> {
  const baseStyle = {
    padding: 20,
    borderRadius: 8,
    fontSize: 14,
  };

  const typeStyles: Record<C4ElementType, Record<string, unknown>> = {
    'person': { backgroundColor: '#08427B', color: '#fff' },
    'software-system': { backgroundColor: '#1168BD', color: '#fff' },
    'container': { backgroundColor: '#438DD5', color: '#fff' },
    'component': { backgroundColor: '#85BBF0', color: '#000' },
    'deployment-node': { backgroundColor: '#C4C4C4', color: '#000' },
    'infrastructure-node': { backgroundColor: '#999', color: '#fff' },
  };

  return { ...baseStyle, ...typeStyles[c4Type] };
}

/**
 * Import C4 DSL and sync to canvas state
 * 
 * @param state - Current C4 sync state
 * @param dsl - C4 DSL string
 * @returns Updated state with workspace
 * 
 * @example
 * ```typescript
 * const updated = importC4DSL(state, dslContent);
 * console.log(`Imported ${updated.workspace?.model.elements.length} elements`);
 * ```
 */
export function importC4DSL(
  state: C4SyncState,
  dsl: string
): C4SyncState {
  const workspace = parseC4DSL(dsl);
  
  return {
    ...state,
    workspace,
    activeView: workspace.views[0]?.key || null,
    lastSyncTimestamp: new Date().toISOString(),
  };
}

/**
 * Set active view
 * 
 * @param state - Current C4 sync state
 * @param viewKey - View key to activate
 * @returns Updated state
 * 
 * @example
 * ```typescript
 * const updated = setActiveView(state, 'system-context-mySystem');
 * ```
 */
export function setActiveView(
  state: C4SyncState,
  viewKey: string
): C4SyncState {
  if (!state.workspace) {
    throw new Error('No workspace loaded');
  }

  const view = state.workspace.views.find(v => v.key === viewKey);
  if (!view) {
    throw new Error(`View not found: ${viewKey}`);
  }

  return {
    ...state,
    activeView: viewKey,
  };
}

/**
 * Build view hierarchy for navigation
 * 
 * @param workspace - C4 workspace
 * @returns View hierarchy
 * 
 * @example
 * ```typescript
 * const hierarchy = buildViewHierarchy(workspace);
 * const systemContextViews = hierarchy.systemContext;
 * ```
 */
export function buildViewHierarchy(workspace: C4Workspace): C4ViewHierarchy {
  const hierarchy: C4ViewHierarchy = {
    systemContext: [],
    containers: new Map(),
    components: new Map(),
  };

  workspace.views.forEach(view => {
    if (view.type === 'system-context') {
      hierarchy.systemContext.push(view);
    } else if (view.type === 'container' && view.softwareSystemId) {
      const containers = hierarchy.containers.get(view.softwareSystemId) || [];
      containers.push(view);
      hierarchy.containers.set(view.softwareSystemId, containers);
    } else if (view.type === 'component' && view.containerId) {
      const components = hierarchy.components.get(view.containerId) || [];
      components.push(view);
      hierarchy.components.set(view.containerId, components);
    }
  });

  return hierarchy;
}

/**
 * Navigate to child view (drill down)
 * 
 * @param state - Current C4 sync state
 * @param elementId - Element to drill into
 * @returns Updated state with new view and breadcrumbs
 * 
 * @example
 * ```typescript
 * // From system context, drill into container view
 * const updated = drillDown(state, 'mySystem');
 * ```
 */
export function drillDown(
  state: C4SyncState,
  elementId: string
): C4SyncState {
  if (!state.workspace || !state.activeView) {
    throw new Error('No workspace or active view');
  }

  const currentView = state.workspace.views.find(v => v.key === state.activeView);
  if (!currentView) {
    throw new Error('Current view not found');
  }

  const hierarchy = buildViewHierarchy(state.workspace);
  let nextView: C4View | null = null;

  // Determine next view based on current view type
  if (currentView.type === 'system-context') {
    // Drill into container view
    const containerViews = hierarchy.containers.get(elementId);
    if (containerViews && containerViews.length > 0) {
      nextView = containerViews[0];
    }
  } else if (currentView.type === 'container') {
    // Drill into component view
    const componentViews = hierarchy.components.get(elementId);
    if (componentViews && componentViews.length > 0) {
      nextView = componentViews[0];
    }
  }

  if (!nextView) {
    throw new Error(`No child view found for element: ${elementId}`);
  }

  // Add to breadcrumbs
  const breadcrumbs = [
    ...state.breadcrumbs,
    {
      viewKey: currentView.key,
      viewType: currentView.type,
      title: currentView.title || currentView.key,
      elementId,
    },
  ];

  return {
    ...state,
    activeView: nextView.key,
    breadcrumbs,
  };
}

/**
 * Navigate up using breadcrumbs
 * 
 * @param state - Current C4 sync state
 * @param breadcrumbIndex - Index of breadcrumb to navigate to
 * @returns Updated state
 * 
 * @example
 * ```typescript
 * // Go back to previous view
 * const updated = navigateUp(state, state.breadcrumbs.length - 1);
 * ```
 */
export function navigateUp(
  state: C4SyncState,
  breadcrumbIndex: number
): C4SyncState {
  if (breadcrumbIndex < 0 || breadcrumbIndex >= state.breadcrumbs.length) {
    throw new Error('Invalid breadcrumb index');
  }

  const targetBreadcrumb = state.breadcrumbs[breadcrumbIndex];

  return {
    ...state,
    activeView: targetBreadcrumb.viewKey,
    breadcrumbs: state.breadcrumbs.slice(0, breadcrumbIndex),
  };
}

/**
 * Set active environment
 * 
 * @param state - Current C4 sync state
 * @param environment - Environment to activate
 * @returns Updated state
 * 
 * @example
 * ```typescript
 * const updated = setActiveEnvironment(state, 'production');
 * ```
 */
export function setActiveEnvironment(
  state: C4SyncState,
  environment: C4Environment
): C4SyncState {
  return {
    ...state,
    activeEnvironment: environment,
  };
}

/**
 * Get environment overlay for current state
 * 
 * @param state - Current C4 sync state
 * @returns Environment overlay data
 * 
 * @example
 * ```typescript
 * const overlay = getEnvironmentOverlay(state);
 * const visibleElements = overlay.elementsToShow;
 * ```
 */
export function getEnvironmentOverlay(
  state: C4SyncState
): C4EnvironmentOverlay {
  if (!state.config.enableEnvironmentOverlays || !state.workspace) {
    return {
      environment: state.activeEnvironment,
      elementsToShow: [],
      elementsToHide: [],
      styleOverrides: new Map(),
    };
  }

  const elementsToShow: string[] = [];
  const elementsToHide: string[] = [];
  const styleOverrides = new Map<string, Record<string, string>>();

  // Filter elements based on environment
  state.workspace.model.elements.forEach(element => {
    if (!element.environment || element.environment === state.activeEnvironment) {
      elementsToShow.push(element.id);
    } else {
      elementsToHide.push(element.id);
    }

    // Apply environment-specific styling
    if (element.environment === state.activeEnvironment) {
      styleOverrides.set(element.id, {
        opacity: '1',
        borderWidth: '3px',
        borderColor: getEnvironmentColor(state.activeEnvironment),
      });
    }
  });

  return {
    environment: state.activeEnvironment,
    elementsToShow,
    elementsToHide,
    styleOverrides,
  };
}

/**
 * Get color for environment
 */
function getEnvironmentColor(env: C4Environment): string {
  const colors: Record<C4Environment, string> = {
    'dev': '#4CAF50',
    'staging': '#FF9800',
    'production': '#F44336',
    'test': '#2196F3',
  };
  return colors[env];
}
