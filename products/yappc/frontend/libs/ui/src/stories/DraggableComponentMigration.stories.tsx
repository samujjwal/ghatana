/**
 * DraggableComponent Stories
 *
 * Interactive demonstration of the DraggableComponent refactored to use design system patterns
 */

import { Box, Typography, Surface as Paper, Stack, Chip } from '@ghatana/ui';
import React, { useState } from 'react';

import { DraggableCanvas } from '../components/canvas';

import type { ComponentType } from '../components/canvas';
import type { Meta, StoryObj } from '@storybook/react';

/**
 * Component showcasing the migration highlights
 */
function MigrationHighlights() {
  return (
    <Paper className="p-6 mb-6 bg-green-100 dark:bg-green-900/30 text-white">
      <Typography as="h5" gutterBottom>
        ✅ Design System Migration Highlights
      </Typography>
      <Stack spacing={1} className="mt-4">
        <Box>
          <Chip label="BEFORE" tone="danger" size="sm" className="mr-2" />
          <Typography component="span" as="p" className="text-sm">
            <code>style={'{'}backgroundColor: '#1976d2'{'}'}</code>
          </Typography>
        </Box>
        <Box>
          <Chip label="AFTER" tone="success" size="sm" className="mr-2" />
          <Typography component="span" as="p" className="text-sm">
            <code>sx={'{'}bgcolor: 'primary.main'{'}'}</code>
          </Typography>
        </Box>

        <Box className="mt-4">
          <Chip label="BEFORE" tone="danger" size="sm" className="mr-2" />
          <Typography component="span" as="p" className="text-sm">
            <code>style={'{'}padding: '16px', margin: '8px'{'}'}</code>
          </Typography>
        </Box>
        <Box>
          <Chip label="AFTER" tone="success" size="sm" className="mr-2" />
          <Typography component="span" as="p" className="text-sm">
            <code>sx={'{'}p: 2, m: 1{'}'}</code>
          </Typography>
        </Box>

        <Box className="mt-4">
          <Chip label="BEFORE" tone="danger" size="sm" className="mr-2" />
          <Typography component="span" as="p" className="text-sm">
            <code>style={'{'}borderRadius: '8px'{'}'}</code>
          </Typography>
        </Box>
        <Box>
          <Chip label="AFTER" tone="success" size="sm" className="mr-2" />
          <Typography component="span" as="p" className="text-sm">
            <code>sx={'{'}borderRadius: 1{'}'}</code>
          </Typography>
        </Box>

        <Box className="mt-6">
          <Typography as="h6" gutterBottom>
            Key Improvements:
          </Typography>
          <Stack spacing={0.5}>
            <Typography as="p" className="text-sm">
              • All hardcoded colors replaced with theme palette tokens
            </Typography>
            <Typography as="p" className="text-sm">
              • Pixel spacing replaced with spacing scale (p: 2 = 16px)
            </Typography>
            <Typography as="p" className="text-sm">
              • Using sx prop instead of style prop for MUI components
            </Typography>
            <Typography as="p" className="text-sm">
              • Consistent hover states using theme colors
            </Typography>
            <Typography as="p" className="text-sm">• Full dark mode support via theme</Typography>
          </Stack>
        </Box>
      </Stack>
    </Paper>
  );
}

/**
 * Interactive draggable canvas demo
 */
const DraggableCanvasDemo = () => {
  const [items, setItems] = useState<
    Array<{ id: string; type: ComponentType; props?: Record<string, unknown> }>
  >([
    { id: 'item-1', type: 'button', props: {} },
    { id: 'item-2', type: 'input', props: {} },
    { id: 'item-3', type: 'card', props: {} },
  ]);

  return (
    <Box className="p-6">
      <Typography as="h3" gutterBottom>
        DraggableComponent - Design System Integration
      </Typography>
      <Typography as="p" color="text.secondary" paragraph>
        This component demonstrates the successful migration from hardcoded styles to design system
        tokens. Try dragging components to reorder them!
      </Typography>

      <MigrationHighlights />

      <Paper className="p-0 overflow-hidden rounded-lg">
        <DraggableCanvas items={items} onItemsChange={setItems} />
      </Paper>

      <Paper className="p-6 mt-6 bg-sky-600 text-white">
        <Typography as="h6" gutterBottom>
          💡 How It Works
        </Typography>
        <Stack spacing={1}>
          <Typography as="p" className="text-sm">
            1. <strong>Click</strong> components in the library to add them to the canvas
          </Typography>
          <Typography as="p" className="text-sm">
            2. <strong>Drag</strong> components in the canvas to reorder them
          </Typography>
          <Typography as="p" className="text-sm">
            3. <strong>Click</strong> a component in the canvas to select it (shows blue outline)
          </Typography>
          <Typography as="p" className="text-sm">
            4. All styling uses theme tokens - try switching between light/dark mode!
          </Typography>
        </Stack>
      </Paper>
    </Box>
  );
};

/**
 * Code comparison view
 */
