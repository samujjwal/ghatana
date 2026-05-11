/**
 * Mock Data Manager
 *
 * Manage mock data injection for preview.
 *
 * @packageDocumentation
 */

import { Database as DatabaseIcon, Plus as PlusIcon, Trash as TrashIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  TextField,
  IconButton,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';
import { useTranslation } from '@ghatana/i18n';

import type { PageConfig } from 'yappc-config-schema';

/**
 * @doc.type component
 * @doc.purpose Manage mock data injection for preview
 * @doc.layer product
 * @doc.pattern Panel Component
 */
interface MockDataManagerProps {
  config: PageConfig;
  onDataChange?: (data: Record<string, unknown>) => void;
}

export const MockDataManager: React.FC<MockDataManagerProps> = ({ config, onDataChange }) => {
  const { t } = useTranslation('common');
  const [mockData, setMockData] = useState<Record<string, unknown>>({});
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');

  const handleAddData = useCallback(() => {
    if (!newKey) return;

    try {
      const parsedValue = JSON.parse(newValue);
      const updated = { ...mockData, [newKey]: parsedValue };
      setMockData(updated);
      onDataChange?.(updated);
      setNewKey('');
      setNewValue('');
    } catch {
      // If JSON parse fails, treat as string
      const updated = { ...mockData, [newKey]: newValue };
      setMockData(updated);
      onDataChange?.(updated);
      setNewKey('');
      setNewValue('');
    }
  }, [newKey, newValue, mockData, onDataChange]);

  const handleRemoveData = useCallback(
    (key: string) => {
      const updated = { ...mockData };
      delete updated[key];
      setMockData(updated);
      onDataChange?.(updated);
    },
    [mockData, onDataChange]
  );

  const handleUpdateValue = useCallback(
    (key: string, value: string) => {
      try {
        const parsedValue = JSON.parse(value);
        const updated = { ...mockData, [key]: parsedValue };
        setMockData(updated);
        onDataChange?.(updated);
      } catch {
        const updated = { ...mockData, [key]: value };
        setMockData(updated);
        onDataChange?.(updated);
      }
    },
    [mockData, onDataChange]
  );

  return (
    <Box data-testid="mock-data-manager" className="p-4">
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 3 }}>
        <DatabaseIcon size={16} />
        <Typography variant="h6">Mock Data Manager</Typography>
      </Stack>

      <Paper variant="outlined" className="p-4 mb-4">
        <Typography variant="subtitle2" gutterBottom>
          Add New Mock Data
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Key"
            value={newKey}
            onChange={(e) => setNewKey(e.target.value)}
            placeholder={t('mockData.keyPlaceholder')}
            fullWidth
            size="small"
          />
          <TextField
            label="Value (JSON or string)"
            value={newValue}
            onChange={(e) => setNewValue(e.target.value)}
            placeholder={t('mockData.valuePlaceholder')}
            fullWidth
            multiline
            rows={2}
            size="small"
          />
          <Button variant="contained" onClick={handleAddData} startIcon={<PlusIcon size={14} />}>
            Add Data
          </Button>
        </Stack>
      </Paper>

      <Typography variant="subtitle2" gutterBottom>
        Current Mock Data ({Object.keys(mockData).length})
      </Typography>

      {Object.keys(mockData).length === 0 ? (
        <Typography color="text.secondary">No mock data configured</Typography>
      ) : (
        <Stack spacing={2}>
          {Object.entries(mockData).map(([key, value]) => (
            <Paper key={key} variant="outlined" className="p-3">
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
                <Box className="flex-1">
                  <Typography variant="subtitle2" gutterBottom>
                    {key}
                  </Typography>
                  <TextField
                    value={typeof value === 'string' ? value : JSON.stringify(value, null, 2)}
                    onChange={(e) => handleUpdateValue(key, e.target.value)}
                    multiline
                    rows={2}
                    fullWidth
                    size="small"
                  />
                </Box>
                <IconButton onClick={() => handleRemoveData(key)} size="small">
                  <TrashIcon size={16} />
                </IconButton>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  );
};
