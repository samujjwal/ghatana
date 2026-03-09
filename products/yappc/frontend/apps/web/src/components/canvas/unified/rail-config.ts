/**
 * Left Rail Configuration Registry
 *
 * Centralized configuration for context-aware panel visibility and content filtering.
 * Implements multi-dimensional filtering: Mode × Role × Phase
 *
 * @doc.type configuration
 * @doc.purpose Canvas left rail configuration and rules
 * @doc.layer components
 * @doc.pattern Registry Pattern
 */

import type {
  RailTabId,
  ModeRailConfig,
  AssetCategory,
  AssetCategoryMeta,
  VisibilityRule,
} from './UnifiedLeftRail.types';
import type { CanvasMode } from '../../../types/canvasMode';
import type { LifecyclePhase } from '../../../types/lifecycle';

// ============================================================================
// Mode-Specific Panel Configurations
// ============================================================================

/**
 * Defines which panels are visible and prioritized for each canvas mode
 */
export const MODE_RAIL_CONFIG: Record<CanvasMode, ModeRailConfig> = {
  // Brainstorming & Ideation
  brainstorm: {
    panels: ['assets', 'favorites', 'ai', 'history'],
    defaultPanel: 'assets',
    featuredCategories: ['stickers', 'mindmap', 'basic'],
    panelSettings: {
      assets: { layout: 'grid', showSearch: true },
    },
  },

  diagram: {
    panels: ['assets', 'layers', 'favorites', 'history'],
    defaultPanel: 'assets',
    featuredCategories: ['flowchart', 'uml', 'basic', 'charts'],
  },

  // Design & UX
  design: {
    panels: ['assets', 'layers', 'components', 'favorites', 'ai', 'history'],
    defaultPanel: 'assets',
    featuredCategories: ['wireframe', 'icons', 'basic', 'charts'],
  },

  // Architecture & Infrastructure
  architecture: {
    panels: [
      'assets',
      'infrastructure',
      'layers',
      'data',
      'components',
      'history',
    ],
    defaultPanel: 'assets',
    featuredCategories: [
      'cloud-aws',
      'cloud-azure',
      'cloud-gcp',
      'uml',
      'data',
    ],
  },

  // Development
  code: {
    panels: ['files', 'components', 'layers', 'data', 'favorites', 'history'],
    defaultPanel: 'files',
    featuredCategories: ['code', 'data', 'basic'],
  },

  // Testing & Quality
  test: {
    panels: ['assets', 'layers', 'data', 'history'],
    defaultPanel: 'layers',
    featuredCategories: ['flowchart', 'basic', 'icons'],
  },

  // Monitoring & Observability
  observe: {
    panels: ['assets', 'infrastructure', 'layers', 'data', 'history'],
    defaultPanel: 'infrastructure',
    featuredCategories: [
      'charts',
      'cloud-aws',
      'cloud-azure',
      'cloud-gcp',
      'icons',
    ],
  },

  // Deployment
  deploy: {
    panels: ['infrastructure', 'layers', 'history'],
    defaultPanel: 'infrastructure',
    featuredCategories: ['cloud-aws', 'cloud-azure', 'cloud-gcp', 'flowchart'],
  },

  // Planning
  plan: {
    panels: ['assets', 'layers', 'favorites', 'ai', 'history'],
    defaultPanel: 'assets',
    featuredCategories: ['flowchart', 'basic', 'icons', 'mindmap'],
  },
};

// ============================================================================
// Role-Based Content Filters
// ============================================================================

/**
 * Maps roles to their preferred asset categories
 */
export const ROLE_ASSET_PREFERENCES: Record<string, AssetCategory[]> = {
  Brainstormer: ['stickers', 'mindmap', 'basic', 'icons'],
  Diagrammer: ['flowchart', 'uml', 'basic', 'charts'],
  Designer: ['wireframe', 'icons', 'basic', 'charts'],
  Architect: ['cloud-aws', 'cloud-azure', 'cloud-gcp', 'uml', 'data'],
  Developer: ['code', 'data', 'flowchart', 'basic'],
  'QA Engineer': ['flowchart', 'basic', 'icons'],
  Observer: ['charts', 'cloud-aws', 'cloud-azure', 'cloud-gcp', 'icons'],
  DevOps: ['cloud-aws', 'cloud-azure', 'cloud-gcp', 'flowchart'],
  'Product Owner': ['flowchart', 'basic', 'icons', 'mindmap'],
};

// ============================================================================
// Phase-Based Visibility Rules
// ============================================================================

/**
 * Defines which panels are most relevant during each lifecycle phase
 */
export const PHASE_PANEL_PRIORITY: Record<LifecyclePhase, RailTabId[]> = {
  INTENT: ['assets', 'ai', 'favorites'],
  SHAPE: ['assets', 'layers', 'components', 'ai'],
  BUILD: ['files', 'components', 'data', 'layers'],
  RUN: ['infrastructure', 'data', 'layers'],
  IMPROVE: ['assets', 'layers', 'data', 'history'],
};

// ============================================================================
// Asset Category Metadata
// ============================================================================

/**
 * Complete metadata for all asset categories
 */
