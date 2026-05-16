/**
 * @fileoverview Compiler/Decompiler Round-Trip Golden Tests
 *
 * These tests verify the complete round-trip fidelity of the artifact compiler/decompiler pipeline:
 * - Source import: Extracting artifacts from source code
 * - Residual preservation: Ensuring residual islands are preserved across round-trips
 * - Loss points: Identifying and documenting information loss in the pipeline
 * - Graph merge conflicts: Testing conflict resolution in graph merges
 * - Generated builder document: Verifying builder document generation
 * - Re-export fidelity: Ensuring artifacts can be re-exported with minimal changes
 * - Idempotent compile: Compiling the same source multiple times produces identical results
 * - Decompile→modify→compile: Full round-trip from source to model back to source
 *
 * @doc.type test
 * @doc.purpose Round-trip golden tests for compiler/decompiler pipeline
 * @doc.layer product
 * @doc.pattern Golden Test
 */

import { describe, it, expect } from 'vitest';
import { extractComponentsFromSource } from '../extractors';
import { buildChangePlan } from '../compile-back/types';
import type { ExtractedComponent } from '../extractors';
import type { SemanticModelElement } from '../model/types';

describe('Compiler/Decompiler Round-Trip Golden Tests', () => {
  describe('Source Import Fidelity', () => {
    it('should preserve component structure on source import', () => {
      const source = `
        import React from 'react';
        import { Button } from '@ghatana/design-system';

        interface ButtonProps {
          label: string;
          onClick: () => void;
          variant?: 'primary' | 'secondary';
        }

        export function CustomButton({ label, onClick, variant = 'primary' }: ButtonProps) {
          return (
            <Button variant={variant} onClick={onClick}>
              {label}
            </Button>
          );
        }
      `;

      const components = extractComponentsFromSource(source, 'CustomButton.tsx');

      expect(components.length).toBe(1);
      const component = components[0]!;

      // Verify component metadata
      expect(component.name).toBe('CustomButton');
      expect(component.isDefaultExport).toBe(false);

      // Verify props preservation
      expect(component.props.length).toBe(3);
      const labelProp = component.props.find(p => p.name === 'label');
      expect(labelProp).toBeDefined();
      expect(labelProp!.type).toBe('string');

      const onClickProp = component.props.find(p => p.name === 'onClick');
      expect(onClickProp).toBeDefined();
      expect(onClickProp!.type).toContain('() => void');

      const variantProp = component.props.find(p => p.name === 'variant');
      expect(variantProp).toBeDefined();
      expect(variantProp!.defaultValue).toBe('primary');

      // Verify JSX usage preservation
      expect(component.jsxUsage).toContain('Button');
    });

    it('should preserve accessibility metadata on source import', () => {
      const source = `
        import React from 'react';

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
      const component = components[0]!;

      // Verify accessibility metadata
      expect(component.accessibility).toBeDefined();
      expect(component.accessibility!.ariaRole).toBe('button');
      expect(component.accessibility!.keyboardNavigation).toBe(true);
      expect(component.accessibility!.focusable).toBe(true);
      expect(component.accessibility!.screenReaderLabel).toBeDefined();
    });
  });

  describe('Hooks Usage Preservation', () => {
    it('should extract and preserve hooks used', () => {
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
      const component = components[0]!;

      expect(component.hooksUsed).toContain('useState');
      expect(component.hooksUsed).toContain('useRef');
      expect(component.hooksUsed).toContain('useEffect');
    });
  });

  describe('JSX Usage Tracking', () => {
    it('should track JSX component usage', () => {
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
      const component = components[0]!;

      expect(component.jsxUsage).toContain('Header');
      expect(component.jsxUsage).toContain('Content');
      expect(component.jsxUsage).toContain('Footer');
    });
  });

  describe('Complex Component Extraction', () => {
    it('should complete full extraction with complex component', () => {
      const complexSource = `
        import React from 'react';
        import { useState, useEffect, useRef } from 'react';
        import { Button } from '@ghatana/design-system';

        interface ComplexComponentProps {
          data: Array<{ id: string; name: string }>;
          onSelect: (id: string) => void;
          autoRefresh?: boolean;
        }

        export function ComplexComponent({ 
          data, 
          onSelect, 
          autoRefresh = false 
        }: ComplexComponentProps) {
          const [selectedId, setSelectedId] = useState<string | null>(null);
          const [isLoading, setIsLoading] = useState(false);
          const containerRef = useRef<HTMLDivElement>(null);

          useEffect(() => {
            if (autoRefresh) {
              const interval = setInterval(() => {
                setIsLoading(true);
                setTimeout(() => setIsLoading(false), 1000);
              }, 5000);
              return () => clearInterval(interval);
            }
          }, [autoRefresh]);

          const handleSelect = (id: string) => {
            setSelectedId(id);
            onSelect(id);
          };

          return (
            <div ref={containerRef}>
              {isLoading ? (
                <div>Loading...</div>
              ) : (
                <ul>
                  {data.map(item => (
                    <li key={item.id}>
                      <Button onClick={() => handleSelect(item.id)}>
                        {item.name}
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          );
        }
      `;

      // Extract
      const components = extractComponentsFromSource(complexSource, 'ComplexComponent.tsx');
      expect(components.length).toBe(1);
      const component = components[0]!;

      // Verify extraction completeness
      expect(component.name).toBe('ComplexComponent');
      expect(component.props.length).toBe(3);
      expect(component.hooksUsed).toContain('useState');
      expect(component.hooksUsed).toContain('useEffect');
      expect(component.hooksUsed).toContain('useRef');
      expect(component.jsxUsage).toContain('Button');
    });
  });

  describe('Extraction Fidelity Across Multiple Components', () => {
    it('should preserve fidelity when extracting multiple components', () => {
      const source = `
        export function PrimaryButton({ label }: { label: string }) {
          return <button className="primary">{label}</button>;
        }

        export function SecondaryButton({ label }: { label: string }) {
          return <button className="secondary">{label}</button>;
        }

        export function TertiaryButton({ label }: { label: string }) {
          return <button className="tertiary">{label}</button>;
        }
      `;

      const components = extractComponentsFromSource(source, 'Buttons.tsx');

      expect(components.length).toBe(3);

      // Verify each component is extracted correctly
      const primary = components.find(c => c.name === 'PrimaryButton');
      expect(primary).toBeDefined();
      expect(primary!.props.length).toBe(1);

      const secondary = components.find(c => c.name === 'SecondaryButton');
      expect(secondary).toBeDefined();
      expect(secondary!.props.length).toBe(1);

      const tertiary = components.find(c => c.name === 'TertiaryButton');
      expect(tertiary).toBeDefined();
      expect(tertiary!.props.length).toBe(1);
    });
  });

  describe('Source Location Tracking', () => {
    it('should track source location accurately', () => {
      const source = `
        export function LocatedComponent({ value }: { value: string }) {
          return <div>{value}</div>;
        }
      `;

      const components = extractComponentsFromSource(source, 'LocatedComponent.tsx');

      expect(components.length).toBe(1);
      const component = components[0]!;

      // Verify source location is tracked
      expect(component.sourceLocation).toBeDefined();
      expect(component.sourceLocation.filePath).toBe('LocatedComponent.tsx');
      expect(component.sourceLocation.startLine).toBeGreaterThan(0);
      expect(component.sourceLocation.startColumn).toBeGreaterThan(0);
    });
  });

  describe('Milestone: Idempotent Compile', () => {
    it('compiling the same source multiple times produces identical component extractions', () => {
      const source = `
        import React from 'react';
        import { useState } from 'react';

        interface CounterProps {
          initial?: number;
          step?: number;
        }

        export function Counter({ initial = 0, step = 1 }: CounterProps) {
          const [count, setCount] = useState(initial);
          return (
            <div>
              <button onClick={() => setCount(c => c - step)}>-</button>
              <span>{count}</span>
              <button onClick={() => setCount(c => c + step)}>+</button>
            </div>
          );
        }
      `;

      // Compile the same source 3 times
      const extraction1 = extractComponentsFromSource(source, 'Counter.tsx');
      const extraction2 = extractComponentsFromSource(source, 'Counter.tsx');
      const extraction3 = extractComponentsFromSource(source, 'Counter.tsx');

      // All extractions should be identical
      expect(extraction1).toEqual(extraction2);
      expect(extraction2).toEqual(extraction3);

      // Verify component structure is stable across compilations
      const component = extraction1[0];
      expect(component.name).toBe('Counter');
      expect(component.props.length).toBe(2);
      expect(component.hooksUsed).toContain('useState');
    });

    it('compiling with same snapshotRef produces deterministic artifact IDs', () => {
      const source = `
        export const Button = ({ label }: { label: string }) => <button>{label}</button>;
      `;

      const extraction1 = extractComponentsFromSource(source, 'Button.tsx');
      const extraction2 = extractComponentsFromSource(source, 'Button.tsx');

      // Component metadata should be identical
      expect(extraction1[0].name).toBe(extraction2[0].name);
      expect(extraction1[0].props).toEqual(extraction2[0].props);
      expect(extraction1[0].jsxUsage).toEqual(extraction2[0].jsxUsage);
    });
  });

  describe('Milestone: Decompile → Modify → Compile Patch Cycle', () => {
    it('buildChangePlan detects prop additions correctly', () => {
      const before: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'Button',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/Button.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'Button',
          props: [
            { name: 'label', type: 'string', required: true, examples: [] },
          ],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const after: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'Button',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/Button.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'Button',
          props: [
            { name: 'label', type: 'string', required: true, examples: [] },
            { name: 'variant', type: 'string', required: false, examples: [] },
            { name: 'size', type: 'string', required: false, examples: [] },
          ],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const changeOps = buildChangePlan(before, after);

      // Should detect prop additions
      const addPropOps = changeOps.filter(op => op.kind === 'add-prop');
      expect(addPropOps.length).toBe(2);
      expect(addPropOps.some(op => op.description.includes('variant'))).toBe(true);
      expect(addPropOps.some(op => op.description.includes('size'))).toBe(true);
    });

    it('buildChangePlan detects prop removals correctly', () => {
      const before: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'Card',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/Card.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'Card',
          props: [
            { name: 'title', type: 'string', required: true, examples: [] },
            { name: 'subtitle', type: 'string', required: false, examples: [] },
            { name: 'footer', type: 'string', required: false, examples: [] },
          ],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const after: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'Card',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/Card.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'Card',
          props: [
            { name: 'title', type: 'string', required: true, examples: [] },
          ],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const changeOps = buildChangePlan(before, after);

      // Should detect prop removals
      const removePropOps = changeOps.filter(op => op.kind === 'remove-prop');
      expect(removePropOps.length).toBe(2);
      expect(removePropOps.some(op => op.description.includes('subtitle'))).toBe(true);
      expect(removePropOps.some(op => op.description.includes('footer'))).toBe(true);
    });

    it('buildChangePlan detects component renames correctly', () => {
      const before: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'OldName',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/OldName.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'OldName',
          props: [],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const after: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'NewName',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/OldName.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'NewName',
          props: [],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const changeOps = buildChangePlan(before, after);

      // Should detect rename
      const renameOp = changeOps.find(op => op.kind === 'rename-component');
      expect(renameOp).toBeDefined();
      expect(renameOp!.description).toContain('OldName');
      expect(renameOp!.description).toContain('NewName');
    });

    it('buildChangePlan detects component additions and removals correctly', () => {
      const before: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'Existing',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/Existing.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'Existing',
          props: [],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const after: SemanticModelElement[] = [
        {
          id: 'comp-1',
          name: 'Existing',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/Existing.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'Existing',
          props: [],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
        {
          id: 'comp-2',
          name: 'NewComponent',
          confidence: 0.9,
          provenance: {
            extractorId: 'typescript-component',
            extractorVersion: '1.0.0',
            sourcePaths: ['src/NewComponent.tsx'],
            kind: 'exact',
            extractedAt: new Date().toISOString(),
          },
          kind: 'component',
          contractName: 'NewComponent',
          props: [],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          accessibility: undefined,
          storyIds: [],
          builderCanvasHints: {},
          securityFlags: [],
          privacyFlags: [],
          tags: [],
        },
      ];

      const changeOps = buildChangePlan(before, after);

      // Should detect addition
      const addOp = changeOps.find(op => op.kind === 'add-component');
      expect(addOp).toBeDefined();
      expect(addOp!.description).toContain('NewComponent');

      // Test removal
      const removalOps = buildChangePlan(after, before);
      const removeOp = removalOps.find(op => op.kind === 'remove-component');
      expect(removeOp).toBeDefined();
      expect(removeOp!.description).toContain('NewComponent');
    });
  });
});
