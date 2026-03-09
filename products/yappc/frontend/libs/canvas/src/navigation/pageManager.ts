/**
 * Multi-page Canvas Management & Deep Linking
 *
 * Provides multi-page document management with:
 * - Page CRUD operations with ordering
 * - Deep linking to specific nodes with highlighting
 * - Cross-diagram portal links with context preservation
 * - URL-based navigation and sharing
 *
 * @module navigation/pageManager
 */

/**
 *
 */
export interface Page {
  /** Unique page identifier */
  id: string;
  /** Page name/title */
  name: string;
  /** Page order (0-indexed) */
  order: number;
  /** Canvas state data */
  data: unknown;
  /** Thumbnail URL (optional) */
  thumbnail?: string;
  /** Page description */
  description?: string;
  /** Creation timestamp */
  createdAt: number;
  /** Last modified timestamp */
  updatedAt: number;
  /** Whether page is currently active */
  active: boolean;
}

/**
 *
 */
export interface DeepLink {
  /** Link identifier */
  id: string;
  /** Target page ID */
  pageId: string;
  /** Target node/element ID */
  elementId: string;
  /** Viewport position for the link */
  viewport?: {
    x: number;
    y: number;
    zoom: number;
  };
  /** Whether to highlight target element */
  highlight: boolean;
  /** Highlight duration (ms) */
  highlightDuration?: number;
  /** Optional context banner message */
  contextMessage?: string;
  /** Timestamp when link was created */
  createdAt: number;
}

/**
 *
 */
export interface PortalLink {
  /** Portal link identifier */
  id: string;
  /** Source page ID */
  sourcePageId: string;
  /** Source element ID (the portal) */
  sourceElementId: string;
  /** Target diagram/canvas ID */
  targetDiagramId: string;
  /** Target page ID (optional) */
  targetPageId?: string;
  /** Target element ID (optional, for focusing) */
  targetElementId?: string;
  /** Context banner message */
  contextMessage?: string;
  /** Creation timestamp */
  createdAt: number;
}

/**
 *
 */
export interface PageManagerState {
  /** All pages by ID */
  pages: Map<string, Page>;
  /** Page order array */
  pageOrder: string[];
  /** Current active page ID */
  activePageId: string | null;
  /** Deep links by ID */
  deepLinks: Map<string, DeepLink>;
  /** Portal links by ID */
  portalLinks: Map<string, PortalLink>;
  /** Navigation history */
  history: string[];
  /** Current position in history */
  historyIndex: number;
}

/**
 * Create empty page manager state
 */
export function createPageManagerState(): PageManagerState {
  return {
    pages: new Map(),
    pageOrder: [],
    activePageId: null,
    deepLinks: new Map(),
    portalLinks: new Map(),
    history: [],
    historyIndex: -1,
  };
}

/**
 * Create a new page
 */
