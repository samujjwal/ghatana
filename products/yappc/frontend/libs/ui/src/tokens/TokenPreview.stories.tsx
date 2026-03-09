/**
 * Token Preview Stories
 *
 * Visual documentation of all design tokens
 */

import { Box, Typography, Grid, Surface as Paper, Stack } from '@ghatana/ui';
import React from 'react';

/** @ts-ignore - MUI Grid item prop typing issue in test config */
const GridItem = Grid as unknown;

import { palette, spacing, borderRadius, fontFamilies, lightShadows } from './index';

import type { Meta, StoryObj } from '@storybook/react';

// Helper component to display color tokens
/**
 *
 */
function ColorToken({ name, value }: { name: string; value: string }) {
  return (
    <Paper className="p-4 text-center">
      <Box
        className="w-full rounded mb-2 h-[80px] border border-solid border-gray-200 dark:border-gray-700" style={{ backgroundColor: 'value' }} />
      <Typography as="p" className="text-sm" fontWeight="bold">
        {name}
      </Typography>
      <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
        {value}
      </Typography>
    </Paper>
  );
}

// Helper component for spacing tokens
/**
 *
 */
function SpacingToken({ name, value }: { name: string; value: number }) {
  return (
    <Paper className="p-4">
      <Stack direction="row" spacing={2} alignItems="center">
        <Box
          className="h-[40px] rounded-[4px] bg-blue-600 w-[value]" />
        <Box>
          <Typography as="p" className="text-sm" fontWeight="bold">
            {name}
          </Typography>
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
            {value}px
          </Typography>
        </Box>
      </Stack>
    </Paper>
  );
}

// Helper for border radius
/**
 *
 */
