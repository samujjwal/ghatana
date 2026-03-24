/**
 * Command Palette Integration (Epic 9)
 *
 * Provides keyboard-first command system for canvas operations.
 * Supports command search, keyboard shortcuts, and palette integration.
 *
 * @doc.type barrel-export
 * @doc.purpose Command system public API
 * @doc.layer product
 * @doc.pattern Facade
 */

export {
  ALL_CANVAS_COMMANDS,
  FRAME_COMMANDS,
  NAVIGATION_COMMANDS,
  PANEL_COMMANDS,
  executeCanvasCommand,
  searchCanvasCommands,
  getCommandsByCategory,
  getCanvasCommand,
  type CanvasCommand,
  type CanvasCommandContext,
  type CanvasCommandCategory,
} from './canvas-commands';

export {
  useCanvasCommands,
  useCanvasCommandsForPalette,
  CanvasCommandProvider,
} from './useCanvasCommands';
