/**
 * Vite plugin to exclude Node.js-only modules from browser builds
 * 
 * This plugin prevents Node.js-specific connector files from being bundled
 * in browser contexts by replacing them with empty virtual modules.
 */
import type { Plugin } from 'vite';

const NODE_ONLY_FILES = [
  'FileSystemConnector',
  'MqttsConnector',
  'MtlsConnector',
  'DeadLetterQueue',
  'security.ts', // utils/security.ts
];

export function excludeNodeModules(): Plugin {
  const virtualPrefix = '\0virtual:empty-module:';
  
  return {
    name: 'exclude-node-modules',
    resolveId(id) {
      // Check if this module should be excluded
      const shouldExclude = NODE_ONLY_FILES.some(file => id.includes(file));
      
      if (shouldExclude) {
        console.log(`[exclude-node-modules] Blocking Node.js-only module: ${id}`);
        // Return a virtual module ID
        return virtualPrefix + id;
      }
      
      return null; // Let other plugins handle
    },
    
    load(id) {
      // If this is our virtual empty module, return empty exports
      if (id.startsWith(virtualPrefix)) {
        console.log(`[exclude-node-modules] Replacing with empty module: ${id.slice(virtualPrefix.length)}`);
        return 'export default {};';
      }
      
      return null; // Let other plugins handle
    },
  };
}
