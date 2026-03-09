/**
 * Theme Switcher Story
 *
 * Interactive theme switching demonstration
 */

import { Box, Button, Card, CardContent, Typography, Stack, TextField, Switch, FormControlLabel, Chip, Surface as Paper } from '@ghatana/ui';
import React, { useState } from 'react';

import {
  EnhancedThemeProvider,
  useThemeMode,
  useBrandTheme,
  useMultiLayerTheme,
} from '../EnhancedThemeProvider';

import type { Meta, StoryObj } from '@storybook/react';

/**
 *
 */
function ThemeSwitcherDemo() {
  const { mode, setMode } = useThemeMode();
  const { setBrandLayer } = useBrandTheme();
  const { layers } = useMultiLayerTheme();

  const [primaryColor, setPrimaryColor] = useState('#1976d2');
  const [secondaryColor, setSecondaryColor] = useState('#dc004e');

  const applyColors = () => {
    setBrandLayer({
      palette: {
        primary: { main: primaryColor },
        secondary: { main: secondaryColor },
      },
    });
  };

  return (
    <Box className="p-8 min-h-screen">
      <Typography as="h3" gutterBottom>
        Theme Switcher Demo
      </Typography>
      <Typography as="p" color="text.secondary" paragraph>
        Interactive demonstration of multi-layer theming system
      </Typography>

      {/* Controls */}
      <Card className="mb-8">
        <CardContent>
          <Typography as="h5" gutterBottom>
            Theme Controls
          </Typography>

          <Stack spacing={3}>
            {/* Light/Dark Mode */}
            <Box>
              <Typography as="p" className="text-sm font-medium" gutterBottom>
                Mode
              </Typography>
              <FormControlLabel
                control={
                  <Switch
                    checked={mode === 'dark'}
                    onChange={() => setMode(mode === 'light' ? 'dark' : 'light')}
                  />
                }
                label={mode === 'light' ? 'Light Mode' : 'Dark Mode'}
              />
            </Box>

            {/* Brand Colors */}
            <Box>
              <Typography as="p" className="text-sm font-medium" gutterBottom>
                Brand Colors
              </Typography>
              <Stack direction="row" spacing={2} alignItems="center">
                <TextField
                  label="Primary"
                  type="color"
                  value={primaryColor}
                  onChange={(e) => setPrimaryColor(e.target.value)}
                  className="w-[120px]"
                />
                <TextField
                  label="Secondary"
                  type="color"
                  value={secondaryColor}
                  onChange={(e) => setSecondaryColor(e.target.value)}
                  className="w-[120px]"
                />
                <Button variant="solid" onClick={applyColors}>
                  Apply Colors
                </Button>
              </Stack>
            </Box>

            {/* Active Layers */}
            <Box>
              <Typography as="p" className="text-sm font-medium" gutterBottom>
                Active Theme Layers
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {layers.map((layer) => (
                  <Chip
                    key={layer.id}
                    label={`${layer.name} (${layer.priority})`}
                    color={layer.id === 'base' ? 'default' : 'primary'}
                    variant={layer.id === 'base' ? 'outlined' : 'filled'}
                  />
                ))}
              </Stack>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* Demo Components */}
      <Typography as="h5" gutterBottom>
        Component Preview
      </Typography>

      <Stack spacing={3}>
        {/* Buttons */}
        <Paper className="p-6">
          <Typography as="h6" gutterBottom>
            Buttons
          </Typography>
          <Stack direction="row" spacing={2} flexWrap="wrap">
            <Button variant="solid" tone="primary">
              Primary
            </Button>
            <Button variant="solid" tone="secondary">
              Secondary
            </Button>
            <Button variant="outlined" tone="primary">
              Outlined
            </Button>
            <Button variant="ghost" tone="primary">
              Text
            </Button>
            <Button variant="solid" disabled>
              Disabled
            </Button>
          </Stack>
        </Paper>

        {/* Cards */}
        <Paper className="p-6">
          <Typography as="h6" gutterBottom>
            Cards
          </Typography>
          <Stack direction="row" spacing={2}>
            <Card variant="outlined" className="flex-1">
              <CardContent>
                <Typography as="h6">Outlined Card</Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                  This card uses the current theme
                </Typography>
              </CardContent>
            </Card>
            <Card variant="elevation" className="flex-1">
              <CardContent>
                <Typography as="h6">Elevated Card</Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                  This card has elevation shadow
                </Typography>
              </CardContent>
            </Card>
          </Stack>
        </Paper>

        {/* Typography */}
        <Paper className="p-6">
          <Typography as="h6" gutterBottom>
            Typography
          </Typography>
          <Stack spacing={1}>
            <Typography as="h4">Heading 4</Typography>
            <Typography as="h5">Heading 5</Typography>
            <Typography as="h6">Heading 6</Typography>
            <Typography as="p">
              Body 1: The quick brown fox jumps over the lazy dog
            </Typography>
            <Typography as="p" className="text-sm">
              Body 2: The quick brown fox jumps over the lazy dog
            </Typography>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
              Caption: The quick brown fox jumps over the lazy dog
            </Typography>
          </Stack>
        </Paper>

        {/* Form Controls */}
        <Paper className="p-6">
          <Typography as="h6" gutterBottom>
            Form Controls
          </Typography>
          <Stack spacing={2}>
            <TextField label="Text Field" variant="outlined" />
            <TextField label="Filled" variant="filled" />
            <TextField label="Standard" variant="standard" />
          </Stack>
        </Paper>
      </Stack>
    </Box>
  );
}

const meta: Meta = {
  title: 'Design System/Theme Switcher',
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

/**
 *
 */
type Story = StoryObj;

export const Interactive: Story = {
  render: () => (
    <EnhancedThemeProvider
      mode="light"
      brandThemeOptions={{
        palette: {
          primary: { main: '#1976d2' },
          secondary: { main: '#dc004e' },
        },
      }}
    >
      <ThemeSwitcherDemo />
    </EnhancedThemeProvider>
  ),
};

export const DarkMode: Story = {
  render: () => (
    <EnhancedThemeProvider
      mode="dark"
      brandThemeOptions={{
        palette: {
          primary: { main: '#90caf9' },
          secondary: { main: '#f48fb1' },
        },
      }}
    >
      <ThemeSwitcherDemo />
    </EnhancedThemeProvider>
  ),
};
