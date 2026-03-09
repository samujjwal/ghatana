/**
 * FlashIt Mobile - Deep Linking Utility
 *
 * Handles URL parsing and navigation for deep links.
 *
 * @doc.type utility
 * @doc.purpose Deep link handling for notifications and external links
 * @doc.layer product
 * @doc.pattern Utility
 */

import { router } from 'expo-router';
import * as Linking from 'expo-linking';

/**
 * Deep link configuration.
 */
export interface DeepLinkConfig {
  scheme: string;
  prefixes: string[];
}

/**
 * Parsed deep link.
 */
export interface ParsedDeepLink {
  path: string;
  params: Record<string, string>;
  isValid: boolean;
}

/**
 * Default deep link configuration.
 */
const DEFAULT_CONFIG: DeepLinkConfig = {
  scheme: 'flashit',
  prefixes: ['flashit://', 'https://flashit.app'],
};

/**
 * Route mappings for deep links.
 */
const ROUTE_MAPPINGS: Record<string, string> = {
  '/moments/:id': '/moments/[id]',
  '/spheres/:id': '/spheres/[id]',
  '/capture': '/capture',
  '/analytics': '/analytics',
  '/settings': '/settings',
  '/profile': '/profile',
  '/search': '/search',
};

/**
 * Deep Linking Service.
 */
class DeepLinkingService {
  private config: DeepLinkConfig = DEFAULT_CONFIG;
  private listeners: Set<(link: ParsedDeepLink) => void> = new Set();

  /**
   * Initialize deep linking.
   */
  async init(config?: Partial<DeepLinkConfig>): Promise<void> {
    if (config) {
      this.config = { ...DEFAULT_CONFIG, ...config };
    }

    // Handle initial URL (app opened via deep link)
    const initialUrl = await Linking.getInitialURL();
    if (initialUrl) {
      const parsed = this.parseUrl(initialUrl);
      if (parsed.isValid) {
        this.handleDeepLink(parsed);
      }
    }

    // Listen for incoming deep links
    Linking.addEventListener('url', ({ url }) => {
      const parsed = this.parseUrl(url);
      if (parsed.isValid) {
        this.handleDeepLink(parsed);
      }
    });

    console.log('[DeepLinking] Initialized');
  }

  /**
   * Parse a URL into a deep link.
   */
  parseUrl(url: string): ParsedDeepLink {
    try {
      const { path, queryParams } = Linking.parse(url);

      // Convert query params to string record
      const params: Record<string, string> = {};
      if (queryParams) {
        Object.entries(queryParams).forEach(([key, value]) => {
          if (typeof value === 'string') {
            params[key] = value;
          } else if (Array.isArray(value) && value.length > 0) {
            params[key] = value[0];
          }
        });
      }

      return {
        path: path || '/',
        params,
        isValid: true,
      };
    } catch (error) {
      console.error('[DeepLinking] Parse error:', error);
      return {
        path: '/',
        params: {},
        isValid: false,
      };
    }
  }

  /**
   * Handle a deep link.
   */
  handleDeepLink(link: ParsedDeepLink): void {
    console.log('[DeepLinking] Handling:', link.path);

    // Notify listeners
    this.listeners.forEach((listener) => listener(link));

    // Navigate to the path
    this.navigateTo(link.path, link.params);
  }

  /**
   * Navigate to a path with params.
   */
  navigateTo(path: string, params?: Record<string, string>): void {
    try {
      // Map the path to expo-router format
      const routerPath = this.mapPath(path, params);
      router.push(routerPath as any);
    } catch (error) {
      console.error('[DeepLinking] Navigation error:', error);
      // Fallback to home
      router.push('/');
    }
  }

  /**
   * Map a URL path to expo-router path.
   */
  private mapPath(path: string, params?: Record<string, string>): string {
    // Extract path segments
    const segments = path.split('/').filter(Boolean);

    // Handle specific routes
    if (segments.length === 0) {
      return '/';
    }

    // Moments detail
    if (segments[0] === 'moments' && segments[1]) {
      return `/moments/${segments[1]}`;
    }

    // Spheres detail
    if (segments[0] === 'spheres' && segments[1]) {
      return `/spheres/${segments[1]}`;
    }

    // Build query string
    let queryString = '';
    if (params && Object.keys(params).length > 0) {
      queryString = '?' + new URLSearchParams(params).toString();
    }

    return `/${segments.join('/')}${queryString}`;
  }

  /**
   * Create a deep link URL.
   */
  createUrl(path: string, params?: Record<string, string>): string {
    const baseUrl = `${this.config.scheme}://`;
    const cleanPath = path.startsWith('/') ? path.slice(1) : path;

    let url = `${baseUrl}${cleanPath}`;

    if (params && Object.keys(params).length > 0) {
      url += '?' + new URLSearchParams(params).toString();
    }

    return url;
  }

  /**
   * Create a shareable web URL.
   */
  createWebUrl(path: string, params?: Record<string, string>): string {
    const baseUrl = 'https://flashit.app';
    const cleanPath = path.startsWith('/') ? path : `/${path}`;

    let url = `${baseUrl}${cleanPath}`;

    if (params && Object.keys(params).length > 0) {
      url += '?' + new URLSearchParams(params).toString();
    }

    return url;
  }

  /**
   * Subscribe to deep link events.
   */
  subscribe(listener: (link: ParsedDeepLink) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Check if URL is a valid deep link.
   */
  isValidDeepLink(url: string): boolean {
    return this.config.prefixes.some((prefix) => url.startsWith(prefix));
  }

  /**
   * Get the app scheme.
   */
  getScheme(): string {
    return this.config.scheme;
  }
}

// Export singleton instance
export const deepLinking = new DeepLinkingService();
export default deepLinking;
