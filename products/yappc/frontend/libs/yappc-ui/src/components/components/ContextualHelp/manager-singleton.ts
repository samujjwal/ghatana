/**
 * Help Content Manager Singleton
 * @module components/ContextualHelp/manager-singleton
 */

import { HelpContentManager } from './manager';

/**
 * Global singleton instance of the help content manager
 *
 * Used throughout the application to access help content.
 * Initialized once on module load with default content.
 *
 * @example
 * ```typescript
 * import { helpContentManager } from '@ghatana/yappc-shared-ui-core/components/ContextualHelp';
 *
 * const results = helpContentManager.search('canvas');
 * const contextHelp = helpContentManager.getContextualHelp('drawing');
 * ```
 */
export const helpContentManager = new HelpContentManager();
