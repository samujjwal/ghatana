/**
 * ESLint Rule: No Deprecated Imports
 *
 * Prevents imports from deprecated package surfaces to ensure code uses
 * canonical, stable APIs instead of legacy compatibility layers.
 *
 * Deprecated surfaces:
 * - @ghatana/canvas/ui-builder (deprecated subpath - use @ghatana/ui-builder instead)
 * - @ghatana/ui-builder/src/core/types.BuilderDocument (use builder-document.ts instead)
 *
 * @type {import('eslint').Rule.RuleModule}
 */
module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Prevent imports from deprecated package surfaces',
      category: 'Best Practices',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          deprecatedImports: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                pattern: { type: 'string' },
                message: { type: 'string' },
                replacement: { type: 'string' },
              },
              required: ['pattern', 'message', 'replacement'],
            },
          },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      deprecatedImport: 'Deprecated import: "{{import}}". {{message}}. Use "{{replacement}}" instead.',
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const deprecatedImports = options.deprecatedImports || [
      {
        pattern: '@ghatana/canvas/ui-builder',
        message: 'The ./ui-builder subpath is deprecated',
        replacement: '@ghatana/ui-builder',
      },
      {
        pattern: '@ghatana/ui-builder/src/core/types',
        message: 'The types.ts file is deprecated for BuilderDocument',
        replacement: '@ghatana/ui-builder (BuilderDocument from builder-document.ts)',
      },
    ];

    return {
      ImportDeclaration(node) {
        const importSource = node.source.value;

        for (const deprecated of deprecatedImports) {
          if (importSource.startsWith(deprecated.pattern)) {
            context.report({
              node,
              messageId: 'deprecatedImport',
              data: {
                import: importSource,
                message: deprecated.message,
                replacement: deprecated.replacement,
              },
            });
            break;
          }
        }
      },
    };
  },
};
