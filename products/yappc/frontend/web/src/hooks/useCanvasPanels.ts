/**
 * useCanvasPanels Hook
 * 
 * Consolidates all canvas panel state management into a single hook.
 * Reduces the 30+ state variables in CanvasScene.tsx to a single hook call.
 * 
 * @doc.type hook
 * @doc.purpose Canvas panel state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useMemo } from 'react';

export type PanelId = 
  | 'ai'
  | 'validation'
  | 'codeGen'
  | 'guidance'
  | 'comments'
  | 'designer'
  | 'versionHistory'
  | 'unified'
  | 'tasks'
  | 'accessibility'
  | 'performance'
  | 'commandPalette';

export interface PanelState {
  isOpen: boolean;
  data?: Record<string, unknown>;
}

export interface UseCanvasPanelsOptions {
  defaultOpenPanels?: PanelId[];
  onPanelChange?: (panelId: PanelId, isOpen: boolean) => void;
}

export interface UseCanvasPanelsResult {
  panels: Record<PanelId, PanelState>;
  
  isOpen: (panelId: PanelId) => boolean;
  open: (panelId: PanelId, data?: Record<string, unknown>) => void;
  close: (panelId: PanelId) => void;
  toggle: (panelId: PanelId) => void;
  closeAll: () => void;
  
  openPanelIds: PanelId[];
  
  // Convenience accessors for common panels
  aiPanelOpen: boolean;
  validationPanelOpen: boolean;
  codeGenPanelOpen: boolean;
  guidancePanelOpen: boolean;
  commentsPanelOpen: boolean;
  designerPanelOpen: boolean;
  versionHistoryOpen: boolean;
  unifiedPanelOpen: boolean;
  unifiedPanelTab: number;
  taskPanelCollapsed: boolean;
  accessibilityPanelOpen: boolean;
  performancePanelOpen: boolean;
  commandPaletteOpen: boolean;
  
  // Convenience setters
  setAiPanelOpen: (open: boolean) => void;
  setValidationPanelOpen: (open: boolean) => void;
  setCodeGenPanelOpen: (open: boolean) => void;
  setGuidancePanelOpen: (open: boolean) => void;
  setCommentsPanelOpen: (open: boolean) => void;
  setDesignerPanelOpen: (open: boolean) => void;
  setVersionHistoryOpen: (open: boolean) => void;
  setUnifiedPanelOpen: (open: boolean) => void;
  setTaskPanelCollapsed: (collapsed: boolean) => void;
  setAccessibilityPanelOpen: (open: boolean) => void;
  setPerformancePanelOpen: (open: boolean) => void;
  setCommandPaletteOpen: (open: boolean) => void;
  
  // Designer-specific state
  designerNodeId: string | null;
  setDesignerNodeId: (nodeId: string | null) => void;
  openDesigner: (nodeId: string) => void;
  closeDesigner: () => void;
}

const DEFAULT_PANEL_STATE: PanelState = { isOpen: false };

const ALL_PANEL_IDS: PanelId[] = [
  'ai',
  'validation',
  'codeGen',
  'guidance',
  'comments',
  'designer',
  'versionHistory',
  'unified',
  'tasks',
  'accessibility',
  'performance',
  'commandPalette',
];

export function useCanvasPanels(options: UseCanvasPanelsOptions = {}): UseCanvasPanelsResult {
  const { defaultOpenPanels = ['guidance'], onPanelChange } = options;

  // Initialize panel states
  const [panels, setPanels] = useState<Record<PanelId, PanelState>>(() => {
    const initial: Record<PanelId, PanelState> = {} as Record<PanelId, PanelState>;
    ALL_PANEL_IDS.forEach((id) => {
      initial[id] = {
        isOpen: defaultOpenPanels.includes(id),
      };
    });
    return initial;
  });

  // Designer-specific state
  const [designerNodeId, setDesignerNodeId] = useState<string | null>(null);

  // Task panel uses "collapsed" instead of "open" (inverted logic)
  const [taskPanelCollapsed, setTaskPanelCollapsedInternal] = useState(false);

  const isOpen = useCallback((panelId: PanelId): boolean => {
    if (panelId === 'tasks') {
      return !taskPanelCollapsed;
    }
    return panels[panelId]?.isOpen ?? false;
  }, [panels, taskPanelCollapsed]);

  const open = useCallback((panelId: PanelId, data?: Record<string, unknown>) => {
    if (panelId === 'tasks') {
      setTaskPanelCollapsedInternal(false);
      onPanelChange?.(panelId, true);
      return;
    }
    
    setPanels((prev) => ({
      ...prev,
      [panelId]: { isOpen: true, data },
    }));
    onPanelChange?.(panelId, true);
  }, [onPanelChange]);

  const close = useCallback((panelId: PanelId) => {
    if (panelId === 'tasks') {
      setTaskPanelCollapsedInternal(true);
      onPanelChange?.(panelId, false);
      return;
    }
    
    setPanels((prev) => ({
      ...prev,
      [panelId]: { ...prev[panelId], isOpen: false },
    }));
    onPanelChange?.(panelId, false);
  }, [onPanelChange]);

  const toggle = useCallback((panelId: PanelId) => {
    if (isOpen(panelId)) {
      close(panelId);
    } else {
      open(panelId);
    }
  }, [isOpen, open, close]);

  const closeAll = useCallback(() => {
    setPanels((prev) => {
      const updated = { ...prev };
      ALL_PANEL_IDS.forEach((id) => {
        updated[id] = { ...updated[id], isOpen: false };
      });
      return updated;
    });
    setTaskPanelCollapsedInternal(true);
  }, []);

  const openPanelIds = useMemo(() => {
    const ids: PanelId[] = [];
    ALL_PANEL_IDS.forEach((id) => {
      if (id === 'tasks') {
        if (!taskPanelCollapsed) ids.push(id);
      } else if (panels[id]?.isOpen) {
        ids.push(id);
      }
    });
    return ids;
  }, [panels, taskPanelCollapsed]);

  // Convenience setters
  // Mapped to unified panel tabs
  const setGuidancePanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('unified', { tab: 0 }) : close('unified');
  }, [open, close]);

  const setAiPanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('unified', { tab: 1 }) : close('unified');
  }, [open, close]);

  const setValidationPanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('unified', { tab: 2 }) : close('unified');
  }, [open, close]);

  const setCodeGenPanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('unified', { tab: 3 }) : close('unified');
  }, [open, close]);


  const setCommentsPanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('comments') : close('comments');
  }, [open, close]);

  const setDesignerPanelOpen = useCallback((isOpen: boolean) => {
    if (isOpen) {
      open('designer');
    } else {
      close('designer');
      setDesignerNodeId(null);
    }
  }, [open, close]);

  const setVersionHistoryOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('versionHistory') : close('versionHistory');
  }, [open, close]);

  const setUnifiedPanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('unified') : close('unified');
  }, [open, close]);

  const setTaskPanelCollapsed = useCallback((collapsed: boolean) => {
    setTaskPanelCollapsedInternal(collapsed);
    onPanelChange?.('tasks', !collapsed);
  }, [onPanelChange]);

  const setAccessibilityPanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('accessibility') : close('accessibility');
  }, [open, close]);

  const setPerformancePanelOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('performance') : close('performance');
  }, [open, close]);

  const setCommandPaletteOpen = useCallback((isOpen: boolean) => {
    isOpen ? open('commandPalette') : close('commandPalette');
  }, [open, close]);

  // Designer helpers
  const openDesigner = useCallback((nodeId: string) => {
    setDesignerNodeId(nodeId);
    open('designer');
  }, [open]);

  const closeDesigner = useCallback(() => {
    setDesignerNodeId(null);
    close('designer');
  }, [close]);

  return {
    panels,
    isOpen,
    open,
    close,
    toggle,
    closeAll,
    openPanelIds,
    
    // Convenience accessors
    aiPanelOpen: panels.ai?.isOpen ?? false,
    validationPanelOpen: panels.validation?.isOpen ?? false,
    codeGenPanelOpen: panels.codeGen?.isOpen ?? false,
    guidancePanelOpen: panels.guidance?.isOpen ?? false,
    commentsPanelOpen: panels.comments?.isOpen ?? false,
    designerPanelOpen: panels.designer?.isOpen ?? false,
    versionHistoryOpen: panels.versionHistory?.isOpen ?? false,
    unifiedPanelOpen: panels.unified?.isOpen ?? false,
    unifiedPanelTab: (panels.unified?.data?.tab as number) ?? 0,
    taskPanelCollapsed,
    accessibilityPanelOpen: panels.accessibility?.isOpen ?? false,
    performancePanelOpen: panels.performance?.isOpen ?? false,
    commandPaletteOpen: panels.commandPalette?.isOpen ?? false,
    
    // Convenience setters
    setAiPanelOpen,
    setValidationPanelOpen,
    setCodeGenPanelOpen,
    setGuidancePanelOpen,
    setCommentsPanelOpen,
    setDesignerPanelOpen,
    setVersionHistoryOpen,
    setUnifiedPanelOpen,
    setTaskPanelCollapsed,
    setAccessibilityPanelOpen,
    setPerformancePanelOpen,
    setCommandPaletteOpen,
    
    // Designer-specific
    designerNodeId,
    setDesignerNodeId,
    openDesigner,
    closeDesigner,
  };
}

export default useCanvasPanels;
