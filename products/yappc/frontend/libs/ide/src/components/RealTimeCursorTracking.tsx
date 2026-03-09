/**
 * @ghatana/yappc-ide - Real-time Cursor Tracking Component
 * 
 * Advanced real-time cursor tracking with presence awareness,
 * conflict detection, and collaborative features.
 * 
 * @doc.type component
 * @doc.purpose Enhanced collaborative cursor tracking for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useCollaborativeEditing, type UserPresence } from '../hooks/useCollaborativeEditing';
import { InteractiveButton } from './MicroInteractions';

/**
 * Enhanced cursor position with additional metadata
 */
interface EnhancedCursorPosition {
  line: number;
  column: number;
  top: number;
  left: number;
  height: number;
  timestamp: number;
  confidence: number;
}

/**
 * Cursor conflict information
 */
interface CursorConflict {
  userId: string;
  userName: string;
  position: { line: number; column: number };
  severity: 'low' | 'medium' | 'high';
  message: string;
}

/**
 * Real-time cursor tracking props
 */
export interface RealTimeCursorTrackingProps {
  editorRef?: React.RefObject<HTMLElement | null>;
  fileId: string;
  lineHeight?: number;
  fontSize?: number;
  enableConflictDetection?: boolean;
  enablePresenceList?: boolean;
  enableTypingPrediction?: boolean;
  showUserAvatars?: boolean;
  showSelections?: boolean;
  showTypingIndicators?: boolean;
  className?: string;
  onConflictDetected?: (conflict: CursorConflict) => void;
  onUserJoined?: (user: UserPresence) => void;
  onUserLeft?: (userId: string) => void;
}

/**
 * Enhanced remote cursor component
 */
interface EnhancedRemoteCursorProps {
  user: UserPresence;
  position: EnhancedCursorPosition;
  showAvatar: boolean;
  showSelection: boolean;
  showTyping: boolean;
  isConflicted?: boolean;
}

