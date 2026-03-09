/**
 * @fileoverview ESLint rule to enforce @ghatana/ui imports via local barrel
 * @author Software-Org Team
 */

import type { Rule } from 'eslint';

/**
 * Components that should be imported from @ghatana/ui via @/components/ui barrel
 */
const GHATANA_UI_COMPONENTS = [
    // Atoms
    'Button', 'IconButton', 'TextField', 'TextArea', 'Badge', 'Spinner',
    'Checkbox', 'Switch', 'Skeleton', 'Tooltip', 'Chip', 'Select', 'Slider',
    'DatePicker', 'FileUpload', 'Avatar', 'Progress', 'Rating',

    // Molecules
    'FormField', 'Alert', 'Card', 'Modal', 'Table', 'Tabs', 'Breadcrumb',
    'Pagination', 'Toast', 'Menu', 'Stepper', 'InteractiveList', 'Timeline',
    'AppBar', 'ConfirmDialog', 'ActionSheet', 'TreeView', 'Form', 'NavLink',
    'Sidebar', 'Toolbar', 'CommandPalette', 'DateRangePicker',

    // Organisms
    'DashboardLayout', 'AppHeader', 'AppSidebar', 'ErrorBoundary',
    'ProtectedRoute', 'DynamicForm', 'ActivityFeed', 'DataGrid', 'StatsDashboard',

    // Data Components
    'TreeTable', 'SplitPane',

    // Layout
    'Box', 'Stack', 'Container', 'Grid', 'Surface', 'Spacer',
];

const rule: Rule.RuleModule = {
    meta: {
        type: 'problem',
        docs: {
            description: 'Enforce @ghatana/ui imports via local barrel (@/components/ui)',
            category: 'Best Practices',
            recommended: true,
        },
        messages: {
            useLocalBarrel:
                'Import "{{componentName}}" from @/components/ui barrel instead of @ghatana/ui directly. Change to: import { {{componentName}} } from \'@/components/ui\';',
            useGhatanaUI:
                'Component "{{componentName}}" should be imported from @/components/ui instead of creating a custom implementation.',
        },
        fixable: 'code',
        schema: [],
    },

    create(context: Rule.RuleContext): Rule.RuleListener {
        return {
            /**
             * Check all import declarations
             */
            ImportDeclaration(node: any): void {
                const source = node.source.value;

                // Detect direct @ghatana/ui imports (should use barrel)
                if (typeof source === 'string' && source === '@ghatana/ui') {
                    const specifiers = node.specifiers;

                    specifiers.forEach((specifier: any) => {
                        if (specifier.type === 'ImportSpecifier' && specifier.imported.type === 'Identifier') {
                            const componentName = specifier.imported.name;

                            // Check if this is a known @ghatana/ui component
                            if (GHATANA_UI_COMPONENTS.includes(componentName)) {
                                context.report({
                                    node: node as any,
                                    messageId: 'useLocalBarrel',
                                    data: {
                                        componentName,
                                    },
                                    fix(fixer) {
                                        // Auto-fix: change @ghatana/ui to @/components/ui
                                        const sourceRange = node.source.range;
                                        if (sourceRange) {
                                            return fixer.replaceTextRange(
                                                sourceRange,
                                                "'@/components/ui'"
                                            );
                                        }
                                        return null;
                                    },
                                });
                            }
                        }
                    });
                }

                // Detect @ghatana/charts imports (also should use barrel)
                if (typeof source === 'string' && source === '@ghatana/charts') {
                    context.report({
                        node: node as any,
                        messageId: 'useLocalBarrel',
                        data: {
                            componentName: 'charts',
                        },
                        fix(fixer) {
                            const sourceRange = node.source.range;
                            if (sourceRange) {
                                return fixer.replaceTextRange(
                                    sourceRange,
                                    "'@/components/ui'"
                                );
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