function BorderRadiusToken({ name, value }: { name: string; value: number }) {
  return (
    <Paper className="p-4 text-center">
      <Box
        className="w-[80px] h-[80px] bg-blue-600" style={{ borderRadius: `${value }}
      />
      <Typography as="p" className="text-sm" fontWeight="bold">
        {name}
      </Typography>
      <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
        {value}px
      </Typography>
    </Paper>
  );
}

// Color Palette Story
const ColorPalettePreview = () => (
  <Box className="p-6">
    <Typography as="h4" gutterBottom>
      Color Palette
    </Typography>

    <Typography as="h6" className="mt-8 mb-4">
      Primary Colors
    </Typography>
    <Grid container spacing={2}>
      {Object.entries(palette.primary).map(([shade, value]) => (
        <GridItem item xs={6} sm={4} md={3} lg={2} key={shade}>
          <ColorToken name={`primary.${shade}`} value={value} />
        </GridItem>
      ))}
    </Grid>

    <Typography as="h6" className="mt-8 mb-4">
      Secondary Colors
    </Typography>
    <Grid container spacing={2}>
      {Object.entries(palette.secondary).map(([shade, value]) => (
        <GridItem item xs={6} sm={4} md={3} lg={2} key={shade}>
          <ColorToken name={`secondary.${shade}`} value={value} />
        </GridItem>
      ))}
    </Grid>

    <Typography as="h6" className="mt-8 mb-4">
      Neutral Colors
    </Typography>
    <Grid container spacing={2}>
      {Object.entries(palette.neutral).map(([shade, value]) => (
        <GridItem item xs={6} sm={4} md={3} lg={2} key={shade}>
          <ColorToken name={`neutral.${shade}`} value={value} />
        </GridItem>
      ))}
    </Grid>

    <Typography as="h6" className="mt-8 mb-4">
      Semantic Colors
    </Typography>
    <Grid container spacing={2}>
      <GridItem item xs={6} sm={4} md={3}>
        <ColorToken name="success.main" value={palette.success.main} />
      </GridItem>
      <GridItem item xs={6} sm={4} md={3}>
        <ColorToken name="warning.main" value={palette.warning.main} />
      </GridItem>
      <GridItem item xs={6} sm={4} md={3}>
        <ColorToken name="error.main" value={palette.error.main} />
      </GridItem>
      <GridItem item xs={6} sm={4} md={3}>
        <ColorToken name="info.main" value={palette.info.main} />
      </GridItem>
    </Grid>
  </Box>
);

// Spacing Story
const SpacingPreview = () => (
  <Box className="p-6">
    <Typography as="h4" gutterBottom>
      Spacing Scale
    </Typography>
    <Typography as="p" color="text.secondary" paragraph>
      Consistent spacing values for layout and component spacing
    </Typography>

    <Stack spacing={2} className="mt-6">
      {Object.entries(spacing).map(([name, value]) => (
        <SpacingToken key={name} name={name} value={value as number} />
      ))}
    </Stack>
  </Box>
);

// Border Radius Story
const BorderRadiusPreview = () => (
  <Box className="p-6">
    <Typography as="h4" gutterBottom>
      Border Radius
    </Typography>
    <Typography as="p" color="text.secondary" paragraph>
      Consistent border radius values for components
    </Typography>

    <Grid container spacing={3} className="mt-4">
      {Object.entries(borderRadius).map(([name, value]) => (
        <GridItem item xs={6} sm={4} md={3} key={name}>
          <BorderRadiusToken name={name} value={value as number} />
        </GridItem>
      ))}
    </Grid>
  </Box>
);

// Typography Story
const TypographyPreview = () => (
  <Box className="p-6">
    <Typography as="h4" gutterBottom>
      Typography
    </Typography>

    <Typography as="h6" className="mt-8 mb-4">
      Font Families
    </Typography>
    <Stack spacing={2}>
      <Paper className="p-4">
        <Typography as="p" className="text-sm" fontWeight="bold" gutterBottom>
          Primary
        </Typography>
        <Typography sx={{ fontFamily: fontFamilies.primary }}>
          {fontFamilies.primary}
        </Typography>
        <Typography as="p" className="mt-2" style={{ fontFamily: 'fontFamilies.primary' }} >
          The quick brown fox jumps over the lazy dog
        </Typography>
      </Paper>

      <Paper className="p-4">
        <Typography as="p" className="text-sm" fontWeight="bold" gutterBottom>
          Primary (Headings)
        </Typography>
        <Typography sx={{ fontFamily: fontFamilies.primary }}>
          {fontFamilies.primary}
        </Typography>
        <Typography as="h5" className="mt-2" >
          The quick brown fox jumps over the lazy dog
        </Typography>
      </Paper>

      <Paper className="p-4">
        <Typography as="p" className="text-sm" fontWeight="bold" gutterBottom>
          Code (Monospace)
        </Typography>
        <Typography sx={{ fontFamily: fontFamilies.code }}>
          {fontFamilies.code}
        </Typography>
        <Typography as="p" className="mt-2" style={{ fontFamily: 'fontFamilies.code', fontFamily: 'fontFamilies.primary' }} >
          const code = "The quick brown fox";
        </Typography>
      </Paper>
    </Stack>

    <Typography as="h6" className="mt-8 mb-4">
      Type Scale
    </Typography>
    <Stack spacing={2}>
      <Paper className="p-4">
        <Typography as="h1">Heading 1</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="h2">Heading 2</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="h3">Heading 3</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="h4">Heading 4</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="h5">Heading 5</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="h6">Heading 6</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="p">Body 1: The quick brown fox jumps over the lazy dog</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="p" className="text-sm">Body 2: The quick brown fox jumps over the lazy dog</Typography>
      </Paper>
      <Paper className="p-4">
        <Typography as="span" className="text-xs text-gray-500">Caption: The quick brown fox jumps over the lazy dog</Typography>
      </Paper>
    </Stack>
  </Box>
);

// Shadows Story
const ShadowsPreview = () => (
  <Box className="p-6">
    <Typography as="h4" gutterBottom>
      Shadows
    </Typography>
    <Typography as="p" color="text.secondary" paragraph>
      Elevation levels using box shadows
    </Typography>

    <Grid container spacing={3} className="mt-4">
      {lightShadows.map((shadow, index) => (
        <GridItem item xs={6} sm={4} md={3} key={index}>
          <Box
            className="p-6 rounded text-center bg-white dark:bg-gray-900" style={{ boxShadow: 'shadow' }} >
            <Typography as="p" className="text-sm" fontWeight="bold">
              Level {index}
            </Typography>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
              Elevation
            </Typography>
          </Box>
        </GridItem>
      ))}
    </Grid>
  </Box>
);

// Storybook meta
const meta: Meta = {
  title: 'Design System/Tokens',
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

/**
 *
 */
type Story = StoryObj;

export const Colors: Story = {
  render: () => <ColorPalettePreview />,
};

export const Spacing: Story = {
  render: () => <SpacingPreview />,
};

export const BorderRadius: Story = {
  render: () => <BorderRadiusPreview />,
};

export const TypographyTokens: Story = {
  render: () => <TypographyPreview />,
};

export const Shadows: Story = {
  render: () => <ShadowsPreview />,
};
