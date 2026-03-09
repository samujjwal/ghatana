/**
 * Dashboard Manager Component
 * 
 * Manages multiple dashboard layouts with:
 * - Layout switching
 * - Layout persistence
 * - Widget management
 * - Import/export functionality
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  Box,
  Button,
  IconButton,
  Tabs,
  Tab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  useTheme,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as _EditIcon,
  Delete as DeleteIcon,
  Download as DownloadIcon,
  Upload as UploadIcon,
} from '@mui/icons-material';
import { DashboardLayout } from './DashboardLayout';
import type { DashboardLayout as DashboardLayoutType } from '../types';

export interface DashboardManagerProps {
  layouts: DashboardLayoutType[];
  activeLayoutId?: string;
  onLayoutChange?: (layout: DashboardLayoutType) => void;
  onLayoutAdd?: (layout: DashboardLayoutType) => void;
  onLayoutRemove?: (layoutId: string) => void;
  onLayoutSelect?: (layoutId: string) => void;
  editable?: boolean;
}

/**
 * Dashboard manager for handling multiple layouts
 */
export const DashboardManager: React.FC<DashboardManagerProps> = ({
  layouts,
  activeLayoutId,
  onLayoutChange,
  onLayoutAdd,
  onLayoutRemove,
  onLayoutSelect,
  editable = false,
}) => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [newLayoutName, setNewLayoutName] = useState('');
  const [newLayoutDescription, setNewLayoutDescription] = useState('');

  // Find active layout
  const activeLayout = layouts.find(l => l.id === activeLayoutId) || layouts[0];

  // Update active tab when layout changes
  useEffect(() => {
    const index = layouts.findIndex(l => l.id === activeLayoutId);
    if (index !== -1) {
      setActiveTab(index);
    }
  }, [activeLayoutId, layouts]);

  // Handle tab change
  const handleTabChange = useCallback(
    (_event: React.SyntheticEvent, newValue: number) => {
      setActiveTab(newValue);
      if (onLayoutSelect && layouts[newValue]) {
        onLayoutSelect(layouts[newValue].id);
      }
    },
    [layouts, onLayoutSelect]
  );

  // Handle create layout
  const handleCreateLayout = useCallback(() => {
    if (!onLayoutAdd || !newLayoutName) return;

    const newLayout: DashboardLayoutType = {
      id: `layout-${Date.now()}`,
      name: newLayoutName,
      description: newLayoutDescription,
      widgets: [],
      columns: 12,
      rowHeight: 100,
      gap: 16,
    };

    onLayoutAdd(newLayout);
    setCreateDialogOpen(false);
    setNewLayoutName('');
    setNewLayoutDescription('');
  }, [onLayoutAdd, newLayoutName, newLayoutDescription]);

  // Handle delete layout
  const handleDeleteLayout = useCallback(
    (layoutId: string) => {
      if (onLayoutRemove && layouts.length > 1) {
        onLayoutRemove(layoutId);
      }
    },
    [onLayoutRemove, layouts.length]
  );

  // Handle export layout
  const handleExportLayout = useCallback(() => {
    if (!activeLayout) return;

    const dataStr = JSON.stringify(activeLayout, null, 2);
    const dataBlob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${activeLayout.name}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }, [activeLayout]);

  // Handle import layout
  const handleImportLayout = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (!file || !onLayoutAdd) return;

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const layout = JSON.parse(e.target?.result as string) as DashboardLayoutType;
          onLayoutAdd(layout);
        } catch (error) {
          console.error('Failed to import layout:', error);
        }
      };
      reader.readAsText(file);
    },
    [onLayoutAdd]
  );

  return (
    <Box sx={{ width: '100%', height: '100%' }}>
      {/* Layout tabs */}
      <Box
        sx={{
          borderBottom: 1,
          borderColor: 'divider',
          backgroundColor: theme.palette.background.paper,
          px: 2,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Tabs value={activeTab} onChange={handleTabChange}>
            {layouts.map((layout) => (
              <Tab
                key={layout.id}
                label={layout.name}
                icon={
                  editable && layouts.length > 1 ? (
                    <IconButton
                      size="small"
                      onClick={(e: React.MouseEvent) => {
                        e.stopPropagation();
                        handleDeleteLayout(layout.id);
                      }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  ) : undefined
                }
                iconPosition="end"
              />
            ))}
          </Tabs>
          {editable && (
            <Box sx={{ display: 'flex', gap: 1, ml: 'auto' }}>
              <IconButton
                size="small"
                onClick={() => setCreateDialogOpen(true)}
                title="Create new layout"
              >
                <AddIcon />
              </IconButton>
              <IconButton
                size="small"
                onClick={handleExportLayout}
                title="Export layout"
              >
                <DownloadIcon />
              </IconButton>
              <IconButton
                size="small"
                component="label"
                title="Import layout"
              >
                <UploadIcon />
                <input
                  type="file"
                  hidden
                  accept=".json"
                  onChange={handleImportLayout}
                />
              </IconButton>
            </Box>
          )}
        </Box>
      </Box>

      {/* Active layout */}
      {activeLayout && (
        <DashboardLayout
          layout={activeLayout}
          onLayoutChange={onLayoutChange}
          editable={editable}
        />
      )}

      {/* Create layout dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create New Dashboard</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Dashboard Name"
            fullWidth
            value={newLayoutName}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNewLayoutName(e.target.value)}
          />
          <TextField
            margin="dense"
            label="Description"
            fullWidth
            multiline
            rows={3}
            value={newLayoutDescription}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNewLayoutDescription(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleCreateLayout}
            variant="contained"
            disabled={!newLayoutName}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

DashboardManager.displayName = 'DashboardManager';

export default DashboardManager;
