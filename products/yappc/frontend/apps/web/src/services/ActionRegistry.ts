/**
 * Action Registry Service
 *
 * Central registry for all actions in the application.
 * Provides context-aware action filtering and keyboard shortcut management.
 *
 * @doc.type service
 * @doc.purpose Action management and context filtering
 * @doc.layer product
 * @doc.pattern Registry Pattern
 */

import { LifecyclePhase } from '../types/lifecycle';

// ============================================================================
// Types
// ============================================================================

/**
 * Action context - defines when an action is available
 */
export interface ActionContext {
  /** Available in these phases (empty = all phases) */
  phases?: LifecyclePhase[];
  /** Requires selection (node/edge/group) */
  requiresSelection?: boolean;
  /** Selection type required */
  selectionType?: 'node' | 'edge' | 'group' | 'any';
  /** Requires a project to be loaded */
  requiresProject?: boolean;
  /** Available in these routes (empty = all routes) */
  routes?: string[];
  /** Custom condition function */
  condition?: (state: ActionState) => boolean;
}

/**
 * Action definition
 */
export interface ActionDefinition {
  /** Unique action ID */
  id: string;
  /** Display label */
  label: string;
  /** Optional description */
  description?: string;
  /** Icon name or component */
  icon?: string;
  /** Category for grouping */
  category: ActionCategory;
  /** Keyboard shortcut */
  shortcut?: string;
  /** Context requirements */
  context: ActionContext;
  /** Action handler */
  handler: (params: ActionParams) => void | Promise<void>;
  /** Priority for sorting (higher = first) */
  priority?: number;
  /** Is this a dangerous action */
  isDangerous?: boolean;
}

/**
 * Action categories
 */
export type ActionCategory =
  | 'file'
  | 'edit'
  | 'view'
  | 'canvas'
  | 'selection'
  | 'ai'
  | 'navigation'
  | 'project'
  | 'deploy'
  | 'help';

/**
 * Current action state (for context evaluation)
 */
export interface ActionState {
  currentPhase: LifecyclePhase | null;
  currentRoute: string;
  projectId: string | null;
  hasSelection: boolean;
  selectionType: 'node' | 'edge' | 'group' | null;
  selectionCount: number;
  isCanvasActive: boolean;
  canUndo: boolean;
  canRedo: boolean;
  isDirty: boolean;
  customState?: Record<string, unknown>;
}

/**
 * Parameters passed to action handlers
 */
export interface ActionParams {
  state: ActionState;
  event?: KeyboardEvent | MouseEvent;
}

/**
 * Grouped actions by category
 */
export interface GroupedActions {
  category: ActionCategory;
  label: string;
  actions: ActionDefinition[];
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Category labels and order
 */
const CATEGORY_CONFIG: Record<
  ActionCategory,
  { label: string; order: number }
> = {
  file: { label: 'File', order: 1 },
  edit: { label: 'Edit', order: 2 },
  view: { label: 'View', order: 3 },
  canvas: { label: 'Canvas', order: 4 },
  selection: { label: 'Selection', order: 5 },
  ai: { label: 'AI', order: 6 },
  navigation: { label: 'Navigation', order: 7 },
  project: { label: 'Project', order: 8 },
  deploy: { label: 'Deploy', order: 9 },
  help: { label: 'Help', order: 10 },
};

/**
 * Platform-specific modifier key
 */
const isMac =
  typeof navigator !== 'undefined' &&
  navigator.platform.toUpperCase().indexOf('MAC') >= 0;
const MOD_KEY = isMac ? '⌘' : 'Ctrl';
const ALT_KEY = isMac ? '⌥' : 'Alt';
const SHIFT_KEY = '⇧';

// ============================================================================
// Action Registry Class
// ============================================================================

/**
 * Action Registry
 *
 * Singleton service for managing and filtering actions.
 */
class ActionRegistryService {
  private actions: Map<string, ActionDefinition> = new Map();
  private shortcuts: Map<string, string> = new Map(); // shortcut -> actionId
  private listeners: Set<() => void> = new Set();
  private boundKeyHandler: ((e: KeyboardEvent) => void) | null = null;
  private currentState: ActionState | null = null;

