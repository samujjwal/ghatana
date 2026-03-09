import { useEffect, useCallback } from 'react';

// Import from shared library
import type { SketchTool } from '@ghatana/yappc-canvas/sketch';

/**
 *
 */
interface UseSketchKeyboardParams {
  enabled?: boolean;
  onToolChange: (tool: SketchTool) => void;
}

export const useSketchKeyboard = ({
  enabled = true,
  onToolChange,
}: UseSketchKeyboardParams): void => {
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (!enabled) {
        return;
      }

      // Ignore if typing in input/textarea
      const target = event.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        return;
      }

      const key = event.key.toLowerCase();

      // Tool shortcuts
      switch (key) {
        case 'v':
          onToolChange('select');
          break;
        case 'p':
          onToolChange('pen');
          break;
        case 'e':
          onToolChange('eraser');
          break;
        case 'r':
          onToolChange('rectangle');
          break;
        case 'o':
          onToolChange('ellipse');
          break;
        case 's':
          if (!event.metaKey && !event.ctrlKey) {
            // Only if not Cmd/Ctrl+S (save)
            onToolChange('sticky');
          }
          break;
        default:
          break;
      }
    },
    [enabled, onToolChange],
  );

  useEffect(() => {
    if (!enabled) {
      return;
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, handleKeyDown]);
};
