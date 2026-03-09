/**
 * Bootstrap Canvas Component
 *
 * @description Wrapper around ProjectCanvas specifically configured for
 * bootstrapping phase. Adds phase lanes (MVP/V2/Future), AI suggestions,
 * and integrates with bootstrapping state atoms.
 *
 * @doc.type component
 * @doc.purpose Bootstrapping canvas UI
 * @doc.layer presentation
 * @doc.phase bootstrapping
 * @doc.reuses ProjectCanvas, React Flow MiniMap
 */

import React, { useCallback, useMemo, useRef } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Layers,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  Info,
  Plus,
  Maximize2,
  Download,
  Share2,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// Reuse existing canvas component
import { ProjectCanvas, type ProjectCanvasRef } from '../canvas/ProjectCanvas';
import { PhaseProgressBar } from './PhaseProgressBar';

// Import bootstrapping state
import type { BootstrapPhase, CanvasNode as BootstrapNode } from '@ghatana/yappc-canvas';
import {
  currentPhaseAtom,
  canvasNodesAtom,
  canvasEdgesAtom,
  selectedCanvasNodeAtom,
  canvasModeAtom,
  canvasViewportAtom,
  validationReportAtom,
  commandSuggestionsAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export type PhaseLane = 'mvp' | 'v2' | 'future';

export interface BootstrapCanvasProps {
  /** Session ID for the bootstrap session */
  sessionId: string;
  /** Called when nodes change */
  onNodesChange?: (nodes: BootstrapNode[]) => void;
  /** Called when canvas is saved */
  onSave?: () => void;
  /** Called when canvas is exported */
  onExport?: (format: 'json' | 'svg' | 'png') => void;
  /** Called when canvas is shared */
  onShare?: () => void;
  /** Show phase lanes background */
  showPhaseLanes?: boolean;
  /** Show validation overlay */
  showValidation?: boolean;
  /** Show AI suggestions panel */
  showAISuggestions?: boolean;
  /** Read-only mode */
  readOnly?: boolean;
  /** Show mini map */
  showMiniMap?: boolean;
  /** Show phase progress bar */
  showPhaseProgress?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Phase Lanes Background
// =============================================================================

interface PhaseLanesProps {
  activeLane?: PhaseLane;
}

const PHASE_LANE_CONFIG: Record<
  PhaseLane,
  { label: string; color: string; bgColor: string; width: string }
> = {
  mvp: {
    label: 'MVP',
    color: 'text-success-400',
    bgColor: 'bg-success-500/5',
    width: '33.33%',
  },
  v2: {
    label: 'Version 2',
    color: 'text-warning-400',
    bgColor: 'bg-warning-500/5',
    width: '33.33%',
  },
  future: {
    label: 'Future',
    color: 'text-zinc-400',
    bgColor: 'bg-zinc-500/5',
    width: '33.33%',
  },
};

const PhaseLanes: React.FC<PhaseLanesProps> = ({ activeLane }) => {
  return (
    <div className="pointer-events-none absolute inset-0 flex">
      {(Object.entries(PHASE_LANE_CONFIG) as [PhaseLane, typeof PHASE_LANE_CONFIG.mvp][]).map(
        ([lane, config]) => (
          <div
            key={lane}
            className={cn(
              'flex flex-col border-r border-dashed border-zinc-800 transition-colors duration-300',
              config.bgColor,
              activeLane === lane && 'bg-opacity-20'
            )}
            style={{ width: config.width }}
          >
            <div
              className={cn(
                'border-b border-dashed border-zinc-800 px-4 py-2',
                config.color
              )}
            >
              <span className="text-xs font-medium uppercase tracking-wider">
                {config.label}
              </span>
            </div>
          </div>
        )
      )}
    </div>
  );
};

// =============================================================================
// Validation Overlay
// =============================================================================

interface ValidationOverlayProps {
  score: number;
  errorCount: number;
  warningCount: number;
  onOpenDetails: () => void;
}

const ValidationOverlay: React.FC<ValidationOverlayProps> = ({
  score,
  errorCount,
  warningCount,
  onOpenDetails,
}) => {
  const scoreColor = useMemo(() => {
    if (score >= 80) return 'text-success-400';
    if (score >= 60) return 'text-warning-400';
    return 'text-error-400';
  }, [score]);

  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      className="absolute right-4 top-4 z-10 rounded-lg border border-zinc-800 bg-zinc-900/95 p-3 backdrop-blur-sm"
    >
      <div className="flex items-center gap-4">
        <div className="text-center">
          <div className={cn('text-2xl font-bold', scoreColor)}>{score}%</div>
          <div className="text-xs text-zinc-400">Validation</div>
        </div>

        <div className="flex gap-2">
          {errorCount > 0 && (
            <Badge variant="destructive" className="gap-1">
              <AlertTriangle className="h-3 w-3" />
              {errorCount}
            </Badge>
          )}
          {warningCount > 0 && (
            <Badge variant="warning" className="gap-1">
              <Info className="h-3 w-3" />
              {warningCount}
            </Badge>
          )}
          {errorCount === 0 && warningCount === 0 && (
            <Badge variant="success" className="gap-1">
              <CheckCircle2 className="h-3 w-3" />
              Valid
            </Badge>
          )}
        </div>

        <Button variant="ghost" size="sm" onClick={onOpenDetails}>
          Details
        </Button>
      </div>
    </motion.div>
  );
};

// =============================================================================
// AI Suggestions Panel
// =============================================================================

interface AISuggestionsPanelProps {
  suggestions: Array<{
    id: string;
    command: string;
    description: string;
    confidence: number;
  }>;
  onApply: (suggestionId: string) => void;
  onDismiss: (suggestionId: string) => void;
}

const AISuggestionsPanel: React.FC<AISuggestionsPanelProps> = ({
  suggestions,
  onApply,
  onDismiss,
}) => {
  if (suggestions.length === 0) return null;

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="absolute right-4 top-20 z-10 w-72 rounded-lg border border-primary-500/30 bg-zinc-900/95 backdrop-blur-sm"
    >
      <div className="flex items-center gap-2 border-b border-zinc-800 px-4 py-3">
        <Sparkles className="h-4 w-4 text-primary-400" />
        <span className="font-medium">AI Suggestions</span>
        <Badge variant="outline" className="ml-auto">
          {suggestions.length}
        </Badge>
      </div>

      <div className="max-h-64 overflow-y-auto p-2">
        {suggestions.slice(0, 5).map((suggestion) => (
          <div
            key={suggestion.id}
            className="mb-2 rounded-md border border-zinc-800 bg-zinc-800/50 p-3 last:mb-0"
          >
            <div className="mb-1 text-sm font-medium text-zinc-100">
              {suggestion.command}
            </div>
            <div className="mb-2 text-xs text-zinc-400">
              {suggestion.description}
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="default"
                size="sm"
                onClick={() => onApply(suggestion.id)}
              >
                Apply
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onDismiss(suggestion.id)}
              >
                Dismiss
              </Button>
              <span className="ml-auto text-xs text-zinc-500">
                {Math.round(suggestion.confidence * 100)}% confident
              </span>
            </div>
          </div>
        ))}
      </div>
    </motion.div>
  );
};

