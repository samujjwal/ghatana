/**
 * Task Node - Task/Checkbox item for task management
 *
 * @doc.type component
 * @doc.purpose Task node with checkbox and status
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';
import { Box, Checkbox } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

export interface TaskNodeData {
  label?: string;
  status?: 'todo' | 'in-progress' | 'done';
  completed?: boolean;
  priority?: 'low' | 'medium' | 'high';
  onLabelChange?: (label: string) => void;
  onStatusChange?: (completed: boolean) => void;
  [key: string]: unknown;
}

const statusColors = {
  todo: '#94a3b8',
  'in-progress': '#3b82f6',
  done: '#10b981',
};

const priorityColors = {
  low: '#94a3b8',
  medium: '#f59e0b',
  high: '#ef4444',
};

interface TaskNodeProps extends NodeProps {
  data: TaskNodeData;
}

function TaskNodeComponent({ data, selected }: TaskNodeProps) {
  const {
    label = 'New Task',
    status = 'todo',
    completed = false,
    priority = 'medium',
    onLabelChange,
    onStatusChange,
  } = data;

  const [isEditing, setIsEditing] = useState(false);
  const [editedLabel, setEditedLabel] = useState(label);

  const handleDoubleClick = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setIsEditing(true);
  }, []);

  const handleBlur = useCallback(() => {
    setIsEditing(false);
    if (editedLabel !== label && onLabelChange) {
      onLabelChange(editedLabel);
    }
  }, [editedLabel, label, onLabelChange]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        setIsEditing(false);
        if (onLabelChange) {
          onLabelChange(editedLabel);
        }
      } else if (e.key === 'Escape') {
        setEditedLabel(label);
        setIsEditing(false);
      }
    },
    [editedLabel, label, onLabelChange]
  );

  const handleCheckboxChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      e.stopPropagation();
      if (onStatusChange) {
        onStatusChange(e.target.checked);
      }
    },
    [onStatusChange]
  );

  const statusColor = statusColors[status];
  const priorityColor = priorityColors[priority];

  return (
    <>
      <NodeResizer
        minWidth={200}
        minHeight={60}
        isVisible={selected}
        lineStyle={{
          borderColor: statusColor,
          borderWidth: 2,
        }}
        handleStyle={{
          backgroundColor: statusColor,
          width: 8,
          height: 8,
          borderRadius: 2,
        }}
      />

      <Box
        onDoubleClick={handleDoubleClick}
        className="w-full h-full flex items-center gap-2 p-4 bg-[#ffffff] border-[2px]" style={{ borderColor: selected ? statusColor : `${statusColor, backgroundColor: 'priorityColor' }}
      >
        {/* Priority indicator */}
        <Box
          className="h-full rounded shrink-0 w-[4px]" />

        {/* Checkbox */}
        <Checkbox
          checked={completed}
          onChange={handleCheckboxChange}
          onClick={(e) => e.stopPropagation()}
          className="p-0 text-xl"
        />

        {/* Task label */}
        <Box className="flex-1 min-w-0">
          {isEditing ? (
            <TextField
              value={editedLabel}
              onChange={(e) => setEditedLabel(e.target.value)}
              onBlur={handleBlur}
              onKeyDown={handleKeyDown}
              autoFocus
              fullWidth
              multiline
              variant="standard"
              InputProps={{
                disableUnderline: true,
                sx: {
                  fontSize: 14,
                  fontWeight: 500,
                  color: '#1f2937',
                },
              }}
            />
          ) : (
            <Box
              className="font-medium break-words text-sm text-[#1f2937] select-none"
            >
              {label}
            </Box>
          )}
        </Box>

        {/* Status badge */}
        <Box
          className="px-2 py-1" style={{ backgroundColor: statusColor }}
        >
          {status}
        </Box>
      </Box>

      {/* Connection handles */}
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        style={{ background: statusColor }}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="bottom"
        style={{ background: statusColor }}
      />
      <Handle
        type="target"
        position={Position.Left}
        id="left"
        style={{ background: statusColor }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        style={{ background: statusColor }}
      />
    </>
  );
}

export const TaskNode = memo(TaskNodeComponent);
TaskNode.displayName = 'TaskNode';
