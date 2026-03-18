import React, { useCallback } from 'react';
import { UnifiedCanvasToolbar } from '@/components/canvas/toolbar/UnifiedCanvasToolbar';
import { performanceMonitor } from '@/services/canvas/CanvasPerformanceMonitor';
import type { CanvasElement } from '@/components/canvas/workspace/canvasAtoms';

export interface CanvasToolbarProps {
  currentMode: unknown;
  setMode: (mode: unknown) => void;

  // Interaction Mode (navigate, sketch, code)
  interactionMode?: 'navigate' | 'sketch' | 'code';
  onInteractionModeChange?: (mode: 'navigate' | 'sketch' | 'code') => void;

  // Abstraction
  currentLevel: unknown;
  setAbstractionLevel: (level: unknown) => void;
  onDrillDown: () => void;
  onZoomOut: () => void;

  // Save State
  saveStatus: 'idle' | 'saving' | 'saved' | 'error';
  lastSaveTime: number;
  setAutoSaveStatus: (status: 'idle' | 'saving' | 'saved' | 'error') => void;
  setLastSaveTime: (time: number) => void;

  // Validation
  validationScore?: number;
  isValidating: boolean;
  errorCount: number;
  warningCount: number;

  // AI
  aiSuggestionCount: number;
  isAnalyzing: boolean;
  canGenerate: boolean;
  isGenerating: boolean;
  generatedFileCount: number;

  // Panels
  unifiedPanelOpen: boolean;
  setUnifiedPanelOpen: (open: boolean) => void;
  setUnifiedPanelTab: (tab: number) => void;

  // Actions
  onOpenOnboarding: () => void;
  onGenerate: () => void;
  onValidate: () => void;

  // Data for Save Retry
  params: { projectId?: string; canvasId?: string };
  nodes: unknown[];
  edges: unknown[];
  persistenceRef: React.MutableRefObject<unknown>;

  // Sketch
  sketchTool: unknown;
  onSketchToolChange: (tool: unknown) => void;
}

export function CanvasToolbar({
  currentMode,
  setMode,
  interactionMode,
  onInteractionModeChange,
  currentLevel,
  setAbstractionLevel,
  onDrillDown,
  onZoomOut,
  saveStatus,
  lastSaveTime,
  setAutoSaveStatus,
  setLastSaveTime,
  validationScore,
  isValidating,
  errorCount,
  warningCount,
  aiSuggestionCount,
  isAnalyzing,
  canGenerate,
  isGenerating,
  generatedFileCount,
  unifiedPanelOpen,
  setUnifiedPanelOpen,
  setUnifiedPanelTab,
  onOpenOnboarding,
  onGenerate,
  onValidate,
  params,
  nodes,
  edges,
  persistenceRef,
  sketchTool,
  onSketchToolChange,
}: CanvasToolbarProps) {

  const handleRetrySave = useCallback(() => {
    const { projectId, canvasId } = params;
    if (projectId && canvasId) {
      setAutoSaveStatus('saving');
      performanceMonitor.measureAsync('manual-save', async () => {
        await persistenceRef.current?.save(
          projectId,
          canvasId,
          { elements: nodes as unknown as CanvasElement[], connections: edges as unknown as unknown[] },
          { label: 'Retry save' }
        );
      }).then(() => {
        setAutoSaveStatus('saved');
        setLastSaveTime(Date.now());
      }).catch((error) => {
        console.error('Retry save failed:', error);
        setAutoSaveStatus('error');
      });
    }
  }, [params, nodes, edges, persistenceRef, setAutoSaveStatus, setLastSaveTime]);

  return (
    <UnifiedCanvasToolbar
      interactionMode={interactionMode}
      onInteractionModeChange={onInteractionModeChange}
      currentMode={currentMode}
      onModeChange={setMode}
      currentLevel={currentLevel}
      onLevelChange={setAbstractionLevel}
      onDrillDown={onDrillDown}
      onZoomOut={onZoomOut}
      saveStatus={saveStatus}
      lastSaveTime={lastSaveTime}
      validationScore={validationScore}
      isValidating={isValidating}
      errorCount={errorCount}
      warningCount={warningCount}
      aiSuggestionCount={aiSuggestionCount}
      isAnalyzing={isAnalyzing}
      canGenerate={canGenerate}
      isGenerating={isGenerating}
      generatedFileCount={generatedFileCount}
      onUnifiedPanelToggle={() => setUnifiedPanelOpen(!unifiedPanelOpen)}
      onGuidanceToggle={() => {
        setUnifiedPanelTab(0);
        setUnifiedPanelOpen(true);
      }}
      onAIPanelToggle={() => {
        setUnifiedPanelTab(1);
        setUnifiedPanelOpen(true);
      }}
      onValidationPanelToggle={() => {
        setUnifiedPanelTab(2);
        setUnifiedPanelOpen(true);
      }}
      onCodeGenPanelToggle={() => {
        setUnifiedPanelTab(3);
        setUnifiedPanelOpen(true);
      }}
      onOpenOnboarding={onOpenOnboarding}
      onGenerate={onGenerate}
      onValidate={onValidate}
      onRetrySave={handleRetrySave}
      sketchTool={sketchTool}
      onSketchToolChange={onSketchToolChange}
    />
  );
}
