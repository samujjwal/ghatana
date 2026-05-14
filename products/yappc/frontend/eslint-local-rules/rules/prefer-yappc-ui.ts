/**
 * @fileoverview Rule to require @ghatana/design-system components instead of custom
 * implementations or deprecated package aliases. Blocks all imports from @ghatana/ui
 * and other removed packages (fix-forward policy — no backward compatibility).
 * @author YAPPC Team
 */

import type { Rule } from 'eslint';
import type { Node } from 'estree';

/**
 * Forbidden (deprecated / removed) platform package names.
 * Any import from these must be migrated to the canonical package listed in
 * the repo copilot-instructions.md Section 32.
 */
const DEPRECATED_PACKAGES: ReadonlySet<string> = new Set([
  '@ghatana/ui',
  '@ghatana/utils',
  '@ghatana/accessibility-audit',
  '@ghatana/canvas-core',
  '@ghatana/canvas-plugins',
  '@ghatana/canvas-tools',
  '@ghatana/canvas-react',
  '@ghatana/canvas-chrome',
]);

/**
 * Forbidden YAPPC-product-scope package names (invalid double-scoped names or
 * deleted compat packages). Use the canonical @yappc/ui instead.
 */
const YAPPC_FORBIDDEN_PACKAGES: ReadonlyMap<string, string> = new Map([
  ['@yappc/yappc-ui', '@yappc/ui'],
  ['@yappc/initialization-ui', '@yappc/ui (PresetCard / ResourcesList)'],
  ['@yappc/base-ui', '@yappc/ui'],
]);

/** Canonical package replacing most deprecated UI packages. */
const CANONICAL_UI_PACKAGE = '@ghatana/design-system';

/**
 * Patterns identifying app-layer route files. Direct @ghatana/design-system
 * imports are not permitted in these files; use components/ui instead.
 */
const APP_ROUTE_PATTERNS: ReadonlyArray<RegExp> = [
  /\/web\/src\/routes\//,
  /\/apps\/web\/src\/routes\//,
];

/**
 * Component names that should be imported from @ghatana/design-system
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
      preferYappcUI: 'Custom "{{componentName}}" component detected. Use @ghatana/design-system instead: import { {{componentName}} } from \'@ghatana/design-system\';',
      missingImport: 'Component "{{componentName}}" is not imported from @ghatana/design-system. Add: import { {{componentName}} } from \'@ghatana/design-system\';',
      deprecatedPackage: 'Import from deprecated package "{{packageName}}" is forbidden. Migrate to "{{canonical}}" (fix-forward — no backward compatibility shims).',
      yappcForbiddenPackage: 'Import from "{{packageName}}" is forbidden. Use "{{canonical}}" instead.',
      appRouteDirectImport: 'App routes must import UI from \'@/components/ui\' — not directly from "{{packageName}}". Layer rule: web/src/routes → components/ui → @yappc/ui → @ghatana/design-system.',
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
       * Block deprecated packages; track canonical design-system imports.
       */
      ImportDeclaration(node: Node): void {
        if (node.type !== 'ImportDeclaration') return;
        
        const source = node.source.type === 'Literal' && typeof node.source.value === 'string'
          ? node.source.value
          : '';

        // Block deprecated @ghatana/* packages (fix-forward policy)
        if (DEPRECATED_PACKAGES.has(source)) {
          context.report({
            node,
            messageId: 'deprecatedPackage',
            data: {
              packageName: source,
              canonical: CANONICAL_UI_PACKAGE,
            },
          });
          return;
        }

        // Block forbidden @yappc/* double-scoped / deleted compat packages
        const yappcCanonical = YAPPC_FORBIDDEN_PACKAGES.get(source);
        if (yappcCanonical !== undefined) {
          context.report({
            node,
            messageId: 'yappcForbiddenPackage',
            data: {
              packageName: source,
              canonical: yappcCanonical,
            },
          });
          return;
        }

        // In app route files, disallow direct @ghatana/design-system imports.
        // App routes must consume UI through components/ui (the canonical app boundary).
        const filename: string = context.getFilename();
        const isAppRoute = APP_ROUTE_PATTERNS.some((pattern) => pattern.test(filename));
        if (isAppRoute && (source === CANONICAL_UI_PACKAGE || source.startsWith('@ghatana/design-system/'))) {
          context.report({
            node,
            messageId: 'appRouteDirectImport',
            data: { packageName: source },
          });
          return;
        }
        
        if (source === CANONICAL_UI_PACKAGE || source.startsWith('@ghatana/design-system/')) {
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
          
          // Only report if NOT imported from @ghatana/design-system
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
          
          // Only report if NOT imported from @ghatana/design-system
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
          // If not imported from @ghatana/design-system and not declared locally
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
