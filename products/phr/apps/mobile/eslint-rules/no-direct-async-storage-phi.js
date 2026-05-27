/**
 * ESLint rule to prevent direct AsyncStorage usage for PHI data.
 * 
 * This rule enforces that PHI-bearing data must go through the phiEncryptedStorage
 * adapter rather than being written directly to AsyncStorage. Direct AsyncStorage
 * usage is only allowed in the phiEncryptedStorage.ts file itself.
 * 
 * Rule: no-direct-async-storage-phi
 * 
 * Allowed patterns:
 * - AsyncStorage.setItem in phiEncryptedStorage.ts (for ciphertext only)
 * - AsyncStorage.getItem in phiEncryptedStorage.ts (for ciphertext only)
 * - AsyncStorage.removeItem in phiEncryptedStorage.ts
 * - AsyncStorage.getAllKeys in phiEncryptedStorage.ts
 * 
 * Forbidden patterns:
 * - AsyncStorage.setItem in any other file
 * - AsyncStorage.multiSet in any file
 * - AsyncStorage.mergeItem in any file
 */

module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Prevent direct AsyncStorage usage for PHI data',
      category: 'Security',
      recommended: true,
    },
    schema: [],
  },
  create(context) {
    const filename = context.getFilename();
    const isPhiEncryptedStorage = filename.includes('phiEncryptedStorage.ts');

    return {
      CallExpression(node) {
        if (isPhiEncryptedStorage) {
          // Allow AsyncStorage usage in phiEncryptedStorage.ts
          return;
        }

        const callee = node.callee;
        
        // Check for AsyncStorage.setItem, AsyncStorage.multiSet, AsyncStorage.mergeItem
        if (
          callee.type === 'MemberExpression' &&
          callee.object.type === 'Identifier' &&
          callee.object.name === 'AsyncStorage' &&
          ['setItem', 'multiSet', 'mergeItem'].includes(callee.property.name)
        ) {
          context.report({
            node,
            message: `Direct AsyncStorage.${callee.property.name} is forbidden for PHI data. Use phiSet/phiGet/phiRemove from phiEncryptedStorage.ts instead.`,
          });
        }
      },
    };
  },
};
