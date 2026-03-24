/**
 * @doc.type service
 * @doc.purpose Storybook story generation service for Journey 7.1 (Frontend Engineer - Component Development)
 * @doc.layer product
 * @doc.pattern Service
 */

import type { Node } from '@xyflow/react';

/**
 * Component prop definition
 */
export interface ComponentProp {
    name: string;
    type: string;
    required: boolean;
    defaultValue?: string;
    description?: string;
}

/**
 * Story variant definition
 */
export interface StoryVariant {
    name: string;
    args: Record<string, unknown>;
    description?: string;
}

/**
 * Storybook story generation options
 */
export interface StorybookGenerationOptions {
    componentName: string;
    props: ComponentProp[];
    variants?: StoryVariant[];
    includeActions?: boolean;
    includeControls?: boolean;
    includeDocsPage?: boolean;
}

/**
 * Generated story result
 */
export interface GeneratedStory {
    filename: string;
    content: string;
    variants: number;
}

/**
 * Storybook Service
 * 
 * Generates Storybook `.stories.tsx` files with multiple variants
 * for component visualization and testing.
 */
export class StorybookService {
    /**
     * Generate Storybook story file
     */
    static generateStory(options: StorybookGenerationOptions): GeneratedStory {
        const {
            componentName,
            props,
            variants = [],
            includeActions = true,
            includeControls = true,
            includeDocsPage = true,
        } = options;

        const filename = `${componentName}.stories.tsx`;

        // Generate default variants if not provided
        const allVariants = variants.length > 0
            ? variants
            : this.generateDefaultVariants(componentName, props);

        const content = this.generateStorybookContent(
            componentName,
            props,
            allVariants,
            includeActions,
            includeControls,
            includeDocsPage
        );

        return {
            filename,
            content,
            variants: allVariants.length,
        };
    }

    /**
     * Generate default story variants based on prop types
     */
    private static generateDefaultVariants(
        componentName: string,
        props: ComponentProp[]
    ): StoryVariant[] {
        const variants: StoryVariant[] = [];

        // Default variant
        const defaultArgs: Record<string, unknown> = {};
        props.forEach(prop => {
            if (prop.defaultValue !== undefined) {
                defaultArgs[prop.name] = this.parseDefaultValue(prop.defaultValue, prop.type);
            } else {
                defaultArgs[prop.name] = this.getTypeDefaultValue(prop.type);
            }
        });

        variants.push({
            name: 'Default',
            args: defaultArgs,
            description: `Default ${componentName} configuration`,
        });

        // With all props variant
        const withAllPropsArgs: Record<string, unknown> = {};
        props.forEach(prop => {
            withAllPropsArgs[prop.name] = this.getTypeExampleValue(prop.type, prop.name);
        });

        variants.push({
            name: 'WithAllProps',
            args: withAllPropsArgs,
            description: `${componentName} with all props provided`,
        });

        // Edge cases
        const hasStringProp = props.some(p => p.type === 'string');
        if (hasStringProp) {
            const emptyStringArgs = { ...defaultArgs };
            props
                .filter(p => p.type === 'string')
                .forEach(p => {
                    emptyStringArgs[p.name] = '';
                });

            variants.push({
                name: 'EmptyStrings',
                args: emptyStringArgs,
                description: 'Edge case with empty string values',
            });
        }

        const hasBooleanProp = props.some(p => p.type === 'boolean');
        if (hasBooleanProp) {
            const allTrueArgs = { ...defaultArgs };
            props
                .filter(p => p.type === 'boolean')
                .forEach(p => {
                    allTrueArgs[p.name] = true;
                });

            variants.push({
                name: 'AllBooleanTrue',
                args: allTrueArgs,
                description: 'All boolean props set to true',
            });
        }

        return variants;
    }

    /**
     * Generate Storybook content
     */
    private static generateStorybookContent(
        componentName: string,
        props: ComponentProp[],
        variants: StoryVariant[],
        includeActions: boolean,
        includeControls: boolean,
        includeDocsPage: boolean
    ): string {
        const propsInterface = this.generatePropsInterface(componentName, props);
        const argTypes = this.generateArgTypes(props, includeActions, includeControls);
        const stories = variants.map(v => this.generateStoryVariant(componentName, v)).join('\n\n');

        return `import type { Meta, StoryObj } from '@storybook/react';
import { ${componentName} } from './${componentName}';
${includeActions ? "import { action } from '@storybook/addon-actions';" : ''}

${propsInterface}

const meta: Meta<typeof ${componentName}> = {
  title: 'Components/${componentName}',
  component: ${componentName},
  parameters: {
    layout: 'centered',
    ${includeDocsPage ? `docs: {
      description: {
        component: 'Auto-generated ${componentName} component from YAPPC canvas.',
      },
    },` : ''}
  },
  tags: ['autodocs'],
  ${argTypes ? `argTypes: ${argTypes},` : ''}
};

export default meta;
type Story = StoryObj<typeof ${componentName}>;

${stories}
`;
    }

