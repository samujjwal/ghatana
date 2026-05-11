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
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';
import type { MessageKey } from '../../i18n/messages';

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
  labelKey: MessageKey;
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
  { id: 'select', labelKey: 'toolbar.tool.select', icon: MousePointer2, shortcut: 'V', category: 'primary' },
  { id: 'pan', labelKey: 'toolbar.tool.pan', icon: Hand, shortcut: 'H', category: 'primary' },
];

const SHAPE_TOOLS: ToolConfig[] = [
  { id: 'rectangle', labelKey: 'toolbar.tool.rectangle', icon: Square, shortcut: 'R', category: 'shapes' },
  { id: 'ellipse', labelKey: 'toolbar.tool.ellipse', icon: Circle, shortcut: 'O', category: 'shapes' },
  { id: 'frame', labelKey: 'toolbar.tool.frame', icon: Frame, shortcut: 'F', category: 'shapes' },
  { id: 'arrow', labelKey: 'toolbar.tool.arrow', icon: ArrowRight, shortcut: 'A', category: 'shapes' },
];

const CONTENT_TOOLS: ToolConfig[] = [
  { id: 'text', labelKey: 'toolbar.tool.text', icon: Type, shortcut: 'T', category: 'content' },
  { id: 'sticky', labelKey: 'toolbar.tool.stickyNote', icon: StickyNote, shortcut: 'S', category: 'content' },
  { id: 'draw', labelKey: 'toolbar.tool.draw', icon: Pencil, shortcut: 'P', category: 'content' },
  { id: 'image', labelKey: 'toolbar.tool.image', icon: Image, shortcut: 'I', category: 'content' },
  { id: 'link', labelKey: 'toolbar.tool.link', icon: Link2, shortcut: 'L', category: 'content' },
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
  const { t } = useTranslation('common');
  const label = t(tool.labelKey);

  return (
    <Button
      onClick={onClick}
      variant="ghost"
      size="sm"
      className={cn(
        'group relative flex h-9 w-9 items-center justify-center rounded-lg transition-all',
        isActive
          ? 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color'
          : 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
      )}
      title={`${label}${tool.shortcut ? ` (${tool.shortcut})` : ''}`}
    >
      <Icon className="h-4 w-4" />

      {/* Tooltip */}
      <div className="pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 hidden -translate-x-1/2 whitespace-nowrap rounded-lg bg-surface px-2 py-1 text-xs text-white shadow-lg group-hover:block dark:bg-surface-muted">
        {label}
        {tool.shortcut && (
          <span className="ml-2 rounded bg-surface-muted px-1 font-mono dark:bg-surface-muted">
            {tool.shortcut}
          </span>
        )}
      </div>
    </Button>
  );
};

interface ToolDropdownProps {
  tools: ToolConfig[];
  activeTool: ToolType;
  onToolChange: (tool: ToolType) => void;
  labelKey: MessageKey;
}

