/**
 * Command Palette
 * 
 * Power-user command interface with comprehensive action coverage.
 * Provides quick access to navigation, canvas modes, panels, and actions.
 * 
 * Keyboard: Cmd+K (Mac) / Ctrl+K (Windows)
 * 
 * @doc.type component
 * @doc.purpose Global command interface
 * @doc.layer product
 * @doc.pattern Command Pattern
 */

import { Search as SearchIcon, Play as RunIcon, Home, Folder, Settings, Lightbulb as EmojiObjects, GitBranch as AccountTree, Paintbrush as Brush, Code, Bug as BugReport, Rocket as RocketLaunch, Eye as Visibility, Globe as Public, LayoutGrid as Apps, File as InsertDriveFile, Braces as DataObject, PanelLeft as ViewSidebar, Sparkles as AutoAwesome, CheckCircle, Download, Save, History, ArrowRight as ArrowForward, ArrowLeft as ArrowBack, Maximize2 as FitScreen, ZoomIn, ZoomOut, HelpCircle as Help, Keyboard } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  Box,
  Typography,
} from '@ghatana/ui';
import { Command } from 'cmdk';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { RADIUS, TRANSITIONS } from '../../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

/**
 * Command action definition
 */
export interface CommandAction {
  id: string;
  label: string;
  description?: string;
  category?: string;
  keywords?: string[];
  shortcut?: string;
  icon?: React.ReactNode;
  disabled?: boolean;
  action: () => void | Promise<void>;
}

/**
 * Command palette props
 */
interface CommandPaletteProps {
  /** Custom actions to include */
  actions: CommandAction[];
  /** Whether the palette is open */
  open: boolean;
  /** Callback when palette closes */
  onClose: () => void;
  /** Navigation handlers */
  onNavigate?: (path: string) => void;
  /** Canvas mode handlers */
  onModeChange?: (mode: string) => void;
  /** Abstraction level handlers */
  onLevelChange?: (level: string) => void;
  /** Panel toggles */
  onTogglePanel?: (panel: string) => void;
  /** Phase transition handler */
  onPhaseTransition?: (direction: 'next' | 'prev') => void;
  /** Validation handler */
  onValidate?: () => void;
  /** Code generation handler */
  onGenerate?: () => void;
  /** Export handler */
  onExport?: () => void;
  /** Save handler */
  onSave?: () => void;
  /** Fit view handler */
  onFitView?: () => void;
  /** Zoom handlers */
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  /** Show help handler */
  onShowHelp?: () => void;
  /** Show keyboard shortcuts handler */
  onShowShortcuts?: () => void;
}

// ============================================================================
// Built-in Commands
// ============================================================================

