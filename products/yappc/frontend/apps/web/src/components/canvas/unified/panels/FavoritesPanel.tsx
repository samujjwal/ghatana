import { useState, useEffect, useMemo } from 'react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  ListItemIcon,
  IconButton,
  Alert,
  Stack,
  Button,
  InteractiveList as List,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { ListItemButton } from '@ghatana/ui';
import { Star, Trash as DeleteOutline, Share as IosShare, Download as FileDownload } from 'lucide-react';
import type { RailPanelProps } from '../UnifiedLeftRail.types';
import type { FavoriteItem } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * Favorites Panel - User's saved assets, components, and designs
 * Quick access to frequently used items
 */
export function FavoritesPanel({ onInsertNode }: RailPanelProps) {
  const [favorites, setFavorites] = useState<FavoriteItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedType, setSelectedType] = useState<
    'all' | FavoriteItem['type']
  >('all');

  // Fetch favorites via RailDataService
  useEffect(() => {
    const fetchFavorites = async () => {
      setLoading(true);
      try {
        const data = await railService.getFavorites();
        setFavorites(data);
      } catch (err) {
        console.error('Failed to load favorites:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchFavorites();
  }, []);

  const filteredFavorites = useMemo(() => {
    if (selectedType === 'all') return favorites;
    return favorites.filter((fav) => fav.type === selectedType);
  }, [favorites, selectedType]);

  const handleAddToCanvas = (favorite: FavoriteItem) => {
    onInsertNode?.(
      {
        type: favorite.type,
        data: {
          label: favorite.name,
          favoriteId: favorite.id,
          fromFavorites: true,
        },
      },
      { x: 100, y: 100 }
    );
  };

  const handleRemoveFavorite = (id: string) => {
    setFavorites(favorites.filter((f) => f.id !== id));
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
          ⭐ Favorites
        </Typography>
      </Stack>

      <Stack direction="row" gap={1} className="mb-4 overflow-auto">
        {(['all', 'asset', 'component', 'design', 'pattern'] as const).map(
          (type) => (
            <Button
              key={type}
              size="small"
              variant={selectedType === type ? 'contained' : 'outlined'}
              onClick={() => setSelectedType(type)}
              className="capitalize" style={{ flex: '0 0 auto' }} >
              {type}
            </Button>
          )
        )}
      </Stack>

      <Box className="flex-1 overflow-auto">
        {filteredFavorites.length === 0 ? (
          <Alert severity="info">
            {selectedType === 'all'
              ? 'No favorites yet. Star items to save them.'
              : `No ${selectedType}s in favorites yet.`}
          </Alert>
        ) : (
          <List dense>
            {filteredFavorites
              .sort((a, b) => b.usageCount - a.usageCount)
              .map((favorite) => (
                <ListItem
                  key={favorite.id}
                  disablePadding
                  secondaryAction={
                    <Stack direction="row" gap={0.5}>
                      <IconButton
                        size="small"
                        title="Remove from favorites"
                        onClick={() => handleRemoveFavorite(favorite.id)}
                      >
                        <DeleteOutline className="text-base" />
                      </IconButton>
                      <IconButton size="small" title="Share">
                        <IosShare className="text-base" />
                      </IconButton>
                    </Stack>
                  }
                >
                  <ListItemButton onClick={() => handleAddToCanvas(favorite)}>
                    <ListItemIcon className="min-w-[28px]">
                      <Star className="text-amber-600 text-lg" />
                    </ListItemIcon>
                    <ListItemText
                      primary={favorite.name}
                      secondary={
                        <>
                          <Typography
                            component="span"
                            variant="caption"
                            color="text.secondary"
                          >
                            {favorite.type}
                          </Typography>
                          {favorite.usageCount > 0 && (
                            <Typography
                              component="span"
                              variant="caption"
                              color="text.disabled"
                              className="ml-2"
                            >
                              Used {favorite.usageCount}x
                            </Typography>
                          )}
                        </>
                      }
                      primaryTypographyProps={{ variant: 'body2' }}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
          </List>
        )}
      </Box>

      <Button
        fullWidth
        variant="outlined"
        size="small"
        startIcon={<FileDownload />}
        className="mt-4"
      >
        Export Favorites
      </Button>
    </Box>
  );
}
