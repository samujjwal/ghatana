/**
 * Multi-Layer Example: Using all theme layers
 *
 * This example demonstrates how to use brand, workspace, and app layers
 * to customize themes at different levels.
 */

import { Button, Container, Typography, Box, TextField, Stack, Card, CardContent, Divider } from '@ghatana/ui';
import React, { useState } from 'react';

import {
  EnhancedThemeProvider,
  useThemeMode,
  useBrandTheme,
  useWorkspaceTheme,
  useAppTheme,
  useMultiLayerTheme,
} from '../../index';

/**
 *
 */
function ThemeControls() {
  const { mode, setMode } = useThemeMode();
  const { setBrandLayer } = useBrandTheme();
  const { setWorkspaceLayer } = useWorkspaceTheme();
  const { setAppLayer } = useAppTheme();
  const { layers } = useMultiLayerTheme();

  const [brandColor, setBrandColor] = useState('#1976d2');
  const [workspaceFontSize, setWorkspaceFontSize] = useState('16');
  const [appBorderRadius, setAppBorderRadius] = useState('8');

  const applyBrandColor = () => {
    setBrandLayer({
      palette: {
        primary: { main: brandColor },
      },
    });
  };

  const applyWorkspaceFontSize = () => {
    setWorkspaceLayer({
      typography: {
        fontSize: parseInt(workspaceFontSize, 10),
      },
    });
  };

  const applyAppBorderRadius = () => {
    setAppLayer({
      components: {
        MuiButton: {
          styleOverrides: {
            root: { borderRadius: parseInt(appBorderRadius, 10) },
          },
        },
        MuiCard: {
          styleOverrides: {
            root: { borderRadius: parseInt(appBorderRadius, 10) },
          },
        },
      },
    });
  };

  return (
    <Container size="lg" className="py-8">
      <Typography as="h3" gutterBottom>
        Multi-Layer Theme System Demo
      </Typography>

      <Typography as="p" paragraph>
        This example shows how to customize themes at different layers:
        Brand, Workspace, and App levels.
      </Typography>

      <Box className="flex gap-4 mb-8">
        <Button
          variant="solid"
          onClick={() => setMode(mode === 'light' ? 'dark' : 'light')}
        >
          Toggle {mode === 'light' ? 'Dark' : 'Light'} Mode
        </Button>
      </Box>

      <Stack spacing={3}>
        {/* Brand Layer Controls */}
        <Card>
          <CardContent>
            <Typography as="h5" gutterBottom>
              Brand Layer (Priority: 100)
            </Typography>
            <Typography as="p" className="text-sm" color="text.secondary" paragraph>
              Brand identity - affects all workspaces and apps
            </Typography>
            <Stack direction="row" spacing={2} alignItems="center">
              <TextField
                label="Primary Color"
                type="color"
                value={brandColor}
                onChange={(e) => setBrandColor(e.target.value)}
                className="w-[150px]"
              />
              <Button variant="outlined" onClick={applyBrandColor}>
                Apply Brand Color
              </Button>
            </Stack>
          </CardContent>
        </Card>

        {/* Workspace Layer Controls */}
        <Card>
          <CardContent>
            <Typography as="h5" gutterBottom>
              Workspace Layer (Priority: 200)
            </Typography>
            <Typography as="p" className="text-sm" color="text.secondary" paragraph>
              Team/user preferences - can vary between workspaces
            </Typography>
            <Stack direction="row" spacing={2} alignItems="center">
              <TextField
                label="Font Size"
                type="number"
                value={workspaceFontSize}
                onChange={(e) => setWorkspaceFontSize(e.target.value)}
                className="w-[150px]"
              />
              <Button variant="outlined" onClick={applyWorkspaceFontSize}>
                Apply Font Size
              </Button>
            </Stack>
          </CardContent>
        </Card>

        {/* App Layer Controls */}
        <Card>
          <CardContent>
            <Typography as="h5" gutterBottom>
              App Layer (Priority: 300)
            </Typography>
            <Typography as="p" className="text-sm" color="text.secondary" paragraph>
              App-specific overrides - highest priority
            </Typography>
            <Stack direction="row" spacing={2} alignItems="center">
              <TextField
                label="Border Radius (px)"
                type="number"
                value={appBorderRadius}
                onChange={(e) => setAppBorderRadius(e.target.value)}
                className="w-[150px]"
              />
              <Button variant="outlined" onClick={applyAppBorderRadius}>
                Apply Border Radius
              </Button>
            </Stack>
          </CardContent>
        </Card>

        <Divider />

        {/* Active Layers Display */}
        <Card>
          <CardContent>
            <Typography as="h5" gutterBottom>
              Active Layers
            </Typography>
            <Stack spacing={1}>
              {layers.map((layer) => (
                <Box
                  key={layer.id}
                  className="p-2 rounded bg-gray-100 dark:bg-gray-800"
                >
                  <Typography as="p" className="text-sm">
                    <strong>{layer.name}</strong> (Priority: {layer.priority})
                    {layer.description && ` - ${layer.description}`}
                  </Typography>
                </Box>
              ))}
            </Stack>
          </CardContent>
        </Card>

        {/* Demo Components */}
        <Card>
          <CardContent>
            <Typography as="h5" gutterBottom>
              Demo Components
            </Typography>
            <Typography as="p" className="text-sm" paragraph>
              These components reflect the current theme settings
            </Typography>
            <Stack spacing={2}>
              <Box className="flex gap-4 flex-wrap">
                <Button variant="solid" tone="primary">
                  Primary Button
                </Button>
                <Button variant="solid" tone="secondary">
                  Secondary Button
                </Button>
                <Button variant="outlined" tone="success">
                  Success Button
                </Button>
                <Button variant="outlined" tone="danger">
                  Error Button
                </Button>
              </Box>
              <Card variant="outlined">
                <CardContent>
                  <Typography as="h6">Sample Card</Typography>
                  <Typography as="p" className="text-sm">
                    Notice how the border radius changes when you modify the app
                    layer settings.
                  </Typography>
                </CardContent>
              </Card>
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </Container>
  );
}

/**
 *
 */
export function MultiLayerExample() {
  return (
    <EnhancedThemeProvider
      mode="light"
      brandThemeOptions={{
        palette: {
          primary: { main: '#1976d2' },
        },
      }}
      workspaceThemeOptions={{
        typography: {
          fontSize: 16,
        },
      }}
      appThemeOptions={{
        components: {
          MuiButton: {
            styleOverrides: {
              root: { borderRadius: 8 },
            },
          },
        },
      }}
    >
      <ThemeControls />
    </EnhancedThemeProvider>
  );
}

export default MultiLayerExample;