const createBuiltInCommands = ({
  onNavigate,
  onModeChange,
  onLevelChange,
  onTogglePanel,
  onPhaseTransition,
  onValidate,
  onGenerate,
  onExport,
  onSave,
  onFitView,
  onZoomIn,
  onZoomOut,
  onShowHelp,
  onShowShortcuts,
}: Partial<CommandPaletteProps>): CommandAction[] => {
  const commands: CommandAction[] = [];

  // Navigation Commands
  if (onNavigate) {
    commands.push(
      {
        id: 'go-home',
        label: 'Go to Home',
        description: 'Return to the main dashboard',
        category: 'Navigation',
        keywords: ['home', 'dashboard', 'main'],
        shortcut: 'G H',
        icon: <Home className="w-4 h-4" />,
        action: () => onNavigate('/app'),
      },
      {
        id: 'go-projects',
        label: 'Go to Projects',
        description: 'View all projects',
        category: 'Navigation',
        keywords: ['projects', 'list', 'all'],
        shortcut: 'G P',
        icon: <Folder className="w-4 h-4" />,
        action: () => onNavigate('/app/projects'),
      },
      {
        id: 'go-settings',
        label: 'Go to Settings',
        description: 'Open settings page',
        category: 'Navigation',
        keywords: ['settings', 'preferences', 'config'],
        shortcut: 'G S',
        icon: <Settings className="w-4 h-4" />,
        action: () => onNavigate('/settings'),
      },
    );
  }

  // Canvas Mode Commands
  if (onModeChange) {
    const modes = [
      { id: 'brainstorm', label: 'Brainstorm', icon: EmojiObjects, key: '1' },
      { id: 'diagram', label: 'Diagram', icon: AccountTree, key: '2' },
      { id: 'design', label: 'Design', icon: Brush, key: '3' },
      { id: 'code', label: 'Code', icon: Code, key: '4' },
      { id: 'test', label: 'Test', icon: BugReport, key: '5' },
      { id: 'deploy', label: 'Deploy', icon: RocketLaunch, key: '6' },
      { id: 'observe', label: 'Observe', icon: Visibility, key: '7' },
    ];

    modes.forEach(mode => {
      const Icon = mode.icon;
      commands.push({
        id: `mode-${mode.id}`,
        label: `Switch to ${mode.label} Mode`,
        description: `Change canvas to ${mode.label.toLowerCase()} mode`,
        category: 'Canvas Mode',
        keywords: ['mode', mode.id, mode.label.toLowerCase()],
        shortcut: mode.key,
        icon: <Icon className="w-4 h-4" />,
        action: () => onModeChange(mode.id),
      });
    });
  }

  // Abstraction Level Commands
  if (onLevelChange) {
    const levels = [
      { id: 'system', label: 'System', icon: Public, desc: 'High-level view' },
      { id: 'component', label: 'Component', icon: Apps, desc: 'Component view' },
      { id: 'file', label: 'File', icon: InsertDriveFile, desc: 'File-level view' },
      { id: 'code', label: 'Code', icon: DataObject, desc: 'Code-level view' },
    ];

    levels.forEach(level => {
      const Icon = level.icon;
      commands.push({
        id: `level-${level.id}`,
        label: `Zoom to ${level.label} Level`,
        description: level.desc,
        category: 'Abstraction',
        keywords: ['level', 'zoom', 'abstraction', level.id],
        icon: <Icon className="w-4 h-4" />,
        action: () => onLevelChange(level.id),
      });
    });
  }

  // Panel Toggle Commands
  if (onTogglePanel) {
    const panels = [
      { id: 'tasks', label: 'Task Panel', shortcut: 'Cmd+B', icon: ViewSidebar },
      { id: 'ai', label: 'AI Assistant', shortcut: 'Cmd+I', icon: AutoAwesome },
      { id: 'validation', label: 'Validation Panel', icon: CheckCircle },
      { id: 'generation', label: 'Code Generation', icon: Code },
      { id: 'history', label: 'Version History', icon: History },
      { id: 'unified', label: 'Unified Panel', shortcut: 'Cmd+.', icon: AutoAwesome },
    ];

    panels.forEach(panel => {
      const Icon = panel.icon;
      commands.push({
        id: `toggle-${panel.id}`,
        label: `Toggle ${panel.label}`,
        category: 'View',
        keywords: ['toggle', 'panel', panel.id],
        shortcut: panel.shortcut,
        icon: <Icon className="w-4 h-4" />,
        action: () => onTogglePanel(panel.id),
      });
    });
  }

  // Phase Transition Commands
  if (onPhaseTransition) {
    commands.push(
      {
        id: 'phase-next',
        label: 'Advance to Next Phase',
        description: 'Move to the next lifecycle phase',
        category: 'Phase',
        keywords: ['phase', 'next', 'advance', 'forward'],
        icon: <ArrowForward className="w-4 h-4" />,
        action: () => onPhaseTransition('next'),
      },
      {
        id: 'phase-prev',
        label: 'Go to Previous Phase',
        description: 'Return to the previous lifecycle phase',
        category: 'Phase',
        keywords: ['phase', 'previous', 'back'],
        icon: <ArrowBack className="w-4 h-4" />,
        action: () => onPhaseTransition('prev'),
      },
    );
  }

  // Action Commands
  if (onValidate) {
    commands.push({
      id: 'validate',
      label: 'Run Validation',
      description: 'Validate current canvas state',
      category: 'Action',
      keywords: ['validate', 'check', 'verify'],
      shortcut: 'Cmd+Shift+V',
      icon: <CheckCircle className="w-4 h-4" />,
      action: onValidate,
    });
  }

  if (onGenerate) {
    commands.push({
      id: 'generate',
      label: 'Generate Code',
      description: 'Generate production-ready code',
      category: 'Action',
      keywords: ['generate', 'code', 'build'],
      shortcut: 'Cmd+Shift+G',
      icon: <Code className="w-4 h-4" />,
      action: onGenerate,
    });
  }

  if (onExport) {
    commands.push({
      id: 'export-zip',
      label: 'Export as ZIP',
      description: 'Download project as ZIP file',
      category: 'Action',
      keywords: ['export', 'download', 'zip'],
      icon: <Download className="w-4 h-4" />,
      action: onExport,
    });
  }

  if (onSave) {
    commands.push({
      id: 'save',
      label: 'Save Project',
      description: 'Save current changes',
      category: 'Action',
      keywords: ['save', 'persist'],
      shortcut: 'Cmd+S',
      icon: <Save className="w-4 h-4" />,
      action: onSave,
    });
  }

  // View Commands
  if (onFitView) {
    commands.push({
      id: 'fit-view',
      label: 'Fit View',
      description: 'Fit all elements in view',
      category: 'View',
      keywords: ['fit', 'view', 'center', 'reset'],
      shortcut: 'F',
      icon: <FitScreen className="w-4 h-4" />,
      action: onFitView,
    });
  }

  if (onZoomIn) {
    commands.push({
      id: 'zoom-in',
      label: 'Zoom In',
      category: 'View',
      keywords: ['zoom', 'in', 'magnify'],
      shortcut: 'Cmd++',
      icon: <ZoomIn className="w-4 h-4" />,
      action: onZoomIn,
    });
  }

  if (onZoomOut) {
    commands.push({
      id: 'zoom-out',
      label: 'Zoom Out',
      category: 'View',
      keywords: ['zoom', 'out', 'shrink'],
      shortcut: 'Cmd+-',
      icon: <ZoomOut className="w-4 h-4" />,
      action: onZoomOut,
    });
  }

  // Help Commands
  if (onShowHelp) {
    commands.push({
      id: 'show-help',
      label: 'Show Help',
      description: 'Open help documentation',
      category: 'Help',
      keywords: ['help', 'docs', 'documentation'],
      shortcut: '?',
      icon: <Help className="w-4 h-4" />,
      action: onShowHelp,
    });
  }

  if (onShowShortcuts) {
    commands.push({
      id: 'show-shortcuts',
      label: 'Keyboard Shortcuts',
      description: 'View all keyboard shortcuts',
      category: 'Help',
      keywords: ['keyboard', 'shortcuts', 'hotkeys'],
      shortcut: 'Cmd+/',
      icon: <Keyboard className="w-4 h-4" />,
      action: onShowShortcuts,
    });
  }

  return commands;
};

