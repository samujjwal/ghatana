/**
 * Import Restriction Rules
 * 
 * Prevents imports from consolidated libraries that should no longer be used.
 */

module.exports = {
  rules: {
    'no-restricted-imports': ['error', {
      patterns: [
        {
          group: ['@yappc/base-ui', '@yappc/base-ui/*'],
          message: 'Import from @yappc/ui instead. base-ui has been consolidated.'
        },
        {
          group: ['@yappc/development-ui', '@yappc/development-ui/*'],
          message: 'Import from @yappc/ui instead. development-ui has been consolidated.'
        },
        {
          group: ['@yappc/initialization-ui', '@yappc/initialization-ui/*'],
          message: 'Import from @yappc/ui instead. initialization-ui has been consolidated.'
        },
        {
          group: ['@yappc/navigation-ui', '@yappc/navigation-ui/*'],
          message: 'Import from @yappc/ui instead. navigation-ui has been consolidated.'
        },
        {
          group: ['@yappc/theme', '@yappc/theme/*'],
          message: 'Import from @yappc/ui instead. theme has been consolidated.'
        },
        {
          group: ['@yappc/messaging', '@yappc/messaging/*'],
          message: 'Import from @yappc/ai instead. messaging has been consolidated.'
        },
        {
          group: ['@yappc/realtime', '@yappc/realtime/*'],
          message: 'Import from @yappc/ai instead. realtime has been consolidated.'
        },
        {
          group: ['@yappc/notifications', '@yappc/notifications/*'],
          message: 'Import from @yappc/ai instead. notifications has been consolidated.'
        },
        {
          group: ['@yappc/config-hooks', '@yappc/config-hooks/*'],
          message: 'Import from @yappc/state instead. config-hooks has been consolidated.'
        },
        {
          group: ['@yappc/crdt', '@yappc/crdt/*'],
          message: 'Import from @yappc/state instead. crdt has been consolidated.'
        },
        {
          group: ['@yappc/types', '@yappc/types/*'],
          message: 'Import from @yappc/core instead. types has been consolidated.'
        },
        {
          group: ['@yappc/utils', '@yappc/utils/*'],
          message: 'Import from @yappc/core instead. utils has been consolidated.'
        }
      ]
    }]
  }
};
