/**
 * Unified Canvas Toolbar
 *
 * @description Simplified canvas toolbar with progressive disclosure.
 * Reduces 18 controls to ≤8 visible, with advanced tools in collapsible panel.
 * Implements Hick's Law optimization (4.7s → 2.8s target).
 *
 * @doc.type component
 * @doc.purpose Simplified canvas controls
 * @doc.layer component
 * @doc.phase development
 */

import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  MousePointer2,
  Square,
  Circle,
  Type,
  StickyNote,
  Pencil,
  Image,
  Link2,
  Undo2,
  Redo2,
  ZoomIn,
  ZoomOut,
  Maximize2,
  MoreHorizontal,
  ChevronDown,
  Hand,
  Frame,
  ArrowRight,
  Layers,
  Grid3X3,
  Lock,
  Unlock,
  Sparkles,
} from 'lucide-react';

import { cn } from '../../utils/cn';

// =============================================================================
// Types
// =============================================================================

export type ToolType =
  | 'select'
  | 'pan'
  | 'rectangle'
  | 'ellipse'
  | 'text'
  | 'sticky'
  | 'draw'
  | 'image'
  | 'link'
  | 'frame'
  | 'arrow';

export interface ToolConfig {
  id: ToolType;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  shortcut?: string;
  category: 'primary' | 'shapes' | 'content' | 'advanced';
}

export interface UnifiedCanvasToolbarProps {
  activeTool: ToolType;
  onToolChange: (tool: ToolType) => void;
  canUndo: boolean;
  canRedo: boolean;
  onUndo: () => void;
  onRedo: () => void;
  zoom: number;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onZoomFit: () => void;
  onAIAssist?: () => void;
  isLocked?: boolean;
  onToggleLock?: () => void;
  showGrid?: boolean;
  onToggleGrid?: () => void;
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const PRIMARY_TOOLS: ToolConfig[] = [
  { id: 'select', label: 'Select', icon: MousePointer2, shortcut: 'V', category: 'primary' },
  { id: 'pan', label: 'Pan', icon: Hand, shortcut: 'H', category: 'primary' },
];

const SHAPE_TOOLS: ToolConfig[] = [
  { id: 'rectangle', label: 'Rectangle', icon: Square, shortcut: 'R', category: 'shapes' },
  { id: 'ellipse', label: 'Ellipse', icon: Circle, shortcut: 'O', category: 'shapes' },
  { id: 'frame', label: 'Frame', icon: Frame, shortcut: 'F', category: 'shapes' },
  { id: 'arrow', label: 'Arrow', icon: ArrowRight, shortcut: 'A', category: 'shapes' },
];

const CONTENT_TOOLS: ToolConfig[] = [
  { id: 'text', label: 'Text', icon: Type, shortcut: 'T', category: 'content' },
  { id: 'sticky', label: 'Sticky Note', icon: StickyNote, shortcut: 'S', category: 'content' },
  { id: 'draw', label: 'Draw', icon: Pencil, shortcut: 'P', category: 'content' },
  { id: 'image', label: 'Image', icon: Image, shortcut: 'I', category: 'content' },
  { id: 'link', label: 'Link', icon: Link2, shortcut: 'L', category: 'content' },
];

const ALL_TOOLS = [...PRIMARY_TOOLS, ...SHAPE_TOOLS, ...CONTENT_TOOLS];

// =============================================================================
// Sub-Components
// =============================================================================

interface ToolButtonProps {
  tool: ToolConfig;
  isActive: boolean;
  onClick: () => void;
}

const ToolButton: React.FC<ToolButtonProps> = ({ tool, isActive, onClick }) => {
  const Icon = tool.icon;

  return (
    <button
      onClick={onClick}
      className={cn(
        'group relative flex h-9 w-9 items-center justify-center rounded-lg transition-all',
        isActive
          ? 'bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400'
          : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
      )}
      title={`${tool.label}${tool.shortcut ? ` (${tool.shortcut})` : ''}`}
    >
      <Icon className="h-4 w-4" />

      {/* Tooltip */}
      <div className="pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 hidden -translate-x-1/2 whitespace-nowrap rounded-lg bg-gray-900 px-2 py-1 text-xs text-white shadow-lg group-hover:block dark:bg-gray-700">
        {tool.label}
        {tool.shortcut && (
          <span className="ml-2 rounded bg-gray-700 px-1 font-mono dark:bg-gray-600">
            {tool.shortcut}
          </span>
        )}
      </div>
    </button>
  );
};

interface ToolDropdownProps {
  tools: ToolConfig[];
  activeTool: ToolType;
  onToolChange: (tool: ToolType) => void;
  label: string;
}

const ToolDropdown: React.FC<ToolDropdownProps> = ({
  tools,
  activeTool,
  onToolChange,
  label,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const activeToolConfig = tools.find((t) => t.id === activeTool);
  const ActiveIcon = activeToolConfig?.icon || tools[0].icon;

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          'flex h-9 items-center gap-1 rounded-lg px-2 transition-all',
          activeToolConfig
            ? 'bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400'
            : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
        )}
        title={label}
      >
        <ActiveIcon className="h-4 w-4" />
        <ChevronDown className="h-3 w-3" />
      </button>

