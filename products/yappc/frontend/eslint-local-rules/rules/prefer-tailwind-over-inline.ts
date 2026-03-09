/**
 * ESLint rule: prefer-tailwind-over-inline
 * 
 * Enforces using Tailwind CSS className prop instead of inline style={{}} prop.
 * 
 * Part of Base UI + Tailwind CSS migration strategy.
 * 
 * @example
 * ```tsx
 * // ❌ BAD
 * <div style={{ padding: '16px', backgroundColor: '#fff' }} />
 * 
 * // ✅ GOOD
 * <div className="p-4 bg-white" />
 * 
 * // ✅ ALLOWED (dynamic styles from API)
 * <div style={{ transform: `rotate(${angle}deg)` }} />
 * ```
 */
import type { Rule } from 'eslint';

/** Minimal JSX AST node shapes for this rule (base eslint lacks JSX types) */
interface ObjectProperty { type: string; value: { type: string }; }
interface JSXExpressionValue { type: string; properties: ObjectProperty[]; }
interface JSXAttributeNode extends Rule.Node {
  name: { type: string; name: string };
  value?: {
    type: string;
    expression: JSXExpressionValue;
  } | null;
}

const rule: Rule.RuleModule = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Prefer Tailwind className over inline style prop',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      preferTailwind: 'Use Tailwind className instead of inline style={{}}. Dynamic styles are acceptable.',
    },
    schema: [],
  },
  
  create(context: Rule.RuleContext): Rule.RuleListener {
    return {
      JSXAttribute(node: JSXAttributeNode) {
        // Check if attribute is "style"
        if (
          node.name.type === 'JSXIdentifier' &&
          node.name.name === 'style' &&
          node.value?.type === 'JSXExpressionContainer'
        ) {
          const expression = node.value.expression;
          
          // Allow dynamic styles (e.g., style={{ transform: ... }})
          // Only flag static object literals
          if (expression.type === 'ObjectExpression') {
            // Check if all properties are static (Literal values)
            const allStatic = expression.properties.every((prop: ObjectProperty) => {
              if (prop.type === 'Property' && prop.value.type === 'Literal') {
                return true;
              }
              return false;
            });
            
            // Only report if all properties are static (hardcoded)
            if (allStatic && expression.properties.length > 0) {
              context.report({
                node,
                messageId: 'preferTailwind',
              });
            }
          }
        }
      },
    };
  },
};

export default rule;
