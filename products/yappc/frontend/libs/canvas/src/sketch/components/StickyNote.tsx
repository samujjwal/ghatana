/**
 * @ghatana/yappc-sketch - StickyNote Component
 *
 * Production-grade sticky note component for whiteboard annotations.
 *
 * @doc.type component
 * @doc.purpose Sticky note for annotations
 * @doc.layer presentation
 * @doc.pattern Component
 */

import { X as CloseIcon, Pencil as EditIcon } from 'lucide-react';
import { Box, IconButton, Surface as Paper } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import React, { useState, useRef, useEffect } from 'react';

import type { StickyNoteData } from '../types';

/**
 * Available sticky note colors
 */
export const STICKY_COLORS = [
  '#fff740', // Yellow
  '#ff7eb9', // Pink
  '#7afcff', // Cyan
  '#feff9c', // Light Yellow
  '#ff9f7a', // Orange
  '#c7ceea', // Light Purple
  '#98fb98', // Pale Green
  '#ffa07a', // Light Salmon
] as const;

/**
 * Props for StickyNote component
 */
export interface StickyNoteProps {
  /** Sticky note data */
  data: StickyNoteData;
  /** Whether the note is selected */
  isSelected?: boolean;
  /** Whether the note is in edit mode */
  isEditing?: boolean;
  /** Callback when note data is updated */
  onUpdate: (data: Partial<StickyNoteData>) => void;
  /** Callback when note is deleted */
  onDelete: () => void;
  /** Callback when edit mode starts */
  onStartEdit: () => void;
  /** Callback when edit mode ends */
  onEndEdit: () => void;
  /** Callback when note is clicked */
  onClick?: () => void;
  /** Callback when note is double-clicked */
  onDoubleClick?: () => void;
  /** Whether the note is draggable */
  draggable?: boolean;
}

/**
 * Sticky note component for whiteboard annotations.
 *
 * Features:
 * - Editable text content
 * - Color palette selection
 * - Drag and drop support
 * - Selection state
 * - Keyboard shortcuts (Enter to save, Escape to cancel)
 *
 * @param props - Component properties
 * @returns Rendered sticky note
 *
 * @example
 * ```tsx
 * <StickyNote
 *   data={{ id: '1', x: 100, y: 100, width: 200, height: 200, content: 'Note', color: '#fff740' }}
 *   isSelected={true}
 *   onUpdate={(data) => updateNote(data)}
 *   onDelete={() => deleteNote()}
 *   onStartEdit={() => setEditing(true)}
 *   onEndEdit={() => setEditing(false)}
 * />
 * ```
 */
export const StickyNote: React.FC<StickyNoteProps> = ({
  data,
  isSelected = false,
  isEditing = false,
  onUpdate,
  onDelete,
  onStartEdit,
  onEndEdit,
  onClick,
  onDoubleClick,
  draggable = true,
}) => {
  const [localContent, setLocalContent] = useState(data.content);
  const textFieldRef = useRef<HTMLInputElement>(null);

  // Focus text field when entering edit mode
  useEffect(() => {
    if (isEditing && textFieldRef.current) {
      textFieldRef.current.focus();
      textFieldRef.current.select();
    }
  }, [isEditing]);

  // Sync local content with data
  useEffect(() => {
    setLocalContent(data.content);
  }, [data.content]);

  const handleContentChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setLocalContent(event.target.value);
  };

  const handleContentBlur = () => {
    onUpdate({ content: localContent });
    onEndEdit();
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      handleContentBlur();
    } else if (event.key === 'Escape') {
      setLocalContent(data.content);
      onEndEdit();
    }
  };

  const handleColorChange = (color: string) => {
    onUpdate({ color });
  };

  const handleDoubleClick = (event: React.MouseEvent) => {
    event.stopPropagation();
    onDoubleClick?.();
    onStartEdit();
  };

  return (
    <Paper
      elevation={isSelected ? 8 : 4}
      data-testid="sticky-note"
      data-sticky-id={data.id}
      className="absolute rounded overflow-hidden hover:shadow-xl" style={{ left: data.x, top: data.y, width: data.width, height: data.height, backgroundColor: data.color, cursor: isEditing ? 'text' : draggable ? 'move' : 'default', border: isSelected ? '2px solid #1976d2' : '1px solid rgba(0,0,0,0.1)', transition: 'box-shadow 0.2s ease' }} onClick={onClick}
      onDoubleClick={handleDoubleClick}
    >
      {/* Toolbar - shown when selected */}
      {isSelected && (
        <Box
          className="absolute flex gap-1 top-[-36px] right-[0px] z-10"
        >
          <IconButton
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              onStartEdit();
            }}
            className="bg-white dark:bg-gray-900 shadow"
            aria-label="Edit sticky note"
          >
            <EditIcon size={16} />
          </IconButton>
          <IconButton
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
            color="error"
            className="bg-white dark:bg-gray-900 shadow"
            aria-label="Delete sticky note"
          >
            <CloseIcon size={16} />
          </IconButton>
        </Box>
      )}

      {/* Color palette - shown when selected */}
      {isSelected && (
        <Box
          className="absolute flex gap-1 top-[-36px] left-[0px] z-10"
        >
          {STICKY_COLORS.map((color) => (
            <Box
              key={color}
              onClick={(e) => {
                e.stopPropagation();
                handleColorChange(color);
              }}
              className="w-[20px] h-[20px] border border-solid border-[rgba(0,0,0,0.2)] rounded-full cursor-pointer transition-all duration-300 hover:scale-[1.2]" style={{ backgroundColor: color, boxShadow: data.color === color ? '0 0 0 2px #1976d2' : 'none', fontFamily: "'Segoe UI', 'Comic Sans MS', cursive" }}
              role="button"
              aria-label={`Change color to ${color}`}
            />
          ))}
        </Box>
      )}

      {/* Content area */}
      <Box className="p-3 h-full overflow-hidden">
        {isEditing ? (
          <TextField
            inputRef={textFieldRef}
            value={localContent}
            onChange={handleContentChange}
            onBlur={handleContentBlur}
            onKeyDown={handleKeyDown}
            multiline
            fullWidth
            variant="standard"
            placeholder="Type your note..."
            InputProps={{
              disableUnderline: true,
              style: {
                fontSize: data.fontSize || 14,
                fontFamily: "'Segoe UI', 'Comic Sans MS', cursive",
                height: '100%',
              },
            }}
            className="[&_.MuiInputBase-root]:h-full [&_.MuiInputBase-root]:items-start [&_.MuiInputBase-input]:!h-full [&_.MuiInputBase-input]:overflow-auto"
          />
        ) : (
          <Box
            className="whitespace-pre-wrap break-words h-full overflow-hidden text-[rgba(0,0,0,0.87)]" style={{ fontSize: data.fontSize || 14 }} >
            {data.content || 'Double-click to edit...'}
          </Box>
        )}
      </Box>
    </Paper>
  );
};

export default StickyNote;
