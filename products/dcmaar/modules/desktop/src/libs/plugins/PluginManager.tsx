/**
 * Plugin Manager Component
 * 
 * UI for managing plugins with:
 * - Plugin list view
 * - Install/uninstall
 * - Enable/disable
 * - Configuration
 * - Resource monitoring
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Switch,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Chip,
  LinearProgress,
} from '@mui/material';
import CardActions from '@mui/material/CardActions';
import {
  Settings as SettingsIcon,
  Delete as DeleteIcon,
  Info as InfoIcon,
} from '@mui/icons-material';
import { pluginLoader } from './PluginLoader';
import type { Plugin } from './types';
import { PluginState } from './types';

export interface PluginManagerProps {
  onPluginChange?: (plugins: Plugin[]) => void;
}

/**
 * Plugin manager UI component
 */
export const PluginManager: React.FC<PluginManagerProps> = ({ onPluginChange }) => {
  const [plugins, setPlugins] = useState<Plugin[]>([]);
  const [selectedPlugin, setSelectedPlugin] = useState<Plugin | null>(null);
  const [configDialogOpen, setConfigDialogOpen] = useState(false);
  const [infoDialogOpen, setInfoDialogOpen] = useState(false);

  // Load plugins
  useEffect(() => {
    const loadedPlugins = pluginLoader.getAllPlugins();
    setPlugins(loadedPlugins);
  }, []);

  // Handle plugin toggle
  const handleToggle = useCallback(
    async (plugin: Plugin) => {
      try {
        if (plugin.state === PluginState.ACTIVE) {
          await pluginLoader.deactivatePlugin(plugin.manifest.metadata.id);
        } else {
          await pluginLoader.activatePlugin(plugin.manifest.metadata.id);
        }

        const updatedPlugins = pluginLoader.getAllPlugins();
        setPlugins(updatedPlugins);
        onPluginChange?.(updatedPlugins);
      } catch (error) {
        console.error('Failed to toggle plugin:', error);
      }
    },
    [onPluginChange]
  );

  // Handle plugin uninstall
  const handleUninstall = useCallback(
    async (plugin: Plugin) => {
      try {
        await pluginLoader.unloadPlugin(plugin.manifest.metadata.id);
        const updatedPlugins = pluginLoader.getAllPlugins();
        setPlugins(updatedPlugins);
        onPluginChange?.(updatedPlugins);
      } catch (error) {
        console.error('Failed to uninstall plugin:', error);
      }
    },
    [onPluginChange]
  );

  // Handle config dialog
  const handleConfigOpen = useCallback((plugin: Plugin) => {
    setSelectedPlugin(plugin);
    setConfigDialogOpen(true);
  }, []);

  // Handle info dialog
  const handleInfoOpen = useCallback((plugin: Plugin) => {
    setSelectedPlugin(plugin);
    setInfoDialogOpen(true);
  }, []);

  // Render plugin card
  const renderPluginCard = (plugin: Plugin) => {
    const { metadata, capabilities } = plugin.manifest;
    const isActive = plugin.state === PluginState.ACTIVE;

    return (
      <Card key={metadata.id} sx={{ mb: 2 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
            <Typography variant="h6" sx={{ flex: 1 }}>
              {metadata.name}
            </Typography>
            <Chip
              label={plugin.state}
              size="small"
              color={isActive ? 'success' : 'default'}
            />
          </Box>

          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {metadata.description}
          </Typography>

          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
            <Chip label={`v${metadata.version}`} size="small" />
            <Chip label={metadata.author} size="small" />
            {metadata.keywords?.map(keyword => (
              <Chip key={keyword} label={keyword} size="small" variant="outlined" />
            ))}
          </Box>

          {/* Resource usage */}
          {isActive && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="caption" color="text.secondary">
                Memory: {capabilities.maxMemoryMB || 100} MB
              </Typography>
              <LinearProgress
                variant="determinate"
                value={50}
                sx={{ mt: 0.5, mb: 1 }}
              />
              <Typography variant="caption" color="text.secondary">
                CPU: {capabilities.maxCpuPercent || 50}%
              </Typography>
              <LinearProgress
                variant="determinate"
                value={30}
                sx={{ mt: 0.5 }}
              />
            </Box>
          )}
        </CardContent>

        <CardActions>
          <Switch
            checked={isActive}
            onChange={() => handleToggle(plugin)}
            disabled={plugin.state === PluginState.ERROR}
          />
          <Box sx={{ flex: 1 }} />
          <IconButton size="small" onClick={() => handleInfoOpen(plugin)}>
            <InfoIcon />
          </IconButton>
          <IconButton size="small" onClick={() => handleConfigOpen(plugin)}>
            <SettingsIcon />
          </IconButton>
          <IconButton
            size="small"
            onClick={() => handleUninstall(plugin)}
            color="error"
          >
            <DeleteIcon />
          </IconButton>
        </CardActions>
      </Card>
    );
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 3 }}>
        Installed Plugins
      </Typography>

      {plugins.length === 0 ? (
        <Typography color="text.secondary">
          No plugins installed. Visit the marketplace to install plugins.
        </Typography>
      ) : (
        plugins.map(renderPluginCard)
      )}

      {/* Config Dialog */}
      <Dialog
        open={configDialogOpen}
        onClose={() => setConfigDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Plugin Configuration</DialogTitle>
        <DialogContent>
          {selectedPlugin && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                {selectedPlugin.manifest.metadata.name}
              </Typography>
              <TextField
                fullWidth
                multiline
                rows={10}
                value={JSON.stringify(selectedPlugin.context.config, null, 2)}
                margin="normal"
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfigDialogOpen(false)}>Cancel</Button>
          <Button variant="contained">Save</Button>
        </DialogActions>
      </Dialog>

      {/* Info Dialog */}
      <Dialog
        open={infoDialogOpen}
        onClose={() => setInfoDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Plugin Information</DialogTitle>
        <DialogContent>
          {selectedPlugin && (
            <Box>
              <Typography variant="h6" gutterBottom>
                {selectedPlugin.manifest.metadata.name}
              </Typography>
              <Typography variant="body2" paragraph>
                {selectedPlugin.manifest.metadata.description}
              </Typography>
              <Typography variant="subtitle2" gutterBottom>
                Details
              </Typography>
              <Typography variant="body2">
                Version: {selectedPlugin.manifest.metadata.version}
              </Typography>
              <Typography variant="body2">
                Author: {selectedPlugin.manifest.metadata.author}
              </Typography>
              <Typography variant="body2">
                License: {selectedPlugin.manifest.metadata.license}
              </Typography>
              {selectedPlugin.manifest.metadata.homepage && (
                <Typography variant="body2">
                  Homepage:{' '}
                  <a
                    href={selectedPlugin.manifest.metadata.homepage}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {selectedPlugin.manifest.metadata.homepage}
                  </a>
                </Typography>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInfoDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

PluginManager.displayName = 'PluginManager';

export default PluginManager;
