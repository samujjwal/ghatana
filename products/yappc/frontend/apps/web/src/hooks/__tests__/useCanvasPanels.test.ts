/**
 * useCanvasPanels Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Unit tests for useCanvasPanels hook
 * @doc.layer product
 */

import { renderHook, act } from '@testing-library/react';
import { useCanvasPanels, type PanelId } from '../useCanvasPanels';

describe('useCanvasPanels', () => {
  describe('Initial State', () => {
    it('should initialize with default panels closed', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      expect(result.current.aiPanelOpen).toBe(false);
      expect(result.current.validationPanelOpen).toBe(false);
      expect(result.current.codeGenPanelOpen).toBe(false);
    });

    it('should initialize with guidance panel open by default', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      expect(result.current.guidancePanelOpen).toBe(true);
    });

    it('should respect custom default open panels', () => {
      const { result } = renderHook(() => 
        useCanvasPanels({ defaultOpenPanels: ['ai', 'validation'] })
      );
      
      expect(result.current.aiPanelOpen).toBe(true);
      expect(result.current.validationPanelOpen).toBe(true);
      expect(result.current.guidancePanelOpen).toBe(false);
    });
  });

  describe('Panel Operations', () => {
    it('should open a panel', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      expect(result.current.aiPanelOpen).toBe(false);
      
      act(() => {
        result.current.open('ai');
      });
      
      expect(result.current.aiPanelOpen).toBe(true);
    });

    it('should close a panel', () => {
      const { result } = renderHook(() => 
        useCanvasPanels({ defaultOpenPanels: ['ai'] })
      );
      
      expect(result.current.aiPanelOpen).toBe(true);
      
      act(() => {
        result.current.close('ai');
      });
      
      expect(result.current.aiPanelOpen).toBe(false);
    });

    it('should toggle a panel', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      expect(result.current.aiPanelOpen).toBe(false);
      
      act(() => {
        result.current.toggle('ai');
      });
      
      expect(result.current.aiPanelOpen).toBe(true);
      
      act(() => {
        result.current.toggle('ai');
      });
      
      expect(result.current.aiPanelOpen).toBe(false);
    });

    it('should close all panels', () => {
      const { result } = renderHook(() => 
        useCanvasPanels({ defaultOpenPanels: ['ai', 'validation', 'guidance'] })
      );
      
      expect(result.current.openPanelIds.length).toBeGreaterThan(0);
      
      act(() => {
        result.current.closeAll();
      });
      
      expect(result.current.aiPanelOpen).toBe(false);
      expect(result.current.validationPanelOpen).toBe(false);
      expect(result.current.guidancePanelOpen).toBe(false);
    });
  });

  describe('isOpen Function', () => {
    it('should correctly report panel open state', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      expect(result.current.isOpen('ai')).toBe(false);
      
      act(() => {
        result.current.open('ai');
      });
      
      expect(result.current.isOpen('ai')).toBe(true);
    });
  });

  describe('Convenience Setters', () => {
    it('should work with setAiPanelOpen', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      act(() => {
        result.current.setAiPanelOpen(true);
      });
      
      expect(result.current.aiPanelOpen).toBe(true);
      
      act(() => {
        result.current.setAiPanelOpen(false);
      });
      
      expect(result.current.aiPanelOpen).toBe(false);
    });

    it('should work with setValidationPanelOpen', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      act(() => {
        result.current.setValidationPanelOpen(true);
      });
      
      expect(result.current.validationPanelOpen).toBe(true);
    });

    it('should work with setCommandPaletteOpen', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      act(() => {
        result.current.setCommandPaletteOpen(true);
      });
      
      expect(result.current.commandPaletteOpen).toBe(true);
    });
  });

  describe('Task Panel (Inverted Logic)', () => {
    it('should handle task panel collapsed state', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      // Task panel is not collapsed by default (i.e., it's open)
      expect(result.current.taskPanelCollapsed).toBe(false);
      expect(result.current.isOpen('tasks')).toBe(true);
      
      act(() => {
        result.current.setTaskPanelCollapsed(true);
      });
      
      expect(result.current.taskPanelCollapsed).toBe(true);
      expect(result.current.isOpen('tasks')).toBe(false);
    });
  });

  describe('Designer Panel', () => {
    it('should track designer node ID', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      expect(result.current.designerNodeId).toBeNull();
      
      act(() => {
        result.current.setDesignerNodeId('node-123');
      });
      
      expect(result.current.designerNodeId).toBe('node-123');
    });

    it('should open designer with node ID', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      act(() => {
        result.current.openDesigner('node-456');
      });
      
      expect(result.current.designerPanelOpen).toBe(true);
      expect(result.current.designerNodeId).toBe('node-456');
    });

    it('should close designer and clear node ID', () => {
      const { result } = renderHook(() => useCanvasPanels());
      
      act(() => {
        result.current.openDesigner('node-789');
      });
      
      expect(result.current.designerPanelOpen).toBe(true);
      
      act(() => {
        result.current.closeDesigner();
      });
      
      expect(result.current.designerPanelOpen).toBe(false);
      expect(result.current.designerNodeId).toBeNull();
    });
  });

  describe('Open Panel IDs', () => {
    it('should track all open panel IDs', () => {
      const { result } = renderHook(() => 
        useCanvasPanels({ defaultOpenPanels: [] })
      );
      
      expect(result.current.openPanelIds).toHaveLength(0);
      
      act(() => {
        result.current.open('ai');
        result.current.open('validation');
      });
      
      expect(result.current.openPanelIds).toContain('ai');
      expect(result.current.openPanelIds).toContain('validation');
    });
  });

  describe('onPanelChange Callback', () => {
    it('should call onPanelChange when panel opens', () => {
      const onPanelChange = jest.fn();
      const { result } = renderHook(() => 
        useCanvasPanels({ onPanelChange })
      );
      
      act(() => {
        result.current.open('ai');
      });
      
      expect(onPanelChange).toHaveBeenCalledWith('ai', true);
    });

    it('should call onPanelChange when panel closes', () => {
      const onPanelChange = jest.fn();
      const { result } = renderHook(() => 
        useCanvasPanels({ defaultOpenPanels: ['ai'], onPanelChange })
      );
      
      act(() => {
        result.current.close('ai');
      });
      
      expect(onPanelChange).toHaveBeenCalledWith('ai', false);
    });
  });
});
