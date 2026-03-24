/**
 * Feature Hints System
 *
 * Contextual hints that appear to guide users through features.
 * Hints are shown based on user actions and dismissed once learned.
 *
 * @doc.type component
 * @doc.purpose Feature discovery
 * @doc.layer product
 * @doc.pattern Hint
 */

import React, { useState, useEffect, useCallback } from 'react';
import { CANVAS_Z_INDEX } from '../config/z-index';

/**
 * Feature hint definition
 */
export interface FeatureHint {
  /** Unique hint ID */
  id: string;
  /** Hint message */
  message: string;
  /** Target element selector */
  target: string;
  /** Placement of hint */
  placement: 'top' | 'bottom' | 'left' | 'right';
  /** Trigger condition */
  trigger: () => boolean;
  /** Icon emoji */
  icon?: string;
  /** Optional action */
  action?: {
    label: string;
    onClick: () => void;
  };
}

/**
 * Canvas feature hints
 */
export const CANVAS_FEATURE_HINTS: FeatureHint[] = [
  {
    id: 'drag-frame',
    message: 'Drag frames to rearrange your workflow',
    target: '.canvas-frame',
    placement: 'top',
    icon: '👆',
    trigger: () => {
      // Show after user creates first frame
      const frames = document.querySelectorAll('.canvas-frame');
      return frames.length >= 1;
    },
  },
  {
    id: 'collapse-frame',
    message: 'Click to collapse/expand frames',
    target: '.frame-collapse-btn',
    placement: 'left',
    icon: '▼',
    trigger: () => {
      const frames = document.querySelectorAll('.canvas-frame');
      return frames.length >= 2;
    },
  },
  {
    id: 'context-bar',
    message: 'Select elements to see contextual actions',
    target: '.canvas-frame',
    placement: 'bottom',
    icon: '⚡',
    trigger: () => {
      // Show after user has been on canvas for 30 seconds
      return true;
    },
  },
  {
    id: 'outline-search',
    message: 'Use search to quickly find elements',
    target: '.outline-panel input',
    placement: 'bottom',
    icon: '🔍',
    trigger: () => {
      const frames = document.querySelectorAll('.canvas-frame');
      return frames.length >= 5;
    },
  },
  {
    id: 'zoom-modes',
    message: 'Zoom levels switch between Overview, Focus, and Detail modes',
    target: '.zoom-mode-indicator',
    placement: 'top',
    icon: '🎯',
    trigger: () => true,
  },
  {
    id: 'keyboard-shortcuts',
    message: 'Press Cmd+K to see all keyboard shortcuts',
    target: 'body',
    placement: 'bottom',
    icon: '⌨️',
    trigger: () => true,
    action: {
      label: 'Show Shortcuts',
      onClick: () => {
        // Open command palette
        console.log('Open command palette');
      },
    },
  },
  {
    id: 'calm-mode',
    message:
      'Enable Calm Mode (Cmd+Shift+C) to hide chrome and focus on content',
    target: '.canvas-calm-indicator',
    placement: 'bottom',
    icon: '🌙',
    trigger: () => {
      const panels = document.querySelectorAll('[class*="layer"]');
      return panels.length >= 3;
    },
  },
  {
    id: 'minimap-navigation',
    message: 'Click the minimap to quickly jump to any canvas area',
    target: '.canvas-minimap-layer',
    placement: 'top',
    icon: '🗺️',
    trigger: () => {
      const frames = document.querySelectorAll('.canvas-frame');
      return frames.length >= 4;
    },
  },
];

/**
 * Feature hint component
 */
export interface FeatureHintProps {
  /** Hint definition */
  hint: FeatureHint;
  /** Callback when dismissed */
  onDismiss: (hintId: string) => void;
}

