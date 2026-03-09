import { useState, useEffect, useMemo, useCallback } from 'react';
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
import { Cloud as CloudOutlined, HardDrive as StorageOutlined, PieChart as DataUsageOutlined, Shield as SecurityOutlined, MoreVertical as MoreVert, Plus as Add } from 'lucide-react';
import type { RailPanelProps } from '../UnifiedLeftRail.types';
import type { InfrastructureResource } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * Infrastructure Panel - Cloud resources and infrastructure
 * Manage compute, storage, database, and network resources
 */
export function InfrastructurePanel({ context, onInsertNode }: RailPanelProps) {
  const [resources, setResources] = useState<InfrastructureResource[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedResource, setSelectedResource] =
    useState<InfrastructureResource | null>(null);

  // Fetch infrastructure via RailDataService
  useEffect(() => {
    const fetchResources = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await railService.getInfrastructure();
        setResources(data);
      } catch (err) {
        console.error('Failed to load infrastructure:', err);
        setError('Failed to load infrastructure resources');
      } finally {
        setLoading(false);
      }
    };

    fetchResources();
  }, [context.projectType]);

  const groupedResources = useMemo(() => {
    const groups = new Map<string, InfrastructureResource[]>();
    resources.forEach((resource) => {
      if (!groups.has(resource.type)) {
        groups.set(resource.type, []);
      }
      groups.get(resource.type)!.push(resource);
    });
    return groups;
  }, [resources]);

  const getResourceIcon = (type: string) => {
    switch (type) {
      case 'compute':
        return (
          <DataUsageOutlined className="text-blue-600 text-lg" />
        );
      case 'storage':
        return <StorageOutlined className="text-sky-600 text-lg" />;
      case 'database':
        return <StorageOutlined className="text-green-600 text-lg" />;
      case 'network':
        return <CloudOutlined className="text-amber-600 text-lg" />;
      case 'security':
        return <SecurityOutlined className="text-red-600 text-lg" />;
      default:
        return <CloudOutlined className="text-lg" />;
    }
  };

  const getStatusChip = (status: string) => {
    switch (status) {
      case 'running':
        return (
          <Chip
            label="Running"
            size="small"
            color="success"
            variant="outlined"
          />
        );
      case 'stopped':
        return (
          <Chip
            label="Stopped"
            size="small"
            color="default"
            variant="outlined"
          />
        );
      case 'pending':
        return (
          <Chip
            label="Pending"
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

  const handleAddToCanvas = useCallback(
    (resource: InfrastructureResource) => {
      onInsertNode?.(
        {
          type: 'infrastructure',
          data: {
            label: resource.name,
            resourceType: resource.type,
            provider: resource.provider,
            status: resource.status,
          },
        },
        { x: 100, y: 100 }
      );
    },
    [onInsertNode]
  );

  const totalCost = resources.reduce((sum, r) => sum + (r.cost || 0), 0);

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
          ☁️ Infrastructure
        </Typography>
        <IconButton size="small" title="Add Resource">
          <Add className="text-lg" />
        </IconButton>
      </Stack>

      {totalCost > 0 && (
        <Chip
          label={`Monthly Cost: $${totalCost.toFixed(2)}`}
          size="small"
          className="mb-4 self-start"
        />
      )}

      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}

      <Box className="flex-1 overflow-auto">
        {resources.length === 0 ? (
          <Typography variant="body2" color="text.secondary" className="mt-4">
            No infrastructure resources yet
          </Typography>
        ) : (
          Array.from(groupedResources.entries()).map(([type, items]) => (
            <Box key={type} className="mb-4">
              <Typography
                variant="caption"
                fontWeight={700}
                className="uppercase ml-2 opacity-[0.7]"
              >
                {type}
              </Typography>
              <List dense>
                {items.map((resource) => (
                  <ListItem
                    key={resource.id}
                    disablePadding
                    secondaryAction={
                      <IconButton size="small">
                        <MoreVert className="text-base" />
                      </IconButton>
                    }
                  >
                    <ListItemButton onClick={() => handleAddToCanvas(resource)}>
                      <ListItemIcon className="min-w-[28px]">
                        {getResourceIcon(resource.type)}
                      </ListItemIcon>
                      <ListItemText
                        primary={resource.name}
                        secondary={
                          <>
                            <Typography
                              component="span"
                              variant="caption"
                              color="text.secondary"
                            >
                              {resource.provider}
                            </Typography>
                            {resource.cost && (
                              <Typography
                                component="span"
                                variant="caption"
                                color="text.disabled"
                                className="ml-2"
                              >
                                ${resource.cost.toFixed(2)}/mo
                              </Typography>
                            )}
                          </>
                        }
                        primaryTypographyProps={{ variant: 'body2' }}
                      />
                      {getStatusChip(resource.status)}
                    </ListItemButton>
                  </ListItem>
                ))}
              </List>
            </Box>
          ))
        )}
      </Box>

      <Button fullWidth variant="outlined" size="small" className="mt-4">
        Add Resource
      </Button>
    </Box>
  );
}
