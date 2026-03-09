/**
 * @doc.type test
 * @doc.purpose Unit tests for StorybookService (Journey 7.1 - Frontend Engineer Component Development)
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect } from 'vitest';
import { StorybookService, type ComponentProp, type StoryVariant } from '../StorybookService';
import type { Node } from '@xyflow/react';

describe('StorybookService', () => {
    describe('generateStory', () => {
        it('should generate basic story file', () => {
            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [
                    { name: 'label', type: 'string', required: true, description: 'Button label' },
                    { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false' },
                ],
            });

            expect(result.filename).toBe('Button.stories.tsx');
            expect(result.content).toContain('import type { Meta, StoryObj }');
            expect(result.content).toContain('import { Button }');
            expect(result.content).toContain('export default meta');
            expect(result.variants).toBeGreaterThan(0);
        });

        it('should include component props interface', () => {
            const result = StorybookService.generateStory({
                componentName: 'Card',
                props: [
                    { name: 'title', type: 'string', required: true },
                    { name: 'subtitle', type: 'string', required: false },
                ],
            });

            expect(result.content).toContain('export interface CardProps');
            expect(result.content).toContain('title: string');
            expect(result.content).toContain('subtitle?: string');
        });

        it('should generate default variants when none provided', () => {
            const result = StorybookService.generateStory({
                componentName: 'Input',
                props: [
                    { name: 'value', type: 'string', required: true },
                    { name: 'placeholder', type: 'string', required: false },
                ],
            });

            expect(result.variants).toBeGreaterThanOrEqual(2);
            expect(result.content).toContain('export const Default:');
            expect(result.content).toContain('export const WithAllProps:');
        });

        it('should use custom variants when provided', () => {
            const variants: StoryVariant[] = [
                { name: 'Primary', args: { variant: 'primary' } },
                { name: 'Secondary', args: { variant: 'secondary' } },
                { name: 'Disabled', args: { variant: 'primary', disabled: true } },
            ];

            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [
                    { name: 'variant', type: "'primary' | 'secondary'", required: true },
                    { name: 'disabled', type: 'boolean', required: false },
                ],
                variants,
            });

            expect(result.variants).toBe(3);
            expect(result.content).toContain('export const Primary:');
            expect(result.content).toContain('export const Secondary:');
            expect(result.content).toContain('export const Disabled:');
        });

        it('should include actions addon when requested', () => {
            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [
                    { name: 'onClick', type: '() => void', required: false },
                ],
                includeActions: true,
            });

            expect(result.content).toContain("import { action } from '@storybook/addon-actions'");
        });

        it('should not include actions when not requested', () => {
            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [
                    { name: 'onClick', type: '() => void', required: false },
                ],
                includeActions: false,
            });

            expect(result.content).not.toContain("import { action } from '@storybook/addon-actions'");
        });

        it('should include argTypes for controls', () => {
            const result = StorybookService.generateStory({
                componentName: 'Input',
                props: [
                    { name: 'label', type: 'string', required: true, description: 'Input label' },
                    { name: 'disabled', type: 'boolean', required: false },
                ],
                includeControls: true,
            });

            expect(result.content).toContain('argTypes:');
            expect(result.content).toContain('"label"');
            expect(result.content).toContain('"disabled"');
        });

        it('should include docs page configuration', () => {
            const result = StorybookService.generateStory({
                componentName: 'Card',
                props: [],
                includeDocsPage: true,
            });

            expect(result.content).toContain('docs:');
            expect(result.content).toContain('description:');
            expect(result.content).toContain('Auto-generated Card component');
        });

        it('should not include docs when not requested', () => {
            const result = StorybookService.generateStory({
                componentName: 'Card',
                props: [],
                includeDocsPage: false,
            });

            expect(result.content).not.toContain('docs:');
        });

        it('should handle components with no props', () => {
            const result = StorybookService.generateStory({
                componentName: 'Logo',
                props: [],
            });

            expect(result.filename).toBe('Logo.stories.tsx');
            expect(result.content).toContain('import { Logo }');
            expect(result.variants).toBeGreaterThan(0);
        });

        it('should handle props with descriptions', () => {
            const result = StorybookService.generateStory({
                componentName: 'Tooltip',
                props: [
                    {
                        name: 'content',
                        type: 'string',
                        required: true,
                        description: 'Tooltip content to display'
                    },
                    {
                        name: 'placement',
                        type: "'top' | 'bottom' | 'left' | 'right'",
                        required: false,
                        description: 'Tooltip placement relative to trigger'
                    },
                ],
            });

            expect(result.content).toContain('/** Tooltip content to display */');
            expect(result.content).toContain('/** Tooltip placement relative to trigger */');
        });
    });

    describe('Default Variants Generation', () => {
        it('should generate empty strings variant for string props', () => {
            const result = StorybookService.generateStory({
                componentName: 'Input',
                props: [
                    { name: 'value', type: 'string', required: true },
                    { name: 'placeholder', type: 'string', required: false },
                ],
            });

            expect(result.content).toContain('export const EmptyStrings:');
        });

        it('should generate all boolean true variant for boolean props', () => {
            const result = StorybookService.generateStory({
                componentName: 'Switch',
                props: [
                    { name: 'checked', type: 'boolean', required: true },
                    { name: 'disabled', type: 'boolean', required: false },
                ],
            });

            expect(result.content).toContain('export const AllBooleanTrue:');
        });

        it('should generate default values from defaultValue prop', () => {
            const result = StorybookService.generateStory({
                componentName: 'Counter',
                props: [
                    { name: 'initial', type: 'number', required: false, defaultValue: '10' },
                    { name: 'step', type: 'number', required: false, defaultValue: '1' },
                ],
            });

            expect(result.content).toContain('"initial": 10');
            expect(result.content).toContain('"step": 1');
        });

        it('should generate appropriate example values for named props', () => {
            const result = StorybookService.generateStory({
                componentName: 'UserCard',
                props: [
                    { name: 'name', type: 'string', required: true },
                    { name: 'email', type: 'string', required: true },
                    { name: 'age', type: 'number', required: false },
                ],
            });

            expect(result.content).toContain('John Doe');
            expect(result.content).toContain('john@example.com');
            expect(result.content).toContain('25');
        });
    });

    describe('Control Type Generation', () => {
        it('should generate text control for string type', () => {
            const result = StorybookService.generateStory({
                componentName: 'Input',
                props: [{ name: 'value', type: 'string', required: true }],
                includeControls: true,
            });

            expect(result.content).toContain('"control": "text"');
        });

        it('should generate number control for number type', () => {
            const result = StorybookService.generateStory({
                componentName: 'Counter',
                props: [{ name: 'count', type: 'number', required: true }],
                includeControls: true,
            });

            expect(result.content).toContain('"control": "number"');
        });

        it('should generate boolean control for boolean type', () => {
            const result = StorybookService.generateStory({
                componentName: 'Toggle',
                props: [{ name: 'enabled', type: 'boolean', required: true }],
                includeControls: true,
            });

            expect(result.content).toContain('"control": "boolean"');
        });

        it('should generate select control for union types', () => {
            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [{ name: 'size', type: "'small' | 'medium' | 'large'", required: true }],
                includeControls: true,
            });

            expect(result.content).toContain('"control": "select"');
        });

        it('should generate object control for array types', () => {
            const result = StorybookService.generateStory({
                componentName: 'List',
                props: [{ name: 'items', type: 'string[]', required: true }],
                includeControls: true,
            });

            expect(result.content).toContain('"control": "object"');
        });
    });

    describe('Action Detection', () => {
        it('should detect function types as actions', () => {
            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [{ name: 'onClick', type: '() => void', required: false }],
                includeActions: true,
                includeControls: true,
            });

            expect(result.content).toContain('"action": "onClick"');
        });

        it('should detect props starting with "on" as actions', () => {
            const result = StorybookService.generateStory({
                componentName: 'Input',
                props: [{ name: 'onChange', type: 'string', required: false }],
                includeActions: true,
                includeControls: true,
            });

            expect(result.content).toContain('"action": "onChange"');
        });

        it('should detect arrow function types as actions', () => {
            const result = StorybookService.generateStory({
                componentName: 'Form',
                props: [{ name: 'onSubmit', type: '(data: FormData) => void', required: false }],
                includeActions: true,
                includeControls: true,
            });

            expect(result.content).toContain('"action": "onSubmit"');
        });
    });

    describe('generateFromNode', () => {
        it('should generate story from canvas node', () => {
            const node: Node = {
                id: '1',
                type: 'wireframe',
                position: { x: 0, y: 0 },
                data: {
                    label: 'ProfileCard',
                    props: [
                        { name: 'user', type: 'User', required: true },
                        { name: 'editable', type: 'boolean', required: false },
                    ],
                },
            };

            const result = StorybookService.generateFromNode(node);

            expect(result.filename).toBe('ProfileCard.stories.tsx');
            expect(result.content).toContain('ProfileCard');
            expect(result.content).toContain('user');
            expect(result.content).toContain('editable');
        });

        it('should handle node with no label', () => {
            const node: Node = {
                id: '1',
                type: 'wireframe',
                position: { x: 0, y: 0 },
                data: {
                    name: 'MyComponent',
                    props: [],
                },
            };

            const result = StorybookService.generateFromNode(node);

            expect(result.filename).toBe('MyComponent.stories.tsx');
        });

        it('should handle node with no props', () => {
            const node: Node = {
                id: '1',
                type: 'wireframe',
                position: { x: 0, y: 0 },
                data: {
                    label: 'Logo',
                },
            };

            const result = StorybookService.generateFromNode(node);

            expect(result.filename).toBe('Logo.stories.tsx');
            expect(result.variants).toBeGreaterThan(0);
        });

        it('should default to "Component" when no name provided', () => {
            const node: Node = {
                id: '1',
                type: 'wireframe',
                position: { x: 0, y: 0 },
                data: {},
            };

            const result = StorybookService.generateFromNode(node);

            expect(result.filename).toBe('Component.stories.tsx');
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle component with many props', () => {
            const props: ComponentProp[] = [
                { name: 'title', type: 'string', required: true, description: 'Card title' },
                { name: 'subtitle', type: 'string', required: false, description: 'Card subtitle' },
                { name: 'description', type: 'string', required: false, description: 'Card description' },
                { name: 'image', type: 'string', required: false, description: 'Image URL' },
                { name: 'variant', type: "'default' | 'outlined' | 'elevated'", required: false, defaultValue: "'default'" },
                { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false' },
                { name: 'onClick', type: '() => void', required: false, description: 'Click handler' },
                { name: 'onDelete', type: '() => void', required: false, description: 'Delete handler' },
            ];

            const result = StorybookService.generateStory({
                componentName: 'Card',
                props,
                includeActions: true,
                includeControls: true,
                includeDocsPage: true,
            });

            expect(result.content).toContain('export interface CardProps');
            expect(result.content).toContain('title: string');
            expect(result.content).toContain('subtitle?: string');
            expect(result.content).toContain('onClick?: () => void');
            expect(result.content).toContain('variant?:');
            expect(result.variants).toBeGreaterThan(2);
        });

        it('should handle component with complex type definitions', () => {
            const props: ComponentProp[] = [
                { name: 'data', type: 'Array<{id: string; name: string}>', required: true },
                { name: 'onSelect', type: '(item: {id: string; name: string}) => void', required: false },
                { name: 'renderItem', type: '(item: {id: string; name: string}) => React.ReactNode', required: false },
            ];

            const result = StorybookService.generateStory({
                componentName: 'DataList',
                props,
                includeActions: true,
                includeControls: true,
            });

            expect(result.filename).toBe('DataList.stories.tsx');
            expect(result.content).toContain('data:');
            expect(result.content).toContain('onSelect');
            expect(result.content).toContain('renderItem');
        });

        it('should generate multiple variants with different combinations', () => {
            const variants: StoryVariant[] = [
                {
                    name: 'Default',
                    args: { text: 'Click me', variant: 'default' },
                    description: 'Default button state'
                },
                {
                    name: 'Primary',
                    args: { text: 'Primary', variant: 'primary', size: 'large' },
                    description: 'Primary variant with large size'
                },
                {
                    name: 'Disabled',
                    args: { text: 'Disabled', variant: 'default', disabled: true },
                    description: 'Disabled state'
                },
                {
                    name: 'Loading',
                    args: { text: 'Loading...', loading: true },
                    description: 'Loading state with spinner'
                },
            ];

            const result = StorybookService.generateStory({
                componentName: 'Button',
                props: [
                    { name: 'text', type: 'string', required: true },
                    { name: 'variant', type: "'default' | 'primary' | 'secondary'", required: false },
                    { name: 'size', type: "'small' | 'medium' | 'large'", required: false },
                    { name: 'disabled', type: 'boolean', required: false },
                    { name: 'loading', type: 'boolean', required: false },
                ],
                variants,
            });

            expect(result.variants).toBe(4);
            expect(result.content).toContain('/** Default button state */');
            expect(result.content).toContain('/** Primary variant with large size */');
            expect(result.content).toContain('/** Disabled state */');
            expect(result.content).toContain('/** Loading state with spinner */');
        });
    });
});