export const FeatureHintBubble: React.FC<FeatureHintProps> = ({
  hint,
  onDismiss,
}) => {
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);
  const [visible, setVisible] = useState(false);

  // Find target element
  useEffect(() => {
    const target = document.querySelector(hint.target);
    if (target) {
      const rect = target.getBoundingClientRect();
      setTargetRect(rect);
      setVisible(true);
    } else {
      setVisible(false);
    }
  }, [hint.target]);

  // Auto-dismiss after 10 seconds
  useEffect(() => {
    if (visible) {
      const timer = setTimeout(() => {
        handleDismiss();
      }, 10000);
      return () => clearTimeout(timer);
    }
  }, [visible]);

  const handleDismiss = () => {
    setVisible(false);
    setTimeout(() => {
      onDismiss(hint.id);
    }, 200);
  };

  if (!visible || !targetRect) return null;

  // Calculate position
  const getPosition = (): React.CSSProperties => {
    const padding = 12;
    let style: React.CSSProperties = { position: 'fixed' };

    switch (hint.placement) {
      case 'top':
        style.left = targetRect.left + targetRect.width / 2;
        style.top = targetRect.top - padding;
        style.transform = 'translate(-50%, -100%)';
        break;
      case 'bottom':
        style.left = targetRect.left + targetRect.width / 2;
        style.top = targetRect.bottom + padding;
        style.transform = 'translateX(-50%)';
        break;
      case 'left':
        style.left = targetRect.left - padding;
        style.top = targetRect.top + targetRect.height / 2;
        style.transform = 'translate(-100%, -50%)';
        break;
      case 'right':
        style.left = targetRect.right + padding;
        style.top = targetRect.top + targetRect.height / 2;
        style.transform = 'translateY(-50%)';
        break;
    }

    return style;
  };

  return (
    <div
      className="feature-hint"
      style={{
        ...getPosition(),
        maxWidth: '280px',
        background: '#212121',
        color: 'white',
        padding: '12px',
        borderRadius: '8px',
        boxShadow: '0 4px 16px rgba(0, 0, 0, 0.3)',
        zIndex: CANVAS_Z_INDEX.TOOLTIP,
        fontSize: '13px',
        lineHeight: 1.4,
        animation: 'hint-slide-in 0.3s ease-out',
      }}
    >
      {/* Arrow */}
      <div
        style={{
          position: 'absolute',
          width: 0,
          height: 0,
          border: '6px solid transparent',
          ...(hint.placement === 'bottom' && {
            top: '-12px',
            left: '50%',
            transform: 'translateX(-50%)',
            borderBottomColor: '#212121',
          }),
          ...(hint.placement === 'top' && {
            bottom: '-12px',
            left: '50%',
            transform: 'translateX(-50%)',
            borderTopColor: '#212121',
          }),
          ...(hint.placement === 'right' && {
            left: '-12px',
            top: '50%',
            transform: 'translateY(-50%)',
            borderRightColor: '#212121',
          }),
          ...(hint.placement === 'left' && {
            right: '-12px',
            top: '50%',
            transform: 'translateY(-50%)',
            borderLeftColor: '#212121',
          }),
        }}
      />

      {/* Content */}
      <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
        {hint.icon && (
          <div style={{ fontSize: '16px', flexShrink: 0 }}>{hint.icon}</div>
        )}
        <div style={{ flex: 1 }}>
          <div style={{ marginBottom: hint.action ? '8px' : 0 }}>
            {hint.message}
          </div>
          {hint.action && (
            <button
              onClick={() => {
                hint.action!.onClick();
                handleDismiss();
              }}
              style={{
                padding: '4px 12px',
                border: 'none',
                borderRadius: '4px',
                background: '#1976d2',
                color: 'white',
                cursor: 'pointer',
                fontSize: '12px',
                fontWeight: 500,
              }}
            >
              {hint.action.label}
            </button>
          )}
        </div>
        <button
          onClick={handleDismiss}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'rgba(255, 255, 255, 0.7)',
            cursor: 'pointer',
            fontSize: '16px',
            padding: 0,
            lineHeight: 1,
          }}
        >
          ✕
        </button>
      </div>

      <style>{`
        @keyframes hint-slide-in {
          from {
            opacity: 0;
            transform: translateY(-10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
      `}</style>
    </div>
  );
};

/**
 * Feature hints manager
 */
export const FeatureHintsManager: React.FC = () => {
  const [activeHints, setActiveHints] = useState<string[]>([]);
  const [dismissedHints, setDismissedHints] = useState<Set<string>>(() => {
    const stored = localStorage.getItem('canvas-dismissed-hints');
    return new Set(stored ? JSON.parse(stored) : []);
  });

  // Check which hints should be shown
  useEffect(() => {
    const interval = setInterval(() => {
      const hintsToShow = CANVAS_FEATURE_HINTS.filter(
        (hint) =>
          !dismissedHints.has(hint.id) &&
          !activeHints.includes(hint.id) &&
          hint.trigger()
      ).map((hint) => hint.id);

      if (hintsToShow.length > 0) {
        // Show one hint at a time
        setActiveHints((prev) => [...prev, hintsToShow[0]]);
      }
    }, 2000); // Check every 2 seconds

    return () => clearInterval(interval);
  }, [dismissedHints, activeHints]);

  const handleDismiss = useCallback((hintId: string) => {
    setActiveHints((prev) => prev.filter((id) => id !== hintId));
    setDismissedHints((prev) => {
      const next = new Set(prev);
      next.add(hintId);
      localStorage.setItem('canvas-dismissed-hints', JSON.stringify([...next]));
      return next;
    });
  }, []);

  return (
    <>
      {activeHints.map((hintId) => {
        const hint = CANVAS_FEATURE_HINTS.find((h) => h.id === hintId);
        if (!hint) return null;
        return (
          <FeatureHintBubble
            key={hint.id}
            hint={hint}
            onDismiss={handleDismiss}
          />
        );
      })}
    </>
  );
};

/**
 * Hook to manage feature hints
 */
export function useFeatureHints() {
  const resetHints = useCallback(() => {
    localStorage.removeItem('canvas-dismissed-hints');
    window.location.reload();
  }, []);

  const dismissHint = useCallback((hintId: string) => {
    const stored = localStorage.getItem('canvas-dismissed-hints');
    const dismissed = new Set(stored ? JSON.parse(stored) : []);
    dismissed.add(hintId);
    localStorage.setItem(
      'canvas-dismissed-hints',
      JSON.stringify([...dismissed])
    );
  }, []);

  return {
    resetHints,
    dismissHint,
  };
}
