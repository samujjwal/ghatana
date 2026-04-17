/**
 * CanvasEditor
 *
 * Thin wrapper around the platform canvas component.
 * Handles document loading, error boundary, and integrates workspace/project
 * context so callers only need to pass a document ID.
 *
 * @doc.type component
 * @doc.purpose Canvas editor wrapper with project context integration
 * @doc.layer product
 * @doc.pattern Wrapper Component
 */

import { Box, CircularProgress, Typography, Alert } from '@mui/material';
import React, { Suspense } from 'react';

import { ErrorBoundary } from '../components/ErrorBoundary';

async function loadCanvasComponent() {
  const module = await import('@ghatana/canvas');
  return { default: module.Canvas };
}

// Lazy-load the heavy canvas to avoid blocking the initial bundle
const CanvasLazy = React.lazy(loadCanvasComponent);

export interface CanvasEditorProps {
  /** The CanvasDocument to render. Pass `null` to show an empty state. */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  document?: any;
  /** Custom theme applied to the canvas surface */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  theme?: any;
  /** Height of the canvas container (default: 100%) */
  height?: string | number;
  /** Width of the canvas container (default: 100%) */
  width?: string | number;
  className?: string;
}

const CanvasLoadingFallback: React.FC = () => (
  <Box
    display="flex"
    alignItems="center"
    justifyContent="center"
    height="100%"
    flexDirection="column"
    gap={1}
  >
    <CircularProgress size={28} />
    <Typography variant="caption" color="text.secondary">
      Loading canvas…
    </Typography>
  </Box>
);

const CanvasErrorFallback: React.FC<{ error: Error }> = ({ error }) => (
  <Alert severity="error" sx={{ m: 2 }}>
    Failed to load canvas: {error.message}
  </Alert>
);

/**
 * Lazy-loaded canvas editor with integrated error boundary and loading state.
 */
export const CanvasEditor: React.FC<CanvasEditorProps> = ({
  document,
  theme,
  height = '100%',
  width = '100%',
  className,
}) => (
  <Box
    className={className}
    sx={{ height, width, position: 'relative', overflow: 'hidden' }}
  >
    <ErrorBoundary
      fallback={(error: Error) => <CanvasErrorFallback error={error} />}
    >
      <Suspense fallback={<CanvasLoadingFallback />}>
        <CanvasLazy document={document} theme={theme} />
      </Suspense>
    </ErrorBoundary>
  </Box>
);
