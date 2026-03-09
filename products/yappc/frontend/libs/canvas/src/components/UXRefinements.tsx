import { Settings as SettingsIcon, Palette as PaletteIcon, Gauge as SpeedIcon, Accessibility as AccessibilityIcon, Brain as PsychologyIcon, Bell as NotificationsIcon, HelpCircle as HelpIcon, MessageCircle as FeedbackIcon, GraduationCap as TutorialIcon, Sparkles as AutoAwesomeIcon, Lightbulb as LightbulbIcon, CheckCircle as CheckCircleIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, Info as InfoIcon, X as CloseIcon, KeyboardArrowUp as KeyboardArrowUpIcon, ChevronDown as KeyboardArrowDownIcon, Eye as VisibilityIcon, EyeOff as VisibilityOffIcon } from 'lucide-react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Switch,
  FormControlLabel,
  Slider,
  Button,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Tooltip,
  Chip,
  Fab,
  LinearProgress,
  Menu,
  Badge,
  Tab,
  Tabs,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import {
  TextField,
  Snackbar,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
  Zoom,
  Collapse,
  Backdrop,
  MenuItem,
} from '@ghatana/ui';
import React, { useState, useCallback, useRef, useEffect } from 'react';

// UX Configuration interfaces
/**
 *
 */
export interface UXSettings {
  theme: {
    mode: 'light' | 'dark' | 'auto';
    primaryColor: string;
    secondaryColor: string;
    customColors: Record<string, string>;
  };
  accessibility: {
    highContrast: boolean;
    largeText: boolean;
    reducedMotion: boolean;
    screenReader: boolean;
    keyboardNavigation: boolean;
  };
  performance: {
    animations: boolean;
    autoSave: boolean;
    autoSaveInterval: number;
    renderOptimization: boolean;
    lazyLoading: boolean;
  };
  behavior: {
    smartSnapping: boolean;
    autoLayout: boolean;
    gestureControls: boolean;
    voiceCommands: boolean;
    aiSuggestions: boolean;
  };
  interface: {
    compactMode: boolean;
    showTooltips: boolean;
    showShortcuts: boolean;
    customToolbar: string[];
    panelLayout: 'left' | 'right' | 'bottom' | 'floating';
  };
  notifications: {
    enabled: boolean;
    sound: boolean;
    desktop: boolean;
    collaboration: boolean;
    errors: boolean;
    achievements: boolean;
  };
}

// Notification types
/**
 *
 */
export interface UXNotification {
  id: string;
  type: 'success' | 'warning' | 'error' | 'info';
  title: string;
  message: string;
  timestamp: Date;
  persistent: boolean;
  actions?: Array<{
    label: string;
    action: () => void;
    primary?: boolean;
  }>;
  context?: {
    component: string;
    feature: string;
    user: string;
  };
}

// Tutorial step interface
/**
 *
 */
export interface TutorialStep {
  id: string;
  title: string;
  content: string;
  target: string;
  position: 'top' | 'bottom' | 'left' | 'right';
  optional: boolean;
  actions?: Array<{
    label: string;
    action: () => void;
  }>;
}

// Smart suggestion interface
/**
 *
 */
export interface SmartSuggestion {
  id: string;
  type: 'layout' | 'connection' | 'optimization' | 'feature' | 'shortcut';
  title: string;
  description: string;
  confidence: number;
  benefit: string;
  action: () => void;
  dismissible: boolean;
  category: string;
}

// Default UX settings
const defaultUXSettings: UXSettings = {
  theme: {
    mode: 'auto',
    primaryColor: '#1976d2',
    secondaryColor: '#dc004e',
    customColors: {}
  },
  accessibility: {
    highContrast: false,
    largeText: false,
    reducedMotion: false,
    screenReader: false,
    keyboardNavigation: true
  },
  performance: {
    animations: true,
    autoSave: true,
    autoSaveInterval: 30,
    renderOptimization: true,
    lazyLoading: true
  },
  behavior: {
    smartSnapping: true,
    autoLayout: false,
    gestureControls: true,
    voiceCommands: false,
    aiSuggestions: true
  },
  interface: {
    compactMode: false,
    showTooltips: true,
    showShortcuts: true,
    customToolbar: ['select', 'pan', 'node', 'edge', 'delete'],
    panelLayout: 'right'
  },
  notifications: {
    enabled: true,
    sound: false,
    desktop: true,
    collaboration: true,
    errors: true,
    achievements: true
  }
};

