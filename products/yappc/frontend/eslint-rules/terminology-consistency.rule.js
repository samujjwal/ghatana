/**
 * Enforces canonical YAPPC terminology consistency.
 *
 * Canonical definitions:
 * - Project: Persisted workspace-scoped delivery container
 * - Product: Real-world business/customer outcome
 * - App: Runnable/generated/deployed software
 *
 * This rule prevents using "Product" as a synonym for "Project" in UI/API/schema.
 * @module eslint-rules/terminology-consistency
 */

/**
 * Checks if a string represents a potential misuse of "Product" as a synonym for "Project"
 * @param {string} text - The text to check
 * @returns {boolean} - True if the text suggests misuse
 */
function isProductAsProjectSynonym(text) {
  // Ignore common legitimate uses
  const legitimatePatterns = [
    /yappc-product-theme/,  // Package name
    /product-theme/,       // Theme package
    /product-type/,        // Type definition
    /productId/,           // Database field
    /product\.ts/,         // File name
    /Product\.ts/,         // File name
    /product\.test/,       // Test file
    /__tests__/,           // Test directory
    /\.spec\./,            // Spec file
    /\.test\./,            // Test file
    /node_modules/,        // Dependencies
    /dist/,                // Build output
    /build/,               // Build output
    /\.generated/,         // Generated code
  ];

  for (const pattern of legitimatePatterns) {
    if (pattern.test(text)) {
      return false;
    }
  }

  // Check for suspicious patterns where Product might be used as Project synonym
  // Contexts where Project should be used instead:
  const suspiciousPatterns = [
    /createProduct/i,      // Should be createProject
    /listProduct/i,        // Should be listProject
    /getProduct/i,         // Should be getProject
    /updateProduct/i,      // Should be updateProject
    /deleteProduct/i,      // Should be deleteProject
    /productList/i,        // Should be projectList
    /projectProduct/i,     // Confusing - should clarify
    /workspaceProduct/i,    // Confusing - should clarify
  ];

  for (const pattern of suspiciousPatterns) {
    if (pattern.test(text)) {
      return true;
    }
  }

  return false;
}

/**
 * ESLint rule definition
 */
const rule = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Enforce canonical YAPPC terminology: Project (delivery container), Product (business outcome), App (runnable software)',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      productAsProject: 'Use "Project" instead of "Product" when referring to workspace-scoped delivery containers. Product should only refer to real-world business/customer outcomes.',
    },
    schema: [], // No options
  },
  create(context) {
    return {
      Identifier(node) {
        if (isProductAsProjectSynonym(node.name)) {
          context.report({
            node,
            messageId: 'productAsProject',
          });
        }
      },
      Literal(node) {
        if (typeof node.value === 'string' && isProductAsProjectSynonym(node.value)) {
          context.report({
            node,
            messageId: 'productAsProject',
          });
        }
      },
      TemplateElement(node) {
        if (isProductAsProjectSynonym(node.value.raw)) {
          context.report({
            node,
            messageId: 'productAsProject',
          });
        }
      },
    };
  },
};

export default {
  rules: {
    productAsProject: rule,
  },
};
