/**
 * Navigation Service - Manages portal navigation and canvas hierarchy
 */

import type {
  NavigationState,
  NavigationNode,
  BreadcrumbItem,
  PortalLink,
  LinkIndex,
  CycleDetectionResult,
  DeepLinkParams,
} from './types';

/**
 *
 */
class NavigationServiceClass {
  private state: NavigationState = {
    currentCanvasId: 'root',
    history: ['root'],
    historyIndex: 0,
    breadcrumbs: [],
  };

  private nodes: Map<string, NavigationNode> = new Map();
  private linkIndex: Map<string, LinkIndex> = new Map();
  private listeners: Set<(state: NavigationState) => void> = new Set();

  /**
   * Navigate to a canvas
   */
  navigateTo(canvasId: string, mode: 'replace' | 'split' = 'replace'): void {
    // Check for cycles
    const cycleCheck = this.detectCycle(this.state.currentCanvasId, canvasId);
    if (cycleCheck.hasCycle) {
      console.warn('Navigation cycle detected:', cycleCheck.cyclePath);
      return;
    }

    if (mode === 'split') {
      this.state.splitView = {
        leftCanvasId: this.state.currentCanvasId,
        rightCanvasId: canvasId,
      };
    } else {
      // Update history
      const newHistory = this.state.history.slice(0, this.state.historyIndex + 1);
      newHistory.push(canvasId);

      this.state = {
        ...this.state,
        currentCanvasId: canvasId,
        history: newHistory,
        historyIndex: newHistory.length - 1,
        breadcrumbs: this.buildBreadcrumbs(canvasId),
        splitView: undefined,
      };
    }

    this.notifyListeners();
  }

  /**
   * Navigate back in history
   */
  goBack(): boolean {
    if (this.state.historyIndex > 0) {
      const newIndex = this.state.historyIndex - 1;
      const canvasId = this.state.history[newIndex];

      this.state = {
        ...this.state,
        currentCanvasId: canvasId,
        historyIndex: newIndex,
        breadcrumbs: this.buildBreadcrumbs(canvasId),
      };

      this.notifyListeners();
      return true;
    }

    return false;
  }

  /**
   * Navigate forward in history
   */
  goForward(): boolean {
    if (this.state.historyIndex < this.state.history.length - 1) {
      const newIndex = this.state.historyIndex + 1;
      const canvasId = this.state.history[newIndex];

      this.state = {
        ...this.state,
        currentCanvasId: canvasId,
        historyIndex: newIndex,
        breadcrumbs: this.buildBreadcrumbs(canvasId),
      };

      this.notifyListeners();
      return true;
    }

    return false;
  }

  /**
   * Close split view
   */
  closeSplitView(): void {
    this.state = {
      ...this.state,
      splitView: undefined,
    };

    this.notifyListeners();
  }

  /**
   * Register a navigation node
   */
  registerNode(node: NavigationNode): void {
    this.nodes.set(node.canvasId, node);
  }

  /**
   * Register a portal link
   */
  registerLink(canvasId: string, elementId: string, link: PortalLink): void {
    if (!this.linkIndex.has(canvasId)) {
      this.linkIndex.set(canvasId, {
        canvasId,
        outgoing: new Map(),
        incoming: new Map(),
      });
    }

    const index = this.linkIndex.get(canvasId)!;
    
    if (!index.outgoing.has(elementId)) {
      index.outgoing.set(elementId, []);
    }
    
    index.outgoing.get(elementId)!.push(link);

    // Update incoming links
    if (link.kind === 'canvas' && link.targetId) {
      if (!this.linkIndex.has(link.targetId)) {
        this.linkIndex.set(link.targetId, {
          canvasId: link.targetId,
          outgoing: new Map(),
          incoming: new Map(),
        });
      }

      const targetIndex = this.linkIndex.get(link.targetId)!;
      if (!targetIndex.incoming.has(canvasId)) {
        targetIndex.incoming.set(canvasId, []);
      }
      targetIndex.incoming.get(canvasId)!.push(elementId);
    }
  }

