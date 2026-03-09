/**
 * @ghatana/yappc-ide - Professional IDE Layout Component
 * 
 * Professional-grade IDE layout with customizable panels,
 * theme support, and responsive design.
 * 
 * @doc.type component
 * @doc.purpose Professional IDE layout shell
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { FileExplorer } from './FileExplorer';
import { TabBar } from './TabBar';
import { StatusBar } from './StatusBar';
import { CollaborationStatusBar } from './CollaborationStatusBar';
import { RealTimeCursorTracking } from './RealTimeCursorTracking';
import { EnhancedConflictResolver } from './EnhancedConflictResolver';
import { KeyboardShortcutsManager } from './KeyboardShortcutsManager';
import { CommandPalette } from './CommandPalette';
import { AdvancedSearchPanel } from './AdvancedSearchPanel';
import { InteractiveButton } from './MicroInteractions';
import { useIDEFileOperations } from '../hooks/useIDEFileOperations';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts';
import type { FileConflict } from './EnhancedConflictResolver';

/**
 * Layout panel configuration
 */
export interface LayoutPanel {
  id: string;
  title: string;
  icon: string;
  component: React.ComponentType<Record<string, unknown>>;
  props?: Record<string, unknown>;
  defaultSize: number;
  minSize: number;
  maxSize: number;
  resizable: boolean;
  collapsible: boolean;
  visible: boolean;
  position: 'left' | 'right' | 'bottom' | 'top';
}

/**
 * IDE theme configuration
 */
export interface IDETheme {
  id: string;
  name: string;
  type: 'light' | 'dark';
  colors: {
    background: string;
    surface: string;
    border: string;
    text: string;
    textSecondary: string;
    accent: string;
    success: string;
    warning: string;
    error: string;
  };
}

/**
 * Professional IDE layout props
 */
export interface ProfessionalIDELayoutProps {
  className?: string;
  theme?: IDETheme;
  enableCollaboration?: boolean;
  enableConflictResolution?: boolean;
  enableKeyboardShortcuts?: boolean;
  enableCommandPalette?: boolean;
  enableAdvancedSearch?: boolean;
  customPanels?: LayoutPanel[];
  onThemeChange?: (theme: IDETheme) => void;
  onLayoutChange?: (layout: string) => void;
}

/**
 * Default IDE themes
 */
const DEFAULT_THEMES: IDETheme[] = [
  {
    id: 'vscode-dark',
    name: 'VS Code Dark',
    type: 'dark',
    colors: {
      background: '#1e1e1e',
      surface: '#252526',
      border: '#3e3e42',
      text: '#cccccc',
      textSecondary: '#969696',
      accent: '#0078d4',
      success: '#4caf50',
      warning: '#ff9800',
      error: '#f44336',
    },
  },
  {
    id: 'vscode-light',
    name: 'VS Code Light',
    type: 'light',
    colors: {
      background: '#ffffff',
      surface: '#f3f3f3',
      border: '#e5e5e5',
      text: '#333333',
      textSecondary: '#666666',
      accent: '#0078d4',
      success: '#4caf50',
      warning: '#ff9800',
      error: '#f44336',
    },
  },
  {
    id: 'github-dark',
    name: 'GitHub Dark',
    type: 'dark',
    colors: {
      background: '#0d1117',
      surface: '#161b22',
      border: '#30363d',
      text: '#c9d1d9',
      textSecondary: '#8b949e',
      accent: '#58a6ff',
      success: '#3fb950',
      warning: '#d29922',
      error: '#f85149',
    },
  },
];

/**
 * Resizable panel component
 */
interface ResizablePanelProps {
  panel: LayoutPanel;
  onResize: (size: number) => void;
  onToggle: () => void;
  children: React.ReactNode;
}