  /**
   * Register an action
   */
  register(action: ActionDefinition): void {
    if (this.actions.has(action.id)) {
      console.warn(
        `[ActionRegistry] Action "${action.id}" is already registered, overwriting`
      );
    }
    this.actions.set(action.id, action);

    if (action.shortcut) {
      const normalizedShortcut = this.normalizeShortcut(action.shortcut);
      this.shortcuts.set(normalizedShortcut, action.id);
    }

    this.notifyListeners();
  }

  /**
   * Register multiple actions
   */
  registerAll(actions: ActionDefinition[]): void {
    actions.forEach((action) => this.register(action));
  }

  /**
   * Unregister an action
   */
  unregister(actionId: string): void {
    const action = this.actions.get(actionId);
    if (action?.shortcut) {
      const normalizedShortcut = this.normalizeShortcut(action.shortcut);
      this.shortcuts.delete(normalizedShortcut);
    }
    this.actions.delete(actionId);
    this.notifyListeners();
  }

  /**
   * Get an action by ID
   */
  get(actionId: string): ActionDefinition | undefined {
    return this.actions.get(actionId);
  }

  /**
   * Get all registered actions
   */
  getAll(): ActionDefinition[] {
    return Array.from(this.actions.values());
  }

  /**
   * Get actions available in current context
   */
  getAvailable(state: ActionState): ActionDefinition[] {
    return this.getAll().filter((action) => this.isAvailable(action, state));
  }

  /**
   * Get actions grouped by category
   */
  getGrouped(state: ActionState): GroupedActions[] {
    const available = this.getAvailable(state);
    const grouped = new Map<ActionCategory, ActionDefinition[]>();

    available.forEach((action) => {
      const list = grouped.get(action.category) || [];
      list.push(action);
      grouped.set(action.category, list);
    });

    return Array.from(grouped.entries())
      .map(([category, actions]) => ({
        category,
        label: CATEGORY_CONFIG[category].label,
        actions: actions.sort((a, b) => (b.priority || 0) - (a.priority || 0)),
      }))
      .sort(
        (a, b) =>
          CATEGORY_CONFIG[a.category].order - CATEGORY_CONFIG[b.category].order
      );
  }

  /**
   * Check if an action is available in current context
   */
  isAvailable(action: ActionDefinition, state: ActionState): boolean {
    const { context } = action;

    // Check phase
    if (context.phases && context.phases.length > 0) {
      if (!state.currentPhase || !context.phases.includes(state.currentPhase)) {
        return false;
      }
    }

    // Check project requirement
    if (context.requiresProject && !state.projectId) {
      return false;
    }

    // Check selection requirement
    if (context.requiresSelection && !state.hasSelection) {
      return false;
    }

    // Check selection type
    if (context.selectionType && context.selectionType !== 'any') {
      if (state.selectionType !== context.selectionType) {
        return false;
      }
    }

    // Check route
    if (context.routes && context.routes.length > 0) {
      const matchesRoute = context.routes.some(
        (route) => state.currentRoute.includes(route) || route === '*'
      );
      if (!matchesRoute) {
        return false;
      }
    }

    // Check custom condition
    if (context.condition && !context.condition(state)) {
      return false;
    }

    return true;
  }

  /**
   * Execute an action
   */
  async execute(
    actionId: string,
    state: ActionState,
    event?: KeyboardEvent | MouseEvent
  ): Promise<boolean> {
    const action = this.actions.get(actionId);
    if (!action) {
      console.warn(`[ActionRegistry] Action "${actionId}" not found`);
      return false;
    }

    if (!this.isAvailable(action, state)) {
      console.warn(
        `[ActionRegistry] Action "${actionId}" not available in current context`
      );
      return false;
    }

    try {
      await action.handler({ state, event });
      return true;
    } catch (error) {
      console.error(
        `[ActionRegistry] Error executing action "${actionId}":`,
        error
      );
      return false;
    }
  }

  /**
   * Enable keyboard shortcut handling
   */
  enableKeyboardShortcuts(getState: () => ActionState): void {
    if (this.boundKeyHandler) {
      this.disableKeyboardShortcuts();
    }

    this.boundKeyHandler = (event: KeyboardEvent) => {
      // Skip if in input/textarea
      const target = event.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        return;
      }

      const shortcut = this.eventToShortcut(event);
      const actionId = this.shortcuts.get(shortcut);

      if (actionId) {
        const state = getState();
        const action = this.actions.get(actionId);

        if (action && this.isAvailable(action, state)) {
          event.preventDefault();
          this.execute(actionId, state, event);
        }
      }
    };

