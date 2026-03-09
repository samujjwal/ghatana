import { useState, useMemo, useCallback, useEffect } from 'react';
import {
  Box,
  Typography,
  InputAdornment,
  ListItem,
  ListItemText,
  ListItemIcon,
  Button,
  Alert,
  Chip,
  Stack,
  InteractiveList as List,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { TextField, ListItemButton } from '@ghatana/ui';
import { Search, Plus as Add, Settings } from 'lucide-react';
import type { RailPanelProps } from '../UnifiedLeftRail.types';
import type { ComponentLibraryItem } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * Components Panel - Reusable component library
 * Fetches components from backend and provides drag-drop insertion
 */
export function ComponentsPanel({ context, onInsertNode }: RailPanelProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [components, setComponents] = useState<ComponentLibraryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch components via RailDataService
  useEffect(() => {
    const fetchComponents = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await railService.getComponents(undefined, searchQuery);
        setComponents(data);
      } catch (err) {
        console.error('Failed to load components:', err);
        setError('Failed to load components');
      } finally {
        setLoading(false);
      }
    };

    // Debounce search if needed, but for now just fetch on mount or query change if we wanted server-side search
    // Since we also have client-side filtering, we can just fetch all or filter on server.
    // The previous implementation fetched all once. Let's stick to that for now to match behavior,
    // unless searchQuery is intended for server-side. The UI does client-side filtering.
    // So we fetch once.
    fetchComponents();
  }, [context.projectType]);

  // Filter by search
  const filteredComponents = useMemo(() => {
    if (!searchQuery) return components;
    const query = searchQuery.toLowerCase();
    return components.filter(
      (comp) =>
        comp.name.toLowerCase().includes(query) ||
        comp.category.toLowerCase().includes(query) ||
        comp.tags.some((tag) => tag.toLowerCase().includes(query))
    );
  }, [components, searchQuery]);

  // Group by category
  const groupedComponents = useMemo(() => {
    const groups = new Map<string, ComponentLibraryItem[]>();
    filteredComponents.forEach((comp) => {
      if (!groups.has(comp.category)) {
        groups.set(comp.category, []);
      }
      groups.get(comp.category)!.push(comp);
    });
    return groups;
  }, [filteredComponents]);

  const handleInsertComponent = useCallback(
    (component: ComponentLibraryItem) => {
      onInsertNode?.(
        {
          type: 'component',
          data: {
            label: component.name,
            componentId: component.id,
            category: component.category,
          },
        },
        { x: 100, y: 100 }
      );
    },
    [onInsertNode]
  );

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
      <Stack direction="row" gap={1} className="mb-4">
        <Typography variant="subtitle2" fontWeight={600} className="flex-1">
          Components
        </Typography>
        <Settings className="cursor-pointer text-lg opacity-[0.6]" />
      </Stack>

      <TextField
        size="small"
        placeholder="Search components..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Search className="text-lg" />
            </InputAdornment>
          ),
        }}
        className="mb-4"
        fullWidth
      />

      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}

      <Box className="flex-1 overflow-auto">
        {groupedComponents.size === 0 ? (
          <Typography variant="body2" color="text.secondary" className="mt-4">
            No components yet
          </Typography>
        ) : (
          Array.from(groupedComponents.entries()).map(([category, items]) => (
            <Box key={category} className="mb-4">
              <Typography
                variant="caption"
                fontWeight={700}
                className="uppercase ml-2 opacity-[0.7]"
              >
                {category}
              </Typography>
              <List dense>
                {items.map((component) => (
                  <ListItem
                    key={component.id}
                    disablePadding
                    secondaryAction={
                      <Button
                        size="small"
                        onClick={() => handleInsertComponent(component)}
                      >
                        <Add className="text-base" />
                      </Button>
                    }
                  >
                    <ListItemButton
                      onClick={() => handleInsertComponent(component)}
                    >
                      <ListItemText
                        primary={component.name}
                        secondary={component.description}
                        primaryTypographyProps={{ variant: 'body2' }}
                        secondaryTypographyProps={{ variant: 'caption' }}
                      />
                    </ListItemButton>
                  </ListItem>
                ))}
              </List>
            </Box>
          ))
        )}
      </Box>
    </Box>
  );
}
