import { Box, Button } from '@ghatana/ui';
import { useState } from 'react';

import { PerformancePanel } from './PerformancePanel';

import type { Meta, StoryObj } from '@storybook/react';

/**
 * Performance monitoring components for canvas operations.
 * Displays metrics like FPS, element count, and render time.
 */
const meta: Meta<typeof PerformancePanel> = {
  title: 'Canvas/Performance',
  component: PerformancePanel,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Performance monitoring components for canvas operations with consistent data-testid attributes for E2E testing.',
      },
    },
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof PerformancePanel>;

/**
 * Default performance panel with metrics.
 */
export const Default: Story = {
  args: {
    open: true,
    onClose: () => console.log('Close clicked'),
    metrics: {
      fps: 58,
      elements: 42,
      frameTime: 17,
      renderTime: 9,
      memoryUsage: 85,
    },
  },
};

/**
 * Performance panel with monitoring enabled.
 */
export const MonitoringEnabled: Story = {
  args: {
    open: true,
    onClose: () => console.log('Close clicked'),
    metrics: {
      fps: 58,
      elements: 42,
      frameTime: 17,
      renderTime: 9,
      memoryUsage: 85,
    },
    monitoringEnabled: true,
  },
};

/**
 * Interactive performance panel that can be toggled.
 */
export const Interactive: StoryObj<typeof PerformancePanel> = {
  render: () => {
     
    const [open, setOpen] = useState(false);
     
    const [enabled, setEnabled] = useState(false);
    
    return (
      <Box className="w-full h-full p-4">
        <Button 
          variant="contained" 
          onClick={() => setOpen(true)}
          data-testid="performance-button"
        >
          Open Performance Panel
        </Button>
        
        <PerformancePanel
          open={open}
          onClose={() => setOpen(false)}
          metrics={{
            fps: enabled ? 58 : 60,
            elements: 42,
            frameTime: enabled ? 17 : 16,
            renderTime: enabled ? 9 : 8,
            memoryUsage: 85,
          }}
          monitoringEnabled={enabled}
          onEnableMonitoring={() => setEnabled(true)}
        />
      </Box>
    );
  },
};
