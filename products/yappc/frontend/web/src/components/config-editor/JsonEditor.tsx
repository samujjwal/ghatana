/**
 * JSON Editor
 *
 * JSON editor with validation and auto-formatting.
 *
 * @packageDocumentation
 */

import { Code2 as CodeIcon, Check as CheckIcon, AlertTriangle as WarningIcon } from 'lucide-react';
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
 * @doc.purpose JSON editor with validation and auto-formatting
 * @doc.layer product
 * @doc.pattern Widget
 */
interface JsonEditorProps {
  value: PageConfig;
  onChange: (value: PageConfig) => void;
  onError?: (error: string) => void;
  readOnly?: boolean;
}

export const JsonEditor: React.FC<JsonEditorProps> = ({
  value,
  onChange,
  onError,
  readOnly = false,
}) => {
  const [jsonText, setJsonText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isValid, setIsValid] = useState(true);

  useEffect(() => {
    try {
      const formatted = JSON.stringify(value, null, 2);
      setJsonText(formatted);
      setIsValid(true);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid JSON');
      setIsValid(false);
    }
  }, [value]);

  const handleJsonChange = useCallback(
    (newJson: string) => {
      setJsonText(newJson);

      try {
        const parsed = JSON.parse(newJson);
        setIsValid(true);
        setError(null);
        onChange(parsed);
        onError?.('');
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Invalid JSON';
        setIsValid(false);
        setError(errorMessage);
        onError?.(errorMessage);
      }
    },
    [onChange, onError]
  );

  const handleFormat = useCallback(() => {
    try {
      const parsed = JSON.parse(jsonText);
      const formatted = JSON.stringify(parsed, null, 2);
      setJsonText(formatted);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot format invalid JSON');
    }
  }, [jsonText]);

  const handleMinify = useCallback(() => {
    try {
      const parsed = JSON.parse(jsonText);
      const minified = JSON.stringify(parsed);
      setJsonText(minified);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot minify invalid JSON');
    }
  }, [jsonText]);

  return (
    <Box data-testid="json-editor">
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Stack direction="row" alignItems="center" spacing={1}>
          <CodeIcon size={16} />
          <Typography variant="h6">JSON Editor</Typography>
        </Stack>

        {!readOnly && (
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              size="small"
              onClick={handleFormat}
              disabled={!isValid}
              startIcon={<CheckIcon size={14} />}
            >
              Format
            </Button>
            <Button
              variant="outlined"
              size="small"
              onClick={handleMinify}
              disabled={!isValid}
            >
              Minify
            </Button>
          </Stack>
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

      <Paper variant="outlined" className="relative">
        <Textarea
          value={jsonText}
          onChange={(event) => handleJsonChange(event.target.value)}
          disabled={readOnly}
          rows={20}
          data-testid="json-textarea"
          className="min-h-[28rem] w-full resize-y border-0 bg-transparent p-3 font-mono text-sm outline-none"
        />
      </Paper>

      <Stack direction="row" justifyContent="space-between" sx={{ mt: 1 }}>
        <Typography variant="caption" color="text.secondary">
          {jsonText.length} characters
        </Typography>
        <Stack direction="row" alignItems="center" spacing={1}>
          {isValid ? (
            <CheckIcon size={14} className="text-success-color" />
          ) : (
            <WarningIcon size={14} className="text-destructive" />
          )}
          <Typography variant="caption" color={isValid ? 'success.main' : 'error.main'}>
            {isValid ? 'Valid JSON' : 'Invalid JSON'}
          </Typography>
        </Stack>
      </Stack>
    </Box>
  );
};
