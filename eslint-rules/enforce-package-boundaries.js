/**
 * ESLint Rule: Enforce Package Boundaries
 * 
 * Enforces that @ghatana/* packages are only used for platform generic code
 * and @yappc/* packages are only used for YAPPC product-specific code.
 * 
 * Platform packages (@ghatana/*):
 * - Can be imported by any product
 * - Should contain generic, reusable functionality
 * - Should not contain product-specific logic
 * 
 * Product packages (@yappc/*):
 * - Can only be imported by YAPPC product code
 * - Should contain YAPPC-specific logic
 * - Cannot be imported by other products
 * 
 * @type {import('eslint').Rule.RuleModule}
 */
module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Enforce package boundaries between platform (@ghatana/*) and product (@yappc/*) packages',
      category: 'Best Practices',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          platformPrefix: {
            type: 'string',
            default: '@ghatana',
          },
          productPrefixes: {
            type: 'array',
            items: { type: 'string' },
            default: ['@yappc'],
          },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      platformInProduct: 'Platform package "{{package}}" should only contain generic code. Move product-specific logic to {{productPrefix}}/* package.',
      productInPlatform: 'Product package "{{package}}" cannot be imported in platform code. Platform code must remain product-agnostic.',
      productCrossImport: 'Product package "{{package}}" from {{fromProduct}} cannot be imported by {{toProduct}}. Products should not import each other directly.',
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const platformPrefix = options.platformPrefix || '@ghatana';
    const productPrefixes = options.productPrefixes || ['@yappc'];
    
    const filename = context.getFilename();
    
    // Determine which product the current file belongs to
    let currentProduct = null;
    for (const prefix of productPrefixes) {
      if (filename.includes(prefix.replace('@', ''))) {
        currentProduct = prefix;
        break;
      }
    }
    
    // Determine if this is platform code
    const isPlatformCode = filename.includes('platform/') || filename.includes('platform-typescript/');
    
    return {
      ImportDeclaration(node) {
        const importSource = node.source.value;
        
        // Check if it's a package import
        if (!importSource.startsWith('@')) {
          return;
        }
        
        // Check if it's a platform package
        if (importSource.startsWith(`${platformPrefix}/`)) {
          // Platform package imported in product code - this is allowed
          // But we should warn if it contains product-specific logic (hard to detect statically)
          return;
        }
        
        // Check if it's a product package
        const importedProduct = productPrefixes.find(prefix => importSource.startsWith(`${prefix}/`));
        
        if (importedProduct) {
          // Product package imported
          if (isPlatformCode) {
            // Product package imported in platform code - not allowed
            context.report({
              node,
              messageId: 'productInPlatform',
              data: { package: importSource },
            });
          } else if (currentProduct && importedProduct !== currentProduct) {
            // Product package imported by different product - not allowed
            context.report({
              node,
              messageId: 'productCrossImport',
              data: { 
                package: importSource,
                fromProduct: importedProduct,
                toProduct: currentProduct,
              },
            });
          }
        }
      },
    };
  },
};
