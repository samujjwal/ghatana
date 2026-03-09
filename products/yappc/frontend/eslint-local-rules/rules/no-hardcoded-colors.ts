/**
 * @fileoverview Rule to prevent hardcoded hex colors and require design system palette tokens
 * @author YAPPC Team
 */

import type { Rule } from 'eslint';
import type { Node } from 'estree';

/**
 * Detects hex color patterns like #fff, #123abc, #FF00FF
 */
function containsHexColor(value: string): boolean {
  // Match hex colors: #RGB, #RRGGBB, #RGBA, #RRGGBBAA (case insensitive)
  const hexColorRegex = /#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{8})\b/;
  return hexColorRegex.test(value);
}

/**
 * Suggests a palette token based on the hex color
 */
function suggestPaletteToken(hexColor: string): string {
  const color = hexColor.toLowerCase();
  
  // Common color mappings (expand as needed)
  const colorMap: Record<string, string> = {
    '#ffffff': 'background.paper',
    '#fff': 'background.paper',
    '#000000': 'text.primary',
    '#000': 'text.primary',
    '#2196f3': 'primary.main',
    '#1976d2': 'primary.dark',
    '#64b5f6': 'primary.light',
    '#4caf50': 'success.main',
    '#f44336': 'error.main',
    '#ff9800': 'warning.main',
    '#9e9e9e': 'grey.500',
    '#e0e0e0': 'grey.300',
    '#bdbdbd': 'grey.400',
    '#757575': 'grey.600',
    '#f5f5f5': 'grey.100',
  };
  
  return colorMap[color] || 'palette.primary.main (or appropriate token)';
}

/**
 * Check a node for hex color values and report violations
 */
function checkForHexColor(context: Rule.RuleContext, node: Node): void {
  let value: string | null = null;
  
  if (node.type === 'Literal' && typeof node.value === 'string') {
    value = node.value;
  } else if (node.type === 'TemplateLiteral' && node.quasis.length === 1) {
    value = node.quasis[0].value.raw;
  }
  
  if (value && containsHexColor(value)) {
    const hexColors = value.match(/#([0-9a-fA-F]{3,8})\b/g);
    hexColors?.forEach((color) => {
      context.report({
        node,
        messageId: 'hardcodedColor',
        data: {
          color,
          suggestion: suggestPaletteToken(color),
        },
      });
    });
  }
}

const rule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Prevent hardcoded hex colors and require design system palette tokens',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      hardcodedColor: 'Hardcoded color "{{color}}" detected. Use design system palette tokens instead (e.g., {{suggestion}}).',
    },
    schema: [],
  },

  create(context: Rule.RuleContext): Rule.RuleListener {
    return {
      /**
       * Check JSX attributes for sx and style props with hardcoded colors
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
              if (prop.type === 'Property' && prop.value) {
                checkForHexColor(context, prop.value);
              }
            });
          }
        }
        
        // Check string literals (bgcolor="#fff")
        if (node.value.type === 'Literal' && typeof node.value.value === 'string') {
          checkForHexColor(context, node.value);
        }
      },
      
      /**
       * Check template literals that might contain styled components or CSS
       */
      TemplateLiteral(node: Node): void {
        if (node.type !== 'TemplateLiteral') return;
        
        node.quasis.forEach((quasi) => {
          if (containsHexColor(quasi.value.raw)) {
            const hexColors = quasi.value.raw.match(/#([0-9a-fA-F]{3,8})\b/g);
            hexColors?.forEach((color) => {
              context.report({
                node: quasi,
                messageId: 'hardcodedColor',
                data: {
                  color,
                  suggestion: suggestPaletteToken(color),
                },
              });
            });
          }
        });
      },
    };
  },
};

export default rule;
