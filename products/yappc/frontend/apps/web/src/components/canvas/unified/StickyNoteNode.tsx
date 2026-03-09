/**
 * Sticky Note Node - Quick brainstorming notes
 *
 * Inline editable sticky notes like Miro
 */

import React, { useState, useRef, useEffect } from 'react';
import { Box, Typography } from '@ghatana/ui';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';

export interface StickyNoteData {
  text: string;
  color: 'yellow' | 'pink' | 'blue' | 'green' | 'purple';
  fontSize: 'small' | 'medium' | 'large';
  onTextChange?: (text: string) => void;
}

const colorMap = {
  yellow: { bg: '#fff9c4', border: '#fbc02d' },
  pink: { bg: '#f8bbd0', border: '#e91e63' },
  blue: { bg: '#bbdefb', border: '#2196f3' },
  green: { bg: '#c8e6c9', border: '#4caf50' },
  purple: { bg: '#e1bee7', border: '#9c27b0' },
};

const fontSizeMap = {
  small: '0.875rem',
  medium: '1rem',
  large: '1.25rem',
};

/**
 * Sticky Note Component
 */
export const StickyNoteNode = React.memo(
  ({ data, selected, id }: NodeProps<StickyNoteData>) => {
    const {
      text = '',
      color = 'yellow',
      fontSize = 'medium',
      onTextChange,
    } = data;
    const [isEditing, setIsEditing] = useState(false);
    const [localText, setLocalText] = useState(text);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const colors = colorMap[color] || colorMap['yellow']; // Fallback to yellow if color undefined

    // Auto-focus when editing
    useEffect(() => {
      if (isEditing && textareaRef.current) {
        textareaRef.current.focus();
        textareaRef.current.select();
      }
    }, [isEditing]);

    // Handle double-click to edit
    const handleDoubleClick = (e: React.MouseEvent) => {
      e.stopPropagation();
      setIsEditing(true);
    };

    // Handle blur - save changes
    const handleBlur = () => {
      setIsEditing(false);
      if (localText !== text && onTextChange) {
        onTextChange(localText);
      }
    };

    // Handle text change
    const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      setLocalText(e.target.value);
    };

    // Handle key press
    const handleKeyDown = (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        setLocalText(text);
        setIsEditing(false);
      } else if (e.key === 'Enter' && e.metaKey) {
        handleBlur();
      }
    };

    return (
      <>
        <NodeResizer
          minWidth={150}
          minHeight={150}
          isVisible={selected}
          lineStyle={{
            borderColor: colors.border,
            borderWidth: 2,
          }}
          handleStyle={{
            backgroundColor: colors.border,
            width: 8,
            height: 8,
            borderRadius: '50%',
          }}
        />
        <Box
          onDoubleClick={handleDoubleClick}
          className="w-full h-full border-[2px] rounded-sm flex flex-col overflow-hidden relative pointer-events-auto"
          style={{ transform: 'rotate(-0.5deg)', backgroundColor: colors.bg, borderColor: selected ? '#2563eb' : colors.border, boxShadow: selected ? '0 1px 3px rgba(0,0,0,0.15)' : '0 2px 4px rgba(0,0,0,0.1)', cursor: isEditing ? 'text' : 'move' }}
        >
          {/* Sticky note "tape" effect */}
          <Box
            className="absolute top-0 left-[40%] w-[20%] h-[10px] bg-amber-200/60 rounded-b-sm"
            style={{ boxShadow: '0 1px 2px rgba(0,0,0,0.1)' }}
          />

          {/* Content */}
          <Box
            className="flex-1 p-4 flex items-start overflow-auto"
          >
            {isEditing ? (
              <textarea
                ref={textareaRef}
                value={localText}
                onChange={handleChange}
                onBlur={handleBlur}
                onKeyDown={handleKeyDown}
                placeholder="Type your note..."
                style={{
                  width: '100%',
                  height: '100%',
                  border: 'none',
                  outline: 'none',
                  background: 'transparent',
                  resize: 'none',
                  fontFamily: 'inherit',
                  fontSize: fontSizeMap[fontSize],
                  color: '#333',
                  lineHeight: 1.5,
                }}
              />
            ) : (
              <Typography
                className="whitespace-pre-wrap break-words w-full text-[#333] leading-normal" >
                {localText || 'Double-click to edit'}
              </Typography>
            )}
          </Box>

          {/* Connection Handles */}
          <Handle
            type="source"
            position={Position.Bottom}
            style={{
              width: 8,
              height: 8,
              background: colors.border,
              border: 'none',
              opacity: selected ? 1 : 0,
            }}
          />
          <Handle
            type="target"
            position={Position.Top}
            style={{
              width: 8,
              height: 8,
              background: colors.border,
              border: 'none',
              opacity: selected ? 1 : 0,
            }}
          />
        </Box>
      </>
    );
  }
);

StickyNoteNode.displayName = 'StickyNoteNode';
