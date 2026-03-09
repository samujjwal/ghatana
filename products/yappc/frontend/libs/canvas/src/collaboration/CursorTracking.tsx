/**
 * Cursor Tracking - Collaboration Cursor Components
 * 
 * @deprecated Use RealTimeCursors, CollaborationCursor from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect } from 'react';

// ============================================================================
// CursorOverlay
// ============================================================================

export interface CursorOverlayProps {
  /** Cursor position */
  x: number;
  y: number;
  /** User identifier */
  userId: string;
  /** User display name */
  userName: string;
  /** User color */
  color?: string;
  /** Cursor visibility */
  visible?: boolean;
  /** Is this the local user's cursor */
  isLocal?: boolean;
  /** Click handler */
  onClick?: () => void;
}

/**
 * CursorOverlay - Bridge to Canvas Collaboration System
 */
export const CursorOverlay: React.FC<CursorOverlayProps> = ({
  x,
  y,
  userId,
  userName,
  color = '#4285f4',
  visible = true,
  isLocal = false,
  onClick,
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] CursorOverlay from @ghatana/yappc-ide is deprecated. ' +
      'Use RemoteCursor or RealTimeCursors from @ghatana/yappc-canvas collaboration module.'
    );
  }, []);

  if (!visible) return null;

  return (
    <div
      className={`cursor-overlay ${isLocal ? 'local' : 'remote'}`}
      style={{
        position: 'absolute',
        left: x,
        top: y,
        pointerEvents: 'none',
        zIndex: 1000,
      }}
      onClick={onClick}
    >
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
        <path
          d="M5.5 3.21V20.8c0 .45.54.67.85.35l4.86-4.86a.5.5 0 01.35-.15h6.87c.44 0 .66-.53.35-.85L6.35 2.85a.5.5 0 00-.85.35z"
          fill={color}
        />
      </svg>
      <span 
        className="cursor-label"
        style={{ 
          backgroundColor: color, 
          color: 'white',
          padding: '2px 6px',
          borderRadius: '3px',
          fontSize: '12px',
          marginLeft: '8px',
          whiteSpace: 'nowrap',
        }}
      >
        {userName}
      </span>
    </div>
  );
};

// ============================================================================
// RealTimeCursorTracking
// ============================================================================

export interface RealTimeCursorTrackingProps {
  /** All tracked cursors */
  cursors: TrackedCursor[];
  /** Container bounds */
  bounds?: { width: number; height: number };
  /** Cursor update handler (for local cursor) */
  onLocalCursorMove?: (position: { x: number; y: number }) => void;
  /** Cursor click handler */
  onCursorClick?: (userId: string) => void;
  /** Additional CSS classes */
  className?: string;
}

export interface TrackedCursor {
  userId: string;
  userName: string;
  x: number;
  y: number;
  color: string;
  lastSeen?: Date;
  isActive?: boolean;
}

/**
 * RealTimeCursorTracking - Bridge Component
 */
export const RealTimeCursorTracking: React.FC<RealTimeCursorTrackingProps> = ({
  cursors,
  bounds,
  onLocalCursorMove,
  onCursorClick,
  className,
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] RealTimeCursorTracking from @ghatana/yappc-ide is deprecated. ' +
      'Use RealTimeCursors or PresenceIndicators from @ghatana/yappc-canvas collaboration module.'
    );
  }, []);

  return (
    <div className={`real-time-cursor-tracking ${className || ''}`}>
      {cursors.map(cursor => (
        <CursorOverlay
          key={cursor.userId}
          x={cursor.x}
          y={cursor.y}
          userId={cursor.userId}
          userName={cursor.userName}
          color={cursor.color}
          visible={cursor.isActive}
          onClick={() => onCursorClick?.(cursor.userId)}
        />
      ))}
    </div>
  );
};

// Re-export with Canvas prefix
export { CursorOverlay as CollaborationCursor };
export { RealTimeCursorTracking as RealTimeCursors };
