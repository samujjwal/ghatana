/**
 * ESLint Rule: No Raw Fetch
 * 
 * Enforces that raw fetch() calls are only allowed in designated HTTP infrastructure.
 * Disallows fetch in route components, hooks, page services, and random libs.
 * 
 * Allowed locations:
 * - lib/http/* (HTTP infrastructure)
 * - lib/api/* (API client infrastructure)
 * - clients/generated/* (OpenAPI-generated client)
 * 
 * Disallowed locations:
 * - routes/* (route components)
 * - hooks/* (React hooks)
 * - pages/* (page components)
 * - services/* (page services)
 * - components/* (React components)
 * 
 * @type {import('eslint').Rule.RuleModule}
 */
module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow raw fetch calls outside HTTP infrastructure',
      category: 'Best Practices',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          allowedPaths: {
            type: 'array',
            items: { type: 'string' },
            default: ['lib/http', 'lib/api', 'clients/generated'],
          },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      noRawFetch: 'Raw fetch() calls are not allowed outside HTTP infrastructure. Use the typed API client from lib/api/client.ts instead.',
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const allowedPaths = options.allowedPaths || ['lib/http', 'lib/api', 'clients/generated'];
    
    const filename = context.getFilename();
    
    // Check if the file is in an allowed path
    const isAllowed = allowedPaths.some(path => filename.includes(path));
    
    // If allowed, skip the rule
    if (isAllowed) {
      return {};
    }
    
    return {
      CallExpression(node) {
        // Check for fetch() calls
        if (node.callee.type === 'Identifier' && node.callee.name === 'fetch') {
          // Check if it's the global fetch (not a method call like someObj.fetch)
          if (node.callee.type === 'Identifier' && node.callee.name === 'fetch') {
            context.report({
              node,
              messageId: 'noRawFetch',
            });
          }
        }
      },
    };
  },
};
