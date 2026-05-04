/**
 * Design System Enforcement Rules
 *
 * ESLint rules to enforce design-system usage across YAPPC.
 * These rules prevent raw HTML elements and ad-hoc styling in product routes.
 *
 * @doc.type eslint-rules
 * @doc.purpose Enforce design-system component usage
 * @doc.layer product
 */

/**
 * Rule: no-raw-buttons
 * Disallows raw <button> elements in favor of design-system Button component.
 */
module.exports.rules = {
  'no-raw-buttons': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Disallow raw <button> elements, use Button component from design-system',
        category: 'Best Practices',
        recommended: true,
      },
      schema: [
        {
          type: 'object',
          properties: {
            allowedFiles: {
              type: 'array',
              items: { type: 'string' },
            },
          },
        },
      ],
    },
    create(context) {
      const { allowedFiles = [] } = context.options[0] || {};
      const filename = context.getFilename();

      // Skip if file is in allowed list
      if (allowedFiles.some(pattern => filename.includes(pattern))) {
        return {};
      }

      return {
        JSXOpeningElement(node) {
          if (node.name.name === 'button') {
            context.report({
              node,
              message: 'Use Button component from @ghatana/design-system instead of raw <button>. See docs/ui-primitive-mapping.md for guidance.',
            });
          }
        },
      };
    },
  },

  /**
   * Rule: no-raw-inputs
   * Disallows raw <input> elements in favor of TextField/Input components.
   */
  'no-raw-inputs': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Disallow raw <input> elements, use TextField or Input component from design-system',
        category: 'Best Practices',
        recommended: true,
      },
      schema: [
        {
          type: 'object',
          properties: {
            allowedFiles: {
              type: 'array',
              items: { type: 'string' },
            },
          },
        },
      ],
    },
    create(context) {
      const { allowedFiles = [] } = context.options[0] || {};
      const filename = context.getFilename();

      if (allowedFiles.some(pattern => filename.includes(pattern))) {
        return {};
      }

      return {
        JSXOpeningElement(node) {
          if (node.name.name === 'input') {
            context.report({
              node,
              message: 'Use TextField or Input component from @ghatana/design-system instead of raw <input>. See docs/ui-primitive-mapping.md for guidance.',
            });
          }
        },
      };
    },
  },

  /**
   * Rule: no-raw-select
   * Disallows raw <select> elements in favor of Select component.
   */
  'no-raw-select': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Disallow raw <select> elements, use Select component from design-system',
        category: 'Best Practices',
        recommended: true,
      },
      schema: [
        {
          type: 'object',
          properties: {
            allowedFiles: {
              type: 'array',
              items: { type: 'string' },
            },
          },
        },
      ],
    },
    create(context) {
      const { allowedFiles = [] } = context.options[0] || {};
      const filename = context.getFilename();

      if (allowedFiles.some(pattern => filename.includes(pattern))) {
        return {};
      }

      return {
        JSXOpeningElement(node) {
          if (node.name.name === 'select') {
            context.report({
              node,
              message: 'Use Select component from @ghatana/design-system instead of raw <select>. See docs/ui-primitive-mapping.md for guidance.',
            });
          }
        },
      };
    },
  },

  /**
   * Rule: no-hardcoded-color-tokens
   * Disallows hardcoded color values outside approved theme files.
   */
  'no-hardcoded-color-tokens': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Disallow hardcoded color values, use theme tokens from design-system',
        category: 'Best Practices',
        recommended: true,
      },
      schema: [
        {
          type: 'object',
          properties: {
            allowedFiles: {
              type: 'array',
              items: { type: 'string' },
            },
          },
        },
      ],
    },
    create(context) {
      const { allowedFiles = ['theme/', 'tokens/'] } = context.options[0] || {};
      const filename = context.getFilename();

      // Allow theme files to define colors
      if (allowedFiles.some(pattern => filename.includes(pattern))) {
        return {};
      }

      const colorPattern = /#[0-9a-fA-F]{3,8}|rgb\(|rgba\(|hsl\(|hsla\(/;

      return {
        Literal(node) {
          if (typeof node.value === 'string' && colorPattern.test(node.value)) {
            context.report({
              node,
              message: 'Use theme tokens from design-system instead of hardcoded color values.',
            });
          }
        },
        TemplateElement(node) {
          const value = node.value.raw;
          if (colorPattern.test(value)) {
            context.report({
              node,
              message: 'Use theme tokens from design-system instead of hardcoded color values.',
            });
          }
        },
      };
    },
  },

  /**
   * Rule: no-ad-hoc-card-classes
   * Disallows custom card classes, use Card component from design-system.
   */
  'no-ad-hoc-card-classes': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Disallow custom card styling, use Card component from design-system',
        category: 'Best Practices',
        recommended: false,
      },
    },
    create(context) {
      const cardClassPattern = /card|Card/i;

      return {
        JSXAttribute(node) {
          if (node.name.name === 'className' && node.value) {
            const className = node.value.type === 'Literal' 
              ? node.value.value 
              : node.value.type === 'TemplateLiteral' 
                ? node.value.quasis.map(q => q.value.raw).join('')
                : '';
            
            if (cardClassPattern.test(className)) {
              context.report({
                node,
                message: 'Consider using Card component from @ghatana/design-system instead of custom card classes.',
              });
            }
          }
        },
      };
    },
  },
};
