/**
 * Visual Regression
 *
 * Visual regression testing integration for PageConfig.
 *
 * @packageDocumentation
 */

import { Camera as CameraIcon, Play as PlayIcon, Download as DownloadIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Alert,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
} from '@ghatana/design-system';
import React, { useState, useCallback, useRef } from 'react';

import type { PageConfig } from 'yappc-config-schema';

/**
 * @doc.type component
 * @doc.purpose Visual regression testing integration
 * @doc.layer product
 * @doc.pattern Panel Component
 */
interface VisualRegressionProps {
  config: PageConfig;
}

interface RegressionTest {
  id: string;
  timestamp: Date;
  status: 'passed' | 'failed' | 'pending';
  diffPercentage?: number;
}

export const VisualRegression: React.FC<VisualRegressionProps> = ({ config }) => {
  const [tests, setTests] = useState<RegressionTest[]>([]);
  const [isRunning, setIsRunning] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const handleRunTest = useCallback(async () => {
    setIsRunning(true);

    // Simulate running a visual regression test
    // In production, this would integrate with Playwright, Percy, or similar tools
    await new Promise((resolve) => setTimeout(resolve, 2000));

    const newTest: RegressionTest = {
      id: `test-${Date.now()}`,
      timestamp: new Date(),
      status: Math.random() > 0.3 ? 'passed' : 'failed',
      diffPercentage: Math.random() > 0.3 ? 0 : Math.random() * 5,
    };

    setTests((prev) => [newTest, ...prev]);
    setIsRunning(false);
  }, []);

  const handleCaptureScreenshot = useCallback(() => {
    // In production, this would capture the actual rendered component
    // For now, we'll simulate it
    const canvas = canvasRef.current;
    if (canvas) {
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.fillStyle = '#f5f5f5';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = '#333';
        ctx.font = '16px sans-serif';
        ctx.fillText('Screenshot captured', 20, 30);
        ctx.fillText(`Config: ${config.title}`, 20, 60);

        // Trigger download
        const link = document.createElement('a');
        link.download = `screenshot-${Date.now()}.png`;
        link.href = canvas.toDataURL();
        link.click();
      }
    }
  }, [config.title]);

  return (
    <Box data-testid="visual-regression" className="p-4">
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 3 }}>
        <CameraIcon size={16} />
        <Typography variant="h6">Visual Regression Testing</Typography>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 4 }}>
        <Button
          variant="contained"
          onClick={handleRunTest}
          disabled={isRunning}
          startIcon={<PlayIcon size={14} />}
        >
          {isRunning ? 'Running...' : 'Run Test'}
        </Button>
        <Button
          variant="outlined"
          onClick={handleCaptureScreenshot}
          startIcon={<DownloadIcon size={14} />}
        >
          Capture Screenshot
        </Button>
      </Stack>

      <canvas
        ref={canvasRef}
        width={800}
        height={400}
        style={{ display: 'none' }}
        data-testid="screenshot-canvas"
      />

      <Divider />

      <Typography variant="subtitle2" gutterBottom className="mt-4">
        Test History
      </Typography>

      {tests.length === 0 ? (
        <Typography color="text.secondary">No tests run yet</Typography>
      ) : (
        <List>
          {tests.map((test) => (
            <ListItem key={test.id}>
              <ListItemIcon>
                {test.status === 'passed' && (
                  <div className="w-2 h-2 rounded-full bg-success-bg" />
                )}
                {test.status === 'failed' && (
                  <div className="w-2 h-2 rounded-full bg-destructive-bg" />
                )}
                {test.status === 'pending' && (
                  <div className="w-2 h-2 rounded-full bg-warning-bg" />
                )}
              </ListItemIcon>
              <ListItemText
                primary={
                  <Stack spacing={0.5}>
                    <Typography variant="subtitle2">
                      {test.status === 'passed' ? 'Passed' : test.status === 'failed' ? 'Failed' : 'Pending'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {test.timestamp.toLocaleString()}
                    </Typography>
                    {test.diffPercentage !== undefined && test.diffPercentage > 0 && (
                      <Typography variant="caption" color="error">
                        Diff: {test.diffPercentage.toFixed(2)}%
                      </Typography>
                    )}
                  </Stack>
                }
              />
            </ListItem>
          ))}
        </List>
      )}

      <Alert severity="info" className="mt-4">
        <Typography variant="body2">
          This is a placeholder for visual regression testing. In production, integrate with
          Playwright, Percy, Chromatic, or similar tools for automated visual testing.
        </Typography>
      </Alert>
    </Box>
  );
};
