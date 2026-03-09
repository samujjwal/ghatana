/**
 * Collaboration Cursors Component
 *
 * @description Renders real-time cursor positions of collaborators
 * as an overlay on the canvas.
 */

import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { UserCursor } from './CanvasCollaboration';
import { useCollaborationCursors } from './hooks';

// =============================================================================
// Types
// =============================================================================

export interface CollaborationCursorsProps {
  cursors: UserCursor[];
  viewport?: { x: number; y: number; zoom: number };
  showNames?: boolean;
  className?: string;
}

// =============================================================================
// Cursor SVG Component
// =============================================================================

const CursorIcon: React.FC<{ color: string }> = ({ color }) => (
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M5.65376 12.4563L8.00376 21.0063L11.0038 14.0063L18.0038 11.0063L5.65376 4.00629L5.65376 12.4563Z"
      fill={color}
      stroke={color}
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M5.65376 12.4563L11.0038 14.0063"
      stroke="white"
      strokeWidth="1"
      strokeLinecap="round"
      strokeLinejoin="round"
      opacity="0.5"
    />
  </svg>
);

// =============================================================================
// Single Cursor Component
// =============================================================================

const Cursor: React.FC<{
  id: string;
  name: string;
  color: string;
  x: number;
  y: number;
  showName: boolean;
}> = ({ id, name, color, x, y, showName }) => {
  return (
    <motion.div
      key={id}
      initial={{ opacity: 0, scale: 0.5 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.5 }}
      transition={{
        type: 'spring',
        stiffness: 500,
        damping: 30,
      }}
      style={{
        position: 'absolute',
        left: x,
        top: y,
        pointerEvents: 'none',
        zIndex: 9999,
      }}
    >
      <motion.div
        animate={{ x: 0, y: 0 }}
        transition={{
          type: 'spring',
          stiffness: 300,
          damping: 25,
        }}
      >
        <CursorIcon color={color} />
        {showName && (
          <motion.div
            initial={{ opacity: 0, y: -5 }}
            animate={{ opacity: 1, y: 0 }}
            style={{
              position: 'absolute',
              left: 16,
              top: 16,
              backgroundColor: color,
              color: 'white',
              padding: '2px 8px',
              borderRadius: 4,
              fontSize: 12,
              fontWeight: 500,
              whiteSpace: 'nowrap',
              boxShadow: '0 1px 3px rgba(0, 0, 0, 0.2)',
            }}
          >
            {name}
          </motion.div>
        )}
      </motion.div>
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const CollaborationCursors: React.FC<CollaborationCursorsProps> = ({
  cursors,
  viewport,
  showNames = true,
  className,
}) => {
  const visibleCursors = useCollaborationCursors(cursors);

  // Transform cursor positions based on viewport
  const transformedCursors = useMemo(() => {
    if (!viewport) return visibleCursors;

    return visibleCursors.map((cursor) => ({
      ...cursor,
      x: (cursor.x - viewport.x) * viewport.zoom,
      y: (cursor.y - viewport.y) * viewport.zoom,
    }));
  }, [visibleCursors, viewport]);

  return (
    <div
      className={className}
      style={{
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        pointerEvents: 'none',
      }}
    >
      <AnimatePresence>
        {transformedCursors.map((cursor) => (
          <Cursor
            key={cursor.id}
            id={cursor.id}
            name={cursor.name}
            color={cursor.color}
            x={cursor.x}
            y={cursor.y}
            showName={showNames}
          />
        ))}
      </AnimatePresence>
    </div>
  );
};

export default CollaborationCursors;