    document.addEventListener('keydown', this.boundKeyHandler);
  }

  /**
   * Disable keyboard shortcut handling
   */
  disableKeyboardShortcuts(): void {
    if (this.boundKeyHandler) {
      document.removeEventListener('keydown', this.boundKeyHandler);
      this.boundKeyHandler = null;
    }
  }

  /**
   * Subscribe to registry changes
   */
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Format shortcut for display
   */
  formatShortcut(shortcut: string): string {
    return shortcut
      .replace(/mod/gi, MOD_KEY)
      .replace(/alt/gi, ALT_KEY)
      .replace(/shift/gi, SHIFT_KEY)
      .replace(/\+/g, ' + ');
  }

  // Private methods

  private normalizeShortcut(shortcut: string): string {
    return shortcut.toLowerCase().replace(/\s+/g, '');
  }

  private eventToShortcut(event: KeyboardEvent): string {
    const parts: string[] = [];
    if (event.metaKey || event.ctrlKey) parts.push('mod');
    if (event.altKey) parts.push('alt');
    if (event.shiftKey) parts.push('shift');
    parts.push(event.key.toLowerCase());
    return parts.join('+');
  }

  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener());
  }
}

// ============================================================================
// Singleton Export
// ============================================================================

export const ActionRegistry = new ActionRegistryService();

// ============================================================================
// Default Actions
// ============================================================================

/**
 * Register default actions
 */
