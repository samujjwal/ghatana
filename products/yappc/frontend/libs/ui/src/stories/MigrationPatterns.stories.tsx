/**
 * Migration Patterns Stories
 *
 * Before/after examples showing how to migrate from legacy patterns to design system
 */

import { Box, Typography, Surface as Paper, Stack, Button as MuiButton } from '@ghatana/ui';
import React from 'react';

import { Button } from '../components/Button';
import { spacing, palette, borderRadius } from '../tokens';

import type { Meta, StoryObj } from '@storybook/react';

/**
 * Helper component to show side-by-side comparison
 */
function MigrationExample({
  title,
  description,
  beforeCode,
  afterCode,
  beforePreview,
  afterPreview,
}: {
  title: string;
  description: string;
  beforeCode: string;
  afterCode: string;
  beforePreview: React.ReactNode;
  afterPreview: React.ReactNode;
}) {
  return (
    <Paper className="p-6 mb-8">
      <Typography as="h5" gutterBottom>
        {title}
      </Typography>
      <Typography as="p" className="text-sm" color="text.secondary" paragraph>
        {description}
      </Typography>

      <Box className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4">
        {/* Before */}
        <Box>
          <Box className="mb-4">
            <Typography as="h6" color="error.main" gutterBottom>
              ❌ Before (Anti-pattern)
            </Typography>
            <Paper
              className="p-4 bg-gray-50 dark:bg-gray-800 border-[2px_solid] border-red-400" >
              {beforePreview}
            </Paper>
          </Box>
          <Paper
            className="p-4 rounded text-sm overflow-auto font-mono bg-gray-900" >
            <pre style={{ margin: 0, color: 'common.white', color: 'common.white', color: 'common.white' }}>{beforeCode}</pre>
          </Paper>
        </Box>

        {/* After */}
        <Box>
          <Box className="mb-4">
            <Typography as="h6" color="success.main" gutterBottom>
              ✅ After (Design System)
            </Typography>
            <Paper
              className="p-4 bgremaining sx: borderColor: 'success.light' */
            >
              {afterPreview}
            </Paper>
          </Box>
          <Paper
            className="p-4 rounded text-sm ovolor: 'common.white' */
          >
            <pre style={{ margin: 0, color: 'common.white' }}>{afterCode}</pre>
          </Paper>
        </Box>
      </Box>
    </Paper>
  );
}

/**
 * All migration pattern examples
 */
