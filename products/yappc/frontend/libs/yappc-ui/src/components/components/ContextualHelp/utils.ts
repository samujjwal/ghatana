/**
 * Help Content Manager utilities
 * Handles searching, indexing, and retrieval of help content
 * @module components/ContextualHelp/utils
 */

import type { HelpContent } from './types';

/**
 * Utility class for help content search and retrieval
 *
 * Provides methods to:
 * - Search help content by keywords
 * - Retrieve contextual help by context
 * - Get help by category
 * - Find related topics
 * - Build and maintain search indices
 *
 * @example
 * ```typescript
 * const manager = new HelpContentUtils();
 * manager.addContent(helpTopic);
 * const results = manager.search('canvas');
 * ```
 */
export class HelpContentUtils {
  /**
   * Counts the number of matches between search terms and content
   * Used for relevance scoring in search results
   *
   * @param content - The help content to check
   * @param searchTerms - Array of search terms to match
   * @returns Number of matches found
   *
   * @internal Used internally for search result sorting
   */
  static countMatches(content: HelpContent, searchTerms: string[]): number {
    const contentText = [content.title, content.content, ...content.keywords]
      .join(' ')
      .toLowerCase();

    return searchTerms.reduce((count, term) => {
      // Escape special regex characters to treat search term as literal text
      const escapedTerm = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      // eslint-disable-next-line security/detect-non-literal-regexp
      const regex = new RegExp(escapedTerm, 'gi');
      const matches = contentText.match(regex);
      return count + (matches ? matches.length : 0);
    }, 0);
  }

  /**
   * Extracts searchable words from content
   *
   * @param text - Text to extract words from
   * @returns Array of searchable words (3+ characters)
   *
   * @internal Used for building search index
   */
  static extractWords(text: string): string[] {
    return text
      .toLowerCase()
      .split(/\s+/)
      .filter((word) => word.length > 2);
  }

  /**
   * Builds searchable text from help content
   *
   * @param content - The help content
   * @returns Concatenated searchable text
   *
   * @internal Used for indexing
   */
  static buildSearchableText(content: HelpContent): string {
    return [content.title, content.content, ...content.keywords]
      .join(' ')
      .toLowerCase();
  }

  /**
   * Calculates relevance score for content against search terms
   *
   * @param content - The help content
   * @param searchTerms - Array of search terms
   * @returns Relevance score (higher = more relevant)
   *
   * @internal Used for sorting search results
   */
  static calculateRelevance(
    content: HelpContent,
    searchTerms: string[]
  ): number {
    const matchCount = this.countMatches(content, searchTerms);
    return matchCount * 10 + content.priority; // Weight matches heavily
  }

  /**
   * Checks if context matches any keywords in content
   *
   * @param content - The help content
   * @param context - Context to match against
   * @returns True if context matches any keywords
   *
   * @internal Used for contextual help retrieval
   */
  static contextMatches(content: HelpContent, context: string): boolean {
    return content.keywords.some(
      (keyword) =>
        keyword.toLowerCase().includes(context.toLowerCase()) ||
        context.toLowerCase().includes(keyword.toLowerCase())
    );
  }

  /**
   * Filters content by category with relevance sorting
   *
   * @param contents - Array of help content items
   * @param category - Category to filter by
   * @returns Sorted array of content matching category
   *
   * @internal Used for category-based retrieval
   */
  static filterByCategory(
    contents: HelpContent[],
    category: HelpContent['category']
  ): HelpContent[] {
    return contents
      .filter((content) => content.category === category)
      .sort((a, b) => b.priority - a.priority);
  }

  /**
   * Sorts help content by priority descending
   *
   * @param contents - Array of help content items
   * @returns Sorted array (highest priority first)
   *
   * @internal Used for default ordering
   */
  static sortByPriority(contents: HelpContent[]): HelpContent[] {
    return contents.sort((a, b) => b.priority - a.priority);
  }

  /**
   * Slices and returns the first N items
   *
   * @param contents - Array of help content
   * @param limit - Maximum number of items to return
   * @returns Limited array slice
   *
   * @internal Used for pagination in UI
   */
  static limitResults(contents: HelpContent[], limit: number): HelpContent[] {
    return contents.slice(0, limit);
  }

  /**
   * Removes duplicate content by ID
   *
   * @param contents - Array of help content items
   * @returns Array with duplicates removed
   *
   * @internal Used after merging search results
   */
  static removeDuplicates(contents: HelpContent[]): HelpContent[] {
    const seen = new Set<string>();
    return contents.filter((content) => {
      if (seen.has(content.id)) {
        return false;
      }
      seen.add(content.id);
      return true;
    });
  }
}
