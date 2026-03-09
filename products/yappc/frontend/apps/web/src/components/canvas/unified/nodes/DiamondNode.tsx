/**
 * Diamond Node - Diamond shape for decision points
 *
 * @doc.type component
 * @doc.purpose Diamond shape node for flowcharts
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';
import { Box } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

export interface DiamondNodeData {
  label?: string;
  color?: string;
  backgroundColor?: string;
  fontSize?: number;
  onLabelChange?: (label: string) => void;
  [key: string]: unknown;
}

interface DiamondNodeProps extends NodeProps {
  data: DiamondNodeData;
}

function DiamondNodeComponent({ data, selected }: DiamondNodeProps) {
  const {
    label = 'Diamond',
    color = '#9c27b0',
    backgroundColor = '#f3e5f5',
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
        minHeight={100}
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
        keepAspectRatio={true}
      />

      <Box
        onDoubleClick={handleDoubleClick}
        className="w-full h-full relative" style={{ cursor: isEditing ? 'text' : 'move', transform: 'translate(-50%' }}
      >
        {/* Diamond SVG shape */}
        <svg
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            pointerEvents: 'none',
          }}
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
        >
          <polygon
            points="50,5 95,50 50,95 5,50"
            fill={backgroundColor}
            stroke={selected ? color : `${color}80`}
            strokeWidth="2"
            style={{
              filter: selected
                ? `drop-shadow(0 0 4px ${color}40)`
                : 'drop-shadow(0 2px 4px rgba(0,0,0,0.1))',
              transition: 'all 0.15s ease',
            }}
          />
        </svg>

        {/* Content */}
        <Box
          className="absolute flex items-center justify-center text-center top-[50%] left-[50%] w-[60%] max-h-[60%] pointer-events-auto" >
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
              className="font-medium break-words text-[#1f2937] select-none"
            >
              {label}
            </Box>
          )}
        </Box>
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

export const DiamondNode = memo(DiamondNodeComponent);
DiamondNode.displayName = 'DiamondNode';