const ResizablePanel: React.FC<ResizablePanelProps> = ({
  panel,
  onResize,
  onToggle,
  children,
}) => {
  const [isResizing, setIsResizing] = useState(false);
  const [size, setSize] = useState(panel.defaultSize);
  const panelRef = useRef<HTMLDivElement>(null);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (!panel.resizable) return;
    e.preventDefault();
    setIsResizing(true);
  }, [panel.resizable]);

  const handleMouseMove = useCallback((e: MouseEvent) => {
    if (!isResizing || !panelRef.current) return;

    const rect = panelRef.current.getBoundingClientRect();
    let newSize: number;

    if (panel.position === 'left' || panel.position === 'right') {
      newSize = panel.position === 'left'
        ? e.clientX - rect.left
        : rect.right - e.clientX;
    } else {
      newSize = panel.position === 'top'
        ? e.clientY - rect.top
        : rect.bottom - e.clientY;
    }

    newSize = Math.max(panel.minSize, Math.min(panel.maxSize, newSize));
    setSize(newSize);
    onResize(newSize);
  }, [isResizing, panel, onResize]);

  const handleMouseUp = useCallback(() => {
    setIsResizing(false);
  }, []);

  useEffect(() => {
    if (isResizing) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      return () => {
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [isResizing, handleMouseMove, handleMouseUp]);

  if (!panel.visible) return null;

  const panelStyles: React.CSSProperties = {
    ...(panel.position === 'left' && { width: size }),
    ...(panel.position === 'right' && { width: size }),
    ...(panel.position === 'top' && { height: size }),
    ...(panel.position === 'bottom' && { height: size }),
  };

  const resizeHandleStyles: React.CSSProperties = {
    ...(panel.position === 'left' && { right: 0, top: 0, bottom: 0, width: 4, cursor: 'ew-resize' }),
    ...(panel.position === 'right' && { left: 0, top: 0, bottom: 0, width: 4, cursor: 'ew-resize' }),
    ...(panel.position === 'top' && { bottom: 0, left: 0, right: 0, height: 4, cursor: 'ns-resize' }),
    ...(panel.position === 'bottom' && { top: 0, left: 0, right: 0, height: 4, cursor: 'ns-resize' }),
  };

  return (
    <div
      ref={panelRef}
      className="relative bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
      style={panelStyles}
    >
      {/* Panel header */}
      <div className="flex items-center justify-between p-2 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
        <div className="flex items-center gap-2">
          <span className="text-sm">{panel.icon}</span>
          <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
            {panel.title}
          </span>
        </div>
        {panel.collapsible && (
          <InteractiveButton
            variant="ghost"
            size="sm"
            onClick={onToggle}
          >
            ◀
          </InteractiveButton>
        )}
      </div>

      {/* Panel content */}
      <div className="flex-1 overflow-hidden">
        {children}
      </div>

      {/* Resize handle */}
      {panel.resizable && (
        <div
          className="absolute bg-gray-300 dark:bg-gray-600 hover:bg-gray-400 dark:hover:bg-gray-500 transition-colors"
          style={resizeHandleStyles}
          onMouseDown={handleMouseDown}
        />
      )}
    </div>
  );
};

/**
 * Professional IDE Layout Component
 */
export const ProfessionalIDELayout: React.FC<ProfessionalIDELayoutProps> = ({
  className = '',
  theme = DEFAULT_THEMES[0],
  enableCollaboration = true,
  enableConflictResolution = true,
  enableKeyboardShortcuts = true,
  enableCommandPalette = true,
  enableAdvancedSearch = true,
  customPanels = [],
  onThemeChange,
}) => {
  const [currentTheme, setCurrentTheme] = useState(theme);
  const [showCommandPalette, setShowCommandPalette] = useState(false);
  const [showKeyboardShortcuts, setShowKeyboardShortcuts] = useState(false);
  const [showAdvancedSearch, setShowAdvancedSearch] = useState(false);
  const [conflicts, setConflicts] = useState<FileConflict[]>([]);
  const [panels, setPanels] = useState<LayoutPanel[]>([
    {
      id: 'explorer',
      title: 'Explorer',
      icon: '📁',
      component: FileExplorer,
      defaultSize: 250,
      minSize: 150,
      maxSize: 400,
      resizable: true,
      collapsible: true,
      visible: true,
      position: 'left',
    },
    ...customPanels,
  ]);

  const { } = useIDEFileOperations();
  const { registerShortcut } = useKeyboardShortcuts();

  // Apply theme
  useEffect(() => {
    const root = document.documentElement;
    Object.entries(currentTheme.colors).forEach(([key, value]) => {
      root.style.setProperty(`--color-${key}`, value);
    });
    root.setAttribute('data-theme', currentTheme.type);
    onThemeChange?.(currentTheme);
  }, [currentTheme, onThemeChange]);

  // Register keyboard shortcuts
  useEffect(() => {
    if (!enableKeyboardShortcuts) return;

    // Command palette
    registerShortcut({
      id: 'command-palette',
      key: 'p',
      modifiers: ['ctrl'],
      action: () => setShowCommandPalette(true),
      description: 'Open command palette',
      category: 'tools',
    });

    // Advanced search
    registerShortcut({
      id: 'advanced-search',
      key: 'f',
      modifiers: ['ctrl', 'shift'],
      action: () => setShowAdvancedSearch(true),
      description: 'Open advanced search',
      category: 'tools',
    });

    // Keyboard shortcuts manager
    registerShortcut({
      id: 'keyboard-shortcuts',
      key: 's',
      modifiers: ['ctrl'],
      action: () => setShowKeyboardShortcuts(true),
      description: 'Open keyboard shortcuts manager',
      category: 'tools',
    });
  }, [enableKeyboardShortcuts, registerShortcut]);

  const togglePanel = useCallback((panelId: string) => {
    setPanels(prev => prev.map(panel =>
      panel.id === panelId ? { ...panel, visible: !panel.visible } : panel
    ));
  }, []);

  const handlePanelResize = useCallback((panelId: string, size: number) => {
    setPanels(prev => prev.map(panel =>
      panel.id === panelId ? { ...panel, size } : panel
    ));
  }, []);

  const handleThemeChange = useCallback((theme: IDETheme) => {
    setCurrentTheme(theme);
  }, []);

  const handleConflictResolution = useCallback((conflictId: string) => {
    setConflicts(prev => prev.filter(c => c.id !== conflictId));
    // Handle conflict resolution logic here
  }, []);

  // Get panels by position
  const leftPanels = panels.filter(p => p.position === 'left' && p.visible);
  const rightPanels = panels.filter(p => p.position === 'right' && p.visible);
  const topPanels = panels.filter(p => p.position === 'top' && p.visible);
  const bottomPanels = panels.filter(p => p.position === 'bottom' && p.visible);

  return (
    <div
      className={`h-screen flex flex-col ${className}`}
      style={{ backgroundColor: currentTheme.colors.background }}
    >
      {/* Top panels */}
      {topPanels.length > 0 && (
        <div className="flex border-b border-gray-200 dark:border-gray-700">
          {topPanels.map(panel => (
            <ResizablePanel
              key={panel.id}
              panel={panel}
              onResize={(size) => handlePanelResize(panel.id, size)}
              onToggle={() => togglePanel(panel.id)}
            >
              <panel.component {...panel.props} />
            </ResizablePanel>
          ))}
        </div>
      )}

      {/* Main content area */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left panels */}
        {leftPanels.length > 0 && (
          <div className="flex border-r border-gray-200 dark:border-gray-700">
            {leftPanels.map(panel => (
              <ResizablePanel
                key={panel.id}
                panel={panel}
                onResize={(size) => handlePanelResize(panel.id, size)}
                onToggle={() => togglePanel(panel.id)}
              >
                <panel.component {...panel.props} />
              </ResizablePanel>
            ))}
          </div>
        )}

        {/* Center content */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Tab bar */}
          <div className="border-b border-gray-200 dark:border-gray-700">
            <TabBar />
          </div>

          {/* Editor area */}
          <div className="flex-1 relative overflow-hidden">
            {/* Main editor */}
            <div className="absolute inset-0 bg-gray-50 dark:bg-gray-800">
              {/* Editor content would go here */}
              <div className="flex items-center justify-center h-full text-gray-500 dark:text-gray-400">
                <div className="text-center">
                  <div className="text-6xl mb-4">💻</div>
                  <div className="text-lg font-medium">Editor Area</div>
                  <div className="text-sm">Open a file to start editing</div>
                </div>
              </div>
            </div>

            {/* Real-time cursor tracking */}
            {enableCollaboration && (
              <RealTimeCursorTracking
                editorRef={{ current: null }}
                fileId="current-file"
                onConflictDetected={(conflict) => {
                  setConflicts(prev => [...prev, conflict as unknown as FileConflict]);
                }}
              />
            )}
          </div>

          {/* Bottom panels */}
          {bottomPanels.length > 0 && (
            <div className="flex border-t border-gray-200 dark:border-gray-700">
              {bottomPanels.map(panel => (
                <ResizablePanel
                  key={panel.id}
                  panel={panel}
                  onResize={(size) => handlePanelResize(panel.id, size)}
                  onToggle={() => togglePanel(panel.id)}
                >
                  <panel.component {...panel.props} />
                </ResizablePanel>
              ))}
            </div>
          )}
        </div>

        {/* Right panels */}
        {rightPanels.length > 0 && (
          <div className="flex border-l border-gray-200 dark:border-gray-700">
            {rightPanels.map(panel => (
              <ResizablePanel
                key={panel.id}
                panel={panel}
                onResize={(size) => handlePanelResize(panel.id, size)}
                onToggle={() => togglePanel(panel.id)}
              >
                <panel.component {...panel.props} />
              </ResizablePanel>
            ))}
          </div>
        )}
      </div>

      {/* Status bar */}
      <div className="border-t border-gray-200 dark:border-gray-700">
        <StatusBar />
        {enableCollaboration && (
          <CollaborationStatusBar />
        )}
      </div>

      {/* Theme selector */}
      <div className="fixed bottom-4 right-4 z-40">
        <select
          value={currentTheme.id}
          onChange={(e) => {
            const theme = DEFAULT_THEMES.find(t => t.id === e.target.value);
            if (theme) handleThemeChange(theme);
          }}
          className="px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
        >
          {DEFAULT_THEMES.map(theme => (
            <option key={theme.id} value={theme.id}>
              {theme.name}
            </option>
          ))}
        </select>
      </div>

      {/* Command Palette */}
      {enableCommandPalette && (
        <CommandPalette
          isOpen={showCommandPalette}
          onClose={() => setShowCommandPalette(false)}
          commands={[]}
          query=""
          onQueryChange={() => { }}
          selectedIndex={0}
          onSelectedIndexChange={() => { }}
          onKeyDown={() => { }}
        />
      )}

      {/* Keyboard Shortcuts Manager */}
      {enableKeyboardShortcuts && (
        <KeyboardShortcutsManager
          isOpen={showKeyboardShortcuts}
          onClose={() => setShowKeyboardShortcuts(false)}
        />
      )}

      {/* Advanced Search */}
      {enableAdvancedSearch && (
        <AdvancedSearchPanel
          isVisible={showAdvancedSearch}
          onClose={() => setShowAdvancedSearch(false)}
        />
      )}

      {/* Conflict Resolution */}
      {enableConflictResolution && conflicts.length > 0 && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-900 rounded-lg shadow-2xl w-full max-w-4xl max-h-[80vh] overflow-hidden">
            <div className="p-4 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                ⚠️ Resolve Conflicts
              </h2>
            </div>
            <div className="max-h-96 overflow-y-auto p-4">
              <EnhancedConflictResolver
                conflicts={conflicts}
                onResolveConflict={handleConflictResolution}
                onPostponeConflict={(id) => {
                  setConflicts(prev => prev.filter(c => c.id !== id));
                }}
                onRequestUserInfo={() => { }}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProfessionalIDELayout;
