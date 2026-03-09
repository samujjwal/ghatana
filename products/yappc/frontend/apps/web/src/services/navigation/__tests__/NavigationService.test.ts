import { describe, test, expect, beforeEach } from 'vitest';

import { NavigationService } from './NavigationService';

describe('NavigationService', () => {
  beforeEach(() => {
    NavigationService.clear();
  });

  describe('navigateTo', () => {
    test('should navigate to a canvas', () => {
      NavigationService.navigateTo('canvas-1');
      
      const state = NavigationService.getState();
      expect(state.currentCanvasId).toBe('canvas-1');
      expect(state.history).toContain('canvas-1');
    });

    test('should update history on navigation', () => {
      NavigationService.navigateTo('canvas-1');
      NavigationService.navigateTo('canvas-2');
      
      const state = NavigationService.getState();
      expect(state.history).toEqual(['root', 'canvas-1', 'canvas-2']);
      expect(state.historyIndex).toBe(2);
    });

    test('should support split view mode', () => {
      NavigationService.navigateTo('canvas-1');
      NavigationService.navigateTo('canvas-2', 'split');
      
      const state = NavigationService.getState();
      expect(state.splitView).toBeTruthy();
      expect(state.splitView?.leftCanvasId).toBe('canvas-1');
      expect(state.splitView?.rightCanvasId).toBe('canvas-2');
    });
  });

  describe('history navigation', () => {
    test('should go back in history', () => {
      NavigationService.navigateTo('canvas-1');
      NavigationService.navigateTo('canvas-2');
      
      const canGoBack = NavigationService.goBack();
      
      expect(canGoBack).toBe(true);
      expect(NavigationService.getState().currentCanvasId).toBe('canvas-1');
    });

    test('should go forward in history', () => {
      NavigationService.navigateTo('canvas-1');
      NavigationService.navigateTo('canvas-2');
      NavigationService.goBack();
      
      const canGoForward = NavigationService.goForward();
      
      expect(canGoForward).toBe(true);
      expect(NavigationService.getState().currentCanvasId).toBe('canvas-2');
    });

    test('should return false when cannot go back', () => {
      const canGoBack = NavigationService.goBack();
      expect(canGoBack).toBe(false);
    });
  });

  describe('cycle detection', () => {
    test('should detect simple cycle', () => {
      NavigationService.registerLink('canvas-1', 'el-1', {
        kind: 'canvas',
        targetId: 'canvas-2',
      });
      NavigationService.registerLink('canvas-2', 'el-2', {
        kind: 'canvas',
        targetId: 'canvas-1',
      });
      
      const result = NavigationService.detectCycle('canvas-1', 'canvas-2');
      
      // After registering the link back, navigating would create a cycle
      const reverseResult = NavigationService.detectCycle('canvas-2', 'canvas-1');
      expect(reverseResult.hasCycle).toBe(true);
    });

    test('should not detect cycle in linear path', () => {
      NavigationService.registerLink('canvas-1', 'el-1', {
        kind: 'canvas',
        targetId: 'canvas-2',
      });
      NavigationService.registerLink('canvas-2', 'el-2', {
        kind: 'canvas',
        targetId: 'canvas-3',
      });
      
      const result = NavigationService.detectCycle('canvas-1', 'canvas-3');
      expect(result.hasCycle).toBe(false);
    });
  });

  describe('deep linking', () => {
    test('should generate deep link', () => {
      const link = NavigationService.generateDeepLink({
        canvasId: 'canvas-1',
        elementId: 'el-1',
      });
      
      expect(link).toContain('canvas-1');
      expect(link).toContain('element=el-1');
    });

    test('should include viewport in deep link', () => {
      const link = NavigationService.generateDeepLink({
        canvasId: 'canvas-1',
        viewport: { x: 100, y: 200, zoom: 1.5 },
      });
      
      expect(link).toContain('x=100');
      expect(link).toContain('y=200');
      expect(link).toContain('zoom=1.5');
    });

    test('should parse deep link', () => {
      const url = '/canvas/canvas-1?element=el-1&x=100&y=200&zoom=1.5';
      const params = NavigationService.parseDeepLink(url);
      
      expect(params).toBeTruthy();
      expect(params?.canvasId).toBe('canvas-1');
      expect(params?.elementId).toBe('el-1');
      expect(params?.viewport).toEqual({ x: 100, y: 200, zoom: 1.5 });
    });
  });

  describe('breadcrumbs', () => {
    test('should build breadcrumbs', () => {
      NavigationService.registerNode({
        canvasId: 'root',
        title: 'Root',
      });
      NavigationService.registerNode({
        canvasId: 'canvas-1',
        title: 'Canvas 1',
        parentId: 'root',
      });
      
      NavigationService.navigateTo('canvas-1');
      
      const breadcrumbs = NavigationService.getBreadcrumbs();
      expect(breadcrumbs).toHaveLength(2);
      expect(breadcrumbs[0].title).toBe('Root');
      expect(breadcrumbs[1].title).toBe('Canvas 1');
    });
  });
});