export function createPage(
  state: PageManagerState,
  name: string,
  data: unknown,
  options?: {
    description?: string;
    thumbnail?: string;
    order?: number;
  }
): { state: PageManagerState; page: Page } {
  const page: Page = {
    id: `page-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    name,
    order: options?.order ?? state.pageOrder.length,
    data,
    thumbnail: options?.thumbnail,
    description: options?.description,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    active: false,
  };

  state.pages.set(page.id, page);
  
  // Insert at specified order or append
  if (options?.order !== undefined && options.order < state.pageOrder.length) {
    state.pageOrder.splice(options.order, 0, page.id);
    // Update order of subsequent pages
    updatePageOrders(state);
  } else {
    state.pageOrder.push(page.id);
  }

  // Set as active if it's the first page and add to history
  if (state.pages.size === 1) {
    page.active = true;
    state.activePageId = page.id;
    state.history = [page.id];
    state.historyIndex = 0;
  }

  return { state, page };
}

/**
 * Get page by ID
 */
export function getPage(state: PageManagerState, pageId: string): Page | null {
  return state.pages.get(pageId) || null;
}

/**
 * Get all pages in order
 */
export function getAllPages(state: PageManagerState): Page[] {
  return state.pageOrder
    .map((id) => state.pages.get(id))
    .filter((page): page is Page => page !== undefined);
}

/**
 * Update page
 */
export function updatePage(
  state: PageManagerState,
  pageId: string,
  updates: Partial<Pick<Page, 'name' | 'data' | 'description' | 'thumbnail'>>
): Page | null {
  const page = state.pages.get(pageId);
  if (!page) return null;

  Object.assign(page, updates, { updatedAt: Date.now() });
  return page;
}

/**
 * Delete page
 */
export function deletePage(state: PageManagerState, pageId: string): boolean {
  const page = state.pages.get(pageId);
  if (!page) return false;

  // Remove from pages map
  state.pages.delete(pageId);

  // Remove from order
  const index = state.pageOrder.indexOf(pageId);
  if (index !== -1) {
    state.pageOrder.splice(index, 1);
  }

  // Update orders
  updatePageOrders(state);

  // Update active page if needed
  if (state.activePageId === pageId) {
    if (state.pageOrder.length > 0) {
      // Set next page as active, or previous if at end
      const newActiveId = state.pageOrder[Math.min(index, state.pageOrder.length - 1)];
      setActivePage(state, newActiveId);
    } else {
      state.activePageId = null;
    }
  }

  // Clean up related deep links
  for (const [linkId, link] of state.deepLinks) {
    if (link.pageId === pageId) {
      state.deepLinks.delete(linkId);
    }
  }

  // Clean up related portal links
  for (const [linkId, link] of state.portalLinks) {
    if (link.sourcePageId === pageId || link.targetPageId === pageId) {
      state.portalLinks.delete(linkId);
    }
  }

  return true;
}

/**
 * Reorder pages
 */
export function reorderPages(
  state: PageManagerState,
  pageId: string,
  newIndex: number
): boolean {
  const currentIndex = state.pageOrder.indexOf(pageId);
  if (currentIndex === -1) return false;

  // Remove from current position
  state.pageOrder.splice(currentIndex, 1);

  // Insert at new position
  state.pageOrder.splice(newIndex, 0, pageId);

  // Update order property of all pages
  updatePageOrders(state);

  return true;
}

/**
 * Set active page
 */
export function setActivePage(state: PageManagerState, pageId: string): boolean {
  const page = state.pages.get(pageId);
  if (!page) return false;

  // Deactivate current active page
  if (state.activePageId) {
    const currentActive = state.pages.get(state.activePageId);
    if (currentActive) {
      currentActive.active = false;
    }
  }

  // Activate new page
  page.active = true;
  state.activePageId = pageId;

  // Add to history
  addToHistory(state, pageId);

  return true;
}

/**
 * Get active page
 */
export function getActivePage(state: PageManagerState): Page | null {
  if (!state.activePageId) return null;
  return state.pages.get(state.activePageId) || null;
}

/**
 * Navigate to next page
 */
export function nextPage(state: PageManagerState): boolean {
  if (!state.activePageId) return false;

  const currentIndex = state.pageOrder.indexOf(state.activePageId);
  if (currentIndex === -1 || currentIndex === state.pageOrder.length - 1) {
    return false; // Already at last page
  }

  const nextPageId = state.pageOrder[currentIndex + 1];
  return setActivePage(state, nextPageId);
}

/**
 * Navigate to previous page
 */
export function previousPage(state: PageManagerState): boolean {
  if (!state.activePageId) return false;

  const currentIndex = state.pageOrder.indexOf(state.activePageId);
  if (currentIndex <= 0) {
    return false; // Already at first page
  }

  const prevPageId = state.pageOrder[currentIndex - 1];
  return setActivePage(state, prevPageId);
}

/**
 * Duplicate page
 */
export function duplicatePage(
  state: PageManagerState,
  pageId: string,
  newName?: string
): { state: PageManagerState; page: Page } | null {
  const original = state.pages.get(pageId);
  if (!original) return null;

  const name = newName || `${original.name} (Copy)`;
  const order = original.order + 1;

  return createPage(state, name, original.data, {
    description: original.description,
    thumbnail: original.thumbnail,
    order,
  });
}

/**
 * Create deep link to element
 */
export function createDeepLink(
  state: PageManagerState,
  pageId: string,
  elementId: string,
  options?: {
    viewport?: { x: number; y: number; zoom: number };
    highlight?: boolean;
    highlightDuration?: number;
    contextMessage?: string;
  }
): DeepLink | null {
  const page = state.pages.get(pageId);
  if (!page) return null;

  const link: DeepLink = {
    id: `link-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    pageId,
    elementId,
    viewport: options?.viewport,
    highlight: options?.highlight ?? true,
    highlightDuration: options?.highlightDuration ?? 2000,
    contextMessage: options?.contextMessage,
    createdAt: Date.now(),
  };

  state.deepLinks.set(link.id, link);
  return link;
}

