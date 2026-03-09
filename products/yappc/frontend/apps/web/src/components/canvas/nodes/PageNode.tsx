import { Handle, Position } from '@xyflow/react';
import { Box, Typography, Surface as Paper } from '@ghatana/ui';
import React from 'react';

import type { NodeProps } from '@xyflow/react';

export const PageNode: React.FC<NodeProps> = ({ data, selected }) => (
  <Paper
    elevation={selected ? 4 : 2}
    className="p-4 min-w-[160px] text-center rounded-lg transition-all duration-200" style={{ backgroundColor: selected ? '#fff3e0' : '#f5f5f5', border: selected ? '2px solid #ff9800' : '1px solid #ddd' }}
  >
    <Box className="mb-2 font-bold text-[1.1em]">{data.icon || 'PG'}</Box>
    <Typography variant="subtitle2" className="font-bold mb-1">
      {data.label || 'Page'}
    </Typography>
    {data.description && (
      <Typography variant="caption" color="text.secondary" className="leading-tight">
        {data.description}
      </Typography>
    )}

    <Handle
      id="top"
      type="target"
      position={Position.Top}
      style={{ background: '#ff9800', width: 10, height: 10 }}
    />
    <Handle
      id="right"
      type="source"
      position={Position.Right}
      style={{ background: '#ff9800', width: 10, height: 10 }}
    />
    <Handle
      id="bottom"
      type="source"
      position={Position.Bottom}
      style={{ background: '#ff9800', width: 10, height: 10 }}
    />
    <Handle
      id="left"
      type="target"
      position={Position.Left}
      style={{ background: '#ff9800', width: 10, height: 10 }}
    />
  </Paper>
);

export default PageNode;