const MigrationPatternsPreview = () => {
  return (
    <Box className="p-6 max-w-[1400px] mx-auto">
      <Typography as="h3" gutterBottom>
        Design System Migration Patterns
      </Typography>
      <Typography as="p" color="text.secondary" paragraph>
        Common anti-patterns and how to fix them using the design system. Use these examples as a
        reference when migrating existing code.
      </Typography>

      {/* Pattern 1: Hardcoded Colors → Palette Tokens */}
      <MigrationExample
        title="1. Hardcoded Colors → Palette Tokens"
        description="Replace hex colors with semantic palette tokens for theme consistency and dark mode support."
        beforeCode={`// ❌ Hardcoded hex colors
<Box
  style={{
    backgroundColor: '#1976d2',
    color: '#ffffff',
    border: '1px solid #ddd'
  }}
>
  Content
</Box>`}
        afterCode={`// ✅ Use palette tokens
import { palette } from '@ghatana/yappc-shared-ui-core/tokens';

<Box
  className="bg-blue-600 border-gray-200 dark:bolor: 'common.white' */
>
  Content
</Box>`}
        beforePreview={
          <Box
            style={{
              backgroundColor: '#1976d2',
              color: '#ffffff',
              border: '1px solid #ddd',
              padding: '16px',
              borderRadius: '8px',
            }}
          >
            Hardcoded colors
          </Box>
        }
        afterPreview={
          <Box
            className="p-4 rounded bg-blue-600 border border-gray-200 dark:border-gray-700" >
            Palette tokens
          </Box>
        }
      />

      {/* Pattern 2: Pixel Spacing → Spacing Tokens */}
      <MigrationExample
        title="2. Pixel Spacing → Spacing Tokens"
        description="Replace hardcoded pixel values with spacing tokens for consistent spacing across the app."
        beforeCode={`// ❌ Hardcoded pixels
<Box
  style={{
    padding: '16px',
    margin: '8px 0',
    gap: '12px'
  }}
>
  Content
</Box>`}
        afterCode={`// ✅ Use spacing tokens
import { spacing } from '@ghatana/yappc-shared-ui-core/tokens';

<Box
  className="p-4"
>
  Content
</Box>`}
        beforePreview={
          <Box
            style={{
              padding: '16px',
              margin: '8px 0',
              gap: '12px',
              backgroundColor: '#f5f5f5',
              borderRadius: '4px',
            }}
          >
            Hardcoded spacing
          </Box>
        }
        afterPreview={
          <Box
            className="p-4 my-2 gap-3 bg-gray-100 dark:bg-gray-800 rounded-sm"
          >
            Spacing tokens
          </Box>
        }
      />

      {/* Pattern 3: style prop → sx prop */}
      <MigrationExample
        title="3. style={{}} → className="" on MUI Components"
        description="Use sx prop instead of style for MUI components to get theme access and better performance."
        beforeCode={`// ❌ Using style prop (no theme access)
<MuiButton
  style={{
    backgroundColor: '#1976d2',
    padding: '16px 32px',
    borderRadius: '8px'
  }}
>
  Click me
</MuiButton>`}
        afterCode={`// ✅ Using sx prop (theme-aware)
<MuiButton
  className="bg-blue-600 p-32" >
  Click me
</MuiButton>`}
        beforePreview={
          <MuiButton
            style={{
              backgroundColor: '#1976d2',
              padding: '16px 32px',
              borderRadius: '8px',
            }}
          >
            style prop
          </MuiButton>
        }
        afterPreview={
          <MuiButton
            className="bg-blue-600 rounded hover:bg-blue-800 p-32" >
            sx prop
          </MuiButton>
        }
      />

      {/* Pattern 4: Custom Components → @ghatana/yappc-ui */}
      <MigrationExample
        title="4. Custom Implementations → @ghatana/yappc-ui Components"
        description="Replace custom component implementations with battle-tested design system components."
        beforeCode={`// ❌ Custom button implementation
const CustomButton = ({ children, onClick }) => (
  <button
    onClick={onClick}
    style={{
      backgroundColor: '#1976d2',
      color: 'white',
      padding: '10px 20px',
      border: 'none',
      borderRadius: '4px',
      cursor: 'pointer'
    }}
  >
    {children}
  </button>
);

<CustomButton onClick={handleClick}>
  Click me
</CustomButton>`}
        afterCode={`// ✅ Use @ghatana/yappc-ui Button
import { Button } from '@ghatana/ui';

<Button
  variant="solid"
  colorScheme="primary"
  onClick={handleClick}
>
  Click me
</Button>`}
        beforePreview={
          <button
            style={{
              backgroundColor: '#1976d2',
              color: 'white',
              padding: '10px 20px',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
            }}
          >
            Custom button
          </button>
        }
        afterPreview={
          <Button variant="solid" colorScheme="primary">
            @ghatana/yappc-ui Button
          </Button>
        }
      />

      {/* Pattern 5: CSS Files → Tailwind */}
      <MigrationExample
        title="5. External CSS → Tailwind classes"
        description="Avoid separate CSS files. Use Tailwind utility classes for all styling."
        beforeCode={`// ❌ Separate CSS file
// MyComponent.css
.my-component {
  background-color: #ffffff;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

// MyComponent.tsx
import './MyComponent.css';

<div className="my-component">
  Content
</div>`}
        afterCode={`// ✅ Use Tailwind utility classes
import { Box } from '@ghatana/ui';

// Simple: className with Tailwind
<Box className="p-4 rounded-lg bg-white dark:bg-gray-900 shadow">
  Content
</Box>

// Complex: compose classes with cn()
import { cn } from '@ghatana/utils';

<Box className={cn(
  'bg-white dark:bg-gray-900 p-4 rounded-lg shadow',
  'hover:shadow-md transition-shadow duration-200'
)}>
  Content with hover
</Box>`}
        beforePreview={
          <div
            style={{
              backgroundColor: '#ffffff',
              padding: '16px',
              borderRadius: '8px',
              boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
            }}
          >
            CSS file styling
          </div>
        }
        afterPreview={
          <Box className="p-4 rounded-lg bg-white shadow">
            Tailwind class styling
          </Box>
        }
      />

      {/* Pattern 6: Inline Styles → Design Tokens */}
      <MigrationExample
        title="6. Magic Numbers → Named Tokens"
        description="Replace magic numbers with semantic token names for better maintainability."
        beforeCode={`// ❌ Magic numbers everywhere
<Box
  style={{
    padding: '24px',
    gap: '16px',
    borderRadius: '12px',
    fontSize: '14px',
    lineHeight: '1.5',
    fontWeight: 500
  }}
>
  Content
</Box>`}
        afterCode={`// ✅ Semantic token names
import { spacing, borderRadius } from '@ghatana/yappc-shared-ui-core/tokens';

<Box
  className="p-6 gap-4 rounded-md text-sm" >
  Content
</Box>`}
        beforePreview={
          <Box
            style={{
              padding: '24px',
              gap: '16px',
              borderRadius: '12px',
              fontSize: '14px',
              lineHeight: '1.5',
              fontWeight: 500,
              backgroundColor: '#f5f5f5',
            }}
          >
            Magic numbers
          </Box>
        }
        afterPreview={
          <Box
            className="p-6 gap-4 rounded-md bg-gray-100 dark:bg-gray-800 text-sm" >
            Semantic tokens
          </Box>
        }
      />

      {/* Summary Section */}
      <Paper className="p-6 mt-8 bg-sky-600 text-white">
        <Typography as="h5" gutterBottom>
          📋 Migration Checklist
        </Typography>
        <Stack spacing={1} className="mt-4">
          <Typography>✅ Replace hex colors with palette tokens (primary.main, grey.100)</Typography>
          <Typography>✅ Replace pixel spacing with spacing tokens (p: 2, my: 1)</Typography>
          <Typography>✅ Use Tailwind classes instead of sx prop on components</Typography>
          <Typography>✅ Import from @ghatana/yappc-ui instead of creating custom components</Typography>
          <Typography>✅ Remove CSS files, use Tailwind classes instead</Typography>
          <Typography>✅ Replace magic numbers with semantic token names</Typography>
          <Typography className="mt-4 font-bold">
            💡 Run `pnpm lint` to find violations automatically!
          </Typography>
        </Stack>
      </Paper>
    </Box>
  );
};

