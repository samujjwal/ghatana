import { Search as SearchIcon, X as CloseIcon, KeyboardArrowUp as ArrowUpIcon, ChevronDown as ArrowDownIcon, Play as PlayIcon, Settings as SettingsIcon, Accessibility as AccessibilityIcon, Keyboard as KeyboardIcon, Palette as PaletteIcon, Type as TextFieldsIcon, Eye as VisibilityIcon, VolumeUp as VolumeUpIcon, Gauge as SpeedIcon, Pointer as TouchAppIcon } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
  Box,
  Chip,
  InputAdornment,
  IconButton,
  Divider,
  Switch,
  FormControlLabel,
  Select,
  FormControl,
  InputLabel,
  Card,
  CardContent,
  CardHeader,
  Button,
  Tooltip,
  Badge,
  Alert,
  InteractiveList as List,
  Surface as Paper,
} from '@ghatana/ui';
import {
  TextField,
  ListItemSecondaryAction,
  Fade,
  MenuItem,
} from '@ghatana/ui';
import React, { useState, useCallback, useRef, useEffect } from 'react';

import {
  useCommandPalette,
  useAccessibility,
  useKeyboardShortcuts
} from './hooks';

import type {
  Command,
  CommandCategory,
  AccessibilityConfig,
  KeyboardShortcut} from './hooks';

// Command Palette Component
/**
 *
 */
export interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  commands?: Command[];
  categories?: CommandCategory[];
  placeholder?: string;
  maxHeight?: number;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({
  open,
  onClose,
  commands = [],
  categories = [],
  placeholder = 'Search commands...',
  maxHeight = 400,
}) => {
  const {
    searchQuery,
    selectedIndex,
    filteredCommands,
    setSearchQuery,
    selectNext,
    selectPrevious,
    executeSelected,
    registerCommand,
    registerCategory,
  } = useCommandPalette({
    commands,
    categories,
    maxResults: 50,
  });

  const searchInputRef = useRef<HTMLInputElement>(null);

  // Register commands and categories
  useEffect(() => {
    commands.forEach(registerCommand);
    categories.forEach(registerCategory);
  }, [commands, categories, registerCommand, registerCategory]);

  // Focus search input when opened
  useEffect(() => {
    if (open && searchInputRef.current) {
      setTimeout(() => {
        searchInputRef.current?.focus();
      }, 100);
    }
  }, [open]);

  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        selectNext();
        break;
      case 'ArrowUp':
        event.preventDefault();
        selectPrevious();
        break;
      case 'Enter':
        event.preventDefault();
        executeSelected();
        break;
      case 'Escape':
        event.preventDefault();
        onClose();
        break;
    }
  }, [selectNext, selectPrevious, executeSelected, onClose]);

  const getCategoryIcon = (categoryId: string) => {
    const category = categories.find(cat => cat.id === categoryId);
    return category?.icon || '📁';
  };

  const groupedCommands = filteredCommands.reduce((groups, command) => {
    const category = command.category;
    if (!groups[category]) {
      groups[category] = [];
    }
    groups[category].push(command);
    return groups;
  }, {} as Record<string, Command[]>);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: {
          position: 'fixed',
          top: '20%',
          m: 0,
          maxHeight: '60vh',
        },
      }}
    >
      <DialogContent className="p-0">
        <Box className="p-4 border-b border-solid border-gray-200 dark:border-gray-700">
          <TextField
            ref={searchInputRef}
            fullWidth
            variant="outlined"
            placeholder={placeholder}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
              endAdornment: searchQuery && (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onClick={() => setSearchQuery('')}
                  >
                    <CloseIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
        </Box>

        <Box className="overflow-auto">
          {Object.keys(groupedCommands).length === 0 ? (
            <Box className="p-8 text-center">
              <Typography color="text.secondary">
                {searchQuery ? 'No commands found' : 'Type to search commands'}
              </Typography>
            </Box>
          ) : (
            Object.entries(groupedCommands).map(([categoryId, categoryCommands]) => (
              <Box key={categoryId}>
                <Box className="px-4 py-2 bg-gray-50 dark:bg-gray-800 border-b border-solid border-gray-200 dark:border-gray-700">
                  <Typography variant="caption" color="text.secondary" className="flex items-center gap-2">
                    <span>{getCategoryIcon(categoryId)}</span>
                    {categoryId.toUpperCase()}
                  </Typography>
                </Box>
                <List dense>
                  {categoryCommands.map((command, index) => {
                    const globalIndex = filteredCommands.indexOf(command);
                    const isSelected = globalIndex === selectedIndex;
                    
                    return (
                      <ListItem
                        key={command.id}
                        button
                        selected={isSelected}
                        onClick={() => command.action()}
                        style={{
                          backgroundColor: isSelected ? '#e3f2fd' : 'transparent',
                          color: isSelected ? '#1565c0' : 'inherit',
                        }}
                      >
                        <ListItemIcon>
                          <span style={{ fontSize: '18px' }}>{command.icon || '⚡'}</span>
                        </ListItemIcon>
                        <ListItemText
                          primary={command.title}
                          secondary={command.description}
                        />
                        <ListItemSecondaryAction>
                          {command.shortcut && (
                            <Chip
                              label={command.shortcut}
                              size="small"
                              variant="outlined"
                              className="text-[10px]"
                            />
                          )}
                        </ListItemSecondaryAction>
                      </ListItem>
                    );
                  })}
                </List>
              </Box>
            ))
          )}
        </Box>

        <Box className="p-4 border-t border-solid border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
          <Box className="flex items-center gap-4 justify-between">
            <Box className="flex items-center gap-4">
              <Box className="flex items-center gap-1">
                <ArrowUpIcon size={16} />
                <ArrowDownIcon size={16} />
                <Typography variant="caption">Navigate</Typography>
              </Box>
              <Box className="flex items-center gap-1">
                <PlayIcon size={16} />
                <Typography variant="caption">Execute</Typography>
              </Box>
            </Box>
            <Typography variant="caption" color="text.secondary">
              {filteredCommands.length} commands
            </Typography>
          </Box>
        </Box>
      </DialogContent>
    </Dialog>
  );
};