// UX Hook
export const useUXRefinements = () => {
  const theme = useTheme();
  const [settings, setSettings] = useState<UXSettings>(defaultUXSettings);
  const [notifications, setNotifications] = useState<UXNotification[]>([]);
  const [currentTutorial, setCurrentTutorial] = useState<string | null>(null);
  const [tutorialStep, setTutorialStep] = useState<number>(0);
  const [suggestions, setSuggestions] = useState<SmartSuggestion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  
  // Update settings
  const updateSettings = useCallback((path: string, value: unknown) => {
    setSettings(prev => {
      const keys = path.split('.');
      const updated = { ...prev };
      let current: Record<string, unknown> = updated as Record<string, unknown>;
      
      for (let i = 0; i < keys.length - 1; i++) {
        current[keys[i]] = { ...(current[keys[i]] as Record<string, unknown>) };
        current = current[keys[i]] as Record<string, unknown>;
      }
      
      current[keys[keys.length - 1]] = value;
      return updated;
    });
  }, []);

  // Show notification
  const showNotification = useCallback((notification: Omit<UXNotification, 'id' | 'timestamp'>) => {
    const newNotification: UXNotification = {
      ...notification,
      id: `notification-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: new Date()
    };

    setNotifications(prev => [...prev, newNotification]);

    // Auto-dismiss non-persistent notifications
    if (!notification.persistent) {
      setTimeout(() => {
        dismissNotification(newNotification.id);
      }, 5000);
    }

    // Play sound if enabled
    if (settings.notifications.sound) {
      // Would play notification sound
    }

    // Show desktop notification if enabled
    if (settings.notifications.desktop && 'Notification' in window) {
      if (Notification.permission === 'granted') {
        new Notification(notification.title, {
          body: notification.message,
          icon: '/icon-192x192.png'
        });
      }
    }
  }, [settings.notifications.sound, settings.notifications.desktop]);

  // Dismiss notification
  const dismissNotification = useCallback((id: string) => {
    setNotifications(prev => prev.filter(n => n.id !== id));
  }, []);

  // Start tutorial
  const startTutorial = useCallback((tutorialId: string) => {
    setCurrentTutorial(tutorialId);
    setTutorialStep(0);
  }, []);

  // Next tutorial step
  const nextTutorialStep = useCallback(() => {
    setTutorialStep(prev => prev + 1);
  }, []);

  // Complete tutorial
  const completeTutorial = useCallback(() => {
    setCurrentTutorial(null);
    setTutorialStep(0);
    
    showNotification({
      type: 'success',
      title: 'Tutorial Completed!',
      message: 'Great job! You\'ve completed the tutorial.',
      persistent: false
    });
  }, [showNotification]);

  // Add smart suggestion
  const addSuggestion = useCallback((suggestion: SmartSuggestion) => {
    setSuggestions(prev => {
      // Avoid duplicates
      if (prev.some(s => s.id === suggestion.id)) {
        return prev;
      }
      return [...prev, suggestion].slice(-10); // Keep only last 10 suggestions
    });
  }, []);

  // Dismiss suggestion
  const dismissSuggestion = useCallback((id: string) => {
    setSuggestions(prev => prev.filter(s => s.id !== id));
  }, []);

  // Apply suggestion
  const applySuggestion = useCallback((id: string) => {
    const suggestion = suggestions.find(s => s.id === id);
    if (suggestion) {
      suggestion.action();
      dismissSuggestion(id);
      
      showNotification({
        type: 'success',
        title: 'Suggestion Applied',
        message: `Applied: ${suggestion.title}`,
        persistent: false
      });
    }
  }, [suggestions, dismissSuggestion, showNotification]);

  // Load settings from storage
  useEffect(() => {
    const savedSettings = localStorage.getItem('canvas-ux-settings');
    if (savedSettings) {
      try {
        const parsed = JSON.parse(savedSettings);
        setSettings(prev => ({ ...prev, ...parsed }));
      } catch (error) {
        console.error('Failed to load UX settings:', error);
      }
    }
  }, []);

  // Save settings to storage
  useEffect(() => {
    localStorage.setItem('canvas-ux-settings', JSON.stringify(settings));
  }, [settings]);

  // Request notification permission
  useEffect(() => {
    if (settings.notifications.desktop && 'Notification' in window) {
      if (Notification.permission === 'default') {
        Notification.requestPermission();
      }
    }
  }, [settings.notifications.desktop]);

  return {
    settings,
    updateSettings,
    notifications,
    showNotification,
    dismissNotification,
    currentTutorial,
    tutorialStep,
    startTutorial,
    nextTutorialStep,
    completeTutorial,
    suggestions,
    addSuggestion,
    dismissSuggestion,
    applySuggestion,
    isLoading,
    setIsLoading
  };
};

// UX Settings Panel Component
export const UXSettingsPanel: React.FC<{
  open: boolean;
  onClose: () => void;
  settings: UXSettings;
  updateSettings: (path: string, value: unknown) => void;
}> = ({ open, onClose, settings, updateSettings }) => {
  const [activeTab, setActiveTab] = useState(0);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  return (
    <Dialog open={open} onClose={onClose} size="md" fullWidth>
      <DialogTitle>
        UX Settings
        <IconButton
          aria-label="close"
          onClick={onClose}
          className="absolute right-[8px] top-[8px]"
        >
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box className="border-gray-200 dark:border-gray-700 border-b" >
          <Tabs value={activeTab} onChange={handleTabChange}>
            <Tab label="Theme" />
            <Tab label="Accessibility" />
            <Tab label="Performance" />
            <Tab label="Behavior" />
            <Tab label="Interface" />
            <Tab label="Notifications" />
          </Tabs>
        </Box>

        {/* Theme Tab */}
        {activeTab === 0 && (
          <Box className="p-6">
            <Typography as="h6" gutterBottom>Theme Settings</Typography>
            
            <FormControlLabel
              control={
                <Switch
                  checked={settings.theme.mode === 'dark'}
                  onChange={(e) => updateSettings('theme.mode', e.target.checked ? 'dark' : 'light')}
                />
              }
              label="Dark Mode"
            />

            <Box className="mt-4">
              <Typography as="p" className="text-sm" gutterBottom>Primary Color</Typography>
              <TextField
                type="color"
                value={settings.theme.primaryColor}
                onChange={(e) => updateSettings('theme.primaryColor', e.target.value)}
                size="sm"
              />
            </Box>

            <Box className="mt-4">
              <Typography as="p" className="text-sm" gutterBottom>Secondary Color</Typography>
              <TextField
                type="color"
                value={settings.theme.secondaryColor}
                onChange={(e) => updateSettings('theme.secondaryColor', e.target.value)}
                size="sm"
              />
            </Box>
          </Box>
        )}

        {/* Accessibility Tab */}
        {activeTab === 1 && (
          <Box className="p-6">
            <Typography as="h6" gutterBottom>Accessibility Settings</Typography>
            
            <FormControlLabel
              control={
                <Switch
                  checked={settings.accessibility.highContrast}
                  onChange={(e) => updateSettings('accessibility.highContrast', e.target.checked)}
                />
              }
              label="High Contrast"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.accessibility.largeText}
                  onChange={(e) => updateSettings('accessibility.largeText', e.target.checked)}
                />
              }
              label="Large Text"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.accessibility.reducedMotion}
                  onChange={(e) => updateSettings('accessibility.reducedMotion', e.target.checked)}
                />
              }
              label="Reduced Motion"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.accessibility.keyboardNavigation}
                  onChange={(e) => updateSettings('accessibility.keyboardNavigation', e.target.checked)}
                />
              }
              label="Keyboard Navigation"
            />
          </Box>
        )}

        {/* Performance Tab */}
        {activeTab === 2 && (
          <Box className="p-6">
            <Typography as="h6" gutterBottom>Performance Settings</Typography>
            
            <FormControlLabel
              control={
                <Switch
                  checked={settings.performance.animations}
                  onChange={(e) => updateSettings('performance.animations', e.target.checked)}
                />
              }
              label="Enable Animations"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.performance.autoSave}
                  onChange={(e) => updateSettings('performance.autoSave', e.target.checked)}
                />
              }
              label="Auto Save"
            />

            {settings.performance.autoSave && (
              <Box className="mt-4">
                <Typography as="p" className="text-sm" gutterBottom>
                  Auto Save Interval: {settings.performance.autoSaveInterval}s
                </Typography>
                <Slider
                  value={settings.performance.autoSaveInterval}
                  onChange={(e, value) => updateSettings('performance.autoSaveInterval', value)}
                  min={10}
                  max={300}
                  step={10}
                  marks
                  valueLabelDisplay="auto"
                />
              </Box>
            )}

            <FormControlLabel
              control={
                <Switch
                  checked={settings.performance.renderOptimization}
                  onChange={(e) => updateSettings('performance.renderOptimization', e.target.checked)}
                />
              }
              label="Render Optimization"
            />
          </Box>
        )}

        {/* Behavior Tab */}
        {activeTab === 3 && (
          <Box className="p-6">
            <Typography as="h6" gutterBottom>Behavior Settings</Typography>
            
            <FormControlLabel
              control={
                <Switch
                  checked={settings.behavior.smartSnapping}
                  onChange={(e) => updateSettings('behavior.smartSnapping', e.target.checked)}
                />
              }
              label="Smart Snapping"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.behavior.autoLayout}
                  onChange={(e) => updateSettings('behavior.autoLayout', e.target.checked)}
                />
              }
              label="Auto Layout"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.behavior.aiSuggestions}
                  onChange={(e) => updateSettings('behavior.aiSuggestions', e.target.checked)}
                />
              }
              label="AI Suggestions"
            />
          </Box>
        )}

        {/* Interface Tab */}
        {activeTab === 4 && (
          <Box className="p-6">
            <Typography as="h6" gutterBottom>Interface Settings</Typography>
            
            <FormControlLabel
              control={
                <Switch
                  checked={settings.interface.compactMode}
                  onChange={(e) => updateSettings('interface.compactMode', e.target.checked)}
                />
              }
              label="Compact Mode"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.interface.showTooltips}
                  onChange={(e) => updateSettings('interface.showTooltips', e.target.checked)}
                />
              }
              label="Show Tooltips"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.interface.showShortcuts}
                  onChange={(e) => updateSettings('interface.showShortcuts', e.target.checked)}
                />
              }
              label="Show Keyboard Shortcuts"
            />
          </Box>
        )}

        {/* Notifications Tab */}
        {activeTab === 5 && (
          <Box className="p-6">
            <Typography as="h6" gutterBottom>Notification Settings</Typography>
            
            <FormControlLabel
              control={
                <Switch
                  checked={settings.notifications.enabled}
                  onChange={(e) => updateSettings('notifications.enabled', e.target.checked)}
                />
              }
              label="Enable Notifications"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.notifications.sound}
                  onChange={(e) => updateSettings('notifications.sound', e.target.checked)}
                />
              }
              label="Sound Notifications"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.notifications.desktop}
                  onChange={(e) => updateSettings('notifications.desktop', e.target.checked)}
                />
              }
              label="Desktop Notifications"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={settings.notifications.collaboration}
                  onChange={(e) => updateSettings('notifications.collaboration', e.target.checked)}
                />
              }
              label="Collaboration Notifications"
            />
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );
};

// Smart Suggestions Panel
export const SmartSuggestionsPanel: React.FC<{
  suggestions: SmartSuggestion[];
  onApply: (id: string) => void;
  onDismiss: (id: string) => void;
}> = ({ suggestions, onApply, onDismiss }) => {
  const [expanded, setExpanded] = useState(true);

  if (suggestions.length === 0) return null;

  return (
    <Card className="fixed top-[80px] right-[16px] w-[320px] z-[1000]">
      <CardContent>
        <Box className="flex items-center justify-between">
          <Box className="flex items-center gap-2">
            <LightbulbIcon tone="primary" />
            <Typography as="h6">Smart Suggestions</Typography>
            <Badge badgeContent={suggestions.length} tone="primary" />
          </Box>
          <IconButton onClick={() => setExpanded(!expanded)} size="sm">
            {expanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </Box>

        <Collapse in={expanded}>
          <Box className="mt-4">
            {suggestions.slice(0, 3).map((suggestion) => (
              <Box key={suggestion.id} className="mb-4 p-2 rounded border border-gray-200 dark:border-gray-700">
                <Box className="flex items-center gap-2 mb-2">
                  <Chip
                    label={suggestion.type}
                    size="sm"
                    tone="primary"
                    variant="outlined"
                  />
                  <Typography as="p" className="text-sm" color="text.secondary">
                    {Math.round(suggestion.confidence * 100)}% confidence
                  </Typography>
                </Box>
                
                <Typography as="p" className="text-sm font-medium" gutterBottom>
                  {suggestion.title}
                </Typography>
                
                <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                  {suggestion.description}
                </Typography>
                
                <Typography as="span" className="text-xs text-gray-500" color="success.main" gutterBottom>
                  Benefit: {suggestion.benefit}
                </Typography>
                
                <Box className="flex gap-2 mt-2">
                  <Button
                    size="sm"
                    variant="solid"
                    onClick={() => onApply(suggestion.id)}
                  >
                    Apply
                  </Button>
                  {suggestion.dismissible && (
                    <Button
                      size="sm"
                      variant="outlined"
                      onClick={() => onDismiss(suggestion.id)}
                    >
                      Dismiss
                    </Button>
                  )}
                </Box>
              </Box>
            ))}
            
            {suggestions.length > 3 && (
              <Typography as="p" className="text-sm" color="text.secondary" align="center">
                +{suggestions.length - 3} more suggestions
              </Typography>
            )}
          </Box>
        </Collapse>
      </CardContent>
    </Card>
  );
};

// UX Notification System
export const UXNotificationSystem: React.FC<{
  notifications: UXNotification[];
  onDismiss: (id: string) => void;
}> = ({ notifications, onDismiss }) => {
  return (
    <>
      {notifications.map((notification) => (
        <Snackbar
          key={notification.id}
          open={true}
          autoHideDuration={notification.persistent ? null : 6000}
          onClose={() => onDismiss(notification.id)}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        >
          <Alert
            onClose={() => onDismiss(notification.id)}
            severity={notification.type}
            variant="filled"
            action={
              notification.actions && (
                <Box>
                  {notification.actions.map((action, index) => (
                    <Button
                      key={index}
                      tone="neutral"
                      size="sm"
                      onClick={action.action}
                      variant={action.primary ? 'contained' : 'text'}
                    >
                      {action.label}
                    </Button>
                  ))}
                </Box>
              )
            }
          >
            <Typography as="p" className="text-sm font-medium">{notification.title}</Typography>
            <Typography as="p" className="text-sm">{notification.message}</Typography>
          </Alert>
        </Snackbar>
      ))}
    </>
  );
};

// Loading overlay
export const UXLoadingOverlay: React.FC<{
  open: boolean;
  message?: string;
}> = ({ open, message = 'Loading...' }) => {
  return (
    <Backdrop
      className="text-[#fff]" style={{ zIndex: (theme) => theme.zIndex.drawer + 1 }} open={open}
    >
      <Box className="flex flex-col items-center gap-4">
        <CircularProgress tone="neutral" />
        <Typography as="p">{message}</Typography>
      </Box>
    </Backdrop>
  );
};

// Quick actions floating button
export const UXQuickActions: React.FC<{
  onOpenSettings: () => void;
  onStartTutorial: () => void;
  onToggleHelp: () => void;
}> = ({ onOpenSettings, onStartTutorial, onToggleHelp }) => {
  return (
    <SpeedDial
      ariaLabel="Quick Actions"
      className="fixed bottom-[16px] right-[16px]"
      icon={<SpeedDialIcon />}
    >
      <SpeedDialAction
        icon={<SettingsIcon />}
        tooltipTitle="Settings"
        onClick={onOpenSettings}
      />
      <SpeedDialAction
        icon={<TutorialIcon />}
        tooltipTitle="Tutorial"
        onClick={onStartTutorial}
      />
      <SpeedDialAction
        icon={<HelpIcon />}
        tooltipTitle="Help"
        onClick={onToggleHelp}
      />
    </SpeedDial>
  );
};