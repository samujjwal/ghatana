/**
 * Phase Canvas Configuration Exports
 *
 * @doc.type module
 * @doc.purpose Phase canvas configuration exports
 * @doc.layer product
 */

export {
  getPhaseCanvasConfig,
  isToolVisibleInPhase,
  isEditingAllowedInPhase,
  isValidationEnabledInPhase,
  isPreviewEnabledInPhase,
  getDefaultToolForPhase,
  getCanvasModeForPhase,
} from './PhaseCanvasConfig';

export type { PhaseCanvasConfig, CanvasMode, CanvasTool } from './PhaseCanvasConfig';

export { default as PHASE_CANVAS_CONFIG } from './PhaseCanvasConfig';
