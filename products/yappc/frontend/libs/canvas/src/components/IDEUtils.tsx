/**
 * IDE Utils - KeyboardShortcutsManager, LoadingStates Bridge
 * 
 * @deprecated Use ShortcutsManager, LoadingOverlay from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect, useCallback, useState } from 'react';

// ============================================================================
// KeyboardShortcutsManager
// ============================================================================

export interface KeyboardShortcutsManagerProps {
  /** Keyboard shortcuts configuration */
  shortcuts: KeyboardShortcut[];
  /** Enable shortcuts */
  enabled?: boolean;
  /** Target element to attach listeners to */
  target?: HTMLElement | null;
  /** Additional CSS classes */
  className?: string;
  /** Show help dialog */
  showHelp?: boolean;
  /** Help toggle handler */
  onToggleHelp?: () => void;
}

export interface KeyboardShortcut {
  id: string;
  key: string;
  modifiers?: ('ctrl' | 'alt' | 'shift' | 'meta')[];
  description: string;
  action: () => void;
  global?: boolean;
}

/**
 * KeyboardShortcutsManager - Bridge to Canvas Keyboard System
 */
export const KeyboardShortcutsManager: React.FC<KeyboardShortcutsManagerProps> = ({
  shortcuts,
  enabled = true,
  target,
  className,
  showHelp = false,
  onToggleHelp,
}) => {
  const [helpVisible, setHelpVisible] = useState(showHelp);

  useEffect(() => {
    console.warn(
      '[MIGRATION] KeyboardShortcutsManager from @ghatana/yappc-ide is deprecated. ' +
      'Use ShortcutsManager or keyboard hooks from @ghatana/yappc-canvas.'
    );
  }, []);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (!enabled) return;

    shortcuts.forEach(shortcut => {
      const hasCtrl = shortcut.modifiers?.includes('ctrl') ?? false;
      const hasAlt = shortcut.modifiers?.includes('alt') ?? false;
      const hasShift = shortcut.modifiers?.includes('shift') ?? false;
      const hasMeta = shortcut.modifiers?.includes('meta') ?? false;

      const matches = 
        e.key.toLowerCase() === shortcut.key.toLowerCase() &&
        e.ctrlKey === hasCtrl &&
        e.altKey === hasAlt &&
        e.shiftKey === hasShift &&
        e.metaKey === hasMeta;

      if (matches) {
        e.preventDefault();
        shortcut.action();
      }
    });
  }, [shortcuts, enabled]);

  useEffect(() => {
    const element = target || window;
    element.addEventListener('keydown', handleKeyDown as EventListener);
    return () => {
      element.removeEventListener('keydown', handleKeyDown as EventListener);
    };
  }, [handleKeyDown, target]);

  return (
    <div className={`keyboard-shortcuts-manager ${className || ''}`}>
      <button className="shortcuts-help-button" onClick={() => {
        setHelpVisible(!helpVisible);
        onToggleHelp?.();
      }}>
        ⌨️ Shortcuts
      </button>

      {helpVisible && (
        <div className="shortcuts-help-dialog">
          <h3>Keyboard Shortcuts</h3>
          <div className="shortcuts-list">
            {shortcuts.map(shortcut => (
              <div key={shortcut.id} className="shortcut-item">
                <span className="shortcut-keys">
                  {shortcut.modifiers?.map(m => m + '+').join('')}
                  {shortcut.key}
                </span>
                <span className="shortcut-description">{shortcut.description}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

// ============================================================================
// LoadingStates
// ============================================================================

export interface LoadingStatesProps {
  /** Loading state */
  isLoading: boolean;
  /** Loading message */
  message?: string;
  /** Progress percentage (0-100) */
  progress?: number;
  /** Show progress bar */
  showProgress?: boolean;
  /** Allow cancel */
  cancellable?: boolean;
  /** Cancel handler */
  onCancel?: () => void;
  /** Loading type */
  type?: 'spinner' | 'skeleton' | 'progress';
  /** Additional CSS classes */
  className?: string;
}

/**
 * LoadingStates - Bridge to Canvas Loading System
 */
export const LoadingStates: React.FC<LoadingStatesProps> = ({
  isLoading,
  message = 'Loading...',
  progress,
  showProgress = false,
  cancellable = false,
  onCancel,
  type = 'spinner',
  className,
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] LoadingStates from @ghatana/yappc-ide is deprecated. ' +
      'Use LoadingOverlay or AutosaveIndicator from @ghatana/yappc-canvas.'
    );
  }, []);

  if (!isLoading) return null;

  return (
    <div className={`loading-states ${className || ''}`} data-type={type}>
      <div className="loading-overlay">
        {type === 'spinner' && (
          <div className="loading-spinner">
            <div className="spinner-icon">⟳</div>
          </div>
        )}

        {type === 'skeleton' && (
          <div className="loading-skeleton">
            <div className="skeleton-line" style={{ width: '80%' }} />
            <div className="skeleton-line" style={{ width: '60%' }} />
            <div className="skeleton-line" style={{ width: '70%' }} />
          </div>
        )}

        {type === 'progress' && showProgress && progress !== undefined && (
          <div className="loading-progress">
            <div className="progress-bar">
              <div 
                className="progress-fill" 
                style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
              />
            </div>
            <span className="progress-text">{Math.round(progress)}%</span>
          </div>
        )}

        <div className="loading-message">{message}</div>

        {cancellable && (
          <button className="loading-cancel" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </div>
  );
};

// Re-export with Canvas prefix
export { KeyboardShortcutsManager as ShortcutsManager };
export { LoadingStates as LoadingOverlay };