const EnhancedRemoteCursor: React.FC<EnhancedRemoteCursorProps> = ({
  user,
  position,
  showAvatar,
  showSelection,
  showTyping,
  isConflicted = false,
}) => {
  const [isHovered, setIsHovered] = useState(false);
  const [showDetails, setShowDetails] = useState(false);

  const getTimeSinceLastUpdate = useCallback(() => {
    const now = Date.now();
    const diff = now - position.timestamp;
    if (diff < 5000) return 'Active now';
    if (diff < 30000) return 'Active recently';
    return `Last seen ${Math.floor(diff / 60000)}m ago`;
  }, [position.timestamp]);

  return (
    <>
      {/* Selection highlight */}
      {showSelection && user.cursor.selection && (
        <div
          className={`
            absolute pointer-events-none transition-all duration-200 ease-out
            ${isConflicted ? 'opacity-50 animate-pulse' : 'opacity-30'}
          `}
          style={{
            backgroundColor: isConflicted ? '#ef4444' : user.userColor,
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
        className={`
          absolute pointer-events-none transition-all duration-200 ease-out
          ${isConflicted ? 'animate-pulse' : ''}
        `}
        style={{
          left: position.left,
          top: position.top,
          height: position.height,
          width: 2,
          backgroundColor: isConflicted ? '#ef4444' : user.userColor,
          zIndex: 2,
        }}
      />

      {/* User label */}
      <div
        className={`
          absolute flex items-center gap-1 pointer-events-none transition-all duration-200 ease-out
          ${isConflicted ? 'animate-bounce' : ''}
        `}
        style={{
          left: position.left + 8,
          top: position.top - 24,
          zIndex: 3,
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onClick={() => setShowDetails(!showDetails)}
      >
        {/* Avatar */}
        {showAvatar && (
          <div className="relative">
            <div
              className="w-5 h-5 rounded-full border-2 border-white shadow-sm flex items-center justify-center text-xs text-white font-semibold"
              style={{ backgroundColor: user.userColor }}
            >
              {user.userName.charAt(0).toUpperCase()}
            </div>

            {/* Typing indicator */}
            {showTyping && user.activity === 'typing' && (
              <div className="absolute -bottom-1 -right-1 w-3 h-3 bg-green-500 rounded-full border-2 border-white">
                <div className="w-full h-full bg-green-500 rounded-full animate-ping" />
              </div>
            )}

            {/* Connection quality indicator */}
            <div className={`absolute -top-1 -right-1 w-2 h-2 rounded-full border border-white ${position.confidence > 0.8 ? 'bg-green-500' :
              position.confidence > 0.5 ? 'bg-yellow-500' : 'bg-red-500'
              }`} />
          </div>
        )}

        {/* User name */}
        <div
          className={`
            px-2 py-1 text-xs text-white rounded shadow-sm
            transition-all duration-200 ease-out
            ${isHovered ? 'opacity-100 scale-100' : 'opacity-90 scale-95'}
            ${isConflicted ? 'bg-red-500' : ''}
          `}
          style={{ backgroundColor: isConflicted ? '#ef4444' : user.userColor }}
        >
          {user.userName}
          {user.activity === 'typing' && <span className="ml-1">✏️</span>}
          {isConflicted && <span className="ml-1">⚠️</span>}
        </div>

        {/* Detailed tooltip */}
        {(isHovered || showDetails) && (
          <div className="absolute bottom-full left-0 mb-2 p-3 bg-gray-900 text-white text-xs rounded shadow-lg whitespace-nowrap z-50">
            <div className="flex items-center justify-between gap-4 mb-2">
              <div className="font-semibold">{user.userName}</div>
              <div className={`px-2 py-1 rounded text-xs ${position.confidence > 0.8 ? 'bg-green-600' :
                position.confidence > 0.5 ? 'bg-yellow-600' : 'bg-red-600'
                }`}>
                {Math.round(position.confidence * 100)}%
              </div>
            </div>
            <div className="text-gray-300 mb-1">
              Line {user.cursor.position.line + 1}, Column {user.cursor.position.column + 1}
            </div>
            <div className="text-gray-400 mb-1">
              {user.activity === 'typing' ? 'Typing...' :
                user.activity === 'selecting' ? 'Selecting...' :
                  'Idle'}
            </div>
            <div className="text-gray-500 text-xs">
              {getTimeSinceLastUpdate()}
            </div>
            {isConflicted && (
              <div className="mt-2 pt-2 border-t border-gray-700 text-red-400">
                ⚠️ Potential edit conflict
              </div>
            )}
            <div className="absolute top-full left-4 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-900" />
          </div>
        )}
      </div>
    </>
  );
};

/**
 * Real-time Cursor Tracking Component
 */
export const RealTimeCursorTracking: React.FC<RealTimeCursorTrackingProps> = ({
  fileId,
  lineHeight = 20,
  fontSize = 14,
  enableConflictDetection = true,
  enablePresenceList = true,
  showUserAvatars = true,
  showSelections = true,
  showTypingIndicators = true,
  className = '',
  onConflictDetected,
}) => {
  const { getUsersInFile } = useCollaborativeEditing();

  const [cursors, setCursors] = useState<Array<{
    user: UserPresence;
    position: EnhancedCursorPosition;
    isConflicted: boolean;
  }>>([]);

  const [conflicts, setConflicts] = useState<CursorConflict[]>([]);
  const [showPresencePanel, setShowPresencePanel] = useState(false);
  const conflictTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Calculate enhanced cursor position
  const calculateCursorPosition = useCallback((
    line: number,
    column: number,
    timestamp: number = Date.now(),
    confidence: number = 1.0
  ): EnhancedCursorPosition => {
    // Enhanced calculation with confidence scoring
    const now = Date.now();
    const age = now - timestamp;
    const confidenceScore = confidence * Math.max(0, 1 - age / 30000); // Decay over 30s

    const top = line * lineHeight;
    const left = column * (fontSize * 0.6);

    return {
      line,
      column,
      top,
      left,
      height: lineHeight,
      timestamp,
      confidence: confidenceScore,
    };
  }, [lineHeight, fontSize]);

  // Detect cursor conflicts
  const detectConflicts = useCallback((users: UserPresence[]) => {
    if (!enableConflictDetection) return [];

    const newConflicts: CursorConflict[] = [];

    for (let i = 0; i < users.length; i++) {
      for (let j = i + 1; j < users.length; j++) {
        const user1 = users[i];
        const user2 = users[j];

        const lineDiff = Math.abs(user1.cursor.position.line - user2.cursor.position.line);
        const colDiff = Math.abs(user1.cursor.position.column - user2.cursor.position.column);

        if (lineDiff === 0 && colDiff < 10) {
          // Same line, close columns - high conflict risk
          newConflicts.push({
            userId: user2.userId,
            userName: user2.userName,
            position: user2.cursor.position,
            severity: 'high',
            message: `${user2.userName} is editing near your cursor`,
          });
        } else if (lineDiff <= 2 && colDiff < 20) {
          // Nearby lines - medium conflict risk
          newConflicts.push({
            userId: user2.userId,
            userName: user2.userName,
            position: user2.cursor.position,
            severity: 'medium',
            message: `${user2.userName} is editing nearby`,
          });
        }
      }
    }

    return newConflicts;
  }, [enableConflictDetection]);



  // Update cursor positions and detect conflicts
  useEffect(() => {
    if (!fileId) return;

    const usersInFile = getUsersInFile(fileId);
    const updatedCursors = usersInFile
      .filter(user => user.cursor.fileId === fileId)
      .map(user => {
        const position = calculateCursorPosition(
          user.cursor.position.line,
          user.cursor.position.column,
          user.lastSeen,
          0.9 // Base confidence for remote cursors
        );

        return {
          user,
          position,
          isConflicted: false, // Will be updated below
        };
      });

    // Detect conflicts
    const detectedConflicts = detectConflicts(usersInFile);
    setConflicts(detectedConflicts);

    // Mark conflicted cursors
    const conflictedUserIds = new Set(detectedConflicts.map(c => c.userId));
    updatedCursors.forEach(cursor => {
      cursor.isConflicted = conflictedUserIds.has(cursor.user.userId);
    });

    setCursors(updatedCursors);

    // Notify about new conflicts
    detectedConflicts.forEach(conflict => {
      onConflictDetected?.(conflict);
    });

    // Auto-clear conflicts after 5 seconds
    if (detectedConflicts.length > 0) {
      if (conflictTimeoutRef.current) {
        clearTimeout(conflictTimeoutRef.current);
      }
      conflictTimeoutRef.current = setTimeout(() => {
        setConflicts([]);
      }, 5000);
    }
  }, [fileId, getUsersInFile, calculateCursorPosition, detectConflicts, onConflictDetected]);

  // Collaboration events are handled inside the collaboration hook; no manual subscription needed here.

  // Cleanup
  useEffect(() => {
    return () => {
      if (conflictTimeoutRef.current) {
        clearTimeout(conflictTimeoutRef.current);
      }
    };
  }, []);

  return (
    <>
      {/* Cursor overlay */}
      <div className={`absolute inset-0 pointer-events-none ${className}`}>
        {cursors.map(({ user, position, isConflicted }) => (
          <EnhancedRemoteCursor
            key={user.userId}
            user={user}
            position={position}
            showAvatar={showUserAvatars}
            showSelection={showSelections}
            showTyping={showTypingIndicators}
            isConflicted={isConflicted}
          />
        ))}
      </div>

      {/* Presence panel toggle */}
      {enablePresenceList && (
        <div className="absolute top-2 right-2 z-40">
          <InteractiveButton
            variant="secondary"
            size="sm"
            onClick={() => setShowPresencePanel(!showPresencePanel)}
            className="bg-white/90 dark:bg-gray-800/90 backdrop-blur-sm"
          >
            👥 {cursors.length}
          </InteractiveButton>
        </div>
      )}

      {/* Presence panel */}
      {showPresencePanel && (
        <div className="absolute top-12 right-2 w-64 bg-white dark:bg-gray-900 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 z-40">
          <div className="p-3 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
              Active Users ({cursors.length})
            </h3>
          </div>
          <div className="max-h-64 overflow-y-auto">
            {cursors.length === 0 ? (
              <div className="p-4 text-center text-sm text-gray-500 dark:text-gray-400">
                No other users in this file
              </div>
            ) : (
              cursors.map(({ user, isConflicted }) => (
                <div
                  key={user.userId}
                  className={`
                    p-3 border-b border-gray-100 dark:border-gray-800
                    ${isConflicted ? 'bg-red-50 dark:bg-red-900/20' : ''}
                  `}
                >
                  <div className="flex items-center gap-2">
                    <div
                      className="w-6 h-6 rounded-full flex items-center justify-center text-xs text-white font-semibold"
                      style={{ backgroundColor: user.userColor }}
                    >
                      {user.userName.charAt(0).toUpperCase()}
                    </div>
                    <div className="flex-1">
                      <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
                        {user.userName}
                      </div>
                      <div className="text-xs text-gray-500 dark:text-gray-400">
                        Line {user.cursor.position.line + 1}
                        {user.activity === 'typing' && ' • Typing...'}
                      </div>
                    </div>
                    {isConflicted && (
                      <div className="text-xs text-red-500">⚠️</div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Conflict notifications */}
      {conflicts.length > 0 && (
        <div className="absolute bottom-4 left-4 right-4 z-40">
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3">
            <div className="flex items-center gap-2">
              <span className="text-red-500">⚠️</span>
              <div className="flex-1">
                <div className="text-sm font-medium text-red-900 dark:text-red-100">
                  Edit Conflict Detected
                </div>
                <div className="text-xs text-red-700 dark:text-red-300">
                  {conflicts[0].message}
                </div>
              </div>
              <InteractiveButton
                variant="ghost"
                size="sm"
                onClick={() => setConflicts([])}
              >
                ✕
              </InteractiveButton>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default RealTimeCursorTracking;
