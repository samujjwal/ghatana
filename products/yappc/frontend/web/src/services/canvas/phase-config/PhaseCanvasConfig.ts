/**
 * Phase Canvas Configuration
 *
 * Defines phase-aware canvas defaults including:
 * - Canvas mode per phase
 * - Visible tools per phase
 * - Default tool selection per phase
 * - Interaction constraints per phase
 *
 * @doc.type service
 * @doc.purpose Phase-aware canvas configuration
 * @doc.layer product
 */

import { LifecyclePhase } from '../../../types/lifecycle';

export type CanvasMode = 'design' | 'preview' | 'code' | 'validate';

export type CanvasTool = 
  | 'select'
  | 'hand'
  | 'zoom'
  | 'add-component'
  | 'add-edge'
  | 'delete'
  | 'duplicate'
  | 'undo'
  | 'redo'
  | 'align'
  | 'distribute'
  | 'group'
  | 'ungroup'
  | 'layer-up'
  | 'layer-down'
  | 'inspect'
  | 'validate'
  | 'preview'
  | 'code-view';

export interface PhaseCanvasConfig {
  /** Canvas mode for this phase */
  mode: CanvasMode;
  /** Visible tools in toolbar */
  visibleTools: CanvasTool[];
  /** Default selected tool */
  defaultTool?: CanvasTool;
  /** Whether editing is allowed */
  allowEditing: boolean;
  /** Whether adding components is allowed */
  allowAddComponent: boolean;
  /** Whether deleting is allowed */
  allowDelete: boolean;
  /** Whether edge manipulation is allowed */
  allowEdgeManipulation: boolean;
  /** Whether validation is enabled */
  enableValidation: boolean;
  /** Whether preview is enabled */
  enablePreview: boolean;
}

/**
 * Phase canvas configuration mapping
 */
const PHASE_CANVAS_CONFIG: Record<LifecyclePhase, PhaseCanvasConfig> = {
  [LifecyclePhase.INTENT]: {
    mode: 'design',
    visibleTools: ['select', 'hand', 'zoom', 'add-component', 'add-edge', 'undo', 'redo'],
    defaultTool: 'select',
    allowEditing: true,
    allowAddComponent: true,
    allowDelete: true,
    allowEdgeManipulation: true,
    enableValidation: false,
    enablePreview: false,
  },

  [LifecyclePhase.SHAPE]: {
    mode: 'design',
    visibleTools: [
      'select',
      'hand',
      'zoom',
      'add-component',
      'add-edge',
      'delete',
      'duplicate',
      'undo',
      'redo',
      'align',
      'distribute',
      'group',
      'ungroup',
      'layer-up',
      'layer-down',
    ],
    defaultTool: 'select',
    allowEditing: true,
    allowAddComponent: true,
    allowDelete: true,
    allowEdgeManipulation: true,
    enableValidation: true,
    enablePreview: true,
  },

  [LifecyclePhase.VALIDATE]: {
    mode: 'validate',
    visibleTools: ['select', 'hand', 'zoom', 'inspect', 'validate', 'preview'],
    defaultTool: 'inspect',
    allowEditing: false,
    allowAddComponent: false,
    allowDelete: false,
    allowEdgeManipulation: false,
    enableValidation: true,
    enablePreview: true,
  },

  [LifecyclePhase.GENERATE]: {
    mode: 'code',
    visibleTools: ['select', 'hand', 'zoom', 'code-view', 'preview'],
    defaultTool: 'code-view',
    allowEditing: false,
    allowAddComponent: false,
    allowDelete: false,
    allowEdgeManipulation: false,
    enableValidation: true,
    enablePreview: true,
  },

  [LifecyclePhase.RUN]: {
    mode: 'preview',
    visibleTools: ['hand', 'zoom', 'preview'],
    defaultTool: 'preview',
    allowEditing: false,
    allowAddComponent: false,
    allowDelete: false,
    allowEdgeManipulation: false,
    enableValidation: false,
    enablePreview: true,
  },

  [LifecyclePhase.OBSERVE]: {
    mode: 'preview',
    visibleTools: ['hand', 'zoom', 'preview', 'inspect'],
    defaultTool: 'preview',
    allowEditing: false,
    allowAddComponent: false,
    allowDelete: false,
    allowEdgeManipulation: false,
    enableValidation: false,
    enablePreview: true,
  },

  [LifecyclePhase.IMPROVE]: {
    mode: 'design',
    visibleTools: [
      'select',
      'hand',
      'zoom',
      'add-component',
      'add-edge',
      'delete',
      'duplicate',
      'undo',
      'redo',
      'validate',
      'preview',
    ],
    defaultTool: 'select',
    allowEditing: true,
    allowAddComponent: true,
    allowDelete: true,
    allowEdgeManipulation: true,
    enableValidation: true,
    enablePreview: true,
  },

  [LifecyclePhase.INSTITUTIONALIZE]: {
    mode: 'validate',
    visibleTools: ['select', 'hand', 'zoom', 'inspect', 'preview'],
    defaultTool: 'inspect',
    allowEditing: false,
    allowAddComponent: false,
    allowDelete: false,
    allowEdgeManipulation: false,
    enableValidation: false,
    enablePreview: true,
  },
};

/**
 * Get canvas configuration for a given phase
 */
export function getPhaseCanvasConfig(phase: LifecyclePhase): PhaseCanvasConfig {
  return PHASE_CANVAS_CONFIG[phase] || PHASE_CANVAS_CONFIG[LifecyclePhase.SHAPE];
}

/**
 * Check if a tool is visible in a given phase
 */
export function isToolVisibleInPhase(tool: CanvasTool, phase: LifecyclePhase): boolean {
  const config = getPhaseCanvasConfig(phase);
  return config.visibleTools.includes(tool);
}

/**
 * Check if editing is allowed in a given phase
 */
export function isEditingAllowedInPhase(phase: LifecyclePhase): boolean {
  const config = getPhaseCanvasConfig(phase);
  return config.allowEditing;
}

/**
 * Check if validation is enabled in a given phase
 */
export function isValidationEnabledInPhase(phase: LifecyclePhase): boolean {
  const config = getPhaseCanvasConfig(phase);
  return config.enableValidation;
}

/**
 * Check if preview is enabled in a given phase
 */
export function isPreviewEnabledInPhase(phase: LifecyclePhase): boolean {
  const config = getPhaseCanvasConfig(phase);
  return config.enablePreview;
}

/**
 * Get default tool for a given phase
 */
export function getDefaultToolForPhase(phase: LifecyclePhase): CanvasTool {
  const config = getPhaseCanvasConfig(phase);
  return config.defaultTool || 'select';
}

/**
 * Get canvas mode for a given phase
 */
export function getCanvasModeForPhase(phase: LifecyclePhase): CanvasMode {
  const config = getPhaseCanvasConfig(phase);
  return config.mode;
}

export default PHASE_CANVAS_CONFIG;
