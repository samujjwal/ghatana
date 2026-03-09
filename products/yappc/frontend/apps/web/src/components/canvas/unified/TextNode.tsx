/**
 * Text Node - Rich text content
 *
 * Renders editable text content on canvas
 */

import React, { useState, useRef, useEffect } from 'react';
import { Box, Typography } from '@ghatana/ui';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';

export interface TextNodeData {
  text: string;
  fontSize?: number;
  fontWeight?: 'normal' | 'bold' | '500' | '600' | '700';
  color?: string;
  onTextChange?: (text: string) => void;
}

/**
 * Text Node Component
 */
export const TextNode = React.memo(
  ({ data, selected, id }: NodeProps<TextNodeData>) => {
    const {
      text = 'Text',
      fontSize = 16,
      fontWeight = 'normal',
      color = '#333',
      onTextChange,
    } = data;

    const [isEditing, setIsEditing] = useState(false);
    const [localText, setLocalText] = useState(text);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

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
          minWidth={200}
          minHeight={100}
          isVisible={selected}
          lineStyle={{
            borderColor: '#1976d2',
            borderWidth: 2,
          }}
          handleStyle={{
            backgroundColor: '#1976d2',
            width: 8,
            height: 8,
            borderRadius: 2,
          }}
        />
        <Box
          onDoubleClick={handleDoubleClick}
          className="w-full h-full bg-[#fafafa] border-[2px] rounded flex flex-col overflow-hidden relative pointer-events-auto p-4" style={{ borderColor: selected ? 'primary.main' : '#e0e0e0', boxShadow: selected ? 3 : 1, cursor: isEditing ? 'text' : 'move' }}
        >
          {isEditing ? (
            <textarea
              ref={textareaRef}
              value={localText}
              onChange={handleChange}
              onBlur={handleBlur}
              onKeyDown={handleKeyDown}
              style={{
                width: '100%',
                height: '100%',
                border: 'none',
                outline: 'none',
                background: 'transparent',
                resize: 'none',
                fontFamily: 'inherit',
                fontSize: `${fontSize}px`,
                fontWeight: fontWeight,
                color: color,
                lineHeight: 1.5,
                padding: 0,
              }}
            />
          ) : (
            <Typography
              style={{ fontSize: `${fontSize }}
            >
              {localText || 'Double-click to edit'}
            </Typography>
          )}

          {/* Connection Handles */}
          <Handle
            type="source"
            position={Position.Bottom}
            style={{
              width: 8,
              height: 8,
              background: '#1976d2',
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
              background: '#1976d2',
              border: 'none',
              opacity: selected ? 1 : 0,
            }}
          />
        </Box>
      </>
    );
  }
);

TextNode.displayName = 'TextNode';
