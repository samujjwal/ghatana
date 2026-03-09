import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  Button,
  Alert,
  Stack,
  Divider,
  InteractiveList as List,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { ListItemButton } from '@ghatana/ui';
import { Undo2 as Undo, Redo2 as Redo, RotateCcw as Restore } from 'lucide-react';
import type { RailPanelProps } from '../UnifiedLeftRail.types';
import type { HistoryEntry } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * History Panel - Undo/redo visualization
 * Shows action history and allows navigation through history
 */
export function HistoryPanel({ context }: RailPanelProps) {
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loading, setLoading] = useState(false);

  // Fetch history via RailDataService
  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      try {
        const data = await railService.getHistory();
        setHistory(data);
        setCurrentIndex(0); // Reset to latest (or logical start)
      } catch (err) {
        console.error('Failed to load history:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, []);

  const formatTime = (dateInput: Date | string) => {
    const date = new Date(dateInput);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    return date.toLocaleDateString();
  };

  const handleUndo = useCallback(() => {
    if (currentIndex > 0) {
      setCurrentIndex(currentIndex - 1);
    }
  }, [currentIndex]);

  const handleRedo = useCallback(() => {
    if (currentIndex < history.length - 1) {
      setCurrentIndex(currentIndex + 1);
    }
  }, [currentIndex, history.length]);

  const handleGoToState = (index: number) => {
    setCurrentIndex(index);
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
          History
        </Typography>
        <Button
          size="small"
          onClick={handleUndo}
          disabled={currentIndex === 0}
          title="Undo"
        >
          <Undo className="text-base" />
        </Button>
        <Button
          size="small"
          onClick={handleRedo}
          disabled={currentIndex >= history.length - 1}
          title="Redo"
        >
          <Redo className="text-base" />
        </Button>
      </Stack>

      {history.length === 0 ? (
        <Typography variant="body2" color="text.secondary" className="mt-4">
          No history yet
        </Typography>
      ) : (
        <List dense className="flex-1 overflow-auto">
          {history.map((entry, idx) => (
            <Box key={entry.id}>
              <ListItem
                disablePadding
                selected={idx === currentIndex}
                className={idx === currentIndex ? 'bg-gray-100 dark:bg-gray-800' : 'bg-transparent'}
                style={{ opacity: idx > currentIndex ? 0.5 : 1 }}
              >
                <ListItemButton onClick={() => handleGoToState(idx)} dense>
                  <ListItemText
                    primary={entry.action}
                    secondary={
                      <>
                        <Typography
                          component="span"
                          variant="caption"
                          color="text.secondary"
                        >
                          {entry.details}
                        </Typography>
                        <Typography
                          component="div"
                          variant="caption"
                          color="text.disabled"
                          className="mt-1"
                        >
                          {formatTime(entry.timestamp)}
                        </Typography>
                      </>
                    }
                    primaryTypographyProps={{ variant: 'body2' }}
                  />
                </ListItemButton>
              </ListItem>
              {idx < history.length - 1 && <Divider className="my-1" />}
            </Box>
          ))}
        </List>
      )}

      <Button
        fullWidth
        size="small"
        variant="outlined"
        startIcon={<Restore />}
        className="mt-4"
      >
        Clear History
      </Button>
    </Box>
  );
}