// Storybook meta
const meta: Meta = {
  title: 'Design System/Migration Patterns',
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'Common anti-patterns and their design system solutions. Use ESLint rules to automatically detect violations.',
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
 * Complete migration guide with before/after examples
 */
export const AllPatterns: Story = {
  render: () => <MigrationPatternsPreview />,
};

/**
 * Quick reference for color migration
 */
export const ColorMigration: Story = {
  render: () => (
    <Box className="p-6">
      <MigrationExample
        title="Color Token Migration"
        description="Replace hardcoded colors with palette tokens"
        beforeCode={`// ❌ Before
<Box style={{ backgroundColor: '#1976d2' }}>Content</Box>`}
        afterCode={`// ✅ After
<Box className="bg-blue-600">Content</Box>`}
        beforePreview={<Box style={{ backgroundColor: '#1976d2', padding: 16 }}>Before</Box>}
        afterPreview={
          <Box className="p-4 bg-blue-600">After</Box>
        }
      />
    </Box>
  ),
};

/**
 * Quick reference for spacing migration
 */
export const SpacingMigration: Story = {
  render: () => (
    <Box className="p-6">
      <MigrationExample
        title="Spacing Token Migration"
        description="Replace pixel values with spacing tokens"
        beforeCode={`// ❌ Before
<Box style={{ padding: '16px', margin: '8px' }}>Content</Box>`}
        afterCode={`// ✅ After
<Box className="p-4 m-2">Content</Box>`}
        beforePreview={<Box style={{ padding: '16px', margin: '8px', backgroundColor: '#f5f5f5' }}>Before</Box>}
        afterPreview={
          <Box className="p-4 m-2 bg-gray-100 dark:bg-gray-800">After</Box>
        }
      />
    </Box>
  ),
};
