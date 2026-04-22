/**
 * @fileoverview TypeScript component extractor tests.
 */

import { describe, it, expect } from 'vitest';
import { extractComponentsFromSource } from './component-extractor';

describe('extractComponentsFromSource', () => {
  it('should extract a simple function component with props', () => {
    const source = `
      import React from 'react';

      export function Button({ label, onClick, disabled = false }: { label: string; onClick: () => void; disabled?: boolean }) {
        return <button onClick={onClick} disabled={disabled}>{label}</button>;
      }
    `;

    const components = extractComponentsFromSource(source, 'Button.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.name).toBe('Button');
    expect(components[0]!.isDefaultExport).toBe(false);
    expect(components[0]!.props.length).toBe(3);

    const props = components[0]!.props;
    expect(props.some(p => p.name === 'label')).toBe(true);
    expect(props.some(p => p.name === 'onClick')).toBe(true);
    expect(props.some(p => p.name === 'disabled')).toBe(true);
  });

  it('should extract an arrow function component', () => {
    const source = `
      export const Card = ({ title, children }: { title: string; children: React.ReactNode }) => {
        return (
          <div>
            <h2>{title}</h2>
            {children}
          </div>
        );
      };
    `;

    const components = extractComponentsFromSource(source, 'Card.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.name).toBe('Card');
    expect(components[0]!.slots.length).toBeGreaterThan(0);
  });

  it('should extract a default export component', () => {
    const source = `
      export default function Header() {
        return <header>Header</header>;
      }
    `;

    const components = extractComponentsFromSource(source, 'Header.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.name).toBe('Header');
    expect(components[0]!.isDefaultExport).toBe(true);
  });

  it('should extract JSX usage within a component', () => {
    const source = `
      export function Page() {
        return (
          <div>
            <Header />
            <Content />
            <Footer />
          </div>
        );
      }
    `;

    const components = extractComponentsFromSource(source, 'Page.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.jsxUsage).toContain('Header');
    expect(components[0]!.jsxUsage).toContain('Content');
    expect(components[0]!.jsxUsage).toContain('Footer');
  });

  it('should extract hooks used', () => {
    const source = `
      export function Counter() {
        const [count, setCount] = useState(0);
        const ref = useRef(null);

        useEffect(() => {
          console.log(count);
        }, [count]);

        return <button onClick={() => setCount(c => c + 1)}>{count}</button>;
      }
    `;

    const components = extractComponentsFromSource(source, 'Counter.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.hooksUsed).toContain('useState');
    expect(components[0]!.hooksUsed).toContain('useRef');
    expect(components[0]!.hooksUsed).toContain('useEffect');
  });

  it('should extract accessibility metadata', () => {
    const source = `
      export function AccessibleButton({ label }: { label: string }) {
        return (
          <button
            role="button"
            aria-label={label}
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter') handleClick(); }}
          >
            {label}
          </button>
        );
      }
    `;

    const components = extractComponentsFromSource(source, 'AccessibleButton.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.accessibility).toBeDefined();
    expect(components[0]!.accessibility!.ariaRole).toBe('button');
    expect(components[0]!.accessibility!.keyboardNavigation).toBe(true);
    expect(components[0]!.accessibility!.focusable).toBe(true);
    expect(components[0]!.accessibility!.screenReaderLabel).toBeDefined();
  });

  it('should extract class component', () => {
    const source = `
      import React from 'react';

      export class Modal extends React.Component<{ title: string }> {
        render() {
          return <div><h1>{this.props.title}</h1></div>;
        }
      }
    `;

    const components = extractComponentsFromSource(source, 'Modal.tsx');

    expect(components.length).toBe(1);
    expect(components[0]!.name).toBe('Modal');
    expect(components[0]!.props.length).toBeGreaterThan(0);
  });

  it('should return empty array for non-component files', () => {
    const source = `
      export function add(a: number, b: number): number {
        return a + b;
      }
    `;

    const components = extractComponentsFromSource(source, 'utils.ts');

    expect(components.length).toBe(0);
  });
});
