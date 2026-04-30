/**
 * Intent Editor
 *
 * Natural language intent editor with AI-powered parsing.
 *
 * @packageDocumentation
 */

import { Sparkles as AutoAwesome, Send } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  TextField,
  Button,
  Paper,
  Alert,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import { IntentParser } from 'yappc-config-compiler';
import type { IntentConfig } from 'yappc-config-schema';

/**
 * @doc.type component
 * @doc.purpose Natural language intent editor with AI-powered parsing
 * @doc.layer product
 * @doc.pattern Widget
 */
interface IntentEditorProps {
  initialIntent?: string;
  onIntentParsed?: (intentConfig: IntentConfig) => void;
  author: string;
  tags?: string[];
}

export const IntentEditor: React.FC<IntentEditorProps> = ({
  initialIntent = '',
  onIntentParsed,
  author,
  tags = [],
}) => {
  const [intent, setIntent] = useState(initialIntent);
  const [isParsing, setIsParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [parsedIntent, setParsedIntent] = useState<IntentConfig | null>(null);

  const intentParser = new IntentParser();

  const handleParseIntent = useCallback(async () => {
    setError(null);
    setIsParsing(true);

    const validation = intentParser.validateIntent(intent);
    if (!validation.valid) {
      setError(validation.errors.join(', '));
      setIsParsing(false);
      return;
    }

    try {
      const intentConfig = await intentParser.parseIntent(intent, { author, tags });
      setParsedIntent(intentConfig);
      onIntentParsed?.(intentConfig);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to parse intent');
    } finally {
      setIsParsing(false);
    }
  }, [intent, author, tags, intentParser, onIntentParsed]);

  const handleKeyPress = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        handleParseIntent();
      }
    },
    [handleParseIntent]
  );

  return (
    <Box data-testid="intent-editor">
      <Typography variant="h6" gutterBottom>
        Intent Editor
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Describe what you want to build in natural language. Press Cmd/Ctrl+Enter to parse.
      </Typography>

      <TextField
        multiline
        rows={4}
        value={intent}
        onChange={(e) => setIntent(e.target.value)}
        onKeyDown={handleKeyPress}
        placeholder="e.g., Create a login page with email and password fields, remember me checkbox, and forgot password link"
        fullWidth
        disabled={isParsing}
        data-testid="intent-input"
      />

      <Stack direction="row" spacing={2} mt={2}>
        <Button
          variant="contained"
          onClick={handleParseIntent}
          disabled={isParsing || !intent.trim()}
          startIcon={isParsing ? null : <AutoAwesome size={16} />}
          endIcon={isParsing ? null : <Send size={16} />}
          data-testid="parse-intent-button"
        >
          {isParsing ? 'Parsing...' : 'Parse Intent'}
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}

      {parsedIntent && (
        <Paper variant="outlined" className="p-4 mt-4">
          <Typography variant="subtitle2" gutterBottom>
            Parsed Intent
          </Typography>
          <Stack spacing={1}>
            <Typography variant="body2">
              <strong>ID:</strong> {parsedIntent.id}
            </Typography>
            <Typography variant="body2">
              <strong>Intent:</strong> {parsedIntent.intent}
            </Typography>
            <Typography variant="body2">
              <strong>Description:</strong> {parsedIntent.description}
            </Typography>
            <Typography variant="body2">
              <strong>AI Confidence:</strong> {parsedIntent.aiConfidence}
            </Typography>
            <Typography variant="body2">
              <strong>AI Model:</strong> {parsedIntent.aiModel}
            </Typography>
            <Typography variant="body2">
              <strong>Author:</strong> {parsedIntent.author}
            </Typography>
            <Typography variant="body2">
              <strong>Tags:</strong> {parsedIntent.tags.join(', ') || 'None'}
            </Typography>
          </Stack>
        </Paper>
      )}
    </Box>
  );
};
