/**
 * Basic Example: Using EnhancedThemeProvider
 *
 * This example shows the simplest usage of the enhanced theme provider,
 * which is backward compatible with the original ThemeProvider.
 */

import { Button, Container, Typography, Box } from '@ghatana/ui';
import React from 'react';

import { EnhancedThemeProvider, useThemeMode } from '../../index';

/**
 *
 */
function ThemeToggleButton() {
  const { mode, setMode } = useThemeMode();

  return (
    <Button
      variant="solid"
      onClick={() => setMode(mode === 'light' ? 'dark' : 'light')}
    >
      Switch to {mode === 'light' ? 'Dark' : 'Light'} Mode
    </Button>
  );
}

/**
 *
 */
function AppContent() {
  return (
    <Container size="md" className="py-8">
      <Typography as="h3" gutterBottom>
        Enhanced Theme Provider Demo
      </Typography>

      <Typography as="p" paragraph>
        This is a basic example showing the enhanced theme provider in action.
        Click the button below to toggle between light and dark modes.
      </Typography>

      <Box className="mt-6">
        <ThemeToggleButton />
      </Box>

      <Box className="mt-8">
        <Typography as="h5" gutterBottom>
          Colors
        </Typography>
        <Box className="flex gap-4 mt-4">
          <Button variant="solid" tone="primary">
            Primary
          </Button>
          <Button variant="solid" tone="secondary">
            Secondary
          </Button>
          <Button variant="solid" tone="success">
            Success
          </Button>
          <Button variant="solid" tone="danger">
            Error
          </Button>
        </Box>
      </Box>
    </Container>
  );
}

/**
 *
 */
export function BasicExample() {
  return (
    <EnhancedThemeProvider mode="light">
      <AppContent />
    </EnhancedThemeProvider>
  );
}

export default BasicExample;