/**
 * Get deep link by ID
 */
export function getDeepLink(state: PageManagerState, linkId: string): DeepLink | null {
  return state.deepLinks.get(linkId) || null;
}

/**
 * Navigate to deep link
 */
export function navigateToDeepLink(
  state: PageManagerState,
  linkId: string,
  callbacks: {
    setPage: (pageId: string) => void;
    setViewport?: (x: number, y: number, zoom: number) => void;
    highlightElement?: (elementId: string, duration: number) => void;
    showContext?: (message: string) => void;
  }
): boolean {
  const link = state.deepLinks.get(linkId);
  if (!link) return false;

  // Navigate to page
  setActivePage(state, link.pageId);
  callbacks.setPage(link.pageId);

  // Set viewport if specified
  if (link.viewport && callbacks.setViewport) {
    callbacks.setViewport(link.viewport.x, link.viewport.y, link.viewport.zoom);
  }

  // Highlight element if enabled
  if (link.highlight && callbacks.highlightElement) {
    callbacks.highlightElement(link.elementId, link.highlightDuration || 2000);
  }

  // Show context message if provided
  if (link.contextMessage && callbacks.showContext) {
    callbacks.showContext(link.contextMessage);
  }

  return true;
}

/**
 * Delete deep link
 */
export function deleteDeepLink(state: PageManagerState, linkId: string): boolean {
  return state.deepLinks.delete(linkId);
}

/**
 * Get all deep links for a page
 */
export function getDeepLinksForPage(state: PageManagerState, pageId: string): DeepLink[] {
  return Array.from(state.deepLinks.values()).filter((link) => link.pageId === pageId);
}

/**
 * Create portal link between diagrams
 */
