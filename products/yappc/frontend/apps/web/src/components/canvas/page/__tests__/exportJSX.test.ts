import { describe, test, expect } from 'vitest';

import { exportToJSX } from './exportJSX';

import type { ComponentData } from './schemas';

describe('exportJSX', () => {
  test('should export single button component', () => {
    const components: ComponentData[] = [
      {
        id: 'btn-1',
        type: 'button',
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
        text: 'Click Me',
      },
    ];

    const jsx = exportToJSX(components, 'TestPage');

    expect(jsx).toContain("import React from 'react'");
    expect(jsx).toContain("import { Button } from '@ghatana/ui';");
    expect(jsx).toContain('export const TestPage: React.FC = () => {');
    expect(jsx).toContain('<Button>');
    expect(jsx).toContain('Click Me');
    expect(jsx).toContain('</Button>');
  });

  test('should export card with header and content', () => {
    const components: ComponentData[] = [
      {
        id: 'card-1',
        type: 'card',
        elevation: 3,
        title: 'My Card',
        subtitle: 'Subtitle text',
        content: 'Card content here',
        showActions: false,
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('Card');
    expect(jsx).toContain('CardHeader');
    expect(jsx).toContain('CardContent');
    expect(jsx).toContain('My Card');
    expect(jsx).toContain('Subtitle text');
    expect(jsx).toContain('Card content here');
    expect(jsx).toContain('elevation={3}');
  });

  test('should export text field with properties', () => {
    const components: ComponentData[] = [
      {
        id: 'tf-1',
        type: 'textfield',
        label: 'Email',
        placeholder: 'Enter email',
        variant: 'outlined',
        size: 'medium',
        required: true,
        disabled: false,
        fullWidth: true,
        multiline: false,
        rows: 1,
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('TextField');
    expect(jsx).toContain('label="Email"');
    expect(jsx).toContain('placeholder="Enter email"');
    expect(jsx).toContain('required');
    expect(jsx).toContain('fullWidth');
  });

  test('should export typography component', () => {
    const components: ComponentData[] = [
      {
        id: 'typo-1',
        type: 'typography',
        variant: 'h1',
        text: 'Welcome',
        align: 'center',
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('Typography');
    expect(jsx).toContain('as="h1"');
    expect(jsx).toContain('align="center"');
    expect(jsx).toContain('Welcome');
  });

  test('should export box container with flex properties', () => {
    const components: ComponentData[] = [
      {
        id: 'box-1',
        type: 'box',
        padding: 3,
        margin: 1,
        borderRadius: 2,
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('Box');
    expect(jsx).toContain('p-6');       // padding 3 * 2 = 6
    expect(jsx).toContain('m-2');       // margin 1 * 2 = 2
    expect(jsx).toContain('rounded-lg');
    expect(jsx).toContain('flex');
    expect(jsx).toContain('flex-row');
  });

  test('should export multiple components with fragment', () => {
    const components: ComponentData[] = [
      {
        id: 'typo-1',
        type: 'typography',
        variant: 'h2',
        text: 'Title',
        align: 'left',
      },
      {
        id: 'btn-1',
        type: 'button',
        variant: 'outlined',
        color: 'secondary',
        size: 'large',
        disabled: false,
        fullWidth: false,
        text: 'Submit',
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('<>');
    expect(jsx).toContain('</>');
    expect(jsx).toContain('Typography');
    expect(jsx).toContain('Button');
    expect(jsx).toContain('Title');
    expect(jsx).toContain('Submit');
  });

  test('should include all necessary MUI imports', () => {
    const components: ComponentData[] = [
      {
        id: 'card-1',
        type: 'card',
        elevation: 2,
        title: 'Test',
        content: 'Content',
        showActions: false,
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('Card');
    expect(jsx).toContain('CardHeader');
    expect(jsx).toContain('CardContent');
    expect(jsx).toContain('Typography');
    expect(jsx).toContain("from '@ghatana/ui'");
  });

  test('should use custom component name', () => {
    const components: ComponentData[] = [
      {
        id: 'btn-1',
        type: 'button',
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
        text: 'Test',
      },
    ];

    const jsx = exportToJSX(components, 'CustomComponent');

    expect(jsx).toContain('export const CustomComponent: React.FC = () => {');
    expect(jsx).toContain('export default CustomComponent;');
  });

  test('should handle empty components array', () => {
    const components: ComponentData[] = [];
    const jsx = exportToJSX(components);

    expect(jsx).toContain("import React from 'react'");
    expect(jsx).toContain('export const MyPage: React.FC = () => {');
  });

  test('should omit default values in JSX output', () => {
    const components: ComponentData[] = [
      {
        id: 'btn-1',
        type: 'button',
        variant: 'contained', // default
        color: 'primary', // default
        size: 'medium', // default
        disabled: false,
        fullWidth: false,
        text: 'Button',
      },
    ];

    const jsx = exportToJSX(components);

    // Should not include default values
    expect(jsx).not.toContain('variant="solid"');
    expect(jsx).not.toContain('tone="primary"');
    expect(jsx).not.toContain('size="md"');
  });

  test('should include non-default values', () => {
    const components: ComponentData[] = [
      {
        id: 'btn-1',
        type: 'button',
        variant: 'outlined', // non-default
        color: 'secondary', // non-default
        size: 'small', // non-default
        disabled: false,
        fullWidth: false,
        text: 'Button',
      },
    ];

    const jsx = exportToJSX(components);

    expect(jsx).toContain('variant="outlined"');
    expect(jsx).toContain('tone="secondary"');
    expect(jsx).toContain('size="sm"');
  });
});
