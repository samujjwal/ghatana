/**
 * Collaboration Cursors Component
 *
 * Real-time cursor tracking and display for collaborative editing.
 * Shows cursor positions, selections, and user presence indicators.
 *
 * @doc.type component
 * @doc.purpose Real-time collaboration visualization
 * @doc.layer presentation
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useAtomValue } from "jotai";
import { chromeCollaboratorsAtom } from "../chrome";

interface CursorPosition {
  x: number;
  y: number;
  timestamp: number;
}

interface UserCursor {
  userId: string;
  userName: string;
  userColor: string;
  position: CursorPosition;
  isActive: boolean;
  lastSeen: number;
}

interface SelectionBox {
  userId: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

interface CollaborationCursorsProps {
  /** Canvas viewport offset */
  viewportOffset?: { x: number; y: number };
  /** Canvas zoom level */
  zoom?: number;
  /** Whether to show cursor trails */
  showTrails?: boolean;
  /** Cursor inactivity timeout (ms) */
  inactivityTimeout?: number;
}

export const CollaborationCursors: React.FC<CollaborationCursorsProps> = ({
  viewportOffset = { x: 0, y: 0 },
  zoom = 1,
  showTrails = true,
  inactivityTimeout = 5000,
}) => {
  const collaborators = useAtomValue(chromeCollaboratorsAtom);
  const [cursors, setCursors] = useState<Map<string, UserCursor>>(new Map());
  const [selections, setSelections] = useState<Map<string, SelectionBox>>(
    new Map(),
  );
  const [cursorTrails, setCursorTrails] = useState<
    Map<string, CursorPosition[]>
  >(new Map());

  // Listen for cursor movement events
  useEffect(() => {
    const handleCursorMove = (event: CustomEvent) => {
      const { userId, userName, userColor, x, y } = event.detail;

      setCursors((prev) => {
        const newCursors = new Map(prev);
        newCursors.set(userId, {
          userId,
          userName,
          userColor,
          position: { x, y, timestamp: Date.now() },
          isActive: true,
          lastSeen: Date.now(),
        });
        return newCursors;
      });

      // Update cursor trail
      if (showTrails) {
        setCursorTrails((prev) => {
          const newTrails = new Map(prev);
          const trail = newTrails.get(userId) || [];
          const newTrail = [...trail, { x, y, timestamp: Date.now() }].slice(
            -10,
          ); // Keep last 10 positions
          newTrails.set(userId, newTrail);
          return newTrails;
        });
      }
    };

    const handleSelection = (event: CustomEvent) => {
      const { userId, x, y, width, height } = event.detail;

      setSelections((prev) => {
        const newSelections = new Map(prev);
        if (width && height) {
          newSelections.set(userId, { userId, x, y, width, height });
        } else {
          newSelections.delete(userId);
        }
        return newSelections;
      });
    };

    const handleUserLeave = (event: CustomEvent) => {
      const { userId } = event.detail;

      setCursors((prev) => {
        const newCursors = new Map(prev);
        newCursors.delete(userId);
        return newCursors;
      });

      setSelections((prev) => {
        const newSelections = new Map(prev);
        newSelections.delete(userId);
        return newSelections;
      });

      setCursorTrails((prev) => {
        const newTrails = new Map(prev);
        newTrails.delete(userId);
        return newTrails;
      });
    };

    window.addEventListener(
      "yappc:cursor-move",
      handleCursorMove as EventListener,
    );
    window.addEventListener(
      "yappc:selection-change",
      handleSelection as EventListener,
    );
    window.addEventListener(
      "yappc:user-leave",
      handleUserLeave as EventListener,
    );

    return () => {
      window.removeEventListener(
        "yappc:cursor-move",
        handleCursorMove as EventListener,
      );
      window.removeEventListener(
        "yappc:selection-change",
        handleSelection as EventListener,
      );
      window.removeEventListener(
        "yappc:user-leave",
        handleUserLeave as EventListener,
      );
    };
  }, [showTrails]);

  // Clean up inactive cursors
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();

      setCursors((prev) => {
        const newCursors = new Map(prev);
        let hasChanges = false;

        for (const [userId, cursor] of newCursors.entries()) {
          if (now - cursor.lastSeen > inactivityTimeout) {
            newCursors.delete(userId);
            hasChanges = true;
          }
        }

        return hasChanges ? newCursors : prev;
      });

      // Clean up old trail positions
      if (showTrails) {
        setCursorTrails((prev) => {
          const newTrails = new Map(prev);
          let hasChanges = false;

          for (const [userId, trail] of newTrails.entries()) {
            const filteredTrail = trail.filter(
              (pos) => now - pos.timestamp < 2000,
            );
            if (filteredTrail.length !== trail.length) {
              if (filteredTrail.length === 0) {
                newTrails.delete(userId);
              } else {
                newTrails.set(userId, filteredTrail);
              }
              hasChanges = true;
            }
          }

          return hasChanges ? newTrails : prev;
        });
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [inactivityTimeout, showTrails]);

  // Transform canvas coordinates to screen coordinates
  const transformPosition = useCallback(
    (x: number, y: number) => {
      return {
        x: (x - viewportOffset.x) * zoom,
        y: (y - viewportOffset.y) * zoom,
      };
    },
    [viewportOffset, zoom],
  );

  // Render cursor trails
  const renderTrails = useMemo(() => {
    if (!showTrails) return null;

    return Array.from(cursorTrails.entries()).map(([userId, trail]) => {
      const cursor = cursors.get(userId);
      if (!cursor || trail.length < 2) return null;

      const points = trail.map((pos) => transformPosition(pos.x, pos.y));
      const pathData = points.reduce((path, point, index) => {
        if (index === 0) {
          return `M ${point.x} ${point.y}`;
        }
        return `${path} L ${point.x} ${point.y}`;
      }, "");

      return (
        <svg
          key={`trail-${userId}`}
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            width: "100%",
            height: "100%",
            pointerEvents: "none",
            zIndex: 999,
          }}
        >
          <path
            d={pathData}
            stroke={cursor.userColor}
            strokeWidth="2"
            fill="none"
            opacity="0.3"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    });
  }, [showTrails, cursorTrails, cursors, transformPosition]);

  // Render selection boxes
  const renderSelections = useMemo(() => {
    return Array.from(selections.values()).map((selection) => {
      const cursor = cursors.get(selection.userId);
      if (!cursor) return null;

      const pos = transformPosition(selection.x, selection.y);
      const width = selection.width * zoom;
      const height = selection.height * zoom;

      return (
        <div
          key={`selection-${selection.userId}`}
          style={{
            position: "absolute",
            left: `${pos.x}px`,
            top: `${pos.y}px`,
            width: `${width}px`,
            height: `${height}px`,
            border: `2px solid ${cursor.userColor}`,
            backgroundColor: `${cursor.userColor}20`,
            pointerEvents: "none",
            zIndex: 998,
            borderRadius: "4px",
          }}
        />
      );
    });
  }, [selections, cursors, transformPosition, zoom]);

  // Render cursors
  const renderCursors = useMemo(() => {
    return Array.from(cursors.values())
      .filter((cursor) => cursor.userId !== "current-user") // Don't show own cursor
      .map((cursor) => {
        const pos = transformPosition(cursor.position.x, cursor.position.y);

        return (
          <div
            key={`cursor-${cursor.userId}`}
            style={{
              position: "absolute",
              left: `${pos.x}px`,
              top: `${pos.y}px`,
              pointerEvents: "none",
              zIndex: 1000,
              transition: "left 0.1s ease-out, top 0.1s ease-out",
            }}
          >
            {/* Cursor SVG */}
            <svg
              width="24"
              height="24"
              viewBox="0 0 24 24"
              style={{
                filter: "drop-shadow(0 2px 4px rgba(0,0,0,0.2))",
              }}
            >
              <path
                d="M5 3 L5 17 L9 13 L12 19 L14 18 L11 12 L17 12 Z"
                fill={cursor.userColor}
                stroke="white"
                strokeWidth="1"
              />
            </svg>

            {/* User name label */}
            <div
              style={{
                position: "absolute",
                left: "24px",
                top: "0px",
                padding: "4px 8px",
                backgroundColor: cursor.userColor,
                color: "white",
                fontSize: "12px",
                fontWeight: 500,
                borderRadius: "4px",
                whiteSpace: "nowrap",
                boxShadow: "0 2px 4px rgba(0,0,0,0.2)",
              }}
            >
              {cursor.userName}
            </div>
          </div>
        );
      });
  }, [cursors, transformPosition]);

  return (
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
        overflow: "hidden",
      }}
    >
      {renderTrails}
      {renderSelections}
      {renderCursors}
    </div>
  );
};

