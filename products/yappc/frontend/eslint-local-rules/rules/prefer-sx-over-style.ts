/**
 * @fileoverview Rule to prefer sx prop over style prop on MUI components
 * @author YAPPC Team
 */

import type { Rule } from 'eslint';
import type { Node } from 'estree';

/**
 * List of MUI component names that should use sx prop
 */
const MUI_COMPONENTS = [
  'Box', 'Stack', 'Container', 'Grid', 'Paper',
  'Card', 'CardContent', 'CardHeader', 'CardActions',
  'Button', 'IconButton', 'Fab',
  'TextField', 'Select', 'Input', 'Checkbox', 'Radio', 'Switch',
  'Typography', 'Link',
  'AppBar', 'Toolbar', 'Drawer', 'Menu', 'MenuItem',
  'Dialog', 'DialogTitle', 'DialogContent', 'DialogActions',
  'Chip', 'Avatar', 'Badge',
  'List', 'ListItem', 'ListItemText', 'ListItemButton',
  'Table', 'TableRow', 'TableCell', 'TableHead', 'TableBody',
  'Tabs', 'Tab',
  'Accordion', 'AccordionSummary', 'AccordionDetails',
  'Alert', 'Snackbar',
];

/**
 * Check if an import is from MUI
 */
function isMuiImport(node: Node): boolean {
  if (node.type !== 'ImportDeclaration') return false;
  
  const source = node.source.type === 'Literal' && typeof node.source.value === 'string'
    ? node.source.value
    : '';
  
  return source === '@mui/material' || 
         source === '@ghatana/ui' || 
         source.startsWith('@mui/material/');
}

const rule: Rule.RuleModule = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Prefer sx prop over style prop on MUI components for theme integration',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      preferSx: 'Prefer sx prop over style prop on MUI component "{{componentName}}" for theme integration and better performance.',
    },
    fixable: 'code',
    schema: [],
  },

  create(context: Rule.RuleContext): Rule.RuleListener {
    // Track which components are imported from MUI
    const muiComponents = new Set<string>();
    
    return {
      /**
       * Track MUI imports
       */
      ImportDeclaration(node: Node): void {
        if (!isMuiImport(node)) return;
        if (node.type !== 'ImportDeclaration') return;
        
        node.specifiers.forEach((specifier) => {
          if (specifier.type === 'ImportSpecifier' || specifier.type === 'ImportDefaultSpecifier') {
            const componentName = specifier.local.name;
            muiComponents.add(componentName);
          }
        });
      },
      
      /**
       * Check JSX elements for style prop usage
       */
      JSXOpeningElement(node: Node): void {
        if (node.type !== 'JSXOpeningElement') return;
        
        const componentName = node.name.type === 'JSXIdentifier' ? node.name.name : '';
        
        // Only check if component is imported from MUI or is a known MUI component
        if (!muiComponents.has(componentName) && !MUI_COMPONENTS.includes(componentName)) {
          return;
        }
        
        // Check if element has both sx and style props (rare but possible)
        let hasStyleProp = false;
        let stylePropNode: Node | null = null;
        
        node.attributes.forEach((attr) => {
          if (attr.type === 'JSXAttribute') {
            const attrName = attr.name.type === 'JSXIdentifier' ? attr.name.name : '';
            
            if (attrName === 'style') {
              hasStyleProp = true;
              stylePropNode = attr;
            }
          }
        });
        
        // Report if style prop is used
        if (hasStyleProp && stylePropNode) {
          context.report({
            node: stylePropNode,
            messageId: 'preferSx',
            data: {
              componentName,
            },
            fix(fixer) {
              // Simple auto-fix: rename 'style' to 'sx'
              // Note: This doesn't convert inline style values to theme tokens
              // That would require more complex AST transformation
              if (stylePropNode?.type === 'JSXAttribute' && stylePropNode.name.type === 'JSXIdentifier') {
                return fixer.replaceText(stylePropNode.name, 'sx');
              }
              return null;
            },
          });
        }
      },
    };
  },
};

export default rule;