// Accessibility Settings Panel
/**
 *
 */
export interface AccessibilityPanelProps {
  open: boolean;
  onClose: () => void;
  config?: Partial<AccessibilityConfig>;
  onConfigChange?: (config: AccessibilityConfig) => void;
}

export const AccessibilityPanel: React.FC<AccessibilityPanelProps> = ({
  open,
  onClose,
  config: initialConfig = {},
  onConfigChange,
}) => {
  const {
    config,
    updateConfig,
    resetConfig,
    announce,
    announcements,
    clearAnnouncements,
  } = useAccessibility({
    initialConfig,
  });

  const handleConfigChange = useCallback((updates: Partial<AccessibilityConfig>) => {
    updateConfig(updates);
    onConfigChange?.({ ...config, ...updates });
    announce('Accessibility settings updated');
  }, [config, updateConfig, onConfigChange, announce]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <CardHeader
        title="Accessibility Settings"
        avatar={<AccessibilityIcon />}
        action={
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        }
      />
      
      <DialogContent>
        {/* Screen Reader */}
        <Card className="mb-4">
          <CardContent>
            <Box className="flex items-center gap-4 mb-4">
              <VolumeUpIcon />
              <Typography variant="h6">Screen Reader Support</Typography>
            </Box>
            
            <FormControlLabel
              control={
                <Switch
                  checked={config.enableScreenReader}
                  onChange={(e) => handleConfigChange({ enableScreenReader: e.target.checked })}
                />
              }
              label="Enable screen reader announcements"
            />
            
            <FormControlLabel
              control={
                <Switch
                  checked={config.announceChanges}
                  onChange={(e) => handleConfigChange({ announceChanges: e.target.checked })}
                />
              }
              label="Announce content changes"
            />
          </CardContent>
        </Card>

        {/* Keyboard Navigation */}
        <Card className="mb-4">
          <CardContent>
            <Box className="flex items-center gap-4 mb-4">
              <KeyboardIcon />
              <Typography variant="h6">Keyboard Navigation</Typography>
            </Box>
            
            <FormControlLabel
              control={
                <Switch
                  checked={config.enableKeyboardNavigation}
                  onChange={(e) => handleConfigChange({ enableKeyboardNavigation: e.target.checked })}
                />
              }
              label="Enable keyboard navigation"
            />
            
            <FormControlLabel
              control={
                <Switch
                  checked={config.focusManagement}
                  onChange={(e) => handleConfigChange({ focusManagement: e.target.checked })}
                />
              }
              label="Automatic focus management"
            />
          </CardContent>
        </Card>

        {/* Visual Settings */}
        <Card className="mb-4">
          <CardContent>
            <Box className="flex items-center gap-4 mb-4">
              <VisibilityIcon />
              <Typography variant="h6">Visual Settings</Typography>
            </Box>
            
            <Box className="flex gap-4 mb-4">
              <FormControl className="min-w-[120px]">
                <InputLabel>Font Size</InputLabel>
                <Select
                  value={config.fontSize}
                  label="Font Size"
                  onChange={(e) => handleConfigChange({ fontSize: e.target.value as unknown })}
                >
                  <MenuItem value="small">Small</MenuItem>
                  <MenuItem value="medium">Medium</MenuItem>
                  <MenuItem value="large">Large</MenuItem>
                  <MenuItem value="extra-large">Extra Large</MenuItem>
                </Select>
              </FormControl>
              
              <FormControl className="min-w-[120px]">
                <InputLabel>Color Scheme</InputLabel>
                <Select
                  value={config.colorScheme}
                  label="Color Scheme"
                  onChange={(e) => handleConfigChange({ colorScheme: e.target.value as unknown })}
                >
                  <MenuItem value="light">Light</MenuItem>
                  <MenuItem value="dark">Dark</MenuItem>
                  <MenuItem value="auto">Auto</MenuItem>
                </Select>
              </FormControl>
            </Box>
            
            <FormControlLabel
              control={
                <Switch
                  checked={config.enableHighContrast}
                  onChange={(e) => handleConfigChange({ enableHighContrast: e.target.checked })}
                />
              }
              label="High contrast mode"
            />
            
            <FormControlLabel
              control={
                <Switch
                  checked={config.enableReducedMotion}
                  onChange={(e) => handleConfigChange({ enableReducedMotion: e.target.checked })}
                />
              }
              label="Reduce motion and animations"
            />
          </CardContent>
        </Card>

        {/* Announcements */}
        {announcements.length > 0 && (
          <Card className="mb-4">
            <CardContent>
              <Box className="flex items-center justify-between mb-4">
                <Typography variant="h6">Recent Announcements</Typography>
                <Button size="small" onClick={clearAnnouncements}>
                  Clear All
                </Button>
              </Box>
              
              <List dense>
                {announcements.slice(-5).map((announcement) => (
                  <ListItem key={announcement.id}>
                    <ListItemIcon>
                      <Badge
                        color={announcement.priority === 'assertive' ? 'error' : 'info'}
                        variant="dot"
                      >
                        <VolumeUpIcon />
                      </Badge>
                    </ListItemIcon>
                    <ListItemText
                      primary={announcement.message}
                      secondary={new Date(announcement.timestamp).toLocaleTimeString()}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        )}

        {/* Action Buttons */}
        <Box className="flex gap-4 justify-end mt-6">
          <Button onClick={resetConfig}>
            Reset to Defaults
          </Button>
          <Button variant="contained" onClick={onClose}>
            Apply Settings
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
};

// Keyboard Shortcuts Help Panel
/**
 *
 */
export interface KeyboardShortcutsHelpProps {
  open: boolean;
  onClose: () => void;
  shortcuts?: KeyboardShortcut[];
}

export const KeyboardShortcutsHelp: React.FC<KeyboardShortcutsHelpProps> = ({
  open,
  onClose,
  shortcuts = [],
}) => {
  const { getShortcutsByCategory, getShortcutString } = useKeyboardShortcuts();

  const categories = [...new Set(shortcuts.map(s => s.category))];
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  const filteredShortcuts = selectedCategory === 'all' 
    ? shortcuts 
    : getShortcutsByCategory(selectedCategory);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <CardHeader
        title="Keyboard Shortcuts"
        avatar={<KeyboardIcon />}
        action={
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        }
      />
      
      <DialogContent>
        <Box className="mb-6">
          <FormControl className="min-w-[200px]">
            <InputLabel>Category</InputLabel>
            <Select
              value={selectedCategory}
              label="Category"
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <MenuItem value="all">All Categories</MenuItem>
              {categories.map(category => (
                <MenuItem key={category} value={category}>
                  {category.charAt(0).toUpperCase() + category.slice(1)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>

        <Paper variant="outlined">
          <List>
            {filteredShortcuts.map((shortcut, index) => (
              <React.Fragment key={shortcut.id}>
                <ListItem>
                  <ListItemText
                    primary={shortcut.description}
                    secondary={shortcut.category}
                  />
                  <ListItemSecondaryAction>
                    <Chip
                      label={getShortcutString(shortcut.keys)}
                      variant="outlined"
                      className="font-mono"
                    />
                  </ListItemSecondaryAction>
                </ListItem>
                {index < filteredShortcuts.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List>
          
          {filteredShortcuts.length === 0 && (
            <Box className="p-8 text-center">
              <Typography color="text.secondary">
                No shortcuts available for this category
              </Typography>
            </Box>
          )}
        </Paper>

        <Alert severity="info" className="mt-4">
          <Typography variant="body2">
            Keyboard shortcuts help you work faster and more efficiently. 
            Most shortcuts work globally, while some are context-specific.
          </Typography>
        </Alert>
      </DialogContent>
    </Dialog>
  );
};

// UX Settings Container
/**
 *
 */
export interface UXSettingsProps {
  open: boolean;
  onClose: () => void;
  accessibilityConfig?: Partial<AccessibilityConfig>;
  onAccessibilityChange?: (config: AccessibilityConfig) => void;
  shortcuts?: KeyboardShortcut[];
}

export const UXSettings: React.FC<UXSettingsProps> = ({
  open,
  onClose,
  accessibilityConfig = {},
  onAccessibilityChange,
  shortcuts = [],
}) => {
  const [activeTab, setActiveTab] = useState<'accessibility' | 'shortcuts'>('accessibility');

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <CardHeader
        title="User Experience Settings"
        avatar={<SettingsIcon />}
        action={
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        }
      />
      
      <DialogContent>
        <Box className="mb-6 border-gray-200 dark:border-gray-700 border-b" >
          <Box className="flex gap-4">
            <Button
              variant={activeTab === 'accessibility' ? 'contained' : 'text'}
              startIcon={<AccessibilityIcon />}
              onClick={() => setActiveTab('accessibility')}
            >
              Accessibility
            </Button>
            <Button
              variant={activeTab === 'shortcuts' ? 'contained' : 'text'}
              startIcon={<KeyboardIcon />}
              onClick={() => setActiveTab('shortcuts')}
            >
              Keyboard Shortcuts
            </Button>
          </Box>
        </Box>

        {activeTab === 'accessibility' && (
          <AccessibilityPanel
            open={true}
            onClose={() => {}}
            config={accessibilityConfig}
            onConfigChange={onAccessibilityChange}
          />
        )}

        {activeTab === 'shortcuts' && (
          <KeyboardShortcutsHelp
            open={true}
            onClose={() => {}}
            shortcuts={shortcuts}
          />
        )}
      </DialogContent>
    </Dialog>
  );
};