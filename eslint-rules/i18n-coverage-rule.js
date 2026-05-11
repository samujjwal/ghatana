/**
 * ESLint rule to enforce i18n coverage in production UI
 * 
 * Detects hardcoded user-facing strings in React components and requires
 * them to use i18n keys instead. This prevents unextracted strings from
 * entering production code.
 * 
 * @doc.type eslint-rule
 * @doc.purpose Enforce i18n coverage for production UI
 * @doc.layer platform
 */

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Enforce i18n coverage for user-facing strings in production UI',
      category: 'Best Practices',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          // Allow specific patterns (e.g., technical terms, URLs)
          allowedPatterns: {
            type: 'array',
            items: { type: 'string' },
          },
          // File patterns to exclude (e.g., test files, story files)
          excludeFiles: {
            type: 'array',
            items: { type: 'string' },
          },
        },
        additionalProperties: false,
      },
    ],
  },
  create(context) {
    const options = context.options[0] || {};
    const allowedPatterns = options.allowedPatterns || [
      // Technical terms
      /^[A-Z_]+$/, // Constants like API_KEY, MAX_RETRIES
      /^[a-z]+-[a-z]+$/, // CSS classes like text-primary
      // URLs and paths
      /^https?:\/\//,
      /^\/[a-z]/,
      // Single words that are technical
      /^(div|span|button|input|form|a|img|svg|path|rect|circle|g|text|tspan|use)$/,
      // Common technical abbreviations
      /^(API|URL|HTTP|HTTPS|JSON|XML|HTML|CSS|JS|TS|TSX|JSX)$/,
      // Numbers and boolean values
      /^\d+$/,
      /^(true|false|null|undefined)$/,
      // Empty strings
      /^$/,
      // Placeholder text patterns
      /^\.\.\.$/,
      // Icon names
      /^[A-Z][a-z]+[A-Z][a-z]+$/, // CamelCase like ChevronDown
    ];
    const excludeFiles = options.excludeFiles || [
      '*.test.ts',
      '*.test.tsx',
      '*.spec.ts',
      '*.spec.tsx',
      '*.stories.ts',
      '*.stories.tsx',
      '__tests__/**',
    ];

    // Check if current file should be excluded
    const filename = context.getFilename();
    for (const pattern of excludeFiles) {
      if (filename.match(pattern.replace(/\*/g, '.*'))) {
        return {};
      }
    }

    // Helper to check if a string is allowed
    function isAllowedString(str) {
      if (!str || typeof str !== 'string') return true;
      
      for (const pattern of allowedPatterns) {
        if (str.match(pattern)) return true;
      }
      
      return false;
    }

    // Helper to check if string is a user-facing string
    function isUserFacingString(str) {
      if (!str || typeof str !== 'string') return false;
      
      // Must have at least 3 characters
      if (str.length < 3) return false;
      
      // Must contain at least one letter
      if (!/[a-zA-Z]/.test(str)) return false;
      
      // Must start with a letter
      if (!/^[a-zA-Z]/.test(str)) return false;
      
      // Must not be all uppercase (technical constant)
      if (str === str.toUpperCase() && str.length > 2) return false;
      
      // Must not be a single word that's clearly technical
      if (/^[a-z]+$/.test(str) && str.length < 4) {
        const technicalWords = ['div', 'span', 'img', 'svg', 'path', 'rect', 'circle', 'g', 'use'];
        return !technicalWords.includes(str);
      }
      
      return true;
    }

    return {
      // Check JSX text content
      JSXText(node) {
        const str = node.value.trim();
        if (isUserFacingString(str) && !isAllowedString(str)) {
          context.report({
            node,
            message: 'User-facing string should use i18n key. Replace hardcoded string "{{string}}" with t("key")',
            data: { string: str },
          });
        }
      },

      // Check JSX attribute values that are strings
      JSXAttribute(node) {
        if (node.value && node.value.type === 'Literal' && typeof node.value.value === 'string') {
          const str = node.value.value.trim();
          
          // Skip certain attributes
          const skipAttributes = ['id', 'className', 'class', 'data-testid', 'data-cy', 'key', 'type', 'name', 'src', 'href', 'alt', 'aria-label'];
          if (skipAttributes.includes(node.name.name)) return;
          
          if (isUserFacingString(str) && !isAllowedString(str)) {
            context.report({
              node,
              message: 'User-facing string in attribute should use i18n key. Replace hardcoded string "{{string}}" with t("key")',
              data: { string: str },
            });
          }
        }
      },

      // Check template literals that might contain user-facing strings
      TemplateLiteral(node) {
        // Check if it's a simple template with only one expression
        if (node.expressions.length === 0) {
          const str = node.quasis[0].value.raw.trim();
          if (isUserFacingString(str) && !isAllowedString(str)) {
            context.report({
              node,
              message: 'User-facing string should use i18n key. Replace hardcoded string "{{string}}" with t("key")',
              data: { string: str },
            });
          }
        }
      },
    };
  },
};