/**
 * Hook to broadcast cursor position
 *
 * @example
 * ```tsx
 * const { broadcastCursor, broadcastSelection } = useCollaborationCursor({
 *   userId: 'user-123',
 *   userName: 'John Doe',
 *   userColor: '#1976d2',
 * });
 *
 * // In mouse move handler
 * broadcastCursor(x, y);
 *
 * // In selection handler
 * broadcastSelection(x, y, width, height);
 * ```
 */
export const useCollaborationCursor = (config: {
  userId: string;
  userName: string;
  userColor: string;
}) => {
  const broadcastCursor = useCallback(
    (x: number, y: number) => {
      const event = new CustomEvent("yappc:cursor-move", {
        detail: {
          userId: config.userId,
          userName: config.userName,
          userColor: config.userColor,
          x,
          y,
        },
      });
      window.dispatchEvent(event);
    },
    [config],
  );

  const broadcastSelection = useCallback(
    (x: number, y: number, width: number, height: number) => {
      const event = new CustomEvent("yappc:selection-change", {
        detail: {
          userId: config.userId,
          x,
          y,
          width,
          height,
        },
      });
      window.dispatchEvent(event);
    },
    [config.userId],
  );

  const broadcastLeave = useCallback(() => {
    const event = new CustomEvent("yappc:user-leave", {
      detail: {
        userId: config.userId,
      },
    });
    window.dispatchEvent(event);
  }, [config.userId]);

  return {
    broadcastCursor,
    broadcastSelection,
    broadcastLeave,
  };
};
