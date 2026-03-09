/**
 * Plugin Marketplace Component
 * 
 * UI for discovering and installing plugins with:
 * - Plugin search and filtering
 * - Plugin details and ratings
 * - Installation management
 * - Update notifications
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  TextField,
  Chip,
  Tab,
  Tabs,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import CardActions from '@mui/material/CardActions';
import InputAdornment from '@mui/material/InputAdornment';
import Rating from '@mui/material/Rating';
import {
  Search as SearchIcon,
  Download as DownloadIcon,
} from '@mui/icons-material';
import { pluginLoader } from './PluginLoader';
import type { PluginManifest } from './types';

/**
 * Marketplace plugin entry
 */
interface MarketplacePlugin {
  manifest: PluginManifest;
  downloadUrl: string;
  rating: number;
  downloads: number;
  lastUpdated: string;
}

export interface PluginMarketplaceProps {
  onInstall?: (plugin: MarketplacePlugin) => void;
}

/**
 * Plugin marketplace UI component
 */
export const PluginMarketplace: React.FC<PluginMarketplaceProps> = ({ onInstall }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [category, setCategory] = useState('all');
  const [plugins, setPlugins] = useState<MarketplacePlugin[]>([]);

  // Load marketplace plugins
  useEffect(() => {
    loadMarketplacePlugins();
  }, []);

  // Load plugins from marketplace
  const loadMarketplacePlugins = async () => {
    try {
      // TODO: Implement actual marketplace API call
      // For now, use mock data
      const mockPlugins: MarketplacePlugin[] = [
        {
          manifest: {
            metadata: {
              id: 'custom-metrics',
              name: 'Custom Metrics',
              version: '1.0.0',
              author: 'DCMaar Team',
              description: 'Create custom metrics and visualizations',
              license: 'MIT',
              keywords: ['metrics', 'visualization'],
            },
            capabilities: {
              canProcessMetrics: true,
              canProvideWidgets: true,
            },
            main: 'plugin.wasm',
          },
          downloadUrl: 'https://marketplace.dcmaar.io/plugins/custom-metrics/1.0.0/plugin.wasm',
          rating: 4.5,
          downloads: 1250,
          lastUpdated: '2025-01-10',
        },
        {
          manifest: {
            metadata: {
              id: 'slack-integration',
              name: 'Slack Integration',
              version: '1.2.0',
              author: 'Community',
              description: 'Send notifications to Slack channels',
              license: 'Apache-2.0',
              keywords: ['integration', 'notifications', 'slack'],
            },
            capabilities: {
              canConnectToServices: true,
              canProcessEvents: true,
            },
            main: 'plugin.wasm',
          },
          downloadUrl: 'https://marketplace.dcmaar.io/plugins/slack-integration/1.2.0/plugin.wasm',
          rating: 4.8,
          downloads: 3420,
          lastUpdated: '2025-01-12',
        },
      ];

      setPlugins(mockPlugins);
    } catch (error) {
      console.error('Failed to load marketplace plugins:', error);
    }
  };

  // Filter plugins
  const filteredPlugins = plugins.filter(plugin => {
    const matchesSearch =
      searchQuery === '' ||
      plugin.manifest.metadata.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      plugin.manifest.metadata.description.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesCategory =
      category === 'all' ||
      plugin.manifest.metadata.keywords?.includes(category);

    return matchesSearch && matchesCategory;
  });

  // Handle install
  const handleInstall = useCallback(
    async (plugin: MarketplacePlugin) => {
      try {
        const manifestUrl = `${plugin.downloadUrl.replace('/plugin.wasm', '')}/manifest.json`;
        await pluginLoader.loadPlugin(manifestUrl, plugin.downloadUrl);
        onInstall?.(plugin);
      } catch (error) {
        console.error('Failed to install plugin:', error);
      }
    },
    [onInstall]
  );

  // Render plugin card
  const renderPluginCard = (plugin: MarketplacePlugin) => {
    const { metadata } = plugin.manifest;

    return (
      <Grid size={{ xs: 12, sm: 6, md: 4 }} key={metadata.id}>
        <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <CardContent sx={{ flex: 1 }}>
            <Typography variant="h6" gutterBottom>
              {metadata.name}
            </Typography>

            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
              <Rating value={plugin.rating} precision={0.5} size="small" readOnly />
              <Typography variant="caption" sx={{ ml: 1 }}>
                ({plugin.downloads.toLocaleString()} downloads)
              </Typography>
            </Box>

            <Typography variant="body2" color="text.secondary" paragraph>
              {metadata.description}
            </Typography>

            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 1 }}>
              {metadata.keywords?.map(keyword => (
                <Chip key={keyword} label={keyword} size="small" />
              ))}
            </Box>

            <Typography variant="caption" color="text.secondary">
              By {metadata.author} • v{metadata.version}
            </Typography>
          </CardContent>

          <CardActions>
            <Button
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => handleInstall(plugin)}
            >
              Install
            </Button>
            <Button size="small">Details</Button>
          </CardActions>
        </Card>
      </Grid>
    );
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 3 }}>
        Plugin Marketplace
      </Typography>

      {/* Search and filters */}
      <Box sx={{ mb: 3 }}>
        <TextField
          fullWidth
          placeholder="Search plugins..."
          value={searchQuery}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
          sx={{ mb: 2 }}
        />

        <Tabs
          value={category}
          onChange={(_: React.SyntheticEvent, value: string) => setCategory(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="All" value="all" />
          <Tab label="Metrics" value="metrics" />
          <Tab label="Visualization" value="visualization" />
          <Tab label="Integration" value="integration" />
          <Tab label="Notifications" value="notifications" />
        </Tabs>
      </Box>

      {/* Plugin grid */}
      <Grid container spacing={2}>
        {filteredPlugins.map(renderPluginCard)}
      </Grid>

      {filteredPlugins.length === 0 && (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: 200,
          }}
        >
          <Typography color="text.secondary">
            No plugins found matching your search.
          </Typography>
        </Box>
      )}
    </Box>
  );
};

PluginMarketplace.displayName = 'PluginMarketplace';

export default PluginMarketplace;
