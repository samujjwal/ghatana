/**
 * Canvas Command Definitions
 *
 * Defines all canvas-related commands for command palette integration.
 * Commands are registered with the global command system and can be
 * invoked via keyboard shortcuts or command palette.
 *
 * @doc.type commands
 * @doc.purpose Command palette integration
 * @doc.layer core
 * @doc.pattern Command
 */

import { LifecyclePhase } from '../types/lifecycle';

/**
 * Command category for canvas commands
 */
export const CANVAS_COMMAND_CATEGORY = 'canvas' as const;

/**
 * Canvas command category type
 */
export type CanvasCommandCategory = typeof CANVAS_COMMAND_CATEGORY;

/**
 * Canvas command definition
 */
export interface CanvasCommand {
  /** Unique command ID */
  id: string;
  /** Category for grouping */
  category: typeof CANVAS_COMMAND_CATEGORY;
  /** Display label */
  label: string;
  /** Command description */
  description: string;
  /** Keyboard shortcut (optional) */
  shortcut?: string;
  /** Icon emoji */
  icon: string;
  /** Whether command is available (can be dynamic) */
  isAvailable?: () => boolean;
  /** Command handler */
  execute: (context: CanvasCommandContext) => void | Promise<void>;
  /** Tags for search */
  tags?: string[];
}

/**
 * Context passed to command handlers
 */
export interface CanvasCommandContext {
  /** Selected element IDs */
  selectedIds: string[];
  /** Selected element type */
  selectedType?: 'frame' | 'artifact';
  /** Current viewport */
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };
  /** Canvas document */
  document: unknown;
  /** State setters */
  setState: {
    setSelection: (selection: unknown) => void;
    setViewport: (viewport: unknown) => void;
    setDocument: (document: unknown) => void;
    setChromeVisibility: (panel: string, visible: boolean) => void;
  };
}

/**
 * Frame creation commands
 */
export const FRAME_COMMANDS: CanvasCommand[] = [
  {
    id: 'canvas.frame.create.discover',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Create Discover Frame',
    description: 'Create a new frame for the Discovery phase',
    shortcut: '⌘⇧1',
    icon: '🔍',
    tags: ['frame', 'create', 'discover', 'discovery', 'research'],
    execute: async (context) => {
      const newFrame = {
        id: `frame-${Date.now()}`,
        phase: 'discover' as LifecyclePhase,
        name: 'Discovery',
        position: { x: 100, y: 100 },
        size: { width: 400, height: 300 },
      };
      // Add frame to document
      const updatedDoc = {
        ...context.document,
        frames: [...(context.document.frames || []), newFrame],
      };
      context.setState.setDocument(updatedDoc);
      context.setState.setSelection({
        selectedIds: [newFrame.id],
        selectedType: 'frame',
        anchorId: newFrame.id,
      });
    },
  },
  {
    id: 'canvas.frame.create.design',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Create Design Frame',
    description: 'Create a new frame for the Design phase',
    shortcut: '⌘⇧2',
    icon: '🎨',
    tags: ['frame', 'create', 'design', 'wireframe', 'mockup'],
    execute: async (context) => {
      const newFrame = {
        id: `frame-${Date.now()}`,
        phase: 'design' as LifecyclePhase,
        name: 'Design',
        position: { x: 600, y: 100 },
        size: { width: 400, height: 300 },
      };
      const updatedDoc = {
        ...context.document,
        frames: [...(context.document.frames || []), newFrame],
      };
      context.setState.setDocument(updatedDoc);
      context.setState.setSelection({
        selectedIds: [newFrame.id],
        selectedType: 'frame',
        anchorId: newFrame.id,
      });
    },
  },
  {
    id: 'canvas.frame.create.build',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Create Build Frame',
    description: 'Create a new frame for the Build phase',
    shortcut: '⌘⇧3',
    icon: '🔨',
    tags: ['frame', 'create', 'build', 'develop', 'implementation'],
    execute: async (context) => {
      const newFrame = {
        id: `frame-${Date.now()}`,
        phase: 'build' as LifecyclePhase,
        name: 'Build',
        position: { x: 1100, y: 100 },
        size: { width: 400, height: 300 },
      };
      const updatedDoc = {
        ...context.document,
        frames: [...(context.document.frames || []), newFrame],
      };
      context.setState.setDocument(updatedDoc);
      context.setState.setSelection({
        selectedIds: [newFrame.id],
        selectedType: 'frame',
        anchorId: newFrame.id,
      });
    },
  },
  {
    id: 'canvas.frame.create.test',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Create Test Frame',
    description: 'Create a new frame for the Test phase',
    shortcut: '⌘⇧4',
    icon: '🧪',
    tags: ['frame', 'create', 'test', 'qa', 'testing'],
    execute: async (context) => {
      const newFrame = {
        id: `frame-${Date.now()}`,
        phase: 'test' as LifecyclePhase,
        name: 'Test',
        position: { x: 100, y: 500 },
        size: { width: 400, height: 300 },
      };
      const updatedDoc = {
        ...context.document,
        frames: [...(context.document.frames || []), newFrame],
      };
      context.setState.setDocument(updatedDoc);
      context.setState.setSelection({
        selectedIds: [newFrame.id],
        selectedType: 'frame',
        anchorId: newFrame.id,
      });
    },
  },
  {
    id: 'canvas.frame.delete',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Delete Frame',
    description: 'Delete the selected frame',
    shortcut: '⌫',
    icon: '🗑️',
    tags: ['frame', 'delete', 'remove'],
    isAvailable: () => true, // Should check if frame is selected
    execute: async (context) => {
      if (
        context.selectedType !== 'frame' ||
        context.selectedIds.length === 0
      ) {
        return;
      }
      const updatedDoc = {
        ...context.document,
        frames: context.document.frames?.filter(
          (f: unknown) => !context.selectedIds.includes(f.id)
        ),
      };
      context.setState.setDocument(updatedDoc);
      context.setState.setSelection({
        selectedIds: [],
        selectedType: undefined,
      });
    },
  },
];

