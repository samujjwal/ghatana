/**
 * ESLint rule: no-arbitrary-tailwind
 * 
 * Prevents arbitrary Tailwind values, requires design tokens.
 * 
 * Enforces using design system tokens instead of arbitrary values.
 * 
 * @example
 * ```tsx
 * // ❌ BAD
 * <div className="p-[16px]" />
 * <div className="bg-[#ff0000]" />
 * <div className="w-[200px]" />
 * 
 * // ✅ GOOD
 * <div className="p-4" />        // Uses spacing token
 * <div className="bg-error-500" /> // Uses color token
 * <div className="w-48" />       // Uses spacing token
 * ```
 */
import type { Rule } from 'eslint';

/** Minimal JSX AST node shapes for this rule (base eslint lacks JSX types) */
interface TemplateQuasiElement { value: { raw: string }; }
interface ASTExpression {
  type: string;
  value?: string;
  quasis: TemplateQuasiElement[];
  arguments: Array<{ type: string; value?: string }>;
}
interface JSXAttributeNode extends Rule.Node {
  name: { type: string; name: string };
  value?: {
    type: string;
    value?: string;
    expression: ASTExpression;
  } | null;
}

const rule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Prevent arbitrary Tailwind values, require design tokens',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      noArbitrary: 'Use design tokens instead of arbitrary values: "{{className}}". Check tailwind.config.ts for available tokens.',
    },
    schema: [],
  },
  
  create(context: Rule.RuleContext): Rule.RuleListener {
    /**
     * Check if a className string contains arbitrary values
     */
    function hasArbitraryValues(classNameStr: string): string[] {
      // Match arbitrary values pattern: class-[value]
      const arbitraryPattern = /(\w+-\[[^\]]+\])/g;
      const matches = classNameStr.match(arbitraryPattern);
      return matches || [];
    }
    
    return {
      JSXAttribute(node: JSXAttributeNode) {
        // Check if attribute is "className"
        if (
          node.name.type === 'JSXIdentifier' &&
          node.name.name === 'className'
        ) {
          let classNameValue = '';
          
          // Handle string literals
          if (node.value?.type === 'Literal' && typeof node.value.value === 'string') {
            classNameValue = node.value.value;
          }
          
          // Handle template literals
          if (node.value?.type === 'JSXExpressionContainer') {
            const expression = node.value.expression;
            
            if (expression.type === 'Literal' && typeof expression.value === 'string') {
              classNameValue = expression.value;
            }
            
            if (expression.type === 'TemplateLiteral') {
              // Concatenate all string parts
              classNameValue = expression.quasis.map((q: TemplateQuasiElement) => q.value.raw).join(' ');
            }
            
            // Handle cn() or clsx() calls with string arguments
            if (expression.type === 'CallExpression') {
              expression.arguments.forEach((arg) => {
                if (arg.type === 'Literal' && typeof arg.value === 'string') {
                  classNameValue += ' ' + arg.value;
                }
              });
            }
          }
          
          // Check for arbitrary values
          if (classNameValue) {
            const arbitraryClasses = hasArbitraryValues(classNameValue);
            
            if (arbitraryClasses.length > 0) {
              arbitraryClasses.forEach((className) => {
                context.report({
                  node,
                  messageId: 'noArbitrary',
                  data: { className },
                });
              });
            }
          }
        }
      },
    };
  },
};

export default rule;