// =============================================================================
// Canvas Action Bar
// =============================================================================

interface CanvasActionBarProps {
  onExport: (format: 'json' | 'svg' | 'png') => void;
  onShare: () => void;
  onFullscreen: () => void;
  nodeCount: number;
  edgeCount: number;
}

const CanvasActionBar: React.FC<CanvasActionBarProps> = ({
  onExport,
  onShare,
  onFullscreen,
  nodeCount,
  edgeCount,
}) => {
  return (
    <div className="absolute bottom-4 left-4 z-10 flex items-center gap-2 rounded-lg border border-zinc-800 bg-zinc-900/95 p-2 backdrop-blur-sm">
      <span className="px-2 text-xs text-zinc-400">
        {nodeCount} nodes · {edgeCount} edges
      </span>

      <div className="h-4 w-px bg-zinc-700" />

      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => onExport('json')}>
            <Download className="h-4 w-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Export Canvas</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onShare}>
            <Share2 className="h-4 w-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Share</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onFullscreen}>
            <Maximize2 className="h-4 w-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Fullscreen</TooltipContent>
      </Tooltip>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const BootstrapCanvas: React.FC<BootstrapCanvasProps> = ({
  sessionId,
  onNodesChange,
  onSave,
  onExport,
  onShare,
  showPhaseLanes = true,
  showValidation = true,
  showAISuggestions = true,
  readOnly = false,
  showMiniMap = true,
  showPhaseProgress = true,
  className,
}) => {
  const canvasRef = useRef<ProjectCanvasRef>(null);

  // State from atoms
  const currentPhase = useAtomValue(currentPhaseAtom);
  const nodes = useAtomValue(canvasNodesAtom);
  const edges = useAtomValue(canvasEdgesAtom);
  const validationReport = useAtomValue(validationReportAtom);
  const commandSuggestions = useAtomValue(commandSuggestionsAtom);
  const setSelectedNode = useSetAtom(selectedCanvasNodeAtom);

  // Determine active lane based on current phase
  const activeLane: PhaseLane | undefined = useMemo(() => {
    if (currentPhase === 'enter' || currentPhase === 'explore') return 'mvp';
    if (currentPhase === 'refine') return 'v2';
    if (currentPhase === 'validate' || currentPhase === 'complete') return undefined;
    return undefined;
  }, [currentPhase]);

  // Handle canvas node selection
  const handleNodeSelect = useCallback(
    (nodeId: string | null) => {
      setSelectedNode(nodeId);
    },
    [setSelectedNode]
  );

  // Handle export
  const handleExport = useCallback(
    (format: 'json' | 'svg' | 'png') => {
      if (format === 'json' && canvasRef.current) {
        const data = canvasRef.current.exportCanvas();
        onExport?.(format);
        // Could also trigger download here
      } else {
        onExport?.(format);
      }
    },
    [onExport]
  );

  // Handle fullscreen
  const handleFullscreen = useCallback(() => {
    const elem = document.querySelector('.bootstrap-canvas-container');
    if (elem && document.fullscreenEnabled) {
      elem.requestFullscreen?.();
    }
  }, []);

  // Handle AI suggestion apply
  const handleApplySuggestion = useCallback((suggestionId: string) => {
    // Implementation would apply the suggestion to the canvas
    console.log('Apply suggestion:', suggestionId);
  }, []);

  // Handle AI suggestion dismiss
  const handleDismissSuggestion = useCallback((suggestionId: string) => {
    // Implementation would dismiss the suggestion
    console.log('Dismiss suggestion:', suggestionId);
  }, []);

  // Validation details handler
  const handleOpenValidationDetails = useCallback(() => {
    // Would open validation panel
    console.log('Open validation details');
  }, []);

  return (
    <div className={cn('bootstrap-canvas-container relative h-full w-full', className)}>
      {/* Phase Progress Bar */}
      {showPhaseProgress && (
        <div className="absolute left-0 right-0 top-0 z-20 border-b border-zinc-800 bg-zinc-900/95 px-4 py-3 backdrop-blur-sm">
          <PhaseProgressBar
            currentPhase={currentPhase}
            showLabels
            size="sm"
          />
        </div>
      )}

      {/* Canvas Area */}
      <div className={cn('relative h-full w-full', showPhaseProgress && 'pt-16')}>
        {/* Phase Lanes Background */}
        {showPhaseLanes && <PhaseLanes activeLane={activeLane} />}

        {/* Main Canvas - REUSING EXISTING COMPONENT */}
        <ProjectCanvas
          ref={canvasRef}
          sessionId={sessionId}
          onNodeSelect={handleNodeSelect}
          onSave={onSave}
          readOnly={readOnly}
          showMiniMap={showMiniMap}
          showControls
          className="h-full w-full"
        />

        {/* Validation Overlay */}
        {showValidation && validationReport && (
          <ValidationOverlay
            score={validationReport.overallScore}
            errorCount={validationReport.checks.filter((c) => c.status === 'failed').length}
            warningCount={validationReport.checks.filter((c) => c.status === 'warning').length}
            onOpenDetails={handleOpenValidationDetails}
          />
        )}

        {/* AI Suggestions Panel */}
        {showAISuggestions && commandSuggestions.length > 0 && (
          <AISuggestionsPanel
            suggestions={commandSuggestions}
            onApply={handleApplySuggestion}
            onDismiss={handleDismissSuggestion}
          />
        )}

        {/* Canvas Action Bar */}
        <CanvasActionBar
          onExport={handleExport}
          onShare={() => onShare?.()}
          onFullscreen={handleFullscreen}
          nodeCount={nodes.length}
          edgeCount={edges.length}
        />
      </div>
    </div>
  );
};

BootstrapCanvas.displayName = 'BootstrapCanvas';

export default BootstrapCanvas;
