/**
 * @ghatana/yappc-ide - Cursor Overlay Component
 * 
 * Visual representation of remote users' cursors and selections
 * in the code editor with real-time updates.
 * 
 * @doc.type component
 * @doc.purpose Shared cursor visualization for collaborative editing
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useEffect, useState, useCallback } from 'react';

import { useCollaborativeEditing, generateUserAvatar, type UserPresence } from '../hooks/useCollaborativeEditing';

/**
 * Cursor position in editor coordinates
 */
interface CursorPosition {
  line: number;
  column: number;
  top: number;
  left: number;
  height: number;
}

/**
 * Cursor overlay props
 */
export interface CursorOverlayProps {
  editorRef?: React.RefObject<HTMLElement | null>;
  lineHeight?: number;
  fontSize?: number;
  showAvatars?: boolean;
  showSelections?: boolean;
  showTypingIndicators?: boolean;
  className?: string;
}

/**
 * Single cursor component
 */
interface RemoteCursorProps {
  user: UserPresence;
  position: CursorPosition;
  showAvatar: boolean;
  showSelection: boolean;
  showTyping: boolean;
}

const RemoteCursor: React.FC<RemoteCursorProps> = ({
  user,
  position,
  showAvatar,
  showSelection,
  showTyping,
}) => {
  const [isHovered, setIsHovered] = useState(false);

  return (
    <>
      {/* Selection highlight */}
      {showSelection && user.cursor.selection && (
        <div
          className="absolute pointer-events-none opacity-30"
          style={{
            backgroundColor: user.userColor,
            top: position.top + (user.cursor.selection.start.line - user.cursor.position.line) * position.height,
            left: position.left + (user.cursor.selection.start.column - user.cursor.position.column) * 8,
            width: Math.abs(user.cursor.selection.end.column - user.cursor.selection.start.column) * 8,
            height: Math.abs(user.cursor.selection.end.line - user.cursor.selection.start.line + 1) * position.height,
            zIndex: 1,
          }}
        />
      )}

      {/* Cursor line */}
      <div
        className="absolute pointer-events-none transition-all duration-150 ease-out"
        style={{
          left: position.left,
          top: position.top,
          height: position.height,
          width: 2,
          backgroundColor: user.userColor,
          zIndex: 2,
        }}
      />

      {/* User label */}
      <div
        className="absolute flex items-center gap-1 pointer-events-none transition-all duration-150 ease-out"
        style={{
          left: position.left + 8,
          top: position.top - 24,
          zIndex: 3,
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
      >
        {/* Avatar */}
        {showAvatar && (
          <div className="relative">
            <img
              src={generateUserAvatar(user.userName, user.userColor)}
              alt={user.userName}
              className="w-5 h-5 rounded-full border-2 border-white shadow-sm"
            />

            {/* Typing indicator */}
            {showTyping && user.activity === 'typing' && (
              <div className="absolute -bottom-1 -right-1 w-3 h-3 bg-green-500 rounded-full border-2 border-white">
                <div className="w-full h-full bg-green-500 rounded-full animate-ping" />
              </div>
            )}
          </div>
        )}

        {/* User name */}
        <div
          className={`
            px-2 py-1 text-xs text-white rounded shadow-sm
            transition-all duration-150 ease-out
            ${isHovered ? 'opacity-100 scale-100' : 'opacity-90 scale-95'}
          `}
          style={{ backgroundColor: user.userColor }}
        >
          {user.userName}
          {user.activity === 'typing' && (
            <span className="ml-1">✏️</span>
          )}
        </div>

        {/* Detailed tooltip on hover */}
        {isHovered && (
          <div className="absolute bottom-full left-0 mb-2 p-2 bg-gray-900 text-white text-xs rounded shadow-lg whitespace-nowrap">
            <div className="font-semibold">{user.userName}</div>
            <div className="text-gray-300">
              Line {user.cursor.position.line + 1}, Column {user.cursor.position.column + 1}
            </div>
            <div className="text-gray-400">
              {user.activity === 'typing' ? 'Typing...' :
                user.activity === 'selecting' ? 'Selecting...' :
                  'Idle'}
            </div>
            <div className="absolute top-full left-4 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-900" />
          </div>
        )}
      </div>
    </>
  );
};

/**
 * Cursor Overlay Component
 */
export const CursorOverlay: React.FC<CursorOverlayProps> = ({
  lineHeight = 20,
  fontSize = 14,
  showAvatars = true,
  showSelections = true,
  showTypingIndicators = true,
  className = '',
}) => {
  const { getUsersInFile, activeFileId } = useCollaborativeEditing();
  const [cursors, setCursors] = useState<Array<{ user: UserPresence; position: CursorPosition }>>([]);

  // Calculate cursor position from line/column to pixel coordinates
  const calculateCursorPosition = useCallback((
    line: number,
    column: number
  ): CursorPosition => {
    // This is a simplified calculation - in a real implementation,
    // you'd use the editor's internal positioning API
    const top = line * lineHeight;
    const left = column * (fontSize * 0.6); // Approximate character width

    return {
      line,
      column,
      top,
      left,
      height: lineHeight,
    };
  }, [lineHeight, fontSize]);

  // Update cursor positions
  useEffect(() => {
    if (!activeFileId) return;

    const usersInFile = getUsersInFile(activeFileId);
    const updatedCursors = usersInFile
      .filter(user => user.cursor.fileId === activeFileId)
      .map(user => ({
        user,
        position: calculateCursorPosition(
          user.cursor.position.line,
          user.cursor.position.column
        ),
      }));

    setCursors(updatedCursors);
  }, [activeFileId, getUsersInFile, calculateCursorPosition, lineHeight, fontSize]);

  if (!activeFileId || cursors.length === 0) {
    return null;
  }

  return (
    <div
      className={`absolute inset-0 pointer-events-none overflow-hidden ${className}`}
      style={{ zIndex: 10 }}
    >
      {cursors.map(({ user, position }) => (
        <RemoteCursor
          key={user.userId}
          user={user}
          position={position}
          showAvatar={showAvatars}
          showSelection={showSelections}
          showTyping={showTypingIndicators}
        />
      ))}
    </div>
  );
};

/**
 * Hook for editor integration
 */
export function useEditorCursorOverlay() {
  const { broadcastCursor, broadcastSelection, broadcastTextEdit } = useCollaborativeEditing();

  // Handle cursor movement
  const handleCursorMove = useCallback((line: number, column: number) => {
    broadcastCursor({ line, column });
  }, [broadcastCursor]);

  // Handle selection change
  const handleSelectionChange = useCallback((
    start: { line: number; column: number },
    end: { line: number; column: number }
  ) => {
    broadcastSelection({ start, end });
  }, [broadcastSelection]);

  // Handle text edit
  const handleTextEdit = useCallback((
    type: 'insert' | 'delete',
    position: { line: number; column: number },
    text?: string,
    length?: number
  ) => {
    broadcastTextEdit({ type, position, text, length });
  }, [broadcastTextEdit]);

  return {
    handleCursorMove,
    handleSelectionChange,
    handleTextEdit,
  };
}
