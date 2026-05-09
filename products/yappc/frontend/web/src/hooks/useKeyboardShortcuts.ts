/**
 * useKeyboardShortcuts - Keyboard Shortcut System
 *
 * @doc.type hook
 * @doc.purpose Global keyboard shortcut management
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useEffect, useCallback } from 'react';
import type { Tool } from '../state/atoms/unifiedCanvasAtom';
import { translate } from '@/i18n/messages';

export interface ShortcutDefinition {
  key: string;
  ctrl?: boolean;
  meta?: boolean; // Cmd on Mac, Windows key on Windows
  shift?: boolean;
  alt?: boolean;
  description: string;
  action: () => void;
  preventDefault?: boolean;
}

export interface UseKeyboardShortcutsProps {
  // Tool shortcuts
  onToolChange?: (tool: Tool) => void;

  // Edit shortcuts
  onUndo?: () => void;
  onRedo?: () => void;
  onCopy?: () => void;
  onPaste?: () => void;
  onDuplicate?: () => void;
  onDelete?: () => void;
  onSelectAll?: () => void;

  // View shortcuts
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onResetZoom?: () => void;
  onZoomToFit?: () => void;

  // Alignment shortcuts
  onAlignLeft?: () => void;
  onAlignCenter?: () => void;
  onAlignRight?: () => void;
  onAlignTop?: () => void;
  onAlignMiddle?: () => void;
  onAlignBottom?: () => void;

  // Layer shortcuts
  onBringForward?: () => void;
  onSendBackward?: () => void;
  onBringToFront?: () => void;
  onSendToBack?: () => void;

  // Group shortcuts
  onGroup?: () => void;
  onUngroup?: () => void;

  // Navigation shortcuts
  onZoomIntoNode?: () => void;
  onZoomOutToParent?: () => void;

  // AI shortcuts
  onOpenAI?: () => void;

  // Save shortcut
  onSave?: () => void;

  // Search
  onSearch?: () => void;

  // Enabled flag
  enabled?: boolean;
}

export function useKeyboardShortcuts(props: UseKeyboardShortcutsProps = {}) {
  const { enabled = true } = props;

  // Build shortcuts map
  const shortcuts = useCallback((): ShortcutDefinition[] => {
    const isMac =
      typeof window !== 'undefined' &&
      /Mac|iPhone|iPad|iPod/.test(navigator.platform);
    const modKey = isMac ? 'meta' : 'ctrl';

    return [
      // Tools
      {
        key: 'v',
        description: translate('shortcut.selectTool'),
        action: () => props.onToolChange?.('select'),
      },
      {
        key: 'h',
        description: translate('shortcut.panTool'),
        action: () => props.onToolChange?.('pan'),
      },
      {
        key: 'p',
        description: translate('shortcut.drawTool'),
        action: () => props.onToolChange?.('draw'),
      },
      {
        key: 't',
        description: translate('shortcut.textTool'),
        action: () => props.onToolChange?.('text'),
      },
      {
        key: 'c',
        description: translate('shortcut.codeTool'),
        action: () => props.onToolChange?.('code'),
      },
      {
        key: 'n',
        description: translate('shortcut.stickyNote'),
        action: () => props.onToolChange?.('sticky'),
      },
      {
        key: 'r',
        description: translate('shortcut.rectangleTool'),
        action: () => props.onToolChange?.('rectangle'),
      },
      {
        key: 'o',
        description: translate('shortcut.ellipseTool'),
        action: () => props.onToolChange?.('ellipse'),
      },
      {
        key: 'l',
        description: translate('shortcut.lineTool'),
        action: () => props.onToolChange?.('line'),
      },
      {
        key: 'a',
        description: translate('shortcut.arrowTool'),
        action: () => props.onToolChange?.('arrow'),
      },

      // Edit
      {
        key: 'z',
        [modKey]: true,
        description: translate('shortcut.undo'),
        action: () => props.onUndo?.(),
        preventDefault: true,
      },
      {
        key: 'z',
        [modKey]: true,
        shift: true,
        description: translate('shortcut.redo'),
        action: () => props.onRedo?.(),
        preventDefault: true,
      },
      {
        key: 'c',
        [modKey]: true,
        description: translate('shortcut.copy'),
        action: () => props.onCopy?.(),
        preventDefault: true,
      },
      {
        key: 'v',
        [modKey]: true,
        description: translate('shortcut.paste'),
        action: () => props.onPaste?.(),
        preventDefault: true,
      },
      {
        key: 'd',
        [modKey]: true,
        description: translate('shortcut.duplicate'),
        action: () => props.onDuplicate?.(),
        preventDefault: true,
      },
      {
        key: 'Delete',
        description: translate('shortcut.delete'),
        action: () => props.onDelete?.(),
      },
      {
        key: 'Backspace',
        description: translate('shortcut.delete'),
        action: () => props.onDelete?.(),
      },
      {
        key: 'a',
        [modKey]: true,
        description: translate('shortcut.selectAll'),
        action: () => props.onSelectAll?.(),
        preventDefault: true,
      },

      // View
      {
        key: '+',
        [modKey]: true,
        description: translate('shortcut.zoomIn'),
        action: () => props.onZoomIn?.(),
        preventDefault: true,
      },
      {
        key: '=',
        [modKey]: true,
        description: translate('shortcut.zoomIn'),
        action: () => props.onZoomIn?.(),
        preventDefault: true,
      },
      {
        key: '-',
        [modKey]: true,
        description: translate('shortcut.zoomOut'),
        action: () => props.onZoomOut?.(),
        preventDefault: true,
      },
      {
        key: '0',
        [modKey]: true,
        description: translate('shortcut.zoomToFit'),
        action: () => props.onZoomToFit?.(),
        preventDefault: true,
      },
      {
        key: '1',
        [modKey]: true,
        description: translate('shortcut.resetZoom'),
        action: () => props.onResetZoom?.(),
        preventDefault: true,
      },

      // Layer
      {
        key: ']',
        description: translate('shortcut.bringForward'),
        action: () => props.onBringForward?.(),
      },
      {
        key: '[',
        description: translate('shortcut.sendBackward'),
        action: () => props.onSendBackward?.(),
      },
      {
        key: ']',
        [modKey]: true,
        description: translate('shortcut.bringToFront'),
        action: () => props.onBringToFront?.(),
        preventDefault: true,
      },
      {
        key: '[',
        [modKey]: true,
        description: translate('shortcut.sendToBack'),
        action: () => props.onSendToBack?.(),
        preventDefault: true,
      },

      // Group
      {
        key: 'g',
        [modKey]: true,
        description: translate('shortcut.group'),
        action: () => props.onGroup?.(),
        preventDefault: true,
      },
      {
        key: 'g',
        [modKey]: true,
        shift: true,
        description: translate('shortcut.ungroup'),
        action: () => props.onUngroup?.(),
        preventDefault: true,
      },

      // Navigation
      {
        key: 'ArrowDown',
        [modKey]: true,
        description: translate('shortcut.zoomIntoNode'),
        action: () => props.onZoomIntoNode?.(),
        preventDefault: true,
      },
      {
        key: 'ArrowUp',
        [modKey]: true,
        description: translate('shortcut.zoomOutToParent'),
        action: () => props.onZoomOutToParent?.(),
        preventDefault: true,
      },

      // Guided Assistant
      {
        key: 'k',
        [modKey]: true,
        description: translate('shortcut.openGuidedAssistant'),
        action: () => props.onOpenAI?.(),
        preventDefault: true,
      },

      // Save
      {
        key: 's',
        [modKey]: true,
        description: translate('shortcut.save'),
        action: () => props.onSave?.(),
        preventDefault: true,
      },

      // Search
      {
        key: 'f',
        [modKey]: true,
        description: translate('shortcut.search'),
        action: () => props.onSearch?.(),
        preventDefault: true,
      },
    ].filter((s) => s.action !== undefined);
  }, [props]);

  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      const activeShortcuts = shortcuts();

      // Check if we're in an input field
      const target = event.target as HTMLElement;
      const isInputField =
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.contentEditable === 'true';

      // Allow certain shortcuts even in input fields
      const allowInInputs = ['s', 'z', 'k', 'f'];

      for (const shortcut of activeShortcuts) {
        const keyMatches =
          event.key.toLowerCase() === shortcut.key.toLowerCase() ||
          event.code === shortcut.key;

        const ctrlMatches = shortcut.ctrl ? event.ctrlKey : !event.ctrlKey;
        const metaMatches = shortcut.meta ? event.metaKey : !event.metaKey;
        const shiftMatches = shortcut.shift ? event.shiftKey : !event.shiftKey;
        const altMatches = shortcut.alt ? event.altKey : !event.altKey;

        if (
          keyMatches &&
          ctrlMatches &&
          metaMatches &&
          shiftMatches &&
          altMatches
        ) {
          // Skip if in input field and shortcut not allowed
          if (
            isInputField &&
            !allowInInputs.includes(shortcut.key.toLowerCase())
          ) {
            continue;
          }

          if (shortcut.preventDefault) {
            event.preventDefault();
          }

          shortcut.action();
          break;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [enabled, shortcuts]);

  // Return shortcuts for display in help panel
  return {
    shortcuts: shortcuts(),
  };
}
