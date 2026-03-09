/**
 * @fileoverview Rule to prevent magic spacing values and require design system spacing tokens
 * @author YAPPC Team
 */

import type { Rule } from 'eslint';
import type { Node } from 'estree';

/**
 * Detects pixel values like '16px', '8px', or numeric spacing that should use tokens
 */
function containsMagicSpacing(value: string | number): boolean {
  if (typeof value === 'string') {
    // Match pixel values: 16px, 0.5rem, 2em
    const pixelRegex = /\d+(\.\d+)?(px|rem|em)\b/;
    return pixelRegex.test(value);
  }
  // For numeric values in padding/margin, we'll check context
  return false;
}

/**
 * Suggests a spacing token based on the pixel value
 */
function suggestSpacingToken(value: string): string {
  const numericValue = parseFloat(value);
  
  // MUI uses 8px as base spacing unit
  // spacing(0.5) = 4px, spacing(1) = 8px, spacing(2) = 16px, etc.
  const spacingMap: Record<string, string> = {
    '0px': '0',
    '2px': '0.25',
    '4px': '0.5',
    '8px': '1',
    '12px': '1.5',
    '16px': '2',
    '20px': '2.5',
    '24px': '3',
    '32px': '4',
    '40px': '5',
    '48px': '6',
  };
  
  const pixelKey = `${numericValue}px`;
  if (spacingMap[pixelKey]) {
    return `spacing(${spacingMap[pixelKey]}) or shorthand: p: ${spacingMap[pixelKey]}, m: ${spacingMap[pixelKey]}`;
  }
  
  // Calculate closest spacing unit
  const spacingUnit = Math.round(numericValue / 8 * 2) / 2;
  return `spacing(${spacingUnit}) or shorthand: p: ${spacingUnit}, m: ${spacingUnit}`;
}

/**
 * Check if property is spacing-related
 */
function isSpacingProperty(propName: string): boolean {
  const spacingProps = [
    'padding', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
    'margin', 'marginTop', 'marginRight', 'marginBottom', 'marginLeft',
    'gap', 'rowGap', 'columnGap',
    'p', 'pt', 'pr', 'pb', 'pl', 'px', 'py',
    'm', 'mt', 'mr', 'mb', 'ml', 'mx', 'my',
  ];
  return spacingProps.includes(propName);
}

/**
 * Check a node for magic spacing values and report violations
 */
function checkForMagicSpacing(context: Rule.RuleContext, node: Node, propName: string): void {
  let value: string | null = null;
  
  if (node.type === 'Literal') {
    if (typeof node.value === 'string') {
      value = node.value;
    } else if (typeof node.value === 'number' && propName) {
      // Numeric values in spacing props are likely pixels (non-MUI pattern)
      // MUI spacing uses unitless numbers with theme.spacing()
      // So raw numbers in padding/margin should use spacing shorthand instead
      if (node.value > 0) {
        const pixelEquivalent = `${node.value}px`;
        context.report({
          node,
          messageId: 'magicSpacing',
          data: {
            value: pixelEquivalent,
            suggestion: suggestSpacingToken(pixelEquivalent),
          },
        });
      }
      return;
    }
  } else if (node.type === 'TemplateLiteral' && node.quasis.length === 1) {
    value = node.quasis[0].value.raw;
  }
  
  if (value && containsMagicSpacing(value)) {
    context.report({
      node,
      messageId: 'magicSpacing',
      data: {
        value,
        suggestion: suggestSpacingToken(value),
      },
    });
  }
}

const rule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Prevent magic spacing values and require design system spacing tokens',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      magicSpacing: 'Magic spacing value "{{value}}" detected. Use design system spacing tokens instead ({{suggestion}}).',
    },
    schema: [],
  },

  create(context: Rule.RuleContext): Rule.RuleListener {
    return {
      /**
       * Check JSX attributes for sx and style props with magic spacing
       */
      JSXAttribute(node: Node): void {
        if (node.type !== 'JSXAttribute' || !node.value) return;
        
        const attrName = node.name.type === 'JSXIdentifier' ? node.name.name : '';
        
        // Only check sx and style props
        if (attrName !== 'sx' && attrName !== 'style') return;
        
        // Check JSXExpressionContainer (sx={{ ... }} or style={{ ... }})
        if (node.value.type === 'JSXExpressionContainer') {
          const expression = node.value.expression;
          
          // Handle object expressions
          if (expression.type === 'ObjectExpression') {
            expression.properties.forEach((prop) => {
              if (prop.type !== 'Property') return;
              
              const propKey = prop.key.type === 'Identifier' 
                ? prop.key.name 
                : prop.key.type === 'Literal' 
                  ? String(prop.key.value) 
                  : '';
              
              // Only check spacing properties
              if (!isSpacingProperty(propKey)) return;
              
              if (prop.value) {
                checkForMagicSpacing(context, prop.value, propKey);
              }
            });
          }
        }
      },
      
      /**
       * Check template literals that might contain styled components with spacing
       */
      TemplateLiteral(node: Node): void {
        if (node.type !== 'TemplateLiteral') return;
        
        node.quasis.forEach((quasi) => {
          // Check for padding/margin with pixel values
          const spacingRegex = /(padding|margin):\s*['"]?(\d+(\.\d+)?(px|rem|em))['"]?/g;
          let match;
          
          while ((match = spacingRegex.exec(quasi.value.raw)) !== null) {
            context.report({
              node: quasi,
              messageId: 'magicSpacing',
              data: {
                value: match[2],
                suggestion: suggestSpacingToken(match[2]),
              },
            });
          }
        });
      },
    };
  },
};

export default rule;