// ============================================================================
// Component
// ============================================================================

export const CommandPalette: React.FC<CommandPaletteProps> = ({
  actions,
  open,
  onClose,
  onNavigate,
  onModeChange,
  onLevelChange,
  onTogglePanel,
  onPhaseTransition,
  onValidate,
  onGenerate,
  onExport,
  onSave,
  onFitView,
  onZoomIn,
  onZoomOut,
  onShowHelp,
  onShowShortcuts,
}) => {
  const [search, setSearch] = useState('');

  // Reset search when closed
  useEffect(() => {
    if (!open) {
      setSearch('');
    }
  }, [open]);

  // Build all commands
  const allCommands = useMemo(() => {
    const builtIn = createBuiltInCommands({
      onNavigate,
      onModeChange,
      onLevelChange,
      onTogglePanel,
      onPhaseTransition,
      onValidate,
      onGenerate,
      onExport,
      onSave,
      onFitView,
      onZoomIn,
      onZoomOut,
      onShowHelp,
      onShowShortcuts,
    });
    return [...builtIn, ...actions];
  }, [
    actions,
    onNavigate,
    onModeChange,
    onLevelChange,
    onTogglePanel,
    onPhaseTransition,
    onValidate,
    onGenerate,
    onExport,
    onSave,
    onFitView,
    onZoomIn,
    onZoomOut,
    onShowHelp,
    onShowShortcuts,
  ]);

  const handleSelect = useCallback(
    async (actionId: string) => {
      const action = allCommands.find((a) => a.id === actionId);
      if (action && !action.disabled) {
        await action.action();
        onClose();
      }
    },
    [allCommands, onClose],
  );

  // Group actions by category
  const groupedActions = useMemo(() => {
    return allCommands.reduce((acc, action) => {
      const category = action.category || 'Other';
      if (!acc[category]) {
        acc[category] = [];
      }
      acc[category].push(action);
      return acc;
    }, {} as Record<string, CommandAction[]>);
  }, [allCommands]);

  // Order categories
  const categoryOrder = [
    'Navigation',
    'Canvas Mode',
    'Abstraction',
    'View',
    'Phase',
    'Action',
    'Help',
    'Analysis',
    'Layout',
    'Other',
  ];

  const sortedCategories = Object.keys(groupedActions).sort((a, b) => {
    const aIndex = categoryOrder.indexOf(a);
    const bIndex = categoryOrder.indexOf(b);
    if (aIndex === -1 && bIndex === -1) return a.localeCompare(b);
    if (aIndex === -1) return 1;
    if (bIndex === -1) return -1;
    return aIndex - bIndex;
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: {
          position: 'fixed',
          top: '15%',
          m: 0,
          borderRadius: 2,
          overflow: 'hidden',
        },
      }}
    >
      <DialogContent className="p-0" data-testid="command-palette">
        <Command label="Command Palette">
          {/* Search Input */}
          <Box
            className="flex items-center px-4 py-3 border-gray-200 dark:border-gray-700 border-b" >
            <SearchIcon className="mr-3 text-gray-500 dark:text-gray-400" />
            <Command.Input
              value={search}
              onValueChange={setSearch}
              placeholder="Type a command or search..."
              style={{
                border: 'none',
                outline: 'none',
                width: '100%',
                fontSize: '1rem',
                background: 'transparent',
              }}
              data-testid="command-search"
              autoFocus
            />
            <kbd className="px-1.5 py-0.5 text-xs text-text-secondary bg-grey-100 dark:bg-grey-800 rounded">
              esc
            </kbd>
          </Box>

          {/* Command List */}
          <Command.List
            style={{
              maxHeight: '400px',
              overflowY: 'auto',
              padding: '8px',
            }}
          >
            <Command.Empty>
              <Box className="p-6 text-center text-gray-500 dark:text-gray-400">
                No commands found. Try a different search.
              </Box>
            </Command.Empty>

            {sortedCategories.map((category) => (
              <Command.Group key={category} heading={category}>
                {groupedActions[category].map((action) => (
                  <Command.Item
                    key={action.id}
                    value={`${action.label} ${action.keywords?.join(' ') || ''}`}
                    onSelect={() => handleSelect(action.id)}
                    disabled={action.disabled}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '10px 12px',
                      borderRadius: '8px',
                      cursor: action.disabled ? 'not-allowed' : 'pointer',
                      marginBottom: '2px',
                      opacity: action.disabled ? 0.5 : 1,
                    }}
                  >
                    {action.icon && (
                      <Box className="mr-3 flex text-gray-500 dark:text-gray-400">
                        {action.icon}
                      </Box>
                    )}
                    <Box className="flex-1 min-w-0">
                      <Typography variant="body2" className="font-medium">
                        {action.label}
                      </Typography>
                      {action.description && (
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          className="block mt-0.5"
                        >
                          {action.description}
                        </Typography>
                      )}
                    </Box>
                    {action.shortcut && (
                      <kbd
                        className="ml-2 px-1.5 py-0.5 text-[10px] font-mono text-text-secondary bg-grey-100 dark:bg-grey-800 rounded"
                      >
                        {action.shortcut}
                      </kbd>
                    )}
                  </Command.Item>
                ))}
              </Command.Group>
            ))}
          </Command.List>

          {/* Footer */}
          <Box
            className="px-4 py-2 flex justify-between border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-800 border-t" >
            <Typography variant="caption" color="text.secondary">
              {allCommands.length} commands available
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ↑↓ to navigate • ↵ to select • esc to close
            </Typography>
          </Box>
        </Command>
      </DialogContent>
    </Dialog>
  );
};