export function registerDefaultActions(handlers: {
  undo?: () => void;
  redo?: () => void;
  save?: () => void;
  copy?: () => void;
  paste?: () => void;
  delete?: () => void;
  selectAll?: () => void;
  zoomIn?: () => void;
  zoomOut?: () => void;
  zoomFit?: () => void;
  toggleGrid?: () => void;
  openAI?: () => void;
  generate?: () => void;
  preview?: () => void;
  deploy?: () => void;
  help?: () => void;
}): void {
  const defaultActions: ActionDefinition[] = [
    // Edit actions
    {
      id: 'edit.undo',
      label: 'Undo',
      icon: 'undo',
      category: 'edit',
      shortcut: 'mod+z',
      context: { condition: (s) => s.canUndo },
      handler: () => handlers.undo?.(),
      priority: 100,
    },
    {
      id: 'edit.redo',
      label: 'Redo',
      icon: 'redo',
      category: 'edit',
      shortcut: 'mod+shift+z',
      context: { condition: (s) => s.canRedo },
      handler: () => handlers.redo?.(),
      priority: 99,
    },
    {
      id: 'file.save',
      label: 'Save',
      icon: 'save',
      category: 'file',
      shortcut: 'mod+s',
      context: { requiresProject: true },
      handler: () => handlers.save?.(),
      priority: 100,
    },
    {
      id: 'edit.copy',
      label: 'Copy',
      icon: 'copy',
      category: 'edit',
      shortcut: 'mod+c',
      context: { requiresSelection: true },
      handler: () => handlers.copy?.(),
      priority: 80,
    },
    {
      id: 'edit.paste',
      label: 'Paste',
      icon: 'paste',
      category: 'edit',
      shortcut: 'mod+v',
      context: { routes: ['/canvas'] },
      handler: () => handlers.paste?.(),
      priority: 79,
    },
    {
      id: 'edit.delete',
      label: 'Delete',
      description: 'Delete selected items',
      icon: 'delete',
      category: 'edit',
      shortcut: 'backspace',
      context: { requiresSelection: true },
      handler: () => handlers.delete?.(),
      isDangerous: true,
      priority: 70,
    },
    {
      id: 'edit.selectAll',
      label: 'Select All',
      icon: 'selectAll',
      category: 'edit',
      shortcut: 'mod+a',
      context: { routes: ['/canvas'] },
      handler: () => handlers.selectAll?.(),
      priority: 60,
    },

    // View actions
    {
      id: 'view.zoomIn',
      label: 'Zoom In',
      icon: 'zoomIn',
      category: 'view',
      shortcut: 'mod+=',
      context: { routes: ['/canvas'] },
      handler: () => handlers.zoomIn?.(),
      priority: 50,
    },
    {
      id: 'view.zoomOut',
      label: 'Zoom Out',
      icon: 'zoomOut',
      category: 'view',
      shortcut: 'mod+-',
      context: { routes: ['/canvas'] },
      handler: () => handlers.zoomOut?.(),
      priority: 49,
    },
    {
      id: 'view.zoomFit',
      label: 'Fit to View',
      icon: 'fitScreen',
      category: 'view',
      shortcut: 'mod+0',
      context: { routes: ['/canvas'] },
      handler: () => handlers.zoomFit?.(),
      priority: 48,
    },
    {
      id: 'view.toggleGrid',
      label: 'Toggle Grid',
      icon: 'grid',
      category: 'view',
      shortcut: "mod+'",
      context: { routes: ['/canvas'] },
      handler: () => handlers.toggleGrid?.(),
      priority: 40,
    },

    // AI actions
    {
      id: 'ai.open',
      label: 'Ask AI',
      description: 'Open AI assistant',
      icon: 'autoAwesome',
      category: 'ai',
      shortcut: 'mod+k',
      context: {},
      handler: () => handlers.openAI?.(),
      priority: 100,
    },

    // Canvas actions
    {
      id: 'canvas.generate',
      label: 'Generate Code',
      description: 'Generate code from canvas',
      icon: 'code',
      category: 'canvas',
      shortcut: 'mod+shift+g',
      context: {
        phases: [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE],
        requiresProject: true,
      },
      handler: () => handlers.generate?.(),
      priority: 90,
    },

    // Deploy actions
    {
      id: 'deploy.preview',
      label: 'Preview',
      description: 'Preview application',
      icon: 'visibility',
      category: 'deploy',
      shortcut: 'mod+shift+p',
      context: {
        phases: [LifecyclePhase.RUN, LifecyclePhase.OBSERVE],
        requiresProject: true,
      },
      handler: () => handlers.preview?.(),
      priority: 80,
    },
    {
      id: 'deploy.deploy',
      label: 'Deploy',
      description: 'Deploy application',
      icon: 'cloudUpload',
      category: 'deploy',
      shortcut: 'mod+shift+d',
      context: { phases: [LifecyclePhase.RUN], requiresProject: true },
      handler: () => handlers.deploy?.(),
      priority: 70,
    },

    // Help actions
    {
      id: 'help.shortcuts',
      label: 'Keyboard Shortcuts',
      icon: 'keyboard',
      category: 'help',
      shortcut: 'mod+/',
      context: {},
      handler: () => handlers.help?.(),
      priority: 50,
    },
  ];

  ActionRegistry.registerAll(defaultActions);
}

/**
 * Register navigation actions (go-to shortcuts)
 */
