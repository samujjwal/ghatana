import { useState, useEffect, useMemo } from 'react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  ListItemIcon,
  Chip,
  Alert,
  Stack,
  IconButton,
  Button,
  InteractiveList as List,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { ListItemButton } from '@ghatana/ui';
import { HardDrive as StorageOutlined, Cloud as CloudQueueOutlined, Cloud as CloudOutlined, MoreVertical as MoreVert, Settings, Plus as Add } from 'lucide-react';
import type { RailPanelProps } from '../UnifiedLeftRail.types';
import type { DataSource } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * Data Panel - Data sources and API connections
 * Manage databases, APIs, and service integrations
 */
export function DataPanel({ context }: RailPanelProps) {
  const [dataSources, setDataSources] = useState<DataSource[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch data sources via RailDataService
  useEffect(() => {
    const fetchDataSources = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await railService.getDataSources();
        setDataSources(data);
      } catch (err) {
        console.error('Failed to load data sources:', err);
        setError('Failed to load data sources');
      } finally {
        setLoading(false);
      }
    };

    fetchDataSources();
  }, []);

  const getSourceIcon = (type: string) => {
    switch (type) {
      case 'database':
        return <StorageOutlined className="text-sky-600 text-lg" />;
      case 'api':
        return (
          <CloudQueueOutlined className="text-green-600 text-lg" />
        );
      case 'service':
        return <CloudOutlined className="text-amber-600 text-lg" />;
      default:
        return <StorageOutlined className="text-lg" />;
    }
  };

  const getStatusChip = (status: string) => {
    switch (status) {
      case 'connected':
        return (
          <Chip
            label="Connected"
            size="small"
            color="success"
            variant="outlined"
          />
        );
      case 'disconnected':
        return (
          <Chip
            label="Disconnected"
            size="small"
            color="warning"
            variant="outlined"
          />
        );
      case 'error':
        return (
          <Chip label="Error" size="small" color="error" variant="outlined" />
        );
      default:
        return null;
    }
  };

  const groupedSources = useMemo(() => {
    const groups = new Map<string, DataSource[]>();
    dataSources.forEach((source) => {
      if (!groups.has(source.type)) {
        groups.set(source.type, []);
      }
      groups.get(source.type)!.push(source);
    });
    return groups;
  }, [dataSources]);

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
          Data Sources
        </Typography>
        <IconButton size="small" title="Add Source">
          <Add className="text-lg" />
        </IconButton>
      </Stack>

      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}

      <Box className="flex-1 overflow-auto">
        {dataSources.length === 0 ? (
          <Typography variant="body2" color="text.secondary" className="mt-4">
            No data sources yet
          </Typography>
        ) : (
          Array.from(groupedSources.entries()).map(([type, sources]) => (
            <Box key={type} className="mb-4">
              <Typography
                variant="caption"
                fontWeight={700}
                className="uppercase ml-2 opacity-[0.7]"
              >
                {type}
              </Typography>
              <List dense>
                {sources.map((source) => (
                  <ListItem
                    key={source.id}
                    disablePadding
                    secondaryAction={
                      <IconButton size="small">
                        <MoreVert className="text-base" />
                      </IconButton>
                    }
                  >
                    <ListItemButton>
                      <ListItemIcon className="min-w-[28px]">
                        {getSourceIcon(source.type)}
                      </ListItemIcon>
                      <ListItemText
                        primary={source.name}
                        secondary={
                          <>
                            <Typography
                              component="span"
                              variant="caption"
                              color="text.secondary"
                            >
                              {source.provider}
                            </Typography>
                            {source.tables && (
                              <Typography
                                component="span"
                                variant="caption"
                                color="text.disabled"
                                className="ml-2"
                              >
                                {source.tables} tables
                              </Typography>
                            )}
                            {source.endpoints && (
                              <Typography
                                component="span"
                                variant="caption"
                                color="text.disabled"
                                className="ml-2"
                              >
                                {source.endpoints} endpoints
                              </Typography>
                            )}
                          </>
                        }
                        primaryTypographyProps={{ variant: 'body2' }}
                      />
                      {getStatusChip(source.status)}
                    </ListItemButton>
                  </ListItem>
                ))}
              </List>
            </Box>
          ))
        )}
      </Box>

      <Button fullWidth variant="outlined" size="small" className="mt-4">
        Add Data Source
      </Button>
    </Box>
  );
}
