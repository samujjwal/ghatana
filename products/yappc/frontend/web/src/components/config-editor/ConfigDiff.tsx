/**
 * Config Diff
 *
 * Visual diff between two PageConfig versions.
 *
 * @packageDocumentation
 */

import { ArrowLeft as LeftArrow, ArrowRight as RightArrow, GitCompare as DiffIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  Divider,
} from '@ghatana/design-system';
import React, { useState, useMemo, useCallback } from 'react';

interface PageComponentConfig {
  id: string;
  [key: string]: unknown;
}

interface PageConfig {
  id: string;
  title: string;
  route: string;
  components?: PageComponentConfig[];
}

/**
 * @doc.type component
 * @doc.purpose Visual diff between two PageConfig versions
 * @doc.layer product
 * @doc.pattern Widget
 */
interface ConfigDiffProps {
  baseConfig: PageConfig;
  targetConfig: PageConfig;
  onApplyChange?: (config: PageConfig) => void;
}

interface DiffChange {
  path: string;
  type: 'added' | 'removed' | 'modified';
  oldValue: unknown;
  newValue: unknown;
}

export const ConfigDiff: React.FC<ConfigDiffProps> = ({
  baseConfig,
  targetConfig,
  onApplyChange,
}) => {
  const [selectedSide, setSelectedSide] = useState<'left' | 'right'>('right');

  const changes = useMemo(() => {
    const diff: DiffChange[] = [];

    // Compare basic fields
    if (baseConfig.id !== targetConfig.id) {
      diff.push({
        path: 'id',
        type: 'modified',
        oldValue: baseConfig.id,
        newValue: targetConfig.id,
      });
    }

    if (baseConfig.title !== targetConfig.title) {
      diff.push({
        path: 'title',
        type: 'modified',
        oldValue: baseConfig.title,
        newValue: targetConfig.title,
      });
    }

    if (baseConfig.route !== targetConfig.route) {
      diff.push({
        path: 'route',
        type: 'modified',
        oldValue: baseConfig.route,
        newValue: targetConfig.route,
      });
    }

    // Compare components
    const baseComponents = baseConfig.components || [];
    const targetComponents = targetConfig.components || [];

    // Check for added components
    targetComponents.forEach((targetComp: PageComponentConfig) => {
      const baseComp = baseComponents.find((c: PageComponentConfig) => c.id === targetComp.id);
      if (!baseComp) {
        diff.push({
          path: `components.${targetComp.id}`,
          type: 'added',
          oldValue: null,
          newValue: targetComp,
        });
      }
    });

    // Check for removed components
    baseComponents.forEach((baseComp: PageComponentConfig) => {
      const targetComp = targetComponents.find((c: PageComponentConfig) => c.id === baseComp.id);
      if (!targetComp) {
        diff.push({
          path: `components.${baseComp.id}`,
          type: 'removed',
          oldValue: baseComp,
          newValue: null,
        });
      }
    });

    // Check for modified components
    targetComponents.forEach((targetComp: PageComponentConfig) => {
      const baseComp = baseComponents.find((c: PageComponentConfig) => c.id === targetComp.id);
      if (baseComp) {
        const baseStr = JSON.stringify(baseComp);
        const targetStr = JSON.stringify(targetComp);
        if (baseStr !== targetStr) {
          diff.push({
            path: `components.${targetComp.id}`,
            type: 'modified',
            oldValue: baseComp,
            newValue: targetComp,
          });
        }
      }
    });

    return diff;
  }, [baseConfig, targetConfig]);

  const handleApplyChange = useCallback(() => {
    onApplyChange?.(selectedSide === 'left' ? baseConfig : targetConfig);
  }, [selectedSide, baseConfig, targetConfig, onApplyChange]);

  const renderChange = (change: DiffChange) => {
    const color =
      change.type === 'added'
        ? 'text-green-600'
        : change.type === 'removed'
        ? 'text-red-600'
        : 'text-blue-600';

    return (
      <Paper key={change.path} variant="outlined" className="p-3 mb-2">
        <Stack spacing={1}>
          <Typography variant="subtitle2" className={color}>
            {change.type === 'added' && '+'}
            {change.type === 'removed' && '-'}
            {change.type === 'modified' && '~'}
            {change.path}
          </Typography>

          {change.type === 'modified' && (
            <>
              <Typography variant="caption" color="text.secondary">
                Old: {JSON.stringify(change.oldValue)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                New: {JSON.stringify(change.newValue)}
              </Typography>
            </>
          )}

          {change.type === 'added' && (
            <Typography variant="caption" color="text.secondary">
              Added: {JSON.stringify(change.newValue)}
            </Typography>
          )}

          {change.type === 'removed' && (
            <Typography variant="caption" color="text.secondary">
              Removed: {JSON.stringify(change.oldValue)}
            </Typography>
          )}
        </Stack>
      </Paper>
    );
  };

  return (
    <Box data-testid="config-diff">
      <Box className="mb-2 flex items-center justify-between">
        <Box className="flex items-center gap-1">
          <DiffIcon size={16} />
          <Typography variant="h6">Config Diff</Typography>
        </Box>

        <Button
          variant="contained"
          onClick={handleApplyChange}
          startIcon={selectedSide === 'left' ? <LeftArrow size={16} /> : <RightArrow size={16} />}
        >
          Apply {selectedSide === 'left' ? 'Left' : 'Right'}
        </Button>
      </Box>

      <Box className="mb-3 flex gap-2">
        <Button
          variant={selectedSide === 'left' ? 'contained' : 'outlined'}
          onClick={() => setSelectedSide('left')}
          size="small"
        >
          Base Version
        </Button>
        <Button
          variant={selectedSide === 'right' ? 'contained' : 'outlined'}
          onClick={() => setSelectedSide('right')}
          size="small"
        >
          Target Version
        </Button>
      </Box>

      <Divider />

      <Typography variant="subtitle2" gutterBottom>
        Changes ({changes.length})
      </Typography>

      {changes.length === 0 ? (
        <Typography color="text.secondary">No changes detected</Typography>
      ) : (
        <Box>{changes.map(renderChange)}</Box>
      )}
    </Box>
  );
};
