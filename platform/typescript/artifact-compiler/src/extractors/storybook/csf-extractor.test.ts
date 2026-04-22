/**
 * @fileoverview Storybook CSF extractor tests.
 */

import { describe, it, expect } from 'vitest';
import { parseCsfSource } from './csf-extractor';

describe('parseCsfSource', () => {
  it('should extract meta and stories from CSF 2', () => {
    const source = `
      import { Button } from './Button';

      export default {
        title: 'Components/Button',
        component: Button,
        tags: ['autodocs'],
        parameters: {
          layout: 'centered',
        },
      };

      export const Primary = {
        args: {
          variant: 'primary',
          label: 'Click me',
        },
      };

      export const Secondary = {
        args: {
          variant: 'secondary',
          label: 'Secondary',
        },
      };
    `;

    const result = parseCsfSource(source, 'Button.stories.tsx');

    expect(result).not.toBeNull();
    expect(result!.meta.title).toBe('Components/Button');
    expect(result!.meta.componentName).toBe('Button');
    expect(result!.meta.tags).toContain('autodocs');
    expect(result!.stories.length).toBe(2);

    const primary = result!.stories.find(s => s.name === 'Primary');
    expect(primary).toBeDefined();
    expect(primary!.args).toEqual({ variant: 'primary', label: 'Click me' });

    const secondary = result!.stories.find(s => s.name === 'Secondary');
    expect(secondary).toBeDefined();
    expect(secondary!.args.variant).toBe('secondary');
  });

  it('should extract CSF 3 function stories', () => {
    const source = `
      import { Card } from './Card';

      export default {
        title: 'Components/Card',
        component: Card,
      };

      export const Default = () => <Card title="Hello" />;
      export const WithImage = () => <Card title="With Image" imageUrl="/img.jpg" />;
    `;

    const result = parseCsfSource(source, 'Card.stories.tsx');

    expect(result).not.toBeNull();
    expect(result!.stories.length).toBe(2);
    expect(result!.stories[0]!.name).toBe('Default');
    expect(result!.stories[1]!.name).toBe('WithImage');
  });

  it('should extract component import path', () => {
    const source = `
      import { Button } from '../components/Button';

      export default {
        title: 'Components/Button',
        component: Button,
      };

      export const Primary = {
        args: { variant: 'primary' },
      };
    `;

    const result = parseCsfSource(source, 'Button.stories.tsx');

    expect(result).not.toBeNull();
    expect(result!.componentFilePath).toBe('../components/Button');
  });

  it('should return null for non-CSF files', () => {
    const source = `
      export function helper() { return 42; }
    `;

    const result = parseCsfSource(source, 'helper.ts');
    expect(result).toBeNull();
  });

  it('should handle stories with decorators', () => {
    const source = `
      export default {
        title: 'Components/Modal',
        decorators: [(Story) => <div style={{ padding: '3rem' }}><Story /></div>],
      };

      export const Default = {
        decorators: [(Story) => <div className="wrapper"><Story /></div>],
        args: { isOpen: true },
      };
    `;

    const result = parseCsfSource(source, 'Modal.stories.tsx');

    expect(result).not.toBeNull();
    const modal = result!.stories.find(s => s.name === 'Default');
    expect(modal).toBeDefined();
    expect(modal!.decorators.length).toBeGreaterThan(0);
  });
});
