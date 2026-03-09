// Minimal DOMPurify-like mock for tests
/**
 *
 */
export interface TestDOMPurify {
  sanitize: (content: string, options?: unknown) => string;
  addHook: (hookName: string, callback: (node: unknown, data?: unknown) => void) => void;
}

export const createTestDOMPurify = (): TestDOMPurify => {
  const hooks = new Map<string, Array<(node: unknown, data?: unknown) => void>>();

  return {
    addHook: (hookName: string, callback: (node: unknown, data?: unknown) => void) => {
      if (!hooks.has(hookName)) hooks.set(hookName, []);
      hooks.get(hookName)!.push(callback);
    },
    sanitize: (content: string, options: unknown = {}) => {
      const opts = options as Record<string, unknown>;
      let sanitized = content;

      // Very small, deterministic sanitization useful for unit tests
      sanitized = sanitized.replace(/<script[^>]*>.*?<\/script>/gis, '');
      sanitized = sanitized.replace(/on\w+\s*=\s*["'][^"']*["']/gi, '');
      sanitized = sanitized.replace(/javascript:/gi, '');
      sanitized = sanitized.replace(/vbscript:/gi, '');

      if (opts.ALLOWED_TAGS && Array.isArray(opts.ALLOWED_TAGS)) {
        const allowedTags = opts.ALLOWED_TAGS.map((t: string) => t.toLowerCase());
        sanitized = sanitized.replace(/<(\/?)([\w-]+)[^>]*>/g, (match, slash, tagName) => {
          if (allowedTags.includes(tagName.toLowerCase())) return match;
          return '';
        });
      }

      return sanitized;
    },
  };
};

export default createTestDOMPurify;
