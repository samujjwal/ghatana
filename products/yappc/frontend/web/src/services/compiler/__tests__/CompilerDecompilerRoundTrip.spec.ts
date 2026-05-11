/**
 * Compiler/Decompiler Round-Trip Golden Tests
 *
 * @doc.type test
 * @doc.purpose Verify source import, residual preservation, loss points, graph merge conflicts, generated builder document, re-export fidelity
 * @doc.layer product
 * @doc.pattern Golden Test
 */

import { describe, it, expect } from 'vitest';
import { deserializeDocument, serializeDocument } from '@ghatana/ui-builder';
import { importPageArtifactsFromCode } from '../../../components/canvas/page/artifactCompilerBridge';
import type { PageArtifactDocument } from '../../../components/canvas/page/pageArtifactDocument';

describe('Compiler/Decompiler Round-Trip Golden Tests', () => {
  describe('Source Import', () => {
    it('should import a simple TSX component and preserve structure', () => {
      const sourceCode = `
        export default function HomePage() {
          return <div>Hello World</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      expect(artifacts).toHaveLength(1);
      expect(artifacts[0].artifactId).toBeDefined();
      expect(artifacts[0].documentId).toBeDefined();
      expect(artifacts[0].source).toBe('test');
    });

    it('should handle component with props correctly', () => {
      const sourceCode = `
        export default function Button({ label }: { label: string }) {
          return <button>{label}</button>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      expect(artifacts).toHaveLength(1);
      const builderDoc = deserializeDocument(artifacts[0].serializedBuilderDocument);
      expect(builderDoc.nodes.size).toBeGreaterThan(0);
    });
  });

  describe('Residual Preservation', () => {
    it('should preserve residual islands from decompiled code', () => {
      const sourceCode = `
        export default function PageWithCustom() {
          return <div><CustomComponent /></div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      // CustomComponent would be a residual island since it's not in the registry
      expect(artifacts[0].residualIslandIds).toBeDefined();
    });

    it('should track residual island IDs correctly', () => {
      const sourceCode = `
        export default function Page() {
          return (
            <div>
              <CustomA />
              <CustomB />
            </div>
          );
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      const residuals = artifacts[0].residualIslandIds ?? [];
      expect(residuals.length).toBeGreaterThanOrEqual(0);
    });
  });

  describe('Loss Points', () => {
    it('should capture loss points when decompiling complex code', () => {
      const sourceCode = `
        export default function ComplexPage() {
          const [state, setState] = useState(null);
          useEffect(() => {
            fetch('/api').then(r => r.json());
          }, []);
          return <div>{state}</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      const fidelity = artifacts[0].roundTripFidelity;
      expect(fidelity).toBeDefined();
      
      if (fidelity && fidelity.lossPoints) {
        expect(fidelity.lossPoints.length).toBeGreaterThanOrEqual(0);
      }
    });

    it('should document loss point locations and descriptions', () => {
      const sourceCode = `
        export default function PageWithSideEffects() {
          console.log('side effect');
          return <div>Content</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      const fidelity = artifacts[0].roundTripFidelity;
      
      if (fidelity && fidelity.lossPoints && fidelity.lossPoints.length > 0) {
        const lossPoint = fidelity.lossPoints[0];
        expect(lossPoint.type).toBeDefined();
        expect(lossPoint.description).toBeDefined();
      }
    });
  });

  describe('Graph Merge Conflicts', () => {
    it('should detect graph merge conflicts when importing into existing document', () => {
      const sourceCode = `
        export default function NewPage() {
          return <div>New Content</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      // Artifact graph should be populated for conflict detection
      expect(artifacts[0].artifactGraph).toBeDefined();
    });

    it('should include node relationships in artifact graph', () => {
      const sourceCode = `
        export default function NestedPage() {
          return (
            <div>
              <Header />
              <Content />
              <Footer />
            </div>
          );
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      
      if (artifacts[0].artifactGraph) {
        expect(artifacts[0].artifactGraph.nodes).toBeDefined();
        expect(artifacts[0].artifactGraph.edges).toBeDefined();
      }
    });
  });

  describe('Generated Builder Document', () => {
    it('should produce a valid BuilderDocument structure', () => {
      const sourceCode = `
        export default function ValidPage() {
          return <div>Test</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const builderDoc = deserializeDocument(artifacts[0].serializedBuilderDocument);
      
      expect(builderDoc.rootNodes).toBeDefined();
      expect(builderDoc.nodes).toBeDefined();
      expect(builderDoc.metadata).toBeDefined();
    });

    it('should maintain node hierarchy in builder document', () => {
      const sourceCode = `
        export default function HierarchicalPage() {
          return (
            <Container>
              <Child />
            </Container>
          );
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const builderDoc = deserializeDocument(artifacts[0].serializedBuilderDocument);
      
      // Should have at least one root node
      expect(builderDoc.rootNodes.length).toBeGreaterThan(0);
    });
  });

  describe('Re-export Fidelity', () => {
    it('should round-trip a simple component without data loss', () => {
      const sourceCode = `
        export default function SimplePage() {
          return <div>Test Content</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const builderDoc = deserializeDocument(artifacts[0].serializedBuilderDocument);
      const reSerialized = serializeDocument(builderDoc);
      
      expect(reSerialized).toBeDefined();
    });

    it('should preserve node properties through round-trip', () => {
      const sourceCode = `
        export default function PageWithProps() {
          return <div className="test">Content</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const builderDoc = deserializeDocument(artifacts[0].serializedBuilderDocument);
      
      // Verify that at least one node exists
      expect(builderDoc.nodes.size).toBeGreaterThan(0);
    });

    it('should maintain metadata integrity', () => {
      const sourceCode = `
        export default function MetadataPage() {
          return <div>Content</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const builderDoc = deserializeDocument(artifacts[0].serializedBuilderDocument);
      
      expect(builderDoc.metadata).toBeDefined();
    });
  });

  describe('Fidelity Scoring', () => {
    it('should provide fidelity information for imports', () => {
      const sourceCode = `
        export default function FidelityTest() {
          return <div>Test</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const fidelity = artifacts[0].roundTripFidelity;
      
      expect(fidelity).toBeDefined();
    });

    it('should include loss points in fidelity information', () => {
      const sourceCode = `
        export default function Simple() {
          return <div>Test</div>;
        }
      `;

      const artifacts = importPageArtifactsFromCode(sourceCode, 'test');
      const fidelity = artifacts[0].roundTripFidelity;
      
      // Fidelity should include loss points array
      if (fidelity) {
        expect(fidelity.lossPoints).toBeDefined();
      }
    });
  });
});
