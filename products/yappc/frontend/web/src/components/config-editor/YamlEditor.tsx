/**
 * YAML Editor
 *
 * YAML editor with validation and auto-formatting.
 *
 * @packageDocumentation
 */

import { FileCode as FileCodeIcon, Check as CheckIcon, AlertTriangle as WarningIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  Alert,
} from '@ghatana/design-system';
import React, { useState, useCallback, useEffect } from 'react';
import { Textarea } from '../ui/Textarea';

import type { PageConfig } from 'yappc-config-schema';

/**
 * @doc.type component
 * @doc.purpose YAML editor with validation and auto-formatting
 * @doc.layer product
 * @doc.pattern Widget
 */
interface YamlEditorProps {
  value: PageConfig;
  onChange: (value: PageConfig) => void;
  onError?: (error: string) => void;
  readOnly?: boolean;
}

export const YamlEditor: React.FC<YamlEditorProps> = ({
  value,
  onChange,
  onError,
  readOnly = false,
}) => {
  const [yamlText, setYamlText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isValid, setIsValid] = useState(true);

  // Simple JSON to YAML converter (placeholder - would use js-yaml in production)
  const jsonToYaml = useCallback((obj: unknown): string => {
    const toYaml = (data: unknown, indent = 0): string => {
      const spaces = '  '.repeat(indent);
      if (typeof data !== 'object' || data === null) {
        return String(data);
      }
      if (Array.isArray(data)) {
        return data.map((item) => `${spaces}- ${toYaml(item, indent + 1)}`).join('\n');
      }
      return Object.entries(data)
        .map(([key, val]) => {
          if (typeof val === 'object' && val !== null) {
            return `${spaces}${key}:\n${toYaml(val, indent + 1)}`;
          }
          return `${spaces}${key}: ${val}`;
        })
        .join('\n');
    };
    return toYaml(obj);
  }, []);

  // Simple YAML to JSON converter (placeholder - would use js-yaml in production)
  const yamlToJson = useCallback((yaml: string): unknown => {
    // This is a simplified YAML parser - in production, use js-yaml library
    const lines = yaml.split('\n');
    const result: Record<string, unknown> = {};
    const stack: Array<{ obj: Record<string, unknown>; indent: number }> = [
      { obj: result, indent: -1 },
    ];

    for (const line of lines) {
      const indent = line.search(/\S|$/);
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;

      const [key, ...valueParts] = trimmed.split(':');
      const value = valueParts.join(':').trim();

      // Pop stack to correct indentation level
      while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
        stack.pop();
      }

      const current = stack[stack.length - 1].obj;

      if (value) {
        current[key.trim()] = value === 'null' ? null : value.replace(/^['"]|['"]$/g, '');
      } else {
        current[key.trim()] = {};
        stack.push({ obj: current[key.trim()] as Record<string, unknown>, indent });
      }
    }

    return result;
  }, []);

  useEffect(() => {
    try {
      const yaml = jsonToYaml(value);
      setYamlText(yaml);
      setIsValid(true);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid YAML conversion');
      setIsValid(false);
    }
  }, [value, jsonToYaml]);

  const handleYamlChange = useCallback(
    (newYaml: string) => {
      setYamlText(newYaml);

      try {
        const parsed = yamlToJson(newYaml) as PageConfig;
        setIsValid(true);
        setError(null);
        onChange(parsed);
        onError?.('');
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Invalid YAML';
        setIsValid(false);
        setError(errorMessage);
        onError?.(errorMessage);
      }
    },
    [onChange, onError, yamlToJson]
  );

  const handleFormat = useCallback(() => {
    try {
      const parsed = yamlToJson(yamlText) as PageConfig;
      const formatted = jsonToYaml(parsed);
      setYamlText(formatted);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot format invalid YAML');
    }
  }, [yamlText, yamlToJson, jsonToYaml]);

  return (
    <Box data-testid="yaml-editor">
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Stack direction="row" alignItems="center" spacing={1}>
          <FileCodeIcon size={16} />
          <Typography variant="h6">YAML Editor</Typography>
        </Stack>

        {!readOnly && (
          <Button
            variant="outlined"
            size="small"
            onClick={handleFormat}
            disabled={!isValid}
            startIcon={<CheckIcon size={14} />}
          >
            Format
          </Button>
        )}
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <WarningIcon size={16} />
            <Typography variant="body2">{error}</Typography>
          </Stack>
        </Alert>
      )}

      <Alert severity="info" sx={{ mb: 2 }}>
        <Typography variant="body2">
          Note: This is a simplified YAML parser. For production, integrate the js-yaml library.
        </Typography>
      </Alert>

      <Paper variant="outlined" className="relative">
        <Textarea
          value={yamlText}
          onChange={(event) => handleYamlChange(event.target.value)}
          disabled={readOnly}
          rows={20}
          data-testid="yaml-textarea"
          className="min-h-[28rem] w-full resize-y border-0 bg-transparent p-3 font-mono text-sm outline-none"
        />
      </Paper>

      <Stack direction="row" justifyContent="space-between" sx={{ mt: 1 }}>
        <Typography variant="caption" color="text.secondary">
          {yamlText.length} characters
        </Typography>
        <Stack direction="row" alignItems="center" spacing={1}>
          {isValid ? (
            <CheckIcon size={14} className="text-success-color" />
          ) : (
            <WarningIcon size={14} className="text-destructive" />
          )}
          <Typography variant="caption" color={isValid ? 'success.main' : 'error.main'}>
            {isValid ? 'Valid YAML' : 'Invalid YAML'}
          </Typography>
        </Stack>
      </Stack>
    </Box>
  );
};