  /**
   * Detect navigation cycles
   */
  detectCycle(fromCanvasId: string, toCanvasId: string): CycleDetectionResult {
    // Use a proper cycle detection algorithm using DFS and recursion stack
    const visited = new Set<string>();
    const recursionStack = new Set<string>();
    const path: string[] = [];

    const dfs = (currentId: string): boolean => {
      if (recursionStack.has(currentId)) {
        // Found a cycle - currentId is already in the current path
        const cycleStart = path.indexOf(currentId);
        if (cycleStart !== -1) {
          path.push(currentId); // Complete the cycle
          return true;
        }
      }

      if (visited.has(currentId)) {
        return false;
      }

      visited.add(currentId);
      recursionStack.add(currentId);
      path.push(currentId);

      const index = this.linkIndex.get(currentId);
      if (index) {
        for (const links of index.outgoing.values()) {
          for (const link of links) {
            if (link.kind === 'canvas' && link.targetId) {
              if (dfs(link.targetId)) {
                return true;
              }
            }
          }
        }
      }

      recursionStack.delete(currentId);
      path.pop();
      return false;
    };

    // Simple case: direct self-loop
    if (fromCanvasId === toCanvasId) {
      return { hasCycle: true, cyclePath: [fromCanvasId, toCanvasId] };
    }

    // Check if there would be a cycle if we add this navigation
    // We simulate adding the navigation link and then check for cycles
    const fromIndex = this.linkIndex.get(fromCanvasId);
    if (fromIndex) {
      // Temporarily add the proposed link
      const tempLink: PortalLink = { kind: 'canvas', targetId: toCanvasId };
      const tempElementId = '__cycle_check__';
      
      if (!fromIndex.outgoing.has(tempElementId)) {
        fromIndex.outgoing.set(tempElementId, []);
      }
      fromIndex.outgoing.get(tempElementId)!.push(tempLink);

      // Check for cycles starting from fromCanvasId
      const hasCycle = dfs(fromCanvasId);

      // Clean up temporary link
      const links = fromIndex.outgoing.get(tempElementId);
      if (links) {
        const linkIndex = links.indexOf(tempLink);
        if (linkIndex !== -1) {
          links.splice(linkIndex, 1);
        }
        if (links.length === 0) {
          fromIndex.outgoing.delete(tempElementId);
        }
      }

      return {
        hasCycle,
        cyclePath: hasCycle ? [...path] : undefined,
      };
    }

    return { hasCycle: false };
  }

  /**
   * Generate deep link
   */
  generateDeepLink(params: DeepLinkParams): string {
    const { canvasId, elementId, viewport } = params;
    const base = `/canvas/${canvasId}`;

    const queryParams = new URLSearchParams();
    if (elementId) queryParams.set('element', elementId);
    if (viewport) {
      queryParams.set('x', viewport.x.toString());
      queryParams.set('y', viewport.y.toString());
      queryParams.set('zoom', viewport.zoom.toString());
    }

    const query = queryParams.toString();
    return query ? `${base}?${query}` : base;
  }

  /**
   * Parse deep link
   */
  parseDeepLink(url: string): DeepLinkParams | null {
    try {
      const urlObj = new URL(url, window.location.origin);
      const pathParts = urlObj.pathname.split('/');
      const canvasId = pathParts[pathParts.length - 1];

      if (!canvasId) return null;

      const params: DeepLinkParams = { canvasId };

      const elementId = urlObj.searchParams.get('element');
      if (elementId) params.elementId = elementId;

      const x = urlObj.searchParams.get('x');
      const y = urlObj.searchParams.get('y');
      const zoom = urlObj.searchParams.get('zoom');

      if (x && y && zoom) {
        params.viewport = {
          x: parseFloat(x),
          y: parseFloat(y),
          zoom: parseFloat(zoom),
        };
      }

      return params;
    } catch {
      return null;
    }
  }

  /**
   * Get current state
   */
  getState(): NavigationState {
    return { ...this.state };
  }

  /**
   * Subscribe to state changes
   */
  subscribe(listener: (state: NavigationState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Get breadcrumbs for current canvas
   */
  getBreadcrumbs(): BreadcrumbItem[] {
    return [...this.state.breadcrumbs];
  }

  /**
   *
   */
  private buildBreadcrumbs(canvasId: string): BreadcrumbItem[] {
    const breadcrumbs: BreadcrumbItem[] = [];
    let currentId: string | undefined = canvasId;

    while (currentId) {
      const node = this.nodes.get(currentId);
      if (node) {
        breadcrumbs.unshift({
          canvasId: node.canvasId,
          title: node.title || node.canvasId,
          path: this.generateDeepLink({ canvasId: node.canvasId }),
        });
        currentId = node.parentId;
      } else {
        break;
      }
    }

    return breadcrumbs;
  }

  /**
   *
   */
  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener(this.state));
  }

  /**
   * Clear all navigation data
   */
  clear(): void {
    this.state = {
      currentCanvasId: 'root',
      history: ['root'],
      historyIndex: 0,
      breadcrumbs: [],
    };
    this.nodes.clear();
    this.linkIndex.clear();
  }
}

// Singleton instance
export const NavigationService = new NavigationServiceClass();
