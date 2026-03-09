import { Handle, Position } from '@xyflow/react';
import { Box, Surface as Paper } from '@ghatana/ui';
import React from 'react';

import type { NodeProps } from '@xyflow/react';

export const FlowNode: React.FC<NodeProps> = ({ data, selected }) => (
  <Paper
    elevation={selected ? 4 : 2}
    className="p-4 min-w-[140px] text-center rounded-lg transition-all duration-200" style={{ backgroundColor: selected ? '#fff3e0' : '#fafafa', border: selected ? '2px solid #ff9800' : '1px solid #ddd' }}
  >
    <Box className="mb-2 text-[1.5em]">{data.icon || '⚡'}</Box>
    <Box className="font-bold mb-2 text-[0.9em]">{data.label || 'Process'}</Box>
    {data.description && (
      <Box className="leading-tight text-[0.75em] text-[#666]">{data.description}</Box>
    )}

    <Handle id="top" type="target" position={Position.Top} style={{ background: '#ff9800' }} />
    <Handle id="right" type="source" position={Position.Right} style={{ background: '#ff9800' }} />
    <Handle id="bottom" type="source" position={Position.Bottom} style={{ background: '#ff9800' }} />
    <Handle id="left" type="target" position={Position.Left} style={{ background: '#ff9800' }} />
  </Paper>
);

export default FlowNode;