export function createPortalLink(
  state: PageManagerState,
  sourcePageId: string,
  sourceElementId: string,
  targetDiagramId: string,
  options?: {
    targetPageId?: string;
    targetElementId?: string;
    contextMessage?: string;
  }
): PortalLink | null {
  const page = state.pages.get(sourcePageId);
  if (!page) return null;

  const link: PortalLink = {
    id: `portal-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    sourcePageId,
    sourceElementId,
    targetDiagramId,
    targetPageId: options?.targetPageId,
    targetElementId: options?.targetElementId,
    contextMessage: options?.contextMessage,
    createdAt: Date.now(),
  };

  state.portalLinks.set(link.id, link);
  return link;
}

/**
 * Get portal link by ID
 */
export function getPortalLink(state: PageManagerState, linkId: string): PortalLink | null {
  return state.portalLinks.get(linkId) || null;
}

/**
 * Get portal link by source element
 */
export function getPortalLinkByElement(
  state: PageManagerState,
  elementId: string
): PortalLink | null {
  return (
    Array.from(state.portalLinks.values()).find(
      (link) => link.sourceElementId === elementId
    ) || null
  );
}

/**
 * Navigate to portal link
 */
export function navigateToPortalLink(
  state: PageManagerState,
  linkId: string,
  callbacks: {
    loadDiagram: (diagramId: string) => Promise<void>;
    setPage?: (pageId: string) => void;
    focusElement?: (elementId: string) => void;
    showContext?: (message: string) => void;
  }
): Promise<boolean> {
  const link = state.portalLinks.get(linkId);
  if (!link) return Promise.resolve(false);

  return (async () => {
    try {
      // Load target diagram
      await callbacks.loadDiagram(link.targetDiagramId);

      // Navigate to specific page if specified
      if (link.targetPageId && callbacks.setPage) {
        callbacks.setPage(link.targetPageId);
      }

      // Focus target element if specified
      if (link.targetElementId && callbacks.focusElement) {
        callbacks.focusElement(link.targetElementId);
      }

      // Show context message
      if (link.contextMessage && callbacks.showContext) {
        callbacks.showContext(link.contextMessage);
      }

      return true;
    } catch (error) {
      console.error('Failed to navigate to portal link:', error);
      return false;
    }
  })();
}

/**
 * Delete portal link
 */
export function deletePortalLink(state: PageManagerState, linkId: string): boolean {
  return state.portalLinks.delete(linkId);
}

/**
 * Get all portal links from a page
 */
export function getPortalLinksFromPage(
  state: PageManagerState,
  pageId: string
): PortalLink[] {
  return Array.from(state.portalLinks.values()).filter(
    (link) => link.sourcePageId === pageId
  );
}

/**
 * Generate shareable URL for deep link
 */
export function generateDeepLinkURL(
  state: PageManagerState,
  linkId: string,
  baseUrl: string
): string | null {
  const link = state.deepLinks.get(linkId);
  if (!link) return null;

  const params = new URLSearchParams({
    page: link.pageId,
    element: link.elementId,
  });

  if (link.viewport) {
    params.set('x', link.viewport.x.toString());
    params.set('y', link.viewport.y.toString());
    params.set('zoom', link.viewport.zoom.toString());
  }

  if (link.highlight) {
    params.set('highlight', 'true');
  }

  return `${baseUrl}?${params.toString()}`;
}

/**
 * Parse deep link from URL
 */
export function parseDeepLinkFromURL(url: string): {
  pageId: string;
  elementId: string;
  viewport?: { x: number; y: number; zoom: number };
  highlight: boolean;
} | null {
  try {
    const urlObj = new URL(url);
    const params = urlObj.searchParams;

    const pageId = params.get('page');
    const elementId = params.get('element');

    if (!pageId || !elementId) return null;

    const result: {
      pageId: string;
      elementId: string;
      viewport?: { x: number; y: number; zoom: number };
      highlight: boolean;
    } = {
      pageId,
      elementId,
      highlight: params.get('highlight') === 'true',
    };

    const x = params.get('x');
    const y = params.get('y');
    const zoom = params.get('zoom');

    if (x && y && zoom) {
      result.viewport = {
        x: parseFloat(x),
        y: parseFloat(y),
        zoom: parseFloat(zoom),
      };
    }

    return result;
  } catch {
    return null;
  }
}

/**
 * Navigation history: go back
 */
export function historyBack(state: PageManagerState): boolean {
  if (state.historyIndex <= 0) return false;

  state.historyIndex--;
  const pageId = state.history[state.historyIndex];

  if (!pageId || !state.pages.has(pageId)) return false;

  // Set page without adding to history
  const page = state.pages.get(pageId)!;
  
  // Deactivate current
  if (state.activePageId) {
    const current = state.pages.get(state.activePageId);
    if (current) current.active = false;
  }

  // Activate target
  page.active = true;
  state.activePageId = pageId;

  return true;
}

/**
 * Navigation history: go forward
 */
export function historyForward(state: PageManagerState): boolean {
  if (state.historyIndex >= state.history.length - 1) return false;

  state.historyIndex++;
  const pageId = state.history[state.historyIndex];

  if (!pageId || !state.pages.has(pageId)) return false;

  // Set page without adding to history
  const page = state.pages.get(pageId)!;
  
  // Deactivate current
  if (state.activePageId) {
    const current = state.pages.get(state.activePageId);
    if (current) current.active = false;
  }

  // Activate target
  page.active = true;
  state.activePageId = pageId;

  return true;
}

/**
 * Check if can go back in history
 */
export function canGoBack(state: PageManagerState): boolean {
  return state.historyIndex > 0;
}

/**
 * Check if can go forward in history
 */
export function canGoForward(state: PageManagerState): boolean {
  return state.historyIndex < state.history.length - 1;
}

/**
 * Get page count
 */
export function getPageCount(state: PageManagerState): number {
  return state.pageOrder.length;
}

/**
 * Search pages by name
 */
export function searchPages(state: PageManagerState, query: string): Page[] {
  const lowerQuery = query.toLowerCase();
  return getAllPages(state).filter((page) =>
    page.name.toLowerCase().includes(lowerQuery) ||
    page.description?.toLowerCase().includes(lowerQuery)
  );
}

// Helper functions

/**
 * Update order property of all pages based on pageOrder array
 */
function updatePageOrders(state: PageManagerState): void {
  state.pageOrder.forEach((pageId, index) => {
    const page = state.pages.get(pageId);
    if (page) {
      page.order = index;
    }
  });
}

/**
 * Add page to navigation history
 */
function addToHistory(state: PageManagerState, pageId: string): void {
  // Truncate forward history if we're not at the end
  if (state.historyIndex < state.history.length - 1) {
    state.history.splice(state.historyIndex + 1);
  }

  // Don't add if it's the same as current
  if (state.history[state.historyIndex] === pageId) {
    return;
  }

  state.history.push(pageId);
  state.historyIndex = state.history.length - 1;
}
