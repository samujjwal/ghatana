/**
 * Preview Host
 *
 * Config-driven preview host for rendering PageConfig with security states.
 *
 * @packageDocumentation
 */

import { Play as PlayIcon, RefreshCw as RefreshIcon, Settings as SettingsIcon, ShieldAlert as ShieldAlertIcon, AlertTriangle as AlertTriangleIcon, FileCheck as FileCheckIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  Divider,
  Alert,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import type { PageConfig } from 'yappc-config-schema';

import { ConfigRenderer } from './ConfigRenderer';
import { MockDataManager } from './MockDataManager';
import { AccessibilityChecker } from './AccessibilityChecker';
import { VisualRegression } from './VisualRegression';

/**
 * Preview state enum for security and policy enforcement.
 */
type PreviewState = 'active' | 'blocked' | 'degraded' | 'policy-required';

/**
 * @doc.type component
 * @doc.purpose Config-driven preview host for rendering PageConfig with security states
 * @doc.layer product
 * @doc.pattern Container Component
 */
interface PreviewHostProps {
  config: PageConfig;
  onConfigChange?: (config: PageConfig) => void;
  readOnly?: boolean;
  previewState?: PreviewState;
  trustLevel?: 'TRUSTED_LOCAL' | 'TRUSTED_CONTROLLED' | 'SEMI_TRUSTED' | 'UNTRUSTED';
  policyMessage?: string;
  onPolicyAction?: () => void;
}

type PreviewPanel = 'preview' | 'mock-data' | 'a11y' | 'visual-regression';

export const PreviewHost: React.FC<PreviewHostProps> = ({
  config,
  onConfigChange,
  readOnly = false,
  previewState = 'active',
  trustLevel = 'TRUSTED_LOCAL',
  policyMessage,
  onPolicyAction,
}) => {
  const [activePanel, setActivePanel] = useState<PreviewPanel>('preview');
  const [mockData, setMockData] = useState<Record<string, unknown>>({});

  const handleRefresh = useCallback(() => {
    window.location.reload();
  }, []);

  const handleMockDataChange = useCallback((data: Record<string, unknown>) => {
    setMockData(data);
  }, []);

  const renderSecurityState = () => {
    switch (previewState) {
      case 'blocked':
        return (
          <Alert severity="error" className="mb-4">
            <ShieldAlertIcon className="size-4" />
            <Typography variant="subtitle2">Preview Blocked</Typography>
            <Typography variant="body2">
              {policyMessage || 'This preview has been blocked due to security policy violations.'}
            </Typography>
            <Box className="mt-2">
              <Typography variant="caption" color="text.secondary">
                Trust Level: {trustLevel}
              </Typography>
            </Box>
          </Alert>
        );
      case 'degraded':
        return (
          <Alert severity="warning" className="mb-4">
            <AlertTriangleIcon className="size-4" />
            <Typography variant="subtitle2">Degraded Preview</Typography>
            <Typography variant="body2">
              {policyMessage || 'This preview is running in degraded mode with limited functionality.'}
            </Typography>
            <Box className="mt-2">
              <Typography variant="caption" color="text.secondary">
                Trust Level: {trustLevel}
              </Typography>
            </Box>
          </Alert>
        );
      case 'policy-required':
        return (
          <Alert severity="info" className="mb-4">
            <FileCheckIcon className="size-4" />
            <Typography variant="subtitle2">Policy Review Required</Typography>
            <Typography variant="body2">
              {policyMessage || 'This preview requires policy review before proceeding.'}
            </Typography>
            <Box className="mt-2">
              <Typography variant="caption" color="text.secondary">
                Trust Level: {trustLevel}
              </Typography>
            </Box>
            {onPolicyAction && (
              <Button
                size="small"
                variant="contained"
                onClick={onPolicyAction}
                className="mt-2"
              >
                Request Review
              </Button>
            )}
          </Alert>
        );
      default:
        return null;
    }
  };

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
              disabled={previewState === 'blocked'}
            >
              Preview
            </Button>
            <Button
              size="small"
              variant={activePanel === 'mock-data' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('mock-data')}
              startIcon={<SettingsIcon size={14} />}
              disabled={previewState === 'blocked'}
            >
              Mock Data
            </Button>
            <Button
              size="small"
              variant={activePanel === 'a11y' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('a11y')}
              disabled={previewState === 'blocked'}
            >
              A11y
            </Button>
            <Button
              size="small"
              variant={activePanel === 'visual-regression' ? 'contained' : 'outlined'}
              onClick={() => setActivePanel('visual-regression')}
              disabled={previewState === 'blocked'}
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

      {renderSecurityState()}

      <Box className="flex-1 overflow-auto">
        {previewState === 'blocked' ? (
          <Box className="flex h-full items-center justify-center">
            <Typography variant="body2" color="text.secondary">
              Preview is blocked. See details above.
            </Typography>
          </Box>
        ) : (
          renderPanel()
        )}
      </Box>
    </Box>
  );
};