    /**
     * Generate TypeScript interface for props
     */
    private static generatePropsInterface(
        componentName: string,
        props: ComponentProp[]
    ): string {
        if (props.length === 0) return '';

        const propsStr = props
            .map(prop => {
                const optional = prop.required ? '' : '?';
                const description = prop.description ? `  /** ${prop.description} */\n` : '';
                return `${description}  ${prop.name}${optional}: ${prop.type};`;
            })
            .join('\n');

        return `export interface ${componentName}Props {
${propsStr}
}`;
    }

    /**
     * Generate argTypes for Storybook controls
     */
    private static generateArgTypes(
        props: ComponentProp[],
        includeActions: boolean,
        includeControls: boolean
    ): string | null {
        if (!includeControls && !includeActions) return null;

        const argTypesObj: Record<string, unknown> = {};

        props.forEach(prop => {
            const argType: Record<string, unknown> = {};

            if (prop.description) {
                argType.description = prop.description;
            }

            if (includeControls) {
                argType.control = this.getControlType(prop.type);
            }

            if (includeActions && this.isActionType(prop.type)) {
                argType.action = prop.name;
            }

            if (Object.keys(argType).length > 0) {
                argTypesObj[prop.name] = argType;
            }
        });

        return Object.keys(argTypesObj).length > 0
            ? JSON.stringify(argTypesObj, null, 2)
            : null;
    }

    /**
     * Generate individual story variant code
     */
    private static generateStoryVariant(componentName: string, variant: StoryVariant): string {
        const argsStr = JSON.stringify(variant.args, null, 2)
            .split('\n')
            .map((line, idx) => (idx === 0 ? line : `  ${line}`))
            .join('\n');

        return `export const ${variant.name}: Story = {
  ${variant.description ? `/** ${variant.description} */\n  ` : ''}args: ${argsStr},
};`;
    }

    /**
     * Get Storybook control type for a TypeScript type
     */
    private static getControlType(type: string): string {
        if (type === 'string') return 'text';
        if (type === 'number') return 'number';
        if (type === 'boolean') return 'boolean';
        if (type.includes('|')) return 'select';
        if (type.includes('[]')) return 'object';
        return 'object';
    }

    /**
     * Check if type is an action (function)
     */
    private static isActionType(type: string): boolean {
        return type.includes('=>') || type.includes('function') || type.startsWith('on');
    }

    /**
     * Get default value for a type
     */
    private static getTypeDefaultValue(type: string): unknown {
        if (type === 'string') return '';
        if (type === 'number') return 0;
        if (type === 'boolean') return false;
        if (type.includes('[]')) return [];
        if (type.includes('|')) {
            const options = type.split('|').map(s => s.trim().replace(/['"]/g, ''));
            return options[0];
        }
        return null;
    }

    /**
     * Get example value for a type
     */
    private static getTypeExampleValue(type: string, propName: string): unknown {
        if (type === 'string') {
            if (propName.toLowerCase().includes('name')) return 'John Doe';
            if (propName.toLowerCase().includes('email')) return 'john@example.com';
            if (propName.toLowerCase().includes('title')) return 'Sample Title';
            if (propName.toLowerCase().includes('description')) return 'This is a sample description';
            return 'Sample text';
        }
        if (type === 'number') {
            if (propName.toLowerCase().includes('count')) return 42;
            if (propName.toLowerCase().includes('age')) return 25;
            if (propName.toLowerCase().includes('price')) return 99.99;
            return 123;
        }
        if (type === 'boolean') return true;
        if (type.includes('[]')) return ['item1', 'item2', 'item3'];
        if (type.includes('|')) {
            const options = type.split('|').map(s => s.trim().replace(/['"]/g, ''));
            return options[0];
        }
        if (type.includes('User')) {
            return { id: 1, name: 'John Doe', email: 'john@example.com' };
        }
        return { example: 'data' };
    }

    /**
     * Parse default value string to actual value
     */
    private static parseDefaultValue(value: string, type: string): unknown {
        if (type === 'string') return value.replace(/['"]/g, '');
        if (type === 'number') return parseFloat(value);
        if (type === 'boolean') return value === 'true';
        try {
            return JSON.parse(value);
        } catch {
            return value;
        }
    }

    /**
     * Generate story from canvas node
     */
    static generateFromNode(node: Node): GeneratedStory {
        const componentName = node.data.label || node.data.name || 'Component';
        const props = node.data.props || [];

        return this.generateStory({
            componentName,
            props,
            includeActions: true,
            includeControls: true,
            includeDocsPage: true,
        });
    }
}
