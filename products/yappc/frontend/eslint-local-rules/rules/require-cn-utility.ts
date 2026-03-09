/**
 * ESLint rule: require-cn-utility
 * 
 * Enforces using cn() utility for className merging in component props.
 * 
 * Ensures proper Tailwind class merging when components accept className prop.
 * 
 * @example
 * ```tsx
 * // ❌ BAD
 * <button className={`px-4 py-2 ${className}`} />
 * <button className={clsx('px-4', className)} />
 * 
 * // ✅ GOOD
 * <button className={cn('px-4 py-2', className)} />
 * ```
 */
import type { Rule } from 'eslint';

/** Minimal AST node shapes used by this rule (base eslint lacks JSX types) */
interface ImportSpecifierNode { type: string; imported?: { name: string }; }
interface ImportDeclarationNode extends Rule.Node {
  source: { value: string };
  specifiers: ImportSpecifierNode[];
}
interface ASTExprNode { type: string; name?: string; property?: { name: string }; }
interface JSXExpressionValue {
  type: string;
  expressions?: ASTExprNode[];
  callee?: { type: string; name?: string };
  arguments?: ASTExprNode[];
}
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
      description: 'Require cn() utility for className merging',
      category: 'Best Practices',
      recommended: true,
    },
    messages: {
      requireCn: 'Use cn() utility for className merging. Import from @ghatana/yappc-shared-ui-core/utils/cn',
      importCn: 'Import cn from @ghatana/ui or @ghatana/yappc-shared-ui-core/utils/cn',
    },
    schema: [],
  },
  
  create(context: Rule.RuleContext): Rule.RuleListener {
    let hasCnImport = false;
    
    return {
      ImportDeclaration(node: ImportDeclarationNode) {
        // Check if cn is imported from design system
        if (
          node.source.value === '@ghatana/ui' ||
          node.source.value === '@ghatana/yappc-shared-ui-core/utils/cn' ||
          node.source.value === '@ghatana/yappc-shared-ui-core/utils'
        ) {
          const cnSpecifier = node.specifiers.find(
            (spec: ImportSpecifierNode) =>
              (spec.type === 'ImportSpecifier' && spec.imported.name === 'cn') ||
              (spec.type === 'ImportDefaultSpecifier')
          );
          
          if (cnSpecifier) {
            hasCnImport = true;
          }
        }
        
        // Also check for clsx import (should suggest cn instead)
        if (node.source.value === 'clsx') {
          const sourceCode = context.getSourceCode();
          const fileContent = sourceCode.getText();
          
          // Only suggest if file uses className prop merging
          if (fileContent.includes('className') && fileContent.includes('clsx(')) {
            context.report({
              node,
              messageId: 'importCn',
            });
          }
        }
      },
      
      JSXAttribute(node: JSXAttributeNode) {
        // Check if attribute is "className"
        if (
          node.name.type === 'JSXIdentifier' &&
          node.name.name === 'className' &&
          node.value?.type === 'JSXExpressionContainer'
        ) {
          const expression = node.value.expression;
          
          // Check for template literals with className variable
          if (expression.type === 'TemplateLiteral') {
            const hasClassNameVar = expression.expressions.some((expr: ASTExprNode) => {
              return (
                (expr.type === 'Identifier' && expr.name === 'className') ||
                (expr.type === 'MemberExpression' && expr.property.name === 'className')
              );
            });
            
            if (hasClassNameVar && !hasCnImport) {
              context.report({
                node,
                messageId: 'requireCn',
              });
            }
          }
          
          // Check for clsx() calls
          if (expression.type === 'CallExpression') {
            const callee = expression.callee;
            
            if (callee.type === 'Identifier' && callee.name === 'clsx') {
              const hasClassNameArg = expression.arguments.some((arg: ASTExprNode) => {
                return (
                  (arg.type === 'Identifier' && arg.name === 'className') ||
                  (arg.type === 'MemberExpression' && arg.property.name === 'className')
                );
              });
              
              if (hasClassNameArg) {
                context.report({
                  node,
                  messageId: 'requireCn',
                });
              }
            }
          }
        }
      },
    };
  },
};

export default rule;
