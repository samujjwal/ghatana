/**
 * React Component Renderer
 * Renders actual Ghatana UI components in the DOM overlay
 */

import React from 'react';
import { ThemeProvider } from '@ghatana/theme/provider';
import { createRoot, Root } from 'react-dom/client';
import {
    Button,
    TextField,
    Card,
    Table,
    Form,
    Modal,
    Alert,
    Tabs,
    Badge,
    Spinner,
    Divider,
    Avatar
} from '@ghatana/design-system';
import {
    LineChart,
    BarChart,
    PieChart,
    AreaChart,
    DonutChart,
    MetricChart,
    SparklineChart
} from '@ghatana/charts';

export interface ComponentConfig {
    componentType: string;
    props: Record<string, unknown>;
    children?: React.ReactNode;
    className?: string;
    style?: Record<string, unknown>;
    events?: Record<string, Function>;
}

/**
 * React Component Renderer - Renders Ghatana UI components
 */
export class ReactComponentRenderer {
    private roots: Map<string, Root> = new Map();

    /**
     * Render a Ghatana UI component in the specified container
     */
    renderComponent(container: HTMLElement, config: ComponentConfig): void {
        const componentId = container.getAttribute('data-component-id') || '';

        // Clean up existing root if present
        this.cleanupRoot(componentId);

        // Create new React root
        const root = createRoot(container);
        this.roots.set(componentId, root);

        // Render the component
        const reactElement = this.createReactElement(config);
        root.render(
            React.createElement(ThemeProvider, null, reactElement)
        );
    }