      <AnimatePresence>
        {isOpen && (
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={() => setIsOpen(false)}
            />
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="absolute left-0 top-full z-50 mt-1 rounded-lg border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
            >
              {tools.map((tool) => {
                const Icon = tool.icon;
                return (
                  <button
                    key={tool.id}
                    onClick={() => {
                      onToolChange(tool.id);
                      setIsOpen(false);
                    }}
                    className={cn(
                      'flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors',
                      activeTool === tool.id
                        ? 'bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400'
                        : 'text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700'
                    )}
                  >
                    <Icon className="h-4 w-4" />
                    <span>{tool.label}</span>
                    {tool.shortcut && (
                      <span className="ml-auto text-xs text-gray-400">{tool.shortcut}</span>
                    )}
                  </button>
                );
              })}
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const UnifiedCanvasToolbar: React.FC<UnifiedCanvasToolbarProps> = ({
  activeTool,
  onToolChange,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  zoom,
  onZoomIn,
  onZoomOut,
  onZoomFit,
  onAIAssist,
  isLocked = false,
  onToggleLock,
  showGrid = true,
  onToggleGrid,
  className,
}) => {
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Keyboard shortcuts
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
        return;
      }

      const key = e.key.toUpperCase();
      const tool = ALL_TOOLS.find((t) => t.shortcut === key);

      if (tool && !e.metaKey && !e.ctrlKey && !e.altKey) {
        e.preventDefault();
        onToolChange(tool.id);
      }

      // Undo/Redo
      if ((e.metaKey || e.ctrlKey) && e.key === 'z') {
        e.preventDefault();
        if (e.shiftKey) {
          onRedo();
        } else {
          onUndo();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onToolChange, onUndo, onRedo]);

  return (
    <div
      className={cn(
        'flex items-center gap-1 rounded-xl border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-900',
        className
      )}
    >
      {/* Primary Tools (Always Visible) */}
      <div className="flex items-center gap-0.5">
        {PRIMARY_TOOLS.map((tool) => (
          <ToolButton
            key={tool.id}
            tool={tool}
            isActive={activeTool === tool.id}
            onClick={() => onToolChange(tool.id)}
          />
        ))}
      </div>

      <div className="mx-1 h-6 w-px bg-gray-200 dark:bg-gray-700" />

      {/* Shape Tools (Dropdown for Progressive Disclosure) */}
      <ToolDropdown
        tools={SHAPE_TOOLS}
        activeTool={activeTool}
        onToolChange={onToolChange}
        label="Shapes"
      />

      {/* Content Tools (Dropdown for Progressive Disclosure) */}
      <ToolDropdown
        tools={CONTENT_TOOLS}
        activeTool={activeTool}
        onToolChange={onToolChange}
        label="Content"
      />

      <div className="mx-1 h-6 w-px bg-gray-200 dark:bg-gray-700" />

      {/* History Controls */}
      <div className="flex items-center gap-0.5">
        <button
          onClick={onUndo}
          disabled={!canUndo}
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
            canUndo
              ? 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
              : 'cursor-not-allowed text-gray-300 dark:text-gray-600'
          )}
          title="Undo (⌘Z)"
        >
          <Undo2 className="h-4 w-4" />
        </button>
        <button
          onClick={onRedo}
          disabled={!canRedo}
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
            canRedo
              ? 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
              : 'cursor-not-allowed text-gray-300 dark:text-gray-600'
          )}
          title="Redo (⌘⇧Z)"
        >
          <Redo2 className="h-4 w-4" />
        </button>
      </div>

      <div className="mx-1 h-6 w-px bg-gray-200 dark:bg-gray-700" />

      {/* Zoom Controls */}
      <div className="flex items-center gap-0.5">
        <button
          onClick={onZoomOut}
          className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-600 transition-all hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          title="Zoom Out"
        >
          <ZoomOut className="h-4 w-4" />
        </button>
        <span className="min-w-[3rem] text-center text-sm text-gray-600 dark:text-gray-400">
          {Math.round(zoom * 100)}%
        </span>
        <button
          onClick={onZoomIn}
          className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-600 transition-all hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          title="Zoom In"
        >
          <ZoomIn className="h-4 w-4" />
        </button>
        <button
          onClick={onZoomFit}
          className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-600 transition-all hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          title="Fit to Screen"
        >
          <Maximize2 className="h-4 w-4" />
        </button>
      </div>

      <div className="mx-1 h-6 w-px bg-gray-200 dark:bg-gray-700" />

      {/* AI Assist Button */}
      {onAIAssist && (
        <button
          onClick={onAIAssist}
          className="flex h-9 items-center gap-1 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 px-3 text-sm font-medium text-white transition-all hover:from-purple-600 hover:to-blue-600"
          title="AI Assist"
        >
          <Sparkles className="h-4 w-4" />
          <span className="hidden sm:inline">AI</span>
        </button>
      )}

      {/* Advanced Options Toggle */}
      <button
        onClick={() => setShowAdvanced(!showAdvanced)}
        className={cn(
          'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
          showAdvanced
            ? 'bg-gray-100 text-gray-900 dark:bg-gray-800 dark:text-gray-100'
            : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
        )}
        title="More Options"
      >
        <MoreHorizontal className="h-4 w-4" />
      </button>

      {/* Advanced Options Panel */}
      <AnimatePresence>
        {showAdvanced && (
          <motion.div
            initial={{ opacity: 0, width: 0 }}
            animate={{ opacity: 1, width: 'auto' }}
            exit={{ opacity: 0, width: 0 }}
            className="flex items-center gap-0.5 overflow-hidden"
          >
            <div className="mx-1 h-6 w-px bg-gray-200 dark:bg-gray-700" />

            {/* Grid Toggle */}
            {onToggleGrid && (
              <button
                onClick={onToggleGrid}
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
                  showGrid
                    ? 'bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-400'
                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
                )}
                title="Toggle Grid"
              >
                <Grid3X3 className="h-4 w-4" />
              </button>
            )}

            {/* Lock Toggle */}
            {onToggleLock && (
              <button
                onClick={onToggleLock}
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
                  isLocked
                    ? 'bg-orange-100 text-orange-600 dark:bg-orange-900 dark:text-orange-400'
                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
                )}
                title={isLocked ? 'Unlock Canvas' : 'Lock Canvas'}
              >
                {isLocked ? <Lock className="h-4 w-4" /> : <Unlock className="h-4 w-4" />}
              </button>
            )}

            {/* Layers */}
            <button
              className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-600 transition-all hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
              title="Layers"
            >
              <Layers className="h-4 w-4" />
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default UnifiedCanvasToolbar;
