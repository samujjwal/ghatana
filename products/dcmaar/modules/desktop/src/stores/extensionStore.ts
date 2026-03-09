/**
 * DEPRECATED: This file is kept for backward compatibility only
 * The store has been migrated to Jotai atoms
 * 
 * @see src/atoms/extensionAtoms.ts - Atom definitions
 * @see src/hooks/useStores.ts - Backward-compatible hooks
 * @see src/hooks/useExtensionBridge.ts - WebSocket connection management
 */

export { useExtensionStore } from '../hooks/useStores';
export type { 
  ExtensionEvent, 
  ExtensionConfig,
  ConnectionState 
} from '../atoms/extensionAtoms';
