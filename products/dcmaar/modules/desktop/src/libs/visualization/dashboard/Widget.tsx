/**
 * Widget Component
 * 
 * Reusable widget container with:
 * - Header with title and actions
 * - Configurable content area
 * - Loading and error states
 * - Refresh capability
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  Box,
  Card,
  CardHeader,
  CardContent,
  IconButton,
  MenuItem,
  CircularProgress,
  Typography,
  useTheme,
} from '@mui/material';
import Menu from '@mui/material/Menu';
import {
  MoreVert as MoreVertIcon,
  Refresh as RefreshIcon,
  Settings as SettingsIcon,
  Close as CloseIcon,
} from '@mui/icons-material';
import type { Widget as WidgetType } from '../types';

export interface WidgetProps {
  widget: WidgetType;
  onRefresh?: () => void;
  onSettings?: () => void;
  onRemove?: () => void;
  loading?: boolean;
  error?: Error;
  children?: React.ReactNode;
}

/**
 * Widget component with header and content area
 */
export const Widget: React.FC<WidgetProps> = ({
  widget,
  onRefresh,
  onSettings,
  onRemove,
  loading = false,
  error,
  children,
}) => {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [autoRefreshTimer, setAutoRefreshTimer] = useState<NodeJS.Timeout | null>(null);

  // Handle menu open
  const handleMenuOpen = useCallback((event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  }, []);

  // Handle menu close
  const handleMenuClose = useCallback(() => {
    setAnchorEl(null);
  }, []);

  // Handle refresh
  const handleRefresh = useCallback(() => {
    if (onRefresh) {
      onRefresh();
    }
    handleMenuClose();
  }, [onRefresh, handleMenuClose]);

  // Handle settings
  const handleSettings = useCallback(() => {
    if (onSettings) {
      onSettings();
    }
    handleMenuClose();
  }, [onSettings, handleMenuClose]);

  // Handle remove
  const handleRemove = useCallback(() => {
    if (onRemove) {
      onRemove();
    }
    handleMenuClose();
  }, [onRemove, handleMenuClose]);

  // Auto-refresh setup
  useEffect(() => {
    if (widget.refreshInterval && onRefresh) {
      const timer = setInterval(() => {
        onRefresh();
      }, widget.refreshInterval);

      setAutoRefreshTimer(timer);

      return () => {
        clearInterval(timer);
      };
    }
  }, [widget.refreshInterval, onRefresh]);

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (autoRefreshTimer) {
        clearInterval(autoRefreshTimer);
      }
    };
  }, [autoRefreshTimer]);

  return (
    <Card
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: theme.palette.background.paper,
        boxShadow: theme.shadows[2],
      }}
    >
      <CardHeader
        title={widget.title}
        subheader={widget.description}
        action={
          <Box>
            {onRefresh && (
              <IconButton
                size="small"
                onClick={handleRefresh}
                disabled={loading}
                aria-label="refresh"
              >
                <RefreshIcon />
              </IconButton>
            )}
            <IconButton
              size="small"
              onClick={handleMenuOpen}
              aria-label="widget menu"
            >
              <MoreVertIcon />
            </IconButton>
            <Menu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={handleMenuClose}
            >
              {onRefresh && (
                <MenuItem onClick={handleRefresh}>
                  <RefreshIcon fontSize="small" sx={{ mr: 1 }} />
                  Refresh
                </MenuItem>
              )}
              {onSettings && (
                <MenuItem onClick={handleSettings}>
                  <SettingsIcon fontSize="small" sx={{ mr: 1 }} />
                  Settings
                </MenuItem>
              )}
              {onRemove && (
                <MenuItem onClick={handleRemove}>
                  <CloseIcon fontSize="small" sx={{ mr: 1 }} />
                  Remove
                </MenuItem>
              )}
            </Menu>
          </Box>
        }
        sx={{
          borderBottom: `1px solid ${theme.palette.divider}`,
          '& .MuiCardHeader-title': {
            fontSize: '1rem',
            fontWeight: 600,
          },
          '& .MuiCardHeader-subheader': {
            fontSize: '0.875rem',
          },
        }}
      />
      <CardContent
        sx={{
          flex: 1,
          overflow: 'auto',
          p: 2,
        }}
      >
        {loading ? (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
            }}
          >
            <CircularProgress />
          </Box>
        ) : error ? (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              color: theme.palette.error.main,
            }}
          >
            <Typography variant="body2">
              Error: {error.message}
            </Typography>
          </Box>
        ) : (
          children
        )}
      </CardContent>
    </Card>
  );
};

Widget.displayName = 'Widget';

export default Widget;
