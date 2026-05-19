/**
 * Config Editor
 *
 * Main configuration editor component with JSON/YAML toggle.
 *
 * @packageDocumentation
 */

import { FileJson as JsonIcon, FileText as YamlIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  ToggleButton,
  ToggleButtonGroup,
  Paper,
  Divider,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import type { PageConfig } from 'yappc-config-schema';

import { JsonEditor } from './JsonEditor';
import { YamlEditor } from './YamlEditor';

/**
 * @doc.type component
 * @doc.purpose Main configuration editor with JSON/YAML toggle
 * @doc.layer product
 * @doc.pattern Container Component
 */
interface ConfigEditorProps {
  value: PageConfig;
  onChange: (value: PageConfig) => void;
  onError?: (error: string) => void;
  readOnly?: boolean;
}

type EditorMode = 'json' | 'yaml';

export const ConfigEditor: React.FC<ConfigEditorProps> = ({
  value,
  onChange,
  onError,
  readOnly = false,
}) => {
  const [mode, setMode] = useState<EditorMode>('json');

  const handleModeChange = useCallback(
    (newMode: string | string[]) => {
      if (newMode && typeof newMode === 'string') {
        setMode(newMode as EditorMode);
      }
    },
    []
  );

  return (
    <Box data-testid="config-editor">
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Typography variant="h6">Config Editor</Typography>

        <ToggleButtonGroup
          value={mode}
          exclusive
          onChange={handleModeChange}
          aria-label="Config editor mode"
        >
          <ToggleButton value="json" aria-label="JSON mode">
            <Stack direction="row" spacing={1} alignItems="center">
              <JsonIcon size={16} />
              <span>JSON</span>
            </Stack>
          </ToggleButton>
          <ToggleButton value="yaml" aria-label="YAML mode">
            <Stack direction="row" spacing={1} alignItems="center">
              <YamlIcon size={16} />
              <span>YAML</span>
            </Stack>
          </ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      <Divider sx={{ mb: 2 }} />

      <Paper variant="outlined" className="p-4">
        {mode === 'json' ? (
          <JsonEditor
            value={value}
            onChange={onChange}
            onError={onError}
            readOnly={readOnly}
          />
        ) : (
          <YamlEditor
            value={value}
            onChange={onChange}
            onError={onError}
            readOnly={readOnly}
          />
        )}
      </Paper>
    </Box>
  );
};