    /**
     * Create React element based on component type
     */
    private createReactElement(config: ComponentConfig): React.ReactElement {
        const { componentType, props = {}, children, className, style, events } = config;

        // Merge style and className into props
        const mergedProps = {
            ...props,
            ...(className && { className }),
            ...(style && { style }),
            ...(events && { ...events })
        };

        switch (componentType) {
            case 'button':
                return React.createElement(Button, mergedProps, children);

            case 'input':
            case 'textfield':
                return React.createElement(TextField, mergedProps, children);

            case 'textarea':
                return React.createElement(TextField, { ...mergedProps, multiline: true, rows: props.rows || 4 }, children);

            case 'select':
                return React.createElement(TextField, { ...mergedProps, select: true }, children);

            case 'checkbox':
                return React.createElement('input', { type: 'checkbox', ...mergedProps });

            case 'radio':
                return React.createElement('input', { type: 'radio', ...mergedProps });

            case 'card':
                return React.createElement(Card, mergedProps, children);

            case 'table':
                // Table requires either data-driven props (TableDataProps) or markup props (TableMarkupProps)
                if (props.columns && props.data) {
                    // Data-driven table - cast to TableDataProps
                    const dataProps = {
                        columns: props.columns,
                        data: props.data,
                        striped: props.striped,
                        hoverable: props.hoverable,
                        className: className,
                        style: style
                    };
                    return React.createElement(Table as React.ComponentType<Record<string, unknown>>, dataProps);
                } else {
                    // Markup table - use TableMarkupProps interface
                    const markupProps = {
                        className: className,
                        style: style,
                        children: children || React.createElement('tbody', {},
                            React.createElement('tr', {},
                                React.createElement('td', { style: { padding: '8px', border: '1px solid #e5e7eb' } }, 'Table Content')
                            )
                        )
                    };
                    return React.createElement(Table as React.ComponentType<Record<string, unknown>>, markupProps);
                }

            case 'form':
                const formProps = {
                    onSubmit: props.onSubmit || (() => { }),
                    initialValues: props.initialValues,
                    className: className
                };
                return React.createElement(Form as React.ComponentType<Record<string, unknown>>, formProps, children || React.createElement('div', {}, 'Form Content'));

            case 'modal':
                return React.createElement(Modal, {
                    ...mergedProps,
                    onClose: props.onClose || (() => { }),
                    open: props.open !== false
                }, children);

            case 'dialog':
                return React.createElement(Modal, {
                    ...mergedProps,
                    onClose: props.onClose || (() => { }),
                    open: props.open !== false
                }, children);

            case 'alert':
                return React.createElement(Alert, mergedProps, children);

            case 'tabs':
                return React.createElement(Tabs, mergedProps, children);

            case 'badge':
                return React.createElement(Badge, mergedProps, children);

            case 'spinner':
                return React.createElement(Spinner, mergedProps);

            case 'divider':
                return React.createElement(Divider, mergedProps);

            case 'avatar':
                return React.createElement(Avatar, mergedProps, children);

            case 'line-chart':
                return React.createElement(LineChart, mergedProps);

            case 'bar-chart':
                return React.createElement(BarChart, mergedProps);

            case 'pie-chart':
                return React.createElement(PieChart, mergedProps);

            case 'area-chart':
                return React.createElement(AreaChart as React.ComponentType<Record<string, unknown>>, mergedProps);

            case 'donut-chart':
                return React.createElement(DonutChart as React.ComponentType<Record<string, unknown>>, mergedProps);

            case 'metric-chart':
                return React.createElement(MetricChart as React.ComponentType<Record<string, unknown>>, mergedProps);

            case 'sparkline-chart':
                return React.createElement(SparklineChart as React.ComponentType<Record<string, unknown>>, mergedProps);

            case 'chart':
                // Placeholder for generic chart components
                return React.createElement('div', {
                    style: {
                        padding: '16px',
                        border: '1px solid #e5e7eb',
                        borderRadius: '8px',
                        textAlign: 'center',
                        backgroundColor: '#f9fafb'
                    },
                    ...mergedProps
                }, `Chart: ${props.type || 'Visualization'}`);

            case 'data-grid':
                // Placeholder for data grid
                return React.createElement('div', {
                    style: {
                        padding: '16px',
                        border: '1px solid #e5e7eb',
                        borderRadius: '8px',
                        textAlign: 'center',
                        backgroundColor: '#f9fafb'
                    },
                    ...mergedProps
                }, 'Data Grid');

            case 'app-header':
                // Placeholder for app header
                return React.createElement('header', {
                    style: {
                        padding: '16px',
                        borderBottom: '1px solid #e5e7eb',
                        backgroundColor: '#ffffff'
                    },
                    ...mergedProps
                }, 'App Header');

            case 'sidebar':
                // Placeholder for sidebar
                return React.createElement('aside', {
                    style: {
                        width: '250px',
                        padding: '16px',
                        borderRight: '1px solid #e5e7eb',
                        backgroundColor: '#f9fafb'
                    },
                    ...mergedProps
                }, 'Sidebar');

            case 'toolbar':
                // Placeholder for toolbar
                return React.createElement('div', {
                    style: {
                        padding: '8px 16px',
                        borderBottom: '1px solid #e5e7eb',
                        backgroundColor: '#ffffff',
                        display: 'flex',
                        gap: '8px'
                    },
                    ...mergedProps
                }, 'Toolbar');

            default:
                return React.createElement('div', {
                    style: {
                        padding: '16px',
                        border: '1px dashed #d1d5db',
                        borderRadius: '6px',
                        backgroundColor: '#f9fafb',
                        textAlign: 'center'
                    },
                    ...mergedProps
                }, `${componentType} Component`);
        }
    }

    /**
     * Update an existing component
     */
    updateComponent(container: HTMLElement, config: ComponentConfig): void {
        this.renderComponent(container, config);
    }

    /**
     * Clean up React root for a component
     */
    private cleanupRoot(componentId: string): void {
        const root = this.roots.get(componentId);
        if (root) {
            root.unmount();
            this.roots.delete(componentId);
        }
    }

    /**
     * Clean up all components
     */
    cleanup(): void {
        this.roots.forEach(root => root.unmount());
        this.roots.clear();
    }

    /**
     * Get component configuration from DOM element
     */
    getComponentConfig(element: HTMLElement): ComponentConfig | null {
        return ((element as unknown as Record<string, unknown>).__ghatanaConfig as ComponentConfig) || null;
    }
}
