/**
 * Context Bar
 *
 * Floating toolbar that appears on selection.
 * Provides contextual actions based on selected elements.
 *
 * Behaviors:
 * - Auto-shown when artifacts/frames are selected
 * - Hidden with Escape key
 * - Positioned near selection centroid
 * - Adapts actions based on selection type
 *
 * @doc.type component
 * @doc.purpose Contextual actions toolbar
 * @doc.layer core
 * @doc.pattern Toolbar
 */

import React, { useMemo } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { canvasSelectionAtom } from '../state/atoms';
import { chromeContextBarVisibleAtom } from '../state/chrome-atoms';

interface ContextBarAction {
  id: string;
  label: string;
  icon: string;
  shortcut?: string;
  disabled?: boolean;
  separator?: 'before' | 'after';
  onAction: () => void;
}

export interface ContextBarProps {
  /** Custom actions to add */
  customActions?: ContextBarAction[];
  /** Callback when an action is triggered */
  onAction?: (actionId: string) => void;
}

/**
 * Get actions for frames
 */
function getFrameActions(selection: unknown): ContextBarAction[] {
  return [
    {
      id: 'frame-add-artifact',
      label: 'Add Artifact',
      icon: '➕',
      shortcut: 'A',
      onAction: () => console.log('Add artifact'),
    },
    {
      id: 'frame-change-phase',
      label: 'Change Phase',
      icon: '🔄',
      shortcut: 'P',
      onAction: () => console.log('Change phase'),
    },
    {
      id: 'frame-collapse',
      label: 'Collapse',
      icon: '▲',
      shortcut: 'C',
      onAction: () => console.log('Collapse frame'),
      separator: 'after',
    },
    {
      id: 'frame-duplicate',
      label: 'Duplicate',
      icon: '📋',
      shortcut: '⌘D',
      onAction: () => console.log('Duplicate frame'),
    },
    {
      id: 'frame-delete',
      label: 'Delete',
      icon: '🗑️',
      shortcut: 'Delete',
      onAction: () => console.log('Delete frame'),
    },
  ];
}

/**
 * Get actions for artifacts
 */
function getArtifactActions(selection: unknown): ContextBarAction[] {
  const count = selection.selectedIds?.length || 0;

  return [
    {
      id: 'artifact-edit',
      label: 'Edit',
      icon: '✏️',
      shortcut: 'E',
      disabled: count > 1,
      onAction: () => console.log('Edit artifact'),
    },
    {
      id: 'artifact-move-to-frame',
      label: 'Move to Frame',
      icon: '📦',
      shortcut: 'M',
      onAction: () => console.log('Move to frame'),
      separator: 'after',
    },
    {
      id: 'artifact-align-left',
      label: 'Align Left',
      icon: '⬅️',
      disabled: count < 2,
      onAction: () => console.log('Align left'),
    },
    {
      id: 'artifact-align-center',
      label: 'Align Center',
      icon: '↔️',
      disabled: count < 2,
      onAction: () => console.log('Align center'),
    },
    {
      id: 'artifact-align-right',
      label: 'Align Right',
      icon: '➡️',
      disabled: count < 2,
      onAction: () => console.log('Align right'),
      separator: 'after',
    },
    {
      id: 'artifact-duplicate',
      label: 'Duplicate',
      icon: '📋',
      shortcut: '⌘D',
      onAction: () => console.log('Duplicate artifact'),
    },
    {
      id: 'artifact-delete',
      label: 'Delete',
      icon: '🗑️',
      shortcut: 'Delete',
      onAction: () => console.log('Delete artifact'),
    },
  ];
}

/**
 * Context Bar Component
 */
export const ContextBar: React.FC<ContextBarProps> = ({
  customActions = [],
  onAction,
}) => {
  const selection = useAtomValue(canvasSelectionAtom);
  const setVisible = useSetAtom(chromeContextBarVisibleAtom);

  // Determine actions based on selection
  const actions = useMemo((): ContextBarAction[] => {
    if (!selection.selectedIds || selection.selectedIds.length === 0) {
      return [];
    }

    const baseActions =
      selection.selectedType === 'frame'
        ? getFrameActions(selection)
        : getArtifactActions(selection);

    return [...baseActions, ...customActions];
  }, [selection, customActions]);

  // Hide if no actions
  if (actions.length === 0) {
    return null;
  }

  // Handle action click
  const handleActionClick = (action: ContextBarAction) => {
    if (action.disabled) return;

    action.onAction();
    onAction?.(action.id);
  };

  return (
    <div
      className="context-bar"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '4px',
        padding: '4px',
        background: 'var(--color-surface-elevated, #ffffff)',
        border: '1px solid var(--color-border, #e0e0e0)',
        borderRadius: '8px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
      }}
    >
      {actions.map((action, index) => (
        <React.Fragment key={action.id}>
          {/* Separator */}
          {action.separator === 'before' && (
            <div
              className="context-bar-separator"
              style={{
                width: '1px',
                height: '24px',
                background: 'var(--color-border, #e0e0e0)',
                margin: '0 4px',
              }}
            />
          )}

          {/* Action Button */}
          <button
            className="context-bar-action"
            onClick={() => handleActionClick(action)}
            disabled={action.disabled}
            title={`${action.label}${action.shortcut ? ` (${action.shortcut})` : ''}`}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
              minWidth: '32px',
              height: '32px',
              padding: '0 8px',
              border: 'none',
              background: 'transparent',
              borderRadius: '6px',
              cursor: action.disabled ? 'not-allowed' : 'pointer',
              opacity: action.disabled ? 0.4 : 1,
              fontSize: '16px',
              transition: 'all 0.15s ease-in-out',
            }}
            onMouseEnter={(e) => {
              if (!action.disabled) {
                e.currentTarget.style.background =
                  'var(--color-hover-background, #f5f5f5)';
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
            }}
          >
            <span className="action-icon">{action.icon}</span>

            {/* Show label on hover or for multi-select actions */}
            <span
              className="action-label"
              style={{
                fontSize: '13px',
                fontWeight: 500,
                color: 'var(--color-text-primary, #212121)',
                whiteSpace: 'nowrap',
              }}
            >
              {action.label}
            </span>
          </button>

          {/* Separator */}
          {action.separator === 'after' && index < actions.length - 1 && (
            <div
              className="context-bar-separator"
              style={{
                width: '1px',
                height: '24px',
                background: 'var(--color-border, #e0e0e0)',
                margin: '0 4px',
              }}
            />
          )}
        </React.Fragment>
      ))}

      {/* Close Button */}
      <button
        className="context-bar-close"
        onClick={() => setVisible(false)}
        title="Close (Esc)"
        style={{
          width: '32px',
          height: '32px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: 'none',
          background: 'transparent',
          borderRadius: '6px',
          cursor: 'pointer',
          fontSize: '16px',
          color: 'var(--color-text-secondary, #757575)',
          marginLeft: '4px',
          transition: 'all 0.15s ease-in-out',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background =
            'var(--color-hover-background, #f5f5f5)';
          e.currentTarget.style.color = 'var(--color-text-primary, #212121)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent';
          e.currentTarget.style.color = 'var(--color-text-secondary, #757575)';
        }}
      >
        ✕
      </button>
    </div>
  );
};