/**
 * Navigation commands
 */
export const NAVIGATION_COMMANDS: CanvasCommand[] = [
  {
    id: 'canvas.navigate.center',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Center Canvas',
    description: 'Center the viewport on all content',
    shortcut: '⌘0',
    icon: '🎯',
    tags: ['navigate', 'center', 'fit', 'view'],
    execute: async (context) => {
      // Calculate bounds of all frames
      const frames = context.document.frames || [];
      if (frames.length === 0) return;

      const bounds = frames.reduce(
        (acc: unknown, frame: unknown) => ({
          minX: Math.min(acc.minX, frame.position.x),
          minY: Math.min(acc.minY, frame.position.y),
          maxX: Math.max(acc.maxX, frame.position.x + frame.size.width),
          maxY: Math.max(acc.maxY, frame.position.y + frame.size.height),
        }),
        { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }
      );

      const centerX = (bounds.minX + bounds.maxX) / 2;
      const centerY = (bounds.minY + bounds.maxY) / 2;

      context.setState.setViewport({
        x: centerX,
        y: centerY,
        zoom: 1,
      });
    },
  },
  {
    id: 'canvas.navigate.zoomIn',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Zoom In',
    description: 'Zoom in on the canvas',
    shortcut: '⌘+',
    icon: '🔍',
    tags: ['zoom', 'in', 'magnify'],
    execute: async (context) => {
      const newZoom = Math.min(context.viewport.zoom * 1.2, 4);
      context.setState.setViewport({
        ...context.viewport,
        zoom: newZoom,
      });
    },
  },
  {
    id: 'canvas.navigate.zoomOut',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Zoom Out',
    description: 'Zoom out on the canvas',
    shortcut: '⌘-',
    icon: '🔍',
    tags: ['zoom', 'out', 'minimize'],
    execute: async (context) => {
      const newZoom = Math.max(context.viewport.zoom / 1.2, 0.1);
      context.setState.setViewport({
        ...context.viewport,
        zoom: newZoom,
      });
    },
  },
  {
    id: 'canvas.navigate.zoomReset',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Reset Zoom',
    description: 'Reset zoom to 100%',
    shortcut: '⌘0',
    icon: '↺',
    tags: ['zoom', 'reset', '100%'],
    execute: async (context) => {
      context.setState.setViewport({
        ...context.viewport,
        zoom: 1,
      });
    },
  },
  {
    id: 'canvas.navigate.focusSelection',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Focus Selection',
    description: 'Center viewport on selected elements',
    shortcut: '⌘F',
    icon: '🎯',
    tags: ['focus', 'selection', 'center'],
    isAvailable: () => true, // Should check if something is selected
    execute: async (context) => {
      if (context.selectedIds.length === 0) return;

      // Find selected frame or artifact
      const frames = context.document.frames || [];
      const selectedFrame = frames.find((f: unknown) =>
        context.selectedIds.includes(f.id)
      );

      if (selectedFrame) {
        const centerX = selectedFrame.position.x + selectedFrame.size.width / 2;
        const centerY =
          selectedFrame.position.y + selectedFrame.size.height / 2;

        context.setState.setViewport({
          x: centerX,
          y: centerY,
          zoom: 1,
        });
      }
    },
  },
];