export function registerNavigationActions(handlers: {
  goHome?: () => void;
  goProjects?: () => void;
  goSettings?: () => void;
  goCanvas?: () => void;
  goComponents?: () => void;
  goCode?: () => void;
  goTests?: () => void;
  goDeploy?: () => void;
  goSearch?: () => void;
  goRecent?: () => void;
}): void {
  const navigationActions: ActionDefinition[] = [
    {
      id: 'nav.home',
      label: 'Go to Home',
      description: 'Navigate to home dashboard',
      icon: 'home',
      category: 'navigation',
      shortcut: 'g h',
      context: {},
      handler: () => handlers.goHome?.(),
      priority: 100,
    },
    {
      id: 'nav.projects',
      label: 'Go to Projects',
      description: 'Navigate to project list',
      icon: 'folder',
      category: 'navigation',
      shortcut: 'g p',
      context: {},
      handler: () => handlers.goProjects?.(),
      priority: 99,
    },
    {
      id: 'nav.settings',
      label: 'Go to Settings',
      description: 'Navigate to settings',
      icon: 'settings',
      category: 'navigation',
      shortcut: 'g s',
      context: {},
      handler: () => handlers.goSettings?.(),
      priority: 98,
    },
    {
      id: 'nav.canvas',
      label: 'Go to Canvas',
      description: 'Navigate to canvas',
      icon: 'dashboard',
      category: 'navigation',
      shortcut: 'g c',
      context: { requiresProject: true },
      handler: () => handlers.goCanvas?.(),
      priority: 97,
    },
    {
      id: 'nav.components',
      label: 'Go to Components',
      description: 'View project components',
      icon: 'widgets',
      category: 'navigation',
      shortcut: 'g m',
      context: { requiresProject: true },
      handler: () => handlers.goComponents?.(),
      priority: 96,
    },
    {
      id: 'nav.code',
      label: 'Go to Code',
      description: 'View generated code',
      icon: 'code',
      category: 'navigation',
      shortcut: 'g o',
      context: { requiresProject: true },
      handler: () => handlers.goCode?.(),
      priority: 95,
    },
    {
      id: 'nav.tests',
      label: 'Go to Tests',
      description: 'View test results',
      icon: 'science',
      category: 'navigation',
      shortcut: 'g t',
      context: { requiresProject: true },
      handler: () => handlers.goTests?.(),
      priority: 94,
    },
    {
      id: 'nav.deploy',
      label: 'Go to Deploy',
      description: 'View deployments',
      icon: 'cloudUpload',
      category: 'navigation',
      shortcut: 'g d',
      context: { requiresProject: true },
      handler: () => handlers.goDeploy?.(),
      priority: 93,
    },
    {
      id: 'nav.search',
      label: 'Quick Search',
      description: 'Search across project',
      icon: 'search',
      category: 'navigation',
      shortcut: 'mod+p',
      context: {},
      handler: () => handlers.goSearch?.(),
      priority: 110,
    },
    {
      id: 'nav.recent',
      label: 'Recent Items',
      description: 'View recently accessed items',
      icon: 'history',
      category: 'navigation',
      shortcut: 'mod+e',
      context: {},
      handler: () => handlers.goRecent?.(),
      priority: 109,
    },
  ];

  ActionRegistry.registerAll(navigationActions);
}

/**
 * Register canvas mode switching actions (1-7 number keys)
 */
export function registerModeSwitchActions(handlers: {
  setMode?: (mode: string) => void;
}): void {
  const modeActions: ActionDefinition[] = [
    {
      id: 'mode.brainstorm',
      label: 'Brainstorm Mode',
      description: 'Switch to brainstorm/ideation mode',
      icon: 'lightbulb',
      category: 'canvas',
      shortcut: '1',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('brainstorm'),
      priority: 60,
    },
    {
      id: 'mode.diagram',
      label: 'Diagram Mode',
      description: 'Switch to diagram/architecture mode',
      icon: 'accountTree',
      category: 'canvas',
      shortcut: '2',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('diagram'),
      priority: 59,
    },
    {
      id: 'mode.design',
      label: 'Design Mode',
      description: 'Switch to UI/UX design mode',
      icon: 'palette',
      category: 'canvas',
      shortcut: '3',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('design'),
      priority: 58,
    },
    {
      id: 'mode.code',
      label: 'Code Mode',
      description: 'Switch to code view mode',
      icon: 'code',
      category: 'canvas',
      shortcut: '4',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('code'),
      priority: 57,
    },
    {
      id: 'mode.test',
      label: 'Test Mode',
      description: 'Switch to testing mode',
      icon: 'science',
      category: 'canvas',
      shortcut: '5',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('test'),
      priority: 56,
    },
    {
      id: 'mode.deploy',
      label: 'Deploy Mode',
      description: 'Switch to deployment mode',
      icon: 'cloudUpload',
      category: 'canvas',
      shortcut: '6',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('deploy'),
      priority: 55,
    },
    {
      id: 'mode.observe',
      label: 'Observe Mode',
      description: 'Switch to monitoring mode',
      icon: 'monitoring',
      category: 'canvas',
      shortcut: '7',
      context: { routes: ['/canvas'] },
      handler: () => handlers.setMode?.('observe'),
      priority: 54,
    },
  ];

  ActionRegistry.registerAll(modeActions);
}

/**
 * Register AI-specific actions with slash command style
 */
