import { Box, Typography, Surface as Paper } from '@ghatana/ui';
import React, { useState } from 'react';

import { DraggableCanvas } from '@ghatana/yappc-ui';

import type { CanvasItem, ComponentType } from '@ghatana/yappc-ui';

const ComponentBuilderDemo: React.FC = () => {
  const [items, setItems] = useState<CanvasItem[]>([
    { id: '1', type: 'card', props: { style: { width: '300px' } } },
    { id: '2', type: 'text', props: { children: 'Welcome to the Component Builder' } },
    { id: '3', type: 'button', props: { children: 'Click me' } },
  ]);

  return (
    <Box className="p-6 max-w-[1200px] mx-auto">
      <Typography as="h4" gutterBottom>
        Component Builder
      </Typography>
      <Typography as="p" paragraph>
        Drag and drop components from the left panel to the canvas. You can reorder them by dragging.
      </Typography>
      
      <Paper elevation={2} className="mt-6 p-4">
        <DraggableCanvas 
          items={items} 
          onItemsChange={setItems} 
        />
      </Paper>
      
      <Box className="mt-8 p-4 rounded bg-[#f8f9fa]">
        <Typography as="h6" gutterBottom>
          Current Components (Debug View)
        </Typography>
        <pre style={{ fontSize: '12px', overflowX: 'auto' }}>
          {JSON.stringify(items, null, 2)}
        </pre>
      </Box>
    </Box>
  );
};

export default ComponentBuilderDemo;