const CodeComparison = () => {
  return (
    <Box className="p-6">
      <Typography as="h3" gutterBottom>
        Before vs After: Code Comparison
      </Typography>

      <Stack spacing={4} className="mt-6">
        {/* Style prop → sx prop */}
        <Paper className="p-6">
          <Typography as="h5" gutterBottom>
            1. Style Prop → SX Prop Migration
          </Typography>
          <Box className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4">
            <Box>
              <Typography as="h6" color="error.main" gutterBottom>
                ❌ Before (No Theme Access)
              </Typography>
              <Paper
                className="p-4 text-sm overflow-auto font-mono bg-gray-900" >
                <pre style={{ margin: 0, color: 'common.white', color: 'common.white', color: 'common.white', color: 'common.white' }}>
{`<Box
  draggable
  style={{
    cursor: 'grab',
    margin: '8px 0',
    padding: '8px',
    borderRadius: '8px',
  }}
>
  <ComponentRenderer />
</Box>`}
                </pre>
              </Paper>
            </Box>

            <Box>
              <Typography as="h6" color="success.main" gutterBottom>
                ✅ After (Theme-Aware)
              </Typography>
              <Paper
                clbgcolor: 'grey.900', color: 'common.white' */
              >
                <pre style={{ margin: 0 }}>
{`<Box
  draggable
  className="cursor-grab my-2 p-2 rounded"
>
  <ComponentRenderer />
</Box>`}
                </pre>
              </Paper>
            </Box>
          </Box>
        </Paper>

        {/* Hardcoded colors → palette */}
        <Paper className="p-6">
          <Typography as="h5" gutterBottom>
            2. Hardcoded Colors → Palette Tokens
          </Typography>
          <Box className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4">
            <Box>
              <Typography as="h6" color="error.main" gutterBottom>
                ❌ Before (Hardcoded Hex)
              </Typography>
              <Paper
                className="p-4 text-sm ovolor: 'common.white' */
              >
                <pre style={{ margin: 0, color: 'common.white', borderRight: '1px solid #e0e0e0', color: 'common.white' }}>
{`<Box
  className="bg-[#f5f5f5] border-[#e0e0e0] hover:bg-[#eeeeee]"
/>`}
                </pre>
              </Paper>
            </Box>

            <Box>
              <Typography as="h6" color="success.main" gutterBottom>
                ✅ After (Theme Palette)
              </Typography>
              <Paper className="p-4 overflow-auto bg-gray-900 text-white"
              >
                <pre style={{ margin: 0 }}>
{`<Box
  className="bg-gray-50 dark:bg-gray-800"
/>`}
                </pre>
              </Paper>
            </Box>
          </Box>
        </Paper>

        {/* Component library theming */}
        <Paper className="p-6">
          <Typography as="h5" gutterBottom>
            3. Component Library Theming
          </Typography>
          <Box className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4">
            <Box>
              <Typography as="h6" color="error.main" gutterBottom>
                ❌ Before (Mixed Approach)
              </Typography>
              <Paper maining sx: bgcolor: 'grey.900', color: 'common.white' */
              >
                <pre style={{ margin: 0 }}>
{`'1px solid #e0e0e0' */
>
  <Typography as="h6">
    Component Library
  </Typography>
</Box>`}
                </pre>
              </Paper>
            </Box>

            <Box>
              <Typography as="h6" color="success.main" gutterBottom>
                ✅ After (Fully Themed)
              </Typography>
              <Paper
                className="p-4 text-sm overflow-auto font-mono bg-gray-900" >
                <pre style={{ margin: 0 }}>
{`<Box
  className="p-4 border-r border-gray-200 dark:border-gray-700"
>
  <Typography as="h6" component="h3">
    Component Library
  </Typography>
</Box>`}
                </pre>
              </Paper>
            </Box>
          </Box>
        </Paper>
      </Stack>

      <Paper className="p-6 mt-8 bg-green-600 text-white">
        <Typography as="h6" gutterBottom>
          ✅ Migration Complete
        </Typography>
        <Stack spacing={1}>
          <Typography>• 100% of hardcoded colors replaced with palette tokens</Typography>
          <Typography>• All spacing uses theme spacing scale</Typography>
          <Typography>• sx prop used throughout for MUI components</Typography>
          <Typography>• Full TypeScript type safety maintained</Typography>
          <Typography>• Dark mode ready (automatically adapts to theme)</Typography>
        </Stack>
      </Paper>
    </Box>
  );
};

// Storybook meta
const meta: Meta = {
  title: 'Design System/DraggableComponent Migration',
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'Real-world example of migrating a component from hardcoded styles to design system tokens. This component demonstrates drag-and-drop functionality while following design system best practices.',
      },
    },
  },
};

export default meta;

/**
 *
 */
type Story = StoryObj;

/**
 * Interactive demo with migration highlights
 */
export const InteractiveDemo: Story = {
  render: () => <DraggableCanvasDemo />,
};

/**
 * Side-by-side code comparison
 */
export const CodeBeforeAfter: Story = {
  render: () => <CodeComparison />,
};

/**
 * Simple example showing just the canvas
 */
export const BasicCanvas: Story = {
  render: () => {
    const [items, setItems] = useState<
      Array<{ id: string; type: ComponentType; props?: Record<string, unknown> }>
    >([
      { id: 'item-1', type: 'button' as ComponentType, props: {} },
      { id: 'item-2', type: 'input' as ComponentType, props: {} },
    ]);

    return (
      <Box className="p-6">
        <Typography as="h4" gutterBottom>
          Basic Draggable Canvas
        </Typography>
        <Paper className="mt-4">
          <DraggableCanvas items={items} onItemsChange={setItems} />
        </Paper>
      </Box>
    );
  },
};
