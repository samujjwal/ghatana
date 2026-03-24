/**
 * Help Content Manager - Main manager for help content
 * @module components/ContextualHelp/manager
 */

import { DEFAULT_HELP_CONTENT } from './content';
import { HelpContentUtils } from './utils';

import type { HelpContent } from './types';

/**
 * Main manager for help content
 *
 * Manages storage, indexing, and retrieval of help topics.
 * Provides full-text search and contextual help matching.
 *
 * Usage:
 * ```typescript
 * const manager = new HelpContentManager();
 * manager.addContent(customTopic);
 * const results = manager.search('canvas');
 * const contextHelp = manager.getContextualHelp('drawing');
 * ```
 */
export class HelpContentManager {
  private content: Map<string, HelpContent> = new Map();
  private searchIndex: Map<string, Set<string>> = new Map();

  /**
   * Initialize the manager with default content and build search index
   */
  constructor() {
    this.loadDefaultContent();
    this.buildSearchIndex();
  }

  /**
   * Add help content to the manager
   *
   * @param content - Help content to add
   *
   * @example
   * ```typescript
   * manager.addContent({
   *   id: 'custom-topic',
   *   title: 'My Topic',
   *   content: '...',
   *   category: 'canvas',
   *   keywords: ['my', 'topic'],
   *   priority: 50
   * });
   * ```
   */
  addContent(content: HelpContent): void {
    this.content.set(content.id, content);
    this.indexContent(content);
  }

  /**
   * Search help content by query string
   *
   * Returns matching content sorted by relevance and priority.
   * Returns all content (sorted by priority) if query is empty.
   *
   * @param query - Search query (space-separated terms)
   * @returns Array of matching content sorted by relevance
   *
   * @example
   * ```typescript
   * const results = manager.search('keyboard shortcuts');
   * ```
   */
  search(query: string): HelpContent[] {
    if (!query.trim()) {
      return this.getAllContent();
    }

    const searchTerms = query
      .toLowerCase()
      .split(' ')
      .filter((term) => term.length > 0);
    const matchingIds = new Set<string>();

    // Find all content matching any search term
    searchTerms.forEach((term) => {
      const matches = this.searchIndex.get(term) || new Set();
      matches.forEach((id) => matchingIds.add(id));
    });

    // Convert to array and sort by relevance
    const results = Array.from(matchingIds)
      .map((id) => this.content.get(id))
      .filter(Boolean) as HelpContent[];

    return results.sort((a, b) => {
      const aRelevance = HelpContentUtils.calculateRelevance(a, searchTerms);
      const bRelevance = HelpContentUtils.calculateRelevance(b, searchTerms);
      return bRelevance - aRelevance;
    });
  }

  /**
   * Get contextual help for specific area/context
   *
   * Returns top 5 help topics relevant to the context.
   *
   * @param context - Context string to match help against
   * @returns Array of relevant help topics (max 5, sorted by priority)
   *
   * @example
   * ```typescript
   * const help = manager.getContextualHelp('canvas');
   * ```
   */
  getContextualHelp(context: string): HelpContent[] {
    const contextContent = Array.from(this.content.values())
      .filter((content) => HelpContentUtils.contextMatches(content, context))
      .sort((a, b) => b.priority - a.priority);

    return HelpContentUtils.limitResults(contextContent, 5);
  }

  /**
   * Get all help content sorted by priority
   *
   * @returns All help topics in priority order
   *
   * @example
   * ```typescript
   * const allHelp = manager.getAllContent();
   * ```
   */
  getAllContent(): HelpContent[] {
    return HelpContentUtils.sortByPriority(Array.from(this.content.values()));
  }

  /**
   * Get help content filtered by category
   *
   * @param category - Help category to filter by
   * @returns Help topics in category, sorted by priority
   *
   * @example
   * ```typescript
   * const canvasHelp = manager.getContentByCategory('canvas');
   * ```
   */
  getContentByCategory(category: HelpContent['category']): HelpContent[] {
    return HelpContentUtils.filterByCategory(
      Array.from(this.content.values()),
      category
    );
  }

  /**
   * Get related help topics for a specific content
   *
   * @param contentId - ID of the content to get related topics for
   * @returns Array of related help topics
   *
   * @example
   * ```typescript
   * const related = manager.getRelatedContent('canvas-basics');
   * ```
   */
  getRelatedContent(contentId: string): HelpContent[] {
    const content = this.content.get(contentId);
    if (!content || !content.relatedTopics) {
      return [];
    }

    return content.relatedTopics
      .map((id) => this.content.get(id))
      .filter(Boolean) as HelpContent[];
  }

  /**
   * Load default help content
   *
   * @internal Used during initialization
   */
  private loadDefaultContent(): void {
    DEFAULT_HELP_CONTENT.forEach((content: HelpContent) =>
      this.addContent(content)
    );
  }

  /**
   * Index content for search
   *
   * @internal Used when adding content
   */
  private indexContent(content: HelpContent): void {
    const searchableText = HelpContentUtils.buildSearchableText(content);
    const words = HelpContentUtils.extractWords(searchableText);

    words.forEach((word) => {
      if (!this.searchIndex.has(word)) {
        this.searchIndex.set(word, new Set());
      }
      const wordSet = this.searchIndex.get(word);
      if (wordSet) {
        wordSet.add(content.id);
      }
    });
  }

  /**
   * Build search index for all content
   *
   * @internal Used during initialization
   */
  private buildSearchIndex(): void {
    Array.from(this.content.values()).forEach((content) => {
      this.indexContent(content);
    });
  }
}