export function registerAIActions(handlers: {
  aiGenerate?: () => void;
  aiExplain?: () => void;
  aiImprove?: () => void;
  aiFix?: () => void;
  aiDoc?: () => void;
  aiTest?: () => void;
  aiChat?: () => void;
}): void {
  const aiActions: ActionDefinition[] = [
    {
      id: 'ai.generate',
      label: '/generate - Generate Code',
      description: 'Generate code from selection or description',
      icon: 'autoAwesome',
      category: 'ai',
      shortcut: 'mod+shift+g',
      context: { requiresProject: true },
      handler: () => handlers.aiGenerate?.(),
      priority: 95,
    },
    {
      id: 'ai.explain',
      label: '/explain - Explain Selection',
      description: 'Get AI explanation of selected element',
      icon: 'lightbulb',
      category: 'ai',
      shortcut: 'mod+shift+e',
      context: { requiresSelection: true },
      handler: () => handlers.aiExplain?.(),
      priority: 94,
    },
    {
      id: 'ai.improve',
      label: '/improve - Suggest Improvements',
      description: 'Get AI suggestions for improvements',
      icon: 'trending_up',
      category: 'ai',
      shortcut: 'mod+shift+i',
      context: { requiresSelection: true },
      handler: () => handlers.aiImprove?.(),
      priority: 93,
    },
    {
      id: 'ai.fix',
      label: '/fix - Fix Issues',
      description: 'AI fix for identified issues',
      icon: 'build',
      category: 'ai',
      context: { requiresSelection: true },
      handler: () => handlers.aiFix?.(),
      priority: 92,
    },
    {
      id: 'ai.doc',
      label: '/doc - Generate Documentation',
      description: 'Generate documentation for selection',
      icon: 'description',
      category: 'ai',
      context: { requiresSelection: true },
      handler: () => handlers.aiDoc?.(),
      priority: 91,
    },
    {
      id: 'ai.test',
      label: '/test - Generate Tests',
      description: 'Generate test cases for selection',
      icon: 'science',
      category: 'ai',
      context: { requiresSelection: true },
      handler: () => handlers.aiTest?.(),
      priority: 90,
    },
    {
      id: 'ai.chat',
      label: 'Open AI Chat',
      description: 'Open full AI assistant panel',
      icon: 'chat',
      category: 'ai',
      shortcut: 'mod+j',
      context: {},
      handler: () => handlers.aiChat?.(),
      priority: 99,
    },
  ];

  ActionRegistry.registerAll(aiActions);
}

// ============================================================================
// React Hook
// ============================================================================

import {
  useSyncExternalStore,
  useCallback as useReactCallback,
  useRef,
  useEffect,
  useMemo,
} from 'react';

/**
 * React hook for using the Action Registry
 *
 * IMPORTANT: Avoids infinite loops by properly memoizing the snapshot.
 * The snapshot must be cached to prevent useSyncExternalStore from
 * triggering infinite updates.
 */
export function useActions(state: ActionState) {
  // Store current state in a ref to avoid dependency issues with useSyncExternalStore
  const stateRef = useRef(state);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  const subscribe = useReactCallback(
    (onStoreChange: () => void) => ActionRegistry.subscribe(onStoreChange),
    []
  );

  // Create a stable snapshot that doesn't change on every render
  // This prevents infinite loops in useSyncExternalStore
  const memoizedSnapshot = useMemo(
    () => ActionRegistry.getAvailable(stateRef.current),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [state] // Depend on state object identity
  );

  const getSnapshot = useReactCallback(
    () => memoizedSnapshot,
    [memoizedSnapshot]
  );

  const available = useSyncExternalStore(subscribe, getSnapshot);

  const execute = useReactCallback(
    (actionId: string, event?: KeyboardEvent | MouseEvent) => {
      return ActionRegistry.execute(actionId, state, event);
    },
    [state]
  );

  const isAvailable = useReactCallback(
    (actionId: string) => {
      const action = ActionRegistry.get(actionId);
      return action ? ActionRegistry.isAvailable(action, state) : false;
    },
    [state]
  );

  return {
    actions: available,
    grouped: ActionRegistry.getGrouped(state),
    execute,
    isAvailable,
    formatShortcut: ActionRegistry.formatShortcut.bind(ActionRegistry),
  };
}

export default ActionRegistry;
