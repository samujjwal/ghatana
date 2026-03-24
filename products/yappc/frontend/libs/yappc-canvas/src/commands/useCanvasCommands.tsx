/**
 * Canvas Command Provider
 *
 * React hook and provider for canvas command integration.
 * Connects canvas commands to the global command palette.
 *
 * @doc.type hook
 * @doc.purpose Command system integration
 * @doc.layer core
 * @doc.pattern Provider
 */

import { useEffect, useMemo } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  ALL_CANVAS_COMMANDS,
  executeCanvasCommand,
  type CanvasCommandContext,
} from './canvas-commands';
import {
  canvasDocumentAtom,
  canvasSelectionAtom,
  canvasViewportAtom,
} from '../state/atoms';
import {
  chromeLeftRailVisibleAtom,
  chromeInspectorVisibleAtom,
  chromeOutlineVisibleAtom,
  chromeMinimapVisibleAtom,
  chromeCalmModeAtom,
} from '../state/chrome-atoms';

/**
 * Hook to register canvas commands with command palette
 *
 * Usage:
 * ```tsx
 * function MyCanvas() {
 *   useCanvasCommands();
 *   return <Canvas>...</Canvas>;
 * }
 * ```
 */
export function useCanvasCommands() {
  // Canvas state
  const [document, setDocument] = useAtom(canvasDocumentAtom);
  const [selection, setSelection] = useAtom(canvasSelectionAtom);
  const [viewport, setViewport] = useAtom(canvasViewportAtom);

  // Chrome state
  const setLeftRailVisible = useSetAtom(chromeLeftRailVisibleAtom);
  const setInspectorVisible = useSetAtom(chromeInspectorVisibleAtom);
  const setOutlineVisible = useSetAtom(chromeOutlineVisibleAtom);
  const setMinimapVisible = useSetAtom(chromeMinimapVisibleAtom);
  const setCalmMode = useSetAtom(chromeCalmModeAtom);

  // Build command context
  const commandContext = useMemo(
    (): CanvasCommandContext => ({
      selectedIds: selection.selectedIds || [],
      selectedType: selection.selectedType,
      viewport: viewport || { x: 0, y: 0, zoom: 1 },
      document: document || { frames: [], artifacts: [] },
      setState: {
        setSelection,
        setViewport,
        setDocument,
        setChromeVisibility: (panel: string, visible: boolean) => {
          switch (panel) {
            case 'leftRail':
              setLeftRailVisible(visible);
              break;
            case 'inspector':
              setInspectorVisible(visible);
              break;
            case 'outline':
              setOutlineVisible(visible);
              break;
            case 'minimap':
              setMinimapVisible(visible);
              break;
            case 'calmMode':
              setCalmMode(visible);
              break;
          }
        },
      },
    }),
    [
      selection,
      viewport,
      document,
      setSelection,
      setViewport,
      setDocument,
      setLeftRailVisible,
      setInspectorVisible,
      setOutlineVisible,
      setMinimapVisible,
      setCalmMode,
    ]
  );

  // Register keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Check if command palette is open (skip if it is)
      if ((e.target as HTMLElement)?.closest('[role="dialog"]')) {
        return;
      }

      // Find matching command by shortcut
      const matchingCommand = ALL_CANVAS_COMMANDS.find((cmd) => {
        if (!cmd.shortcut) return false;

        // Parse shortcut (e.g., "⌘⇧1" = Cmd+Shift+1)
        const hasCmd = cmd.shortcut.includes('⌘');
        const hasShift = cmd.shortcut.includes('⇧');
        const hasCtrl = cmd.shortcut.includes('⌃');
        const hasAlt = cmd.shortcut.includes('⌥');

        // Get key (last character)
        const key = cmd.shortcut.replace(/[⌘⇧⌃⌥]/g, '');

        return (
          (!hasCmd || e.metaKey || e.ctrlKey) &&
          (!hasShift || e.shiftKey) &&
          (!hasCtrl || e.ctrlKey) &&
          (!hasAlt || e.altKey) &&
          e.key.toLowerCase() === key.toLowerCase()
        );
      });

      if (matchingCommand) {
        e.preventDefault();
        executeCanvasCommand(matchingCommand.id, commandContext);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [commandContext]);

  // Return command execution function
  return {
    executeCommand: (commandId: string) =>
      executeCanvasCommand(commandId, commandContext),
    commands: ALL_CANVAS_COMMANDS,
    context: commandContext,
  };
}

/**
 * Hook to get canvas commands for command palette
 *
 * Usage with command palette:
 * ```tsx
 * function CommandPaletteIntegration() {
 *   const canvasCommands = useCanvasCommandsForPalette();
 *   return <CommandPalette commands={[...otherCommands, ...canvasCommands]} />;
 * }
 * ```
 */
export function useCanvasCommandsForPalette() {
  const { executeCommand, commands } = useCanvasCommands();

  // Transform canvas commands to command palette format
  return commands.map((cmd) => ({
    id: cmd.id,
    label: cmd.label,
    description: cmd.description,
    icon: cmd.icon,
    shortcut: cmd.shortcut,
    category: cmd.category,
    tags: cmd.tags || [],
    isAvailable: cmd.isAvailable ? cmd.isAvailable() : true,
    onSelect: () => executeCommand(cmd.id),
  }));
}

/**
 * Canvas command provider component
 *
 * Wraps canvas and automatically registers commands.
 *
 * Usage:
 * ```tsx
 * <CanvasCommandProvider>
 *   <Canvas>...</Canvas>
 * </CanvasCommandProvider>
 * ```
 */
export function CanvasCommandProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  useCanvasCommands();
  return <>{children}</>;
}
