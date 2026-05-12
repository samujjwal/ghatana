/**
 * ESLint Rule: No Production TODOs
 * 
 * Disallows TODO, FIXME, HACK, and other temporary markers in production code.
 * These markers should be removed before code reaches production.
 * 
 * Allowed in:
 * - Test files (*.test.ts, *.test.tsx, *.spec.ts, *.spec.tsx)
 * - Documentation files (*.md)
 * - Comments that explicitly reference a tracking issue (e.g., "TODO-123", "FIXME: tracked in issue #456")
 * 
 * Disallowed in:
 * - Production source code without tracking references
 * 
 * @type {import('eslint').Rule.RuleModule}
 */
module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow TODO, FIXME, HACK, and other temporary markers in production code',
      category: 'Best Practices',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          allowTrackingReferences: {
            type: 'boolean',
            default: true,
          },
          keywords: {
            type: 'array',
            items: { type: 'string' },
            default: ['TODO', 'FIXME', 'HACK', 'XXX', 'TEMP', 'TEMPORARY', 'FALLBACK'],
          },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      noProductionTodo: 'Production code contains "{{keyword}}" marker. Either remove it or add a tracking reference (e.g., "TODO-123", "tracked in issue #456").',
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const allowTrackingReferences = options.allowTrackingReferences !== false;
    const keywords = options.keywords || ['TODO', 'FIXME', 'HACK', 'XXX', 'TEMP', 'TEMPORARY', 'FALLBACK'];
    
    const filename = context.getFilename();
    
    // Skip test files and documentation
    if (filename.includes('.test.') || filename.includes('.spec.') || filename.endsWith('.md')) {
      return {};
    }
    
    const keywordPattern = keywords.join('|');
    
    return {
      Program() {
        const sourceCode = context.getSourceCode().getText();
        const lines = sourceCode.split('\n');
        
        lines.forEach((line, lineIndex) => {
          // Check for single-line comments
          const singleLineCommentMatch = line.match(new RegExp(`//\\s*(${keywordPattern})\\b`, 'i'));
          if (singleLineCommentMatch) {
            const keyword = singleLineCommentMatch[1].toUpperCase();
            
            // Allow if it has a tracking reference (e.g., TODO-123)
            if (allowTrackingReferences && /-\d+|tracked in issue|#\d+|issue #\d+/i.test(line)) {
              return;
            }
            
            context.report({
              loc: { line: lineIndex + 1, column: line.indexOf(keyword) },
              messageId: 'noProductionTodo',
              data: { keyword },
            });
            return;
          }
          
          // Check for multi-line comments
          const multiLineCommentMatch = line.match(new RegExp(`\\*\\s*(${keywordPattern})\\b`, 'i'));
          if (multiLineCommentMatch) {
            const keyword = multiLineCommentMatch[1].toUpperCase();
            
            // Allow if it has a tracking reference
            if (allowTrackingReferences && /-\d+|tracked in issue|#\d+|issue #\d+/i.test(line)) {
              return;
            }
            
            context.report({
              loc: { line: lineIndex + 1, column: line.indexOf(keyword) },
              messageId: 'noProductionTodo',
              data: { keyword },
            });
          }
        });
      },
    };
  },
};
