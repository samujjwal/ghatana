import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import { Box, Surface as Paper } from '@ghatana/ui';
import React from 'react';

export const ComponentNode: React.FC<NodeProps> = ({ data, selected }) => (
  <Paper
    elevation={selected ? 4 : 2}
    className="p-4 min-w-[140px] text-center rounded-lg transition-all duration-200" style={{ backgroundColor: selected ? '#e3f2fd' : '#f5f5f5', border: selected ? '2px solid #1976d2' : '1px solid #ddd' }}
  >
    <Box className="mb-2 font-bold text-[1.1em]">{data.icon || 'CMP'}</Box>
    <Box className="font-bold mb-2 text-[0.9em]">{data.label || 'Component'}</Box>
    {data.description && (
      <Box className="leading-tight text-[0.75em] text-[#666]">{data.description}</Box>
    )}

    <Handle id="top" type="target" position={Position.Top} style={{ background: '#1976d2' }} />
    <Handle id="right" type="source" position={Position.Right} style={{ background: '#1976d2' }} />
    <Handle id="bottom" type="source" position={Position.Bottom} style={{ background: '#1976d2' }} />
    <Handle id="left" type="target" position={Position.Left} style={{ background: '#1976d2' }} />
  </Paper>
);

export default ComponentNode;
