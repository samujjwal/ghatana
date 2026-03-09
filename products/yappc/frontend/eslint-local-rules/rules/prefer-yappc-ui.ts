/**
 * @fileoverview Rule to require @ghatana/ui components instead of custom implementations
 * @author YAPPC Team
 */

import type { Rule } from 'eslint';
import type { Node } from 'estree';

/**
 * Component names that should be imported from @ghatana/ui
 */
const YAPPC_UI_COMPONENTS = [
  'Button', 'Input', 'Card', 'Text', 'Select', 'Checkbox', 'Radio',
  'Dialog', 'Modal', 'Drawer', 'Menu', 'Dropdown',
  'Table', 'DataGrid', 'List',
  'Tabs', 'Tab', 'Accordion',
  'Alert', 'Snackbar', 'Toast',
  'Avatar', 'Badge', 'Chip',
  'Tooltip', 'Popover',
  'TextField', 'TextArea', 'Switch', 'Slider',
  'DatePicker', 'TimePicker',
  'Breadcrumb', 'Pagination',
  'Spinner', 'Skeleton', 'ProgressBar',
  'Icon', 'IconButton',
];

/**
 * Check if function body returns a simple HTML element
 */
function checkFunctionBody(body: Node): boolean {
  // Direct JSX return: () => <button>
  if (body.type === 'JSXElement' || body.type === 'JSXFragment') {
    return true;
  }
  
  // Block with return: () => { return <button> }
  if (body.type === 'BlockStatement') {
    const returnStatement = body.body.find(stmt => stmt.type === 'ReturnStatement');
    if (returnStatement && returnStatement.type === 'ReturnStatement' && returnStatement.argument) {
      const returnArg = returnStatement.argument;
      if (returnArg.type === 'JSXElement' || returnArg.type === 'JSXFragment') {
        return true;
      }
    }
  }
  
  return false;
}

/**
 * Check if a component is a simple wrapper around HTML elements
 * (likely a duplicate of a design system component)
 */
function isLikelyDuplicateComponent(node: Node): boolean {
  // Check for patterns like:
  // const Button = ({ children, ...props }) => <button ...>
  // const Input = (props) => <input ...>
  
  if (node.type === 'VariableDeclarator') {
    const init = node.init;
    if (!init) return false;
    
    // Arrow function or function expression
    if (init.type === 'ArrowFunctionExpression' || init.type === 'FunctionExpression') {
      return checkFunctionBody(init.body);
    }
  } else if (node.type === 'FunctionDeclaration') {
    return checkFunctionBody(node.body);
  }
  
  return false;
}

const rule: Rule.RuleModule = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Require @ghatana/ui components instead of custom implementations',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      preferYappcUI: 'Custom "{{componentName}}" component detected. Use @ghatana/ui instead: import { {{componentName}} } from \'@ghatana/ui\';',
      missingImport: 'Component "{{componentName}}" is not imported from @ghatana/ui. Add: import { {{componentName}} } from \'@ghatana/ui\';',
    },
    schema: [],
  },

  create(context: Rule.RuleContext): Rule.RuleListener {
    // Track which components are imported from @ghatana/ui
    const yappcUIImports = new Set<string>();
    
    // Track local component declarations
    const localComponents = new Map<string, Node>();
    
    return {
      /**
       * Track @ghatana/ui imports
       */
      ImportDeclaration(node: Node): void {
        if (node.type !== 'ImportDeclaration') return;
        
        const source = node.source.type === 'Literal' && typeof node.source.value === 'string'
          ? node.source.value
          : '';
        
        if (source === '@ghatana/ui' || source.startsWith('@ghatana/yappc-shared-ui-core/')) {
          node.specifiers.forEach((specifier) => {
            if (specifier.type === 'ImportSpecifier') {
              const importedName = specifier.imported.type === 'Identifier'
                ? specifier.imported.name
                : '';
              yappcUIImports.add(importedName);
            }
          });
        }
      },
      
      /**
       * Check variable declarations for custom component implementations
       */
      VariableDeclarator(node: Node): void {
        if (node.type !== 'VariableDeclarator') return;
        if (!node.id || node.id.type !== 'Identifier') return;
        
        const componentName = node.id.name;
        
        // Check if this is a known component name
        if (!YAPPC_UI_COMPONENTS.includes(componentName)) return;
        
        // Check if it's a likely duplicate (simple wrapper)
        if (isLikelyDuplicateComponent(node)) {
          localComponents.set(componentName, node);
          
          // Only report if NOT imported from @ghatana/ui
          if (!yappcUIImports.has(componentName)) {
            context.report({
              node: node.id,
              messageId: 'preferYappcUI',
              data: {
                componentName,
              },
            });
          }
        }
      },
      
      /**
       * Check function declarations for custom component implementations
       */
      FunctionDeclaration(node: Node): void {
        if (node.type !== 'FunctionDeclaration') return;
        if (!node.id) return;
        
        const componentName = node.id.name;
        
        // Check if this is a known component name
        if (!YAPPC_UI_COMPONENTS.includes(componentName)) return;
        
        // Check if it's a likely duplicate
        if (isLikelyDuplicateComponent(node)) {
          localComponents.set(componentName, node);
          
          // Only report if NOT imported from @ghatana/ui
          if (!yappcUIImports.has(componentName)) {
            context.report({
              node: node.id,
              messageId: 'preferYappcUI',
              data: {
                componentName,
              },
            });
          }
        }
      },
      
      /**
       * Check JSX usage of components
       */
      JSXOpeningElement(node: Node): void {
        if (node.type !== 'JSXOpeningElement') return;
        if (node.name.type !== 'JSXIdentifier') return;
        
        const componentName = node.name.name;
        
        // Check if using a known component that should be from @ghatana/ui
        if (YAPPC_UI_COMPONENTS.includes(componentName)) {
          // If not imported from @ghatana/yappc-ui and not declared locally
          if (!yappcUIImports.has(componentName) && !localComponents.has(componentName)) {
            context.report({
              node: node.name,
              messageId: 'missingImport',
              data: {
                componentName,
              },
            });
          }
        }
      },
    };
  },
};

export default rule;
