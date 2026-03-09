import { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  ListItemIcon,
  Button,
  Alert,
  Chip,
  Stack,
  LinearProgress,
  InteractiveList as List,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { ListItemButton } from '@ghatana/ui';
import { Sparkles as AutoAwesome, TrendingUp, Lightbulb } from 'lucide-react';
import type { RailPanelProps } from '../UnifiedLeftRail.types';
import type { AISuggestion } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * AI Panel - AI-powered suggestions and patterns
 * Shows contextual AI recommendations based on canvas content
 */
export function AIPanel({ context, selectedNodeIds = [] }: RailPanelProps) {
  const [suggestions, setSuggestions] = useState<AISuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);

  // Fetch suggestions via RailDataService
  useEffect(() => {
    const fetchSuggestions = async () => {
      setLoading(true);
      try {
        const data = await railService.getSuggestions({
          context,
          selectedNodeIds,
        });
        setSuggestions(data);
      } catch (err) {
        console.error('Failed to load AI suggestions:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchSuggestions();
  }, [context, selectedNodeIds]);

  const handleAnalyze = async () => {
    setAnalyzing(true);
    try {
      // Force refresh suggestions
      const data = await railService.getSuggestions({
        context,
        selectedNodeIds,
        forceRefresh: true,
      });
      setSuggestions(data);
    } catch (err) {
      console.error('Analysis failed', err);
    } finally {
      setAnalyzing(false);
    }
  };

  const getSuggestionIcon = (type: string) => {
    switch (type) {
      case 'pattern-match':
        return <TrendingUp className="text-blue-600 text-lg" />;
      case 'improvement':
        return <Lightbulb className="text-amber-600 text-lg" />;
      case 'optimization':
        return <AutoAwesome className="text-green-600 text-lg" />;
      default:
        return <Lightbulb className="text-lg" />;
    }
  };

  const getConfidenceColor = (confidence: number) => {
    if (confidence >= 0.8) return 'success';
    if (confidence >= 0.6) return 'warning';
    return 'info';
  };

  if (loading) {
    return (
      <Box className="p-4 flex justify-center">
        <CircularProgress size={32} />
      </Box>
    );
  }

  return (
    <Box
      className="p-4 flex flex-col h-full"
    >
      <Stack direction="row" gap={1} className="mb-4" alignItems="center">
        <Typography variant="subtitle2" fontWeight={600} className="flex-1">
          ✨ AI Suggestions
        </Typography>
      </Stack>

      {selectedNodeIds.length === 0 && (
        <Alert severity="info" className="mb-4">
          Select elements to get AI suggestions
        </Alert>
      )}

      <Box className="flex-1 overflow-auto">
        {suggestions.length === 0 ? (
          <Typography variant="body2" color="text.secondary" className="mt-4">
            No suggestions yet. Run analysis to get AI insights.
          </Typography>
        ) : (
          <List dense>
            {suggestions.map((suggestion) => (
              <ListItem
                key={suggestion.id}
                disablePadding
                className="mb-2"
                secondaryAction={
                  <Button
                    size="small"
                    variant="text"
                    className="normal-case text-xs"
                  >
                    {suggestion.action}
                  </Button>
                }
              >
                <ListItemButton
                  className="flex-col items-start"
                >
                  <Stack
                    direction="row"
                    gap={1}
                    alignItems="center"
                    className="w-full mb-1"
                  >
                    <ListItemIcon className="min-w-[28px]">
                      {getSuggestionIcon(suggestion.type)}
                    </ListItemIcon>
                    <ListItemText
                      primary={suggestion.title}
                      primaryTypographyProps={{ variant: 'body2' }}
                      className="flex-1"
                    />
                    <Chip
                      label={`${Math.round(suggestion.confidence * 100)}%`}
                      size="small"
                      color={getConfidenceColor(suggestion.confidence)}
                      variant="outlined"
                    />
                  </Stack>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    className="ml-8"
                  >
                    {suggestion.description}
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={suggestion.confidence * 100}
                    className="w-full mt-1 ml-8"
                  />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        )}
      </Box>

      <Button
        fullWidth
        variant="contained"
        startIcon={analyzing ? <CircularProgress size={16} /> : <AutoAwesome />}
        onClick={handleAnalyze}
        disabled={analyzing || selectedNodeIds.length === 0}
        className="mt-4"
      >
        {analyzing ? 'Analyzing...' : 'Analyze Design'}
      </Button>
    </Box>
  );
}
