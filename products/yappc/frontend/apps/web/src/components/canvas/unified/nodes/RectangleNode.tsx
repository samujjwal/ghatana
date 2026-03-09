/**
 * Rectangle Node - Rectangle shape for flowcharts and diagrams
 *
 * @doc.type component
 * @doc.purpose Rectangle shape node for canvas
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';
import { Box } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

export interface RectangleNodeData {
  label?: string;
  color?: string;
  backgroundColor?: string;
  fontSize?: number;
  onLabelChange?: (label: string) => void;
  [key: string]: unknown;
}

interface RectangleNodeProps extends NodeProps {
  data: RectangleNodeData;
}

function RectangleNodeComponent({ data, selected }: RectangleNodeProps) {
  const {
    label = 'Rectangle',
    color = '#2196f3',
    backgroundColor = '#e3f2fd',
    fontSize = 14,
    onLabelChange,
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
      if (e.key === 'Enter') {
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

  return (
    <>
      <NodeResizer
        minWidth={100}
        minHeight={60}
        isVisible={selected}
        lineStyle={{
          borderColor: color,
          borderWidth: 2,
        }}
        handleStyle={{
          backgroundColor: color,
          width: 10,
          height: 10,
          borderRadius: 2,
        }}
      />

      <Box
        onDoubleClick={handleDoubleClick}
        className="w-full h-full border-[2px]" style={{ borderColor: selected ? color : `${color }}
      >
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
                fontSize,
                fontWeight: 500,
                textAlign: 'center',
                color: '#1f2937',
              },
            }}
          />
        ) : (
          <Box
            className="font-medium text-center break-words w-full text-[#1f2937] select-none"
          >
            {label}
          </Box>
        )}
      </Box>

      {/* Connection handles */}
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        style={{ background: color }}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="bottom"
        style={{ background: color }}
      />
      <Handle
        type="target"
        position={Position.Left}
        id="left"
        style={{ background: color }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        style={{ background: color }}
      />
    </>
  );
}

export const RectangleNode = memo(RectangleNodeComponent);
RectangleNode.displayName = 'RectangleNode';
