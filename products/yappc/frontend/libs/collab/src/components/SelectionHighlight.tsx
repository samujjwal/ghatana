/**
 * Selection Highlight Component
 *
 * @description Renders selection highlights for collaborators
 * in a document or text editor.
 */

import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TextCursor } from '../DocumentCollaboration';
import { cn } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

export interface SelectionHighlightProps {
  cursors: TextCursor[];
  getPositionFromOffset: (offset: number) => { top: number; left: number; height: number };
  className?: string;
}

export interface SelectionRange {
  userId: string;
  userName: string;
  userColor: string;
  start: { top: number; left: number; height: number };
  end: { top: number; left: number; height: number };
  width: number;
}

// =============================================================================
// Main Component
// =============================================================================

export const SelectionHighlight: React.FC<SelectionHighlightProps> = ({
  cursors,
  getPositionFromOffset,
  className,
}) => {
  // Calculate selection ranges
  const selections = useMemo<SelectionRange[]>(() => {
    return cursors
      .filter((cursor) => cursor.selection && cursor.selection.start !== cursor.selection.end)
      .map((cursor) => {
        const start = getPositionFromOffset(cursor.selection!.start);
        const end = getPositionFromOffset(cursor.selection!.end);
        const width = end.left - start.left;

        return {
          userId: cursor.userId,
          userName: cursor.userName,
          userColor: cursor.userColor,
          start,
          end,
          width: Math.max(width, 0),
        };
      });
  }, [cursors, getPositionFromOffset]);

  return (
    <div className={cn('pointer-events-none', className)}>
      <AnimatePresence>
        {selections.map((selection) => (
          <motion.div
            key={`selection-${selection.userId}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 0.3 }}
            exit={{ opacity: 0 }}
            style={{
              position: 'absolute',
              top: selection.start.top,
              left: selection.start.left,
              width: selection.width,
              height: selection.start.height,
              backgroundColor: selection.userColor,
              borderRadius: 2,
            }}
          />
        ))}
      </AnimatePresence>
    </div>
  );
};

// =============================================================================
// Text Cursor Component
// =============================================================================

export interface TextCursorIndicatorProps {
  cursors: TextCursor[];
  getPositionFromOffset: (offset: number) => { top: number; left: number; height: number };
  showNames?: boolean;
  className?: string;
}

export const TextCursorIndicator: React.FC<TextCursorIndicatorProps> = ({
  cursors,
  getPositionFromOffset,
  showNames = true,
  className,
}) => {
  const cursorPositions = useMemo(() => {
    return cursors.map((cursor) => {
      const position = getPositionFromOffset(cursor.position);
      return {
        ...cursor,
        ...position,
      };
    });
  }, [cursors, getPositionFromOffset]);

  return (
    <div className={cn('pointer-events-none', className)}>
      <AnimatePresence>
        {cursorPositions.map((cursor) => (
          <motion.div
            key={`cursor-${cursor.userId}`}
            initial={{ opacity: 0, scaleY: 0 }}
            animate={{ opacity: 1, scaleY: 1 }}
            exit={{ opacity: 0, scaleY: 0 }}
            style={{
              position: 'absolute',
              top: cursor.top,
              left: cursor.left,
              width: 2,
              height: cursor.height,
              backgroundColor: cursor.userColor,
              transformOrigin: 'top',
            }}
          >
            {/* Cursor animation (blinking) */}
            <motion.div
              animate={{ opacity: [1, 0] }}
              transition={{ duration: 0.8, repeat: Infinity, repeatType: 'reverse' }}
              className="w-full h-full"
              style={{ backgroundColor: cursor.userColor }}
            />

            {/* Name tag */}
            {showNames && (
              <motion.div
                initial={{ opacity: 0, y: -5 }}
                animate={{ opacity: 1, y: 0 }}
                style={{
                  position: 'absolute',
                  left: 0,
                  top: -18,
                  backgroundColor: cursor.userColor,
                  color: 'white',
                  padding: '1px 6px',
                  borderRadius: 3,
                  fontSize: 10,
                  fontWeight: 500,
                  whiteSpace: 'nowrap',
                }}
              >
                {cursor.userName}
              </motion.div>
            )}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
};

export default SelectionHighlight;
