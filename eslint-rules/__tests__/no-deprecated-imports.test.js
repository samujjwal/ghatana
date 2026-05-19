/**
 * Tests for no-deprecated-imports ESLint rule
 */

const rule = require('../no-deprecated-imports.js');
const RuleTester = require('eslint').RuleTester;

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2020,
    sourceType: 'module',
  },
});

ruleTester.run('no-deprecated-imports', rule, {
  valid: [
    // Import from canonical ui-builder
    "import { BuilderDocument } from '@ghatana/ui-builder';",
    // Import from canvas public API
    "import { HybridCanvas } from '@ghatana/canvas';",
    // Import from canvas react
    "import { CanvasRenderer } from '@ghatana/canvas/react';",
    // Relative imports
    "import { something } from './local-file';",
  ],
  invalid: [
    {
      code: "import { something } from '@ghatana/canvas/ui-builder';",
      errors: [
        {
          messageId: 'deprecatedImport',
          data: {
            import: '@ghatana/canvas/ui-builder',
            message: 'The ./ui-builder subpath is deprecated',
            replacement: '@ghatana/ui-builder',
          },
        },
      ],
    },
    {
      code: "import { BuilderDocument } from '@ghatana/ui-builder/src/core/types';",
      errors: [
        {
          messageId: 'deprecatedImport',
          data: {
            import: '@ghatana/ui-builder/src/core/types',
            message: 'The types.ts file is deprecated for BuilderDocument',
            replacement: '@ghatana/ui-builder (BuilderDocument from builder-document.ts)',
          },
        },
      ],
    },
  ],
});