/**
 * Panel toggle commands
 */
export const PANEL_COMMANDS: CanvasCommand[] = [
  {
    id: 'canvas.panel.toggleLeftRail',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Toggle Left Rail',
    description: 'Show or hide the left rail panel',
    shortcut: '⌘⇧L',
    icon: '📋',
    tags: ['panel', 'toggle', 'left', 'rail', 'palette'],
    execute: async (context) => {
      // Toggle handled by Chrome layout
      console.log('Toggle left rail');
    },
  },
  {
    id: 'canvas.panel.toggleInspector',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Toggle Inspector',
    description: 'Show or hide the inspector panel',
    shortcut: '⌘⇧I',
    icon: '🔍',
    tags: ['panel', 'toggle', 'inspector', 'properties'],
    execute: async (context) => {
      console.log('Toggle inspector');
    },
  },
  {
    id: 'canvas.panel.toggleOutline',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Toggle Outline',
    description: 'Show or hide the outline panel',
    shortcut: '⌘⇧O',
    icon: '🗂️',
    tags: ['panel', 'toggle', 'outline', 'navigator', 'tree'],
    execute: async (context) => {
      console.log('Toggle outline');
    },
  },
  {
    id: 'canvas.panel.toggleMinimap',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Toggle Minimap',
    description: 'Show or hide the minimap',
    shortcut: '⌘⇧M',
    icon: '🗺️',
    tags: ['panel', 'toggle', 'minimap', 'navigation'],
    execute: async (context) => {
      console.log('Toggle minimap');
    },
  },
  {
    id: 'canvas.panel.toggleCalmMode',
    category: CANVAS_COMMAND_CATEGORY,
    label: 'Toggle Calm Mode',
    description: 'Toggle calm mode (hide all chrome)',
    shortcut: '⌘⇧C',
    icon: '🌙',
    tags: ['panel', 'calm', 'mode', 'zen', 'focus'],
    execute: async (context) => {
      console.log('Toggle calm mode');
    },
  },
];

/**
 * All canvas commands
 */
export const ALL_CANVAS_COMMANDS: CanvasCommand[] = [
  ...FRAME_COMMANDS,
  ...NAVIGATION_COMMANDS,
  ...PANEL_COMMANDS,
];

/**
 * Get command by ID
 */
export function getCanvasCommand(id: string): CanvasCommand | undefined {
  return ALL_CANVAS_COMMANDS.find((cmd) => cmd.id === id);
}

/**
 * Search commands by query
 */
export function searchCanvasCommands(query: string): CanvasCommand[] {
  const lowerQuery = query.toLowerCase();
  return ALL_CANVAS_COMMANDS.filter((cmd) => {
    return (
      cmd.label.toLowerCase().includes(lowerQuery) ||
      cmd.description.toLowerCase().includes(lowerQuery) ||
      cmd.tags?.some((tag) => tag.includes(lowerQuery))
    );
  });
}

/**
 * Get commands by category
 */
export function getCommandsByCategory(category: string): CanvasCommand[] {
  return ALL_CANVAS_COMMANDS.filter((cmd) => cmd.category === category);
}

/**
 * Execute command by ID
 */
export async function executeCanvasCommand(
  id: string,
  context: CanvasCommandContext
): Promise<void> {
  const command = getCanvasCommand(id);
  if (!command) {
    throw new Error(`Command not found: ${id}`);
  }

  if (command.isAvailable && !command.isAvailable()) {
    throw new Error(`Command not available: ${id}`);
  }

  await command.execute(context);
}