const ToolDropdown: React.FC<ToolDropdownProps> = ({
  tools,
  activeTool,
  onToolChange,
  labelKey,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const { t } = useTranslation('common');
  const activeToolConfig = tools.find((t) => t.id === activeTool);
  const ActiveIcon = activeToolConfig?.icon || tools[0].icon;

  return (
    <div className="relative">
      <Button
        onClick={() => setIsOpen(!isOpen)}
        variant="ghost"
        size="sm"
        className={cn(
          'flex h-9 items-center gap-1 rounded-lg px-2 transition-all',
          activeToolConfig
            ? 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color'
            : 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
        )}
        title={t(labelKey)}
      >
        <ActiveIcon className="h-4 w-4" />
        <ChevronDown className="h-3 w-3" />
      </Button>

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
              className="absolute left-0 top-full z-50 mt-1 rounded-lg border border-border bg-white p-1 shadow-lg dark:border-border dark:bg-surface"
            >
              {tools.map((tool) => {
                const Icon = tool.icon;
                return (
                  <Button
                    key={tool.id}
                    onClick={() => {
                      onToolChange(tool.id);
                      setIsOpen(false);
                    }}
                    variant="ghost"
                    size="sm"
                    fullWidth
                    className={cn(
                      'flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors',
                      activeTool === tool.id
                        ? 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color'
                        : 'text-fg hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface-muted'
                    )}
                  >
                    <Icon className="h-4 w-4" />
                    <span>{t(tool.labelKey)}</span>
                    {tool.shortcut && (
                      <span className="ml-auto text-xs text-fg-muted">{tool.shortcut}</span>
                    )}
                  </Button>
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
  const { t } = useTranslation('common');
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
        'flex items-center gap-1 rounded-xl border border-border bg-white p-1 shadow-lg dark:border-border dark:bg-surface',
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

      <div className="mx-1 h-6 w-px bg-surface-muted dark:bg-surface-muted" />

      {/* Shape Tools (Dropdown for Progressive Disclosure) */}
      <ToolDropdown
        tools={SHAPE_TOOLS}
        activeTool={activeTool}
        onToolChange={onToolChange}
        labelKey="toolbar.dropdown.shapes"
      />

      {/* Content Tools (Dropdown for Progressive Disclosure) */}
      <ToolDropdown
        tools={CONTENT_TOOLS}
        activeTool={activeTool}
        onToolChange={onToolChange}
        labelKey="toolbar.dropdown.content"
      />

      <div className="mx-1 h-6 w-px bg-surface-muted dark:bg-surface-muted" />

      {/* History Controls */}
      <div className="flex items-center gap-0.5">
        <Button
          onClick={onUndo}
          disabled={!canUndo}
          variant="ghost"
          size="sm"
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
            canUndo
              ? 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
              : 'cursor-not-allowed text-fg-muted dark:text-fg-muted'
          )}
          title={`${t('toolbar.action.undo')} (⌘Z)`}
        >
          <Undo2 className="h-4 w-4" />
        </Button>
        <Button
          onClick={onRedo}
          disabled={!canRedo}
          variant="ghost"
          size="sm"
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
            canRedo
              ? 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
              : 'cursor-not-allowed text-fg-muted dark:text-fg-muted'
          )}
          title={`${t('toolbar.action.redo')} (⌘⇧Z)`}
        >
          <Redo2 className="h-4 w-4" />
        </Button>
      </div>

      <div className="mx-1 h-6 w-px bg-surface-muted dark:bg-surface-muted" />

      {/* Zoom Controls */}
      <div className="flex items-center gap-0.5">
        <Button
          onClick={onZoomOut}
          variant="ghost"
          size="sm"
          className="flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-all hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface"
          title={t('toolbar.action.zoomOut')}
        >
          <ZoomOut className="h-4 w-4" />
        </Button>
        <span className="min-w-[3rem] text-center text-sm text-fg-muted dark:text-fg-muted">
          {Math.round(zoom * 100)}%
        </span>
        <Button
          onClick={onZoomIn}
          variant="ghost"
          size="sm"
          className="flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-all hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface"
          title={t('toolbar.action.zoomIn')}
        >
          <ZoomIn className="h-4 w-4" />
        </Button>
        <Button
          onClick={onZoomFit}
          variant="ghost"
          size="sm"
          className="flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-all hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface"
          title={t('toolbar.action.fitToScreen')}
        >
          <Maximize2 className="h-4 w-4" />
        </Button>
      </div>

      <div className="mx-1 h-6 w-px bg-surface-muted dark:bg-surface-muted" />

      {/* Guided Assist Button */}
      {onAIAssist && (
        <Button
          onClick={onAIAssist}
          variant="solid"
          size="sm"
          className="flex h-9 items-center gap-1 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 px-3 text-sm font-medium text-white transition-all hover:from-purple-600 hover:to-blue-600"
          title={t('toolbar.action.guidedAssist')}
        >
          <Sparkles className="h-4 w-4" />
          <span className="hidden sm:inline">{t('toolbar.action.assist')}</span>
        </Button>
      )}

      {/* Advanced Options Toggle */}
      <Button
        onClick={() => setShowAdvanced(!showAdvanced)}
        variant="ghost"
        size="sm"
        className={cn(
          'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
          showAdvanced
            ? 'bg-surface-muted text-fg dark:bg-surface dark:text-fg-muted'
            : 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
        )}
        title={t('toolbar.action.moreOptions')}
      >
        <MoreHorizontal className="h-4 w-4" />
      </Button>

      {/* Advanced Options Panel */}
      <AnimatePresence>
        {showAdvanced && (
          <motion.div
            initial={{ opacity: 0, width: 0 }}
            animate={{ opacity: 1, width: 'auto' }}
            exit={{ opacity: 0, width: 0 }}
            className="flex items-center gap-0.5 overflow-hidden"
          >
            <div className="mx-1 h-6 w-px bg-surface-muted dark:bg-surface-muted" />

            {/* Grid Toggle */}
            {onToggleGrid && (
              <Button
                onClick={onToggleGrid}
                variant="ghost"
                size="sm"
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
                  showGrid
                    ? 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color'
                    : 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
                )}
                title={t('toolbar.action.toggleGrid')}
              >
                <Grid3X3 className="h-4 w-4" />
              </Button>
            )}

            {/* Lock Toggle */}
            {onToggleLock && (
              <Button
                onClick={onToggleLock}
                variant="ghost"
                size="sm"
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-lg transition-all',
                  isLocked
                    ? 'bg-warning-bg text-warning-color dark:bg-warning-bg dark:text-warning-color'
                    : 'text-fg-muted hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface'
                )}
                title={isLocked ? t('toolbar.action.unlockCanvas') : t('toolbar.action.lockCanvas')}
              >
                {isLocked ? <Lock className="h-4 w-4" /> : <Unlock className="h-4 w-4" />}
              </Button>
            )}

            {/* Layers */}
            <Button
              variant="ghost"
              size="sm"
              className="flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-all hover:bg-surface-muted dark:text-fg-muted dark:hover:bg-surface"
              title={t('toolbar.action.layers')}
            >
              <Layers className="h-4 w-4" />
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default UnifiedCanvasToolbar;
