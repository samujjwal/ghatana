import { Handle, Position } from '@xyflow/react';
import { Box, Surface as Paper } from '@ghatana/ui';
import React from 'react';

import type { NodeProps } from '@xyflow/react';

export const DataNode: React.FC<NodeProps> = ({ data, selected }) => (
  <Paper
    elevation={selected ? 4 : 2}
    className="p-4 min-w-[140px] text-center rounded-lg transition-all duration-200" style={{ backgroundColor: selected ? '#e8f5e8' : '#fafafa', border: selected ? '2px solid #4caf50' : '1px solid #ddd' }}
  >
    <Box className="mb-2 font-bold text-[1.1em]">{data.icon || 'DB'}</Box>
    <Box className="font-bold mb-2 text-[0.9em]">{data.label || 'Data'}</Box>
    {data.type && (
      <Box
        className="inline-block text-[0.75em] text-[#666] bg-[#c8e6c9] p-[2px 6px] rounded-[64px]"
      >
        {data.type}
      </Box>
    )}

    <Handle id="top" type="target" position={Position.Top} style={{ background: '#4caf50' }} />
    <Handle id="right" type="source" position={Position.Right} style={{ background: '#4caf50' }} />
    <Handle id="bottom" type="source" position={Position.Bottom} style={{ background: '#4caf50' }} />
    <Handle id="left" type="target" position={Position.Left} style={{ background: '#4caf50' }} />
  </Paper>
);

export default DataNode;
