// Lightweight local eslint rules plugin used for local development.
// Enforces importing @ghatana/ui components through the @/components/ui barrel.

/**
 * List of known @ghatana/ui components that should be imported via barrel.
 * Expand this list as new components are added to the design system.
 */
const GHATANA_UI_COMPONENTS = [
    // Atoms
    'Badge', 'Button', 'Checkbox', 'Icon', 'Input', 'Label', 'Radio',
    'Select', 'Switch', 'Textarea', 'Toggle', 'Avatar', 'Spinner',
    // Molecules
    'Card', 'Dropdown', 'Modal', 'Popover', 'Tooltip', 'Tabs',
    'Accordion', 'Alert', 'Breadcrumb', 'Pagination', 'Toast',
    // Organisms
    'Table', 'DataTable', 'Form', 'Navigation', 'Sidebar',
    'Header', 'Footer', 'KpiCard', 'MetricCard',
    // Charts
    'LineChart', 'AreaChart', 'BarChart', 'PieChart', 'ScatterChart',
    'ChartContainer', 'ChartTooltip', 'ChartLegend',
];

module.exports = {
    rules: {
        'prefer-ghatana-ui': {
            meta: {
                type: 'suggestion',
                docs: {
                    description: 'Prefer imports from @/components/ui barrel export instead of direct @ghatana/ui imports',
                    category: 'Best Practices',
                    recommended: true,
                },
                fixable: 'code',
                schema: [],
                messages: {
                    preferBarrel: 'Import "{{ name }}" from "@/components/ui" instead of "@ghatana/ui"',
                },
            },
            create(context) {
                return {
                    ImportDeclaration(node) {
                        // Check if importing from @ghatana/ui
                        if (node.source.value !== '@ghatana/ui') {
                            return;
                        }

                        // Check each imported specifier
                        node.specifiers.forEach((specifier) => {
                            if (specifier.type === 'ImportSpecifier') {
                                const importedName = specifier.imported.name;

                                // Check if it's a known component
                                if (GHATANA_UI_COMPONENTS.includes(importedName)) {
                                    context.report({
                                        node: specifier,
                                        messageId: 'preferBarrel',
                                        data: {
                                            name: importedName,
                                        },
                                        fix(fixer) {
                                            // Auto-fix: replace source with @/components/ui
                                            return fixer.replaceText(node.source, '"@/components/ui"');
                                        },
                                    });
                                }
                            }
                        });
                    },
                };
            },
        },
    },
};