export const ASSET_CATEGORY_META: Record<AssetCategory, AssetCategoryMeta> = {
  basic: {
    id: 'basic',
    label: 'Basic Shapes',
    icon: '▭',
    description: 'Rectangles, circles, lines, and arrows',
    defaultExpanded: true,
  },

  flowchart: {
    id: 'flowchart',
    label: 'Flowcharts',
    icon: '⟁',
    description: 'Process diagrams and decision flows',
    visibility: {
      modes: ['diagram', 'plan', 'test', 'design'],
    },
  },

  uml: {
    id: 'uml',
    label: 'UML Diagrams',
    icon: '◇',
    description: 'Class, sequence, and state diagrams',
    visibility: {
      modes: ['architecture', 'diagram', 'code'],
      roles: ['Architect', 'Developer', 'Diagrammer'],
    },
  },

  wireframe: {
    id: 'wireframe',
    label: 'UI Components',
    icon: '⌗',
    description: 'Wireframe elements and UI patterns',
    visibility: {
      modes: ['design', 'brainstorm'],
      roles: ['Designer', 'Product Owner'],
    },
  },

  'cloud-aws': {
    id: 'cloud-aws',
    label: 'AWS Services',
    icon: '☁️',
    description: 'Amazon Web Services icons',
    visibility: {
      modes: ['architecture', 'deploy', 'observe'],
      roles: ['Architect', 'DevOps', 'Observer'],
    },
  },

  'cloud-azure': {
    id: 'cloud-azure',
    label: 'Azure Services',
    icon: '⚡',
    description: 'Microsoft Azure icons',
    visibility: {
      modes: ['architecture', 'deploy', 'observe'],
      roles: ['Architect', 'DevOps', 'Observer'],
    },
  },

  'cloud-gcp': {
    id: 'cloud-gcp',
    label: 'GCP Services',
    icon: '🔷',
    description: 'Google Cloud Platform icons',
    visibility: {
      modes: ['architecture', 'deploy', 'observe'],
      roles: ['Architect', 'DevOps', 'Observer'],
    },
  },

  icons: {
    id: 'icons',
    label: 'Icons',
    icon: '★',
    description: 'General purpose icons and symbols',
    defaultExpanded: true,
  },

  stickers: {
    id: 'stickers',
    label: 'Stickers',
    icon: '📝',
    description: 'Sticky notes and annotations',
    visibility: {
      modes: ['brainstorm', 'design', 'plan'],
    },
  },

  charts: {
    id: 'charts',
    label: 'Charts & Graphs',
    icon: '📊',
    description: 'Data visualization components',
    visibility: {
      modes: ['observe', 'design', 'diagram'],
    },
  },

  mindmap: {
    id: 'mindmap',
    label: 'Mind Maps',
    icon: '🧠',
    description: 'Thought bubbles and connections',
    visibility: {
      modes: ['brainstorm', 'plan'],
    },
  },

  code: {
    id: 'code',
    label: 'Code Blocks',
    icon: '💻',
    description: 'Code snippets and terminals',
    visibility: {
      modes: ['code', 'architecture'],
      roles: ['Developer', 'Architect'],
    },
  },

  data: {
    id: 'data',
    label: 'Data & APIs',
    icon: '🗄️',
    description: 'Database and API shapes',
    visibility: {
      modes: ['architecture', 'code', 'observe'],
      roles: ['Architect', 'Developer', 'DevOps'],
    },
  },
};

// ============================================================================
// Panel Visibility Rules
// ============================================================================

/**
 * Defines complex visibility rules for panels
 */
export const PANEL_VISIBILITY_RULES: Record<RailTabId, VisibilityRule> = {
  assets: {
    // Always visible
  },

  layers: {
    // Always visible
  },

  components: {
    phases: ['SHAPE', 'BUILD'],
  },

  infrastructure: {
    modes: ['architecture', 'deploy', 'observe'],
    roles: ['Architect', 'DevOps', 'Observer'],
  },

  history: {
    // Always visible
  },

  files: {
    modes: ['code'],
    phases: ['BUILD'],
  },

  data: {
    modes: ['architecture', 'code', 'observe'],
  },

  ai: {
    // Always visible (but content varies by context)
  },

  favorites: {
    // Always visible
  },

  team: {
    condition: (context) => {
      // Show only if collaborative features are enabled
      // (would check a feature flag here)
      return false; // Disabled for MVP
    },
  },
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Checks if a visibility rule matches the current context
 */
export function matchesVisibilityRule(
  rule: VisibilityRule | undefined,
  context: {
    mode: CanvasMode;
    role?: string;
    phase?: LifecyclePhase;
  }
): boolean {
  if (!rule) return true;

  // Check mode filter
  if (rule.modes && !rule.modes.includes(context.mode)) {
    return false;
  }

  // Check role filter
  if (rule.roles && context.role && !rule.roles.includes(context.role)) {
    return false;
  }

  // Check phase filter
  if (rule.phases && context.phase && !rule.phases.includes(context.phase)) {
    return false;
  }

  // Check custom condition
  if (rule.condition && !rule.condition(context as unknown)) {
    return false;
  }

  return true;
}

/**
 * Gets filtered asset categories for current context
 */
export function getVisibleAssetCategories(context: {
  mode: CanvasMode;
  role?: string;
  phase?: LifecyclePhase;
}): AssetCategory[] {
  const allCategories = Object.values(ASSET_CATEGORY_META);

  return allCategories
    .filter((cat) => matchesVisibilityRule(cat.visibility, context))
    .map((cat) => cat.id);
}

/**
 * Gets prioritized categories based on mode and role
 */
export function getPrioritizedCategories(
  mode: CanvasMode,
  role?: string
): AssetCategory[] {
  const modeConfig = MODE_RAIL_CONFIG[mode];
  const featured = modeConfig.featuredCategories || [];

  if (role && ROLE_ASSET_PREFERENCES[role]) {
    // Merge mode preferences with role preferences
    const rolePrefs = ROLE_ASSET_PREFERENCES[role];
    return [...new Set([...featured, ...rolePrefs])];
  }

  return featured;
}
