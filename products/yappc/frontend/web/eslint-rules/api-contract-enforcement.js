/**
 * API Contract Enforcement Rules
 *
 * ESLint rules to enforce API client usage and prevent raw fetch calls.
 * These rules ensure all REST calls use typed clients and prevent GraphQL-owned domains in REST client.
 *
 * @doc.type eslint-rules
 * @doc.purpose Enforce API client usage and contract compliance
 * @doc.layer product
 */

/**
 * Rule: no-raw-fetch
 * Disallows direct fetch() calls in favor of typed API clients.
 * All REST calls should use the domain-scoped clients or latentApis.
 */
const rules = {
  'no-raw-fetch': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Disallow direct fetch() calls, use typed API clients from @/lib/api instead',
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
      const { allowedFiles = ['lib/http.ts', 'lib/api/'] } = context.options[0] || {};
      const filename = context.getFilename();

      // Allow HTTP library and API client files to use fetch
      if (allowedFiles.some(pattern => filename.includes(pattern))) {
        return {};
      }

      return {
        CallExpression(node) {
          if (
            node.callee.type === 'Identifier' &&
            node.callee.name === 'fetch'
          ) {
            context.report({
              node,
              message: 'Use typed API client from @/lib/api instead of direct fetch(). Import from yappcLifecycleClient, yappcArtifactClient, yappcWorkflowsClient, yappcVectorClient, yappcAgentsClient, scaffoldClient, refactorerClient, or latentApis.',
            });
          }
        },
      };
    },
  },

  /**
   * Rule: no-graphql-in-rest-client
   * Prevents GraphQL-owned domains from being called via REST client.
   * GraphQL domains should use the GraphQL client instead.
   */
  'no-graphql-in-rest-client': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Prevent GraphQL-owned domains from being called via REST client',
        category: 'Best Practices',
        recommended: true,
      },
      schema: [],
    },
    create(context) {
      // GraphQL-owned domains that should NOT use the REST client
      const graphqlDomains = [
        'workflows',
        'requirements',
        'approvals',
        'agents',
        'versioning',
        'devsecops',
      ];

      return {
        MemberExpression(node) {
          // Check for yappcApi.domainName patterns
          if (
            node.object.type === 'Identifier' &&
            node.object.name === 'yappcApi' &&
            node.property.type === 'Identifier' &&
            graphqlDomains.includes(node.property.name)
          ) {
            context.report({
              node,
              message: `Domain '${node.property.name}' is GraphQL-owned. Use the GraphQL client instead of the REST client.`,
            });
          }
        },
      };
    },
  },

  /**
   * Rule: no-latent-api-in-production
   * Prevents latent APIs (marked as @experimental) from being used in production routes.
   * Latent APIs should only be used with explicit feature flags.
   */
  'no-latent-api-in-production': {
    meta: {
      type: 'suggestion',
      docs: {
        description: 'Prevent latent APIs from being used in production routes without feature flags',
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
      const { allowedFiles = ['lib/api/latentApis.ts'] } = context.options[0] || {};
      const filename = context.getFilename();

      // Allow latentApis file itself
      if (allowedFiles.some(pattern => filename.includes(pattern))) {
        return {};
      }

      return {
        ImportDeclaration(node) {
          if (node.source.value === '@/lib/api/latentApis') {
            context.report({
              node,
              message: 'Latent APIs are experimental. Use only with explicit feature flags and plan to mount or archive these APIs. See docs/api/API_CLIENT_REFACTORING_PLAN.md for guidance.',
            });
          }
        },
      };
    },
  },
};

export default { rules };
