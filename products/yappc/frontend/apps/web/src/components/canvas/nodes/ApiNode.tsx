import { Handle, Position } from '@xyflow/react';
import { Box, Surface as Paper } from '@ghatana/ui';
import React from 'react';

import type { NodeProps } from '@xyflow/react';

export const ApiNode: React.FC<NodeProps> = ({ data, selected }) => (
  <Paper
    elevation={selected ? 4 : 2}
    className="p-4 min-w-[140px] text-center rounded-lg transition-all duration-200" style={{ backgroundColor: selected ? '#f3e5f5' : '#fafafa', border: selected ? '2px solid #9c27b0' : '1px solid #ddd' }}
  >
    <Box className="mb-2 font-bold text-[1.1em]">{data.icon || 'API'}</Box>
    <Box className="font-bold mb-2 text-[0.9em]">{data.label || 'API'}</Box>
    {data.method && (
      <Box
        className="inline-block text-[0.75em] text-[#666] bg-[#e1bee7] p-[2px 6px] rounded-[64px]"
      >
        {data.method}
      </Box>
    )}

    <Handle id="top" type="target" position={Position.Top} style={{ background: '#9c27b0' }} />
    <Handle id="right" type="source" position={Position.Right} style={{ background: '#9c27b0' }} />
    <Handle id="bottom" type="source" position={Position.Bottom} style={{ background: '#9c27b0' }} />
    <Handle id="left" type="target" position={Position.Left} style={{ background: '#9c27b0' }} />
  </Paper>
);

export default ApiNode;
