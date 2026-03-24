/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi } from 'vitest';

import {
  createPageManagerState,
  createPage,
  getPage,
  getAllPages,
  updatePage,
  deletePage,
  reorderPages,
  setActivePage,
  getActivePage,
  nextPage,
  previousPage,
  duplicatePage,
  createDeepLink,
  getDeepLink,
  navigateToDeepLink,
  deleteDeepLink,
  getDeepLinksForPage,
  createPortalLink,
  getPortalLink,
  getPortalLinkByElement,
  navigateToPortalLink,
  deletePortalLink,
  getPortalLinksFromPage,
  generateDeepLinkURL,
  parseDeepLinkFromURL,
  historyBack,
  historyForward,
  canGoBack,
  canGoForward,
  getPageCount,
  searchPages,
} from '../pageManager';

describe('pageManager', () => {
  describe('State Creation', () => {
    it('should create empty page manager state', () => {
      const state = createPageManagerState();

      expect(state.pages.size).toBe(0);
      expect(state.pageOrder).toEqual([]);
      expect(state.activePageId).toBeNull();
      expect(state.deepLinks.size).toBe(0);
      expect(state.portalLinks.size).toBe(0);
      expect(state.history).toEqual([]);
      expect(state.historyIndex).toBe(-1);
    });
  });

  describe('Page CRUD', () => {
    it('should create page', () => {
      const state = createPageManagerState();
      const data = { nodes: [], edges: [] };

      const result = createPage(state, 'Page 1', data);

      expect(result.page.name).toBe('Page 1');
      expect(result.page.order).toBe(0);
      expect(result.page.data).toBe(data);
      expect(result.page.active).toBe(true); // First page is active
      expect(state.pages.size).toBe(1);
      expect(state.pageOrder).toHaveLength(1);
      expect(state.activePageId).toBe(result.page.id);
    });

    it('should create page with options', () => {
      const state = createPageManagerState();
      
      const result = createPage(state, 'Page 1', {}, {
        description: 'Test page',
        thumbnail: 'thumb.png',
      });

      expect(result.page.description).toBe('Test page');
      expect(result.page.thumbnail).toBe('thumb.png');
    });

    it('should not set second page as active', () => {
      const state = createPageManagerState();

      createPage(state, 'Page 1', {});
      const result2 = createPage(state, 'Page 2', {});

      expect(result2.page.active).toBe(false);
    });

    it('should get page by ID', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});

      const retrieved = getPage(state, page.id);

      expect(retrieved).toBe(page);
    });

    it('should return null for non-existent page', () => {
      const state = createPageManagerState();
      const retrieved = getPage(state, 'nonexistent');

      expect(retrieved).toBeNull();
    });

    it('should get all pages in order', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});
      const { page: page3 } = createPage(state, 'Page 3', {});

      const all = getAllPages(state);

      expect(all).toHaveLength(3);
      expect(all[0]).toBe(page1);
      expect(all[1]).toBe(page2);
      expect(all[2]).toBe(page3);
    });

    it('should update page', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});

      const updated = updatePage(state, page.id, {
        name: 'Updated',
        description: 'New description',
      });

      expect(updated?.name).toBe('Updated');
      expect(updated?.description).toBe('New description');
      expect(updated?.updatedAt).toBeGreaterThanOrEqual(page.createdAt);
    });

    it('should return null when updating non-existent page', () => {
      const state = createPageManagerState();
      const updated = updatePage(state, 'nonexistent', { name: 'Test' });

      expect(updated).toBeNull();
    });

    it('should delete page', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      createPage(state, 'Page 2', {});

      const deleted = deletePage(state, page.id);

      expect(deleted).toBe(true);
      expect(state.pages.size).toBe(1);
      expect(state.pageOrder).toHaveLength(1);
    });

    it('should update active page when deleting active', () => {
      const state = createPageManagerState();
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      deletePage(state, page1.id);

      expect(state.activePageId).toBe(page2.id);
      expect(page2.active).toBe(true);
    });

    it('should update page orders after deletion', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});
      const { page: page3 } = createPage(state, 'Page 3', {});

      deletePage(state, page2.id);

      expect(page3.order).toBe(1);
    });

    it('should return false when deleting non-existent page', () => {
      const state = createPageManagerState();
      const deleted = deletePage(state, 'nonexistent');

      expect(deleted).toBe(false);
    });
  });

  describe('Page Ordering', () => {
    it('should reorder pages', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});
      const { page: page3 } = createPage(state, 'Page 3', {});

      reorderPages(state, page3.id, 0);

      expect(state.pageOrder[0]).toBe(page3.id);
      expect(page3.order).toBe(0);
      expect(page1.order).toBe(1);
      expect(page2.order).toBe(2);
    });

    it('should return false when reordering non-existent page', () => {
      const state = createPageManagerState();
      const result = reorderPages(state, 'nonexistent', 0);

      expect(result).toBe(false);
    });

    it('should create page at specific order', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});
      const { page: page3 } = createPage(state, 'Page 3', {}, { order: 1 });

      expect(state.pageOrder[0]).toBe(page1.id);
      expect(state.pageOrder[1]).toBe(page3.id);
      expect(state.pageOrder[2]).toBe(page2.id);
      expect(page3.order).toBe(1);
      expect(page2.order).toBe(2);
    });
  });

  describe('Active Page Management', () => {
    it('should set active page', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);

      expect(state.activePageId).toBe(page2.id);
      expect(page2.active).toBe(true);
      expect(page1.active).toBe(false);
    });

    it('should return false when setting non-existent page as active', () => {
      const state = createPageManagerState();
      const result = setActivePage(state, 'nonexistent');

      expect(result).toBe(false);
    });

    it('should get active page', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);
      const active = getActivePage(state);

      expect(active).toBe(page2);
    });

    it('should return null when no active page', () => {
      const state = createPageManagerState();
      const active = getActivePage(state);

      expect(active).toBeNull();
    });

    it('should navigate to next page', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});
      createPage(state, 'Page 3', {});

      nextPage(state);

      expect(state.activePageId).toBe(page2.id);
    });

    it('should not navigate past last page', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);
      const result = nextPage(state);

      expect(result).toBe(false);
      expect(state.activePageId).toBe(page2.id);
    });

    it('should navigate to previous page', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);
      previousPage(state);

      expect(state.activePageId).toBe(page1.id);
    });

    it('should not navigate before first page', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});

      const result = previousPage(state);

      expect(result).toBe(false);
      expect(state.activePageId).toBe(page1.id);
    });
  });

  describe('Page Duplication', () => {
    it('should duplicate page', () => {
      const state = createPageManagerState();
      const data = { nodes: ['a', 'b'] };
      
      const { page: original } = createPage(state, 'Original', data, {
        description: 'Test',
        thumbnail: 'thumb.png',
      });

      const result = duplicatePage(state, original.id);

      expect(result).not.toBeNull();
      expect(result!.page.name).toBe('Original (Copy)');
      expect(result!.page.data).toBe(data);
      expect(result!.page.description).toBe('Test');
      expect(result!.page.order).toBe(1);
    });

    it('should duplicate with custom name', () => {
      const state = createPageManagerState();
      
      const { page: original } = createPage(state, 'Original', {});
      const result = duplicatePage(state, original.id, 'New Name');

      expect(result!.page.name).toBe('New Name');
    });

    it('should return null when duplicating non-existent page', () => {
      const state = createPageManagerState();
      const result = duplicatePage(state, 'nonexistent');

      expect(result).toBeNull();
    });
  });

  describe('Deep Links', () => {
    it('should create deep link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});

      const link = createDeepLink(state, page.id, 'element-1');

      expect(link).not.toBeNull();
      expect(link!.pageId).toBe(page.id);
      expect(link!.elementId).toBe('element-1');
      expect(link!.highlight).toBe(true);
      expect(state.deepLinks.size).toBe(1);
    });

    it('should create deep link with options', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});

      const link = createDeepLink(state, page.id, 'element-1', {
        viewport: { x: 100, y: 200, zoom: 1.5 },
        highlight: false,
        highlightDuration: 5000,
        contextMessage: 'Check this out',
      });

      expect(link!.viewport).toEqual({ x: 100, y: 200, zoom: 1.5 });
      expect(link!.highlight).toBe(false);
      expect(link!.highlightDuration).toBe(5000);
      expect(link!.contextMessage).toBe('Check this out');
    });

    it('should return null when creating link for non-existent page', () => {
      const state = createPageManagerState();
      const link = createDeepLink(state, 'nonexistent', 'element-1');

      expect(link).toBeNull();
    });

    it('should get deep link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createDeepLink(state, page.id, 'element-1')!;

      const retrieved = getDeepLink(state, link.id);

      expect(retrieved).toBe(link);
    });

    it('should navigate to deep link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createDeepLink(state, page.id, 'element-1', {
        viewport: { x: 100, y: 200, zoom: 1.5 },
        contextMessage: 'Test',
      })!;

      const setPage = vi.fn();
      const setViewport = vi.fn();
      const highlightElement = vi.fn();
      const showContext = vi.fn();

      const result = navigateToDeepLink(state, link.id, {
        setPage,
        setViewport,
        highlightElement,
        showContext,
      });

      expect(result).toBe(true);
      expect(setPage).toHaveBeenCalledWith(page.id);
      expect(setViewport).toHaveBeenCalledWith(100, 200, 1.5);
      expect(highlightElement).toHaveBeenCalledWith('element-1', 2000);
      expect(showContext).toHaveBeenCalledWith('Test');
    });

    it('should delete deep link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createDeepLink(state, page.id, 'element-1')!;

      const deleted = deleteDeepLink(state, link.id);

      expect(deleted).toBe(true);
      expect(state.deepLinks.size).toBe(0);
    });

    it('should get deep links for page', () => {
      const state = createPageManagerState();
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      createDeepLink(state, page1.id, 'element-1');
      createDeepLink(state, page1.id, 'element-2');
      createDeepLink(state, page2.id, 'element-3');

      const links = getDeepLinksForPage(state, page1.id);

      expect(links).toHaveLength(2);
    });

    it('should clean up deep links when deleting page', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      createPage(state, 'Page 2', {});

      createDeepLink(state, page.id, 'element-1');
      createDeepLink(state, page.id, 'element-2');

      deletePage(state, page.id);

      expect(state.deepLinks.size).toBe(0);
    });
  });

  describe('Portal Links', () => {
    it('should create portal link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});

      const link = createPortalLink(
        state,
        page.id,
        'portal-element',
        'target-diagram'
      );

      expect(link).not.toBeNull();
      expect(link!.sourcePageId).toBe(page.id);
      expect(link!.sourceElementId).toBe('portal-element');
      expect(link!.targetDiagramId).toBe('target-diagram');
      expect(state.portalLinks.size).toBe(1);
    });

    it('should create portal link with options', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});

      const link = createPortalLink(
        state,
        page.id,
        'portal-element',
        'target-diagram',
        {
          targetPageId: 'target-page',
          targetElementId: 'target-element',
          contextMessage: 'Opening related diagram',
        }
      );

      expect(link!.targetPageId).toBe('target-page');
      expect(link!.targetElementId).toBe('target-element');
      expect(link!.contextMessage).toBe('Opening related diagram');
    });

    it('should get portal link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createPortalLink(state, page.id, 'portal', 'target')!;

      const retrieved = getPortalLink(state, link.id);

      expect(retrieved).toBe(link);
    });

    it('should get portal link by element', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createPortalLink(state, page.id, 'portal-1', 'target')!;

      const retrieved = getPortalLinkByElement(state, 'portal-1');

      expect(retrieved).toBe(link);
    });

    it('should navigate to portal link', async () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createPortalLink(state, page.id, 'portal', 'target', {
        targetPageId: 'target-page',
        targetElementId: 'target-element',
        contextMessage: 'Test',
      })!;

      const loadDiagram = vi.fn(async () => {});
      const setPage = vi.fn();
      const focusElement = vi.fn();
      const showContext = vi.fn();

      const result = await navigateToPortalLink(state, link.id, {
        loadDiagram,
        setPage,
        focusElement,
        showContext,
      });

      expect(result).toBe(true);
      expect(loadDiagram).toHaveBeenCalledWith('target');
      expect(setPage).toHaveBeenCalledWith('target-page');
      expect(focusElement).toHaveBeenCalledWith('target-element');
      expect(showContext).toHaveBeenCalledWith('Test');
    });

    it('should delete portal link', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createPortalLink(state, page.id, 'portal', 'target')!;

      const deleted = deletePortalLink(state, link.id);

      expect(deleted).toBe(true);
      expect(state.portalLinks.size).toBe(0);
    });

    it('should get portal links from page', () => {
      const state = createPageManagerState();
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      createPortalLink(state, page1.id, 'portal-1', 'target-1');
      createPortalLink(state, page1.id, 'portal-2', 'target-2');
      createPortalLink(state, page2.id, 'portal-3', 'target-3');

      const links = getPortalLinksFromPage(state, page1.id);

      expect(links).toHaveLength(2);
    });
  });

  describe('URL Generation & Parsing', () => {
    it('should generate deep link URL', () => {
      const state = createPageManagerState();
      const { page } = createPage(state, 'Page 1', {});
      const link = createDeepLink(state, page.id, 'element-1', {
        viewport: { x: 100, y: 200, zoom: 1.5 },
      })!;

      const url = generateDeepLinkURL(state, link.id, 'https://example.com/canvas');

      expect(url).toContain('page=');
      expect(url).toContain('element=element-1');
      expect(url).toContain('x=100');
      expect(url).toContain('y=200');
      expect(url).toContain('zoom=1.5');
      expect(url).toContain('highlight=true');
    });

    it('should parse deep link from URL', () => {
      const url = 'https://example.com/canvas?page=page-1&element=element-1&x=100&y=200&zoom=1.5&highlight=true';
      
      const parsed = parseDeepLinkFromURL(url);

      expect(parsed).not.toBeNull();
      expect(parsed!.pageId).toBe('page-1');
      expect(parsed!.elementId).toBe('element-1');
      expect(parsed!.viewport).toEqual({ x: 100, y: 200, zoom: 1.5 });
      expect(parsed!.highlight).toBe(true);
    });

    it('should parse minimal deep link URL', () => {
      const url = 'https://example.com/canvas?page=page-1&element=element-1';
      
      const parsed = parseDeepLinkFromURL(url);

      expect(parsed!.pageId).toBe('page-1');
      expect(parsed!.elementId).toBe('element-1');
      expect(parsed!.viewport).toBeUndefined();
      expect(parsed!.highlight).toBe(false);
    });

    it('should return null for invalid URL', () => {
      const parsed = parseDeepLinkFromURL('invalid-url');

      expect(parsed).toBeNull();
    });

    it('should return null for URL without required params', () => {
      const parsed = parseDeepLinkFromURL('https://example.com/canvas?page=page-1');

      expect(parsed).toBeNull();
    });
  });

  describe('Navigation History', () => {
    it('should add to history when setting active page', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);

      expect(state.history).toEqual([page1.id, page2.id]);
      expect(state.historyIndex).toBe(1);
    });

    it('should go back in history', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);
      historyBack(state);

      expect(state.activePageId).toBe(page1.id);
      expect(state.historyIndex).toBe(0);
    });

    it('should go forward in history', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);
      historyBack(state);
      historyForward(state);

      expect(state.activePageId).toBe(page2.id);
      expect(state.historyIndex).toBe(1);
    });

    it('should truncate forward history', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});
      const { page: page3 } = createPage(state, 'Page 3', {});

      setActivePage(state, page2.id);
      setActivePage(state, page3.id);
      historyBack(state);
      setActivePage(state, page1.id);

      expect(state.history).toEqual([page1.id, page2.id, page1.id]);
    });

    it('should check if can go back', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      expect(canGoBack(state)).toBe(false);

      setActivePage(state, page2.id);
      expect(canGoBack(state)).toBe(true);
    });

    it('should check if can go forward', () => {
      const state = createPageManagerState();
      
      const { page: page1 } = createPage(state, 'Page 1', {});
      const { page: page2 } = createPage(state, 'Page 2', {});

      setActivePage(state, page2.id);
      expect(canGoForward(state)).toBe(false);

      historyBack(state);
      expect(canGoForward(state)).toBe(true);
    });
  });

  describe('Utility Functions', () => {
    it('should get page count', () => {
      const state = createPageManagerState();
      
      expect(getPageCount(state)).toBe(0);

      createPage(state, 'Page 1', {});
      createPage(state, 'Page 2', {});

      expect(getPageCount(state)).toBe(2);
    });

    it('should search pages by name', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Dashboard', {});
      createPage(state, 'Settings', {});
      createPage(state, 'User Dashboard', {});

      const results = searchPages(state, 'dash');

      expect(results).toHaveLength(2);
      expect(results[0].name).toBe('Dashboard');
      expect(results[1].name).toBe('User Dashboard');
    });

    it('should search pages by description', () => {
      const state = createPageManagerState();
      
      createPage(state, 'Page 1', {}, { description: 'Main dashboard' });
      createPage(state, 'Page 2', {}, { description: 'User settings' });

      const results = searchPages(state, 'dashboard');

      expect(results).toHaveLength(1);
      expect(results[0].name).toBe('Page 1');
    });

    it('should search case-insensitively', () => {
      const state = createPageManagerState();
      
      createPage(state, 'UPPERCASE', {});
      createPage(state, 'lowercase', {});

      const results = searchPages(state, 'Case');

      expect(results).toHaveLength(2);
    });
  });
});
