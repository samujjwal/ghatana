/**
 * Remote Cursor Component
 * 
 * Displays cursor position and name for remote collaborators.
 * Follows cursor with smooth animation and shows user color.
 * 
 * @module canvas/components
 */

import React, { useEffect, useRef } from 'react';
import type { RemoteUser } from '../hooks/useCanvasCollaborationBackend';

export interface RemoteCursorProps {
  user: RemoteUser;
  /** Viewport offset for canvas positioning */
  viewportOffset?: { x: number; y: number };
  /** Show user name label */
  showLabel?: boolean;
}

/**
 * Remote Cursor Component
 * 
 * Renders a cursor for a remote user with smooth position transitions.
 * 
 * @example
 * ```tsx
 * <RemoteCursor 
 *   user={remoteUser} 
 *   viewportOffset={{ x: 0, y: 0 }}
 *   showLabel={true}
 * />
 * ```
 */
export const RemoteCursor: React.FC<RemoteCursorProps> = ({
  user,
  viewportOffset = { x: 0, y: 0 },
  showLabel = true,
}) => {
  const cursorRef = useRef<HTMLDivElement>(null);

  // Smooth cursor animation
  useEffect(() => {
    if (!cursorRef.current || !user.cursor) return;

    const element = cursorRef.current;
    const x = user.cursor.x + viewportOffset.x;
    const y = user.cursor.y + viewportOffset.y;

    element.style.transform = `translate(${x}px, ${y}px)`;
  }, [user.cursor, viewportOffset]);

  if (!user.cursor || !user.isOnline) {
    return null;
  }

  return (
    <div
      ref={cursorRef}
      className="remote-cursor"
      style={{
        position: 'fixed',
        pointerEvents: 'none',
        zIndex: 9999,
        transition: 'transform 0.1s ease-out',
        willChange: 'transform',
      }}
    >
      {/* Cursor SVG */}
      <svg
        width="24"
        height="24"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        style={{
          filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.2))',
        }}
      >
        <path
          d="M5.5 3.5L18.5 10.5L11 13L8.5 20.5L5.5 3.5Z"
          fill={user.userColor}
          stroke="white"
          strokeWidth="1.5"
          strokeLinejoin="round"
        />
      </svg>

      {/* User name label */}
      {showLabel && (
        <div
          style={{
            position: 'absolute',
            top: '24px',
            left: '12px',
            backgroundColor: user.userColor,
            color: 'white',
            padding: '4px 8px',
            borderRadius: '4px',
            fontSize: '12px',
            fontWeight: 500,
            whiteSpace: 'nowrap',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          }}
        >
          {user.userName}
        </div>
      )}
    </div>
  );
};

export default RemoteCursor;
