/**
 * Preview Host
 *
 * Config-driven preview host for rendering PageConfig.
 *
 * @packageDocumentation
 */

import { Play as PlayIcon, RefreshCw as RefreshIcon, Settings as SettingsIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  Divider,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import type { PageConfig } from 'yappc-config-schema';

import { ConfigRenderer } from './ConfigRenderer';
import { MockDataManager } from './MockDataManager';
import { AccessibilityChecker } from './AccessibilityChecker';
import { VisualRegression } from './VisualRegression';

/**
 * @doc.type component
 * @doc.purpose Config-driven preview host for rendering PageConfig
 * @doc.layer product
 * @doc.pattern Container Component
 */
interface PreviewHostProps {
  config: PageConfig;
  onConfigChange?: (config: PageConfig) => void;
  readOnly?: boolean;
}

type PreviewPanel = 'preview' | 'mock-data' | 'a11y' | 'visual-regression';

export const PreviewHost: React.FC<PreviewHostProps> = ({
  config,
  onConfigChange,
  readOnly = false,
}) => {
  const [activePanel, setActivePanel] = useState<PreviewPanel>('preview');
  const [mockData, setMockData] = useState<Record<string, unknown>>({});

  const handleRefresh = useCallback(() => {
    window.location.reload();
  }, []);

  const handleMockDataChange = useCallback((data: Record<string, unknown>) => {
    setMockData(data);
  }, []);

  const renderPanel = () => {
    switch (activePanel) {
      case 'preview':
        return <ConfigRenderer config={config} mockData={mockData} />;
      case 'mock-data':
        return <MockDataManager config={config} onDataChange={handleMockDataChange} />;
      case 'a11y':
        return <AccessibilityChecker config={config} />;
      case 'visual-regression':
        return <VisualRegression config={config} />;
      default:
        return <ConfigRenderer config={config} mockData={mockData} />;
    }
  };

  return (
    <Box data-testid="preview-host" className="h-full flex flex-col">
      <Paper variant="outlined" className="mb-4">
        <Box className="flex items-center gap-2 p-3">
          <Stack direction="row" spacing={1} alignItems="center" className="flex-1">
            <Typography variant="h6">{config.title}</Typography>
            <Typography variant="caption" color="text.secondary">
              {config.route}
            </Typography>
          </Stack>

          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant={activePanel === 'preview' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('preview')}
              startIcon={<PlayIcon size={14} />}
            >
              Preview
            </Button>
            <Button
              size="small"
              variant={activePanel === 'mock-data' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('mock-data')}
              startIcon={<SettingsIcon size={14} />}
            >
              Mock Data
            </Button>
            <Button
              size="small"
              variant={activePanel === 'a11y' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('a11y')}
            >
              A11y
            </Button>
            <Button
              size="small"
              variant={activePanel === 'visual-regression' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('visual-regression')}
            >
              Visual
            </Button>
            <Divider orientation="vertical" flexItem />
            <Button size="small" variant="outlined" onClick={handleRefresh} startIcon={<RefreshIcon size={14} />}>
              Refresh
            </Button>
          </Stack>
        </Box>
      </Paper>

      <Box className="flex-1 overflow-auto">
        {renderPanel()}
      </Box>
    </Box>
  );
};
