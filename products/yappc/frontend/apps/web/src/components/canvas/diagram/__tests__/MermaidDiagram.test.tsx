/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MermaidDiagram } from '../MermaidDiagram';

// Mock mermaid library
vi.mock('mermaid', () => ({
    default: {
        initialize: vi.fn(),
        render: vi.fn(async (id: string, content: string) => {
            return { svg: '<svg>Mocked Diagram</svg>', bindFunctions: vi.fn() };
        }),
    },
}));

describe('MermaidDiagram', () => {
    const mockContent = 'graph TD\n  A[Start] --> B[End]';

    it('renders diagram container', () => {
        render(<MermaidDiagram content={mockContent} zoom={1} />);

        const container = screen.getByTestId('mermaid-diagram');
        expect(container).toBeInTheDocument();
    });

    it('applies zoom level to container', () => {
        render(<MermaidDiagram content={mockContent} zoom={1.5} />);

        const container = screen.getByTestId('mermaid-diagram');
        const styles = window.getComputedStyle(container);

        // Should apply zoom transform
        expect(styles.transform).toContain('scale');
    });

    it('handles empty content gracefully', () => {
        render(<MermaidDiagram content="" zoom={1} />);

        const container = screen.getByTestId('mermaid-diagram');
        expect(container).toBeInTheDocument();
    });

    it('handles zoom of 0.5 (50%)', () => {
        render(<MermaidDiagram content={mockContent} zoom={0.5} />);

        const container = screen.getByTestId('mermaid-diagram');
        expect(container).toBeInTheDocument();
    });

    it('handles zoom of 2 (200%)', () => {
        render(<MermaidDiagram content={mockContent} zoom={2} />);

        const container = screen.getByTestId('mermaid-diagram');
        expect(container).toBeInTheDocument();
    });

    it('renders with className prop', () => {
        const { container } = render(
            <MermaidDiagram content={mockContent} zoom={1} className="custom-diagram" />
        );

        const element = container.querySelector('.custom-diagram');
        expect(element).toBeInTheDocument();
    });

    it('handles flowchart syntax', () => {
        const flowchart = 'graph LR\n  A --> B';
        render(<MermaidDiagram content={flowchart} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('handles sequence diagram syntax', () => {
        const sequence = 'sequenceDiagram\n  Alice->>John: Hello';
        render(<MermaidDiagram content={sequence} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('handles class diagram syntax', () => {
        const classDiagram = 'classDiagram\n  Animal <|-- Duck';
        render(<MermaidDiagram content={classDiagram} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('handles state diagram syntax', () => {
        const stateDiagram = 'stateDiagram-v2\n  [*] --> Active';
        render(<MermaidDiagram content={stateDiagram} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('handles gantt chart syntax', () => {
        const gantt = 'gantt\n  title A Gantt Diagram\n  section Section\n  Task: 2014-01-01, 30d';
        render(<MermaidDiagram content={gantt} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('handles ER diagram syntax', () => {
        const erDiagram = 'erDiagram\n  CUSTOMER ||--o{ ORDER : places';
        render(<MermaidDiagram content={erDiagram} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('is full-screen by default', () => {
        render(<MermaidDiagram content={mockContent} zoom={1} />);

        const container = screen.getByTestId('mermaid-diagram');
        const styles = window.getComputedStyle(container);

        expect(styles.position).toBe('absolute');
        expect(styles.inset).toBeTruthy();
    });

    it('centers diagram content', () => {
        render(<MermaidDiagram content={mockContent} zoom={1} />);

        const container = screen.getByTestId('mermaid-diagram');
        const styles = window.getComputedStyle(container);

        expect(styles.display).toBe('flex');
        expect(styles.justifyContent).toContain('center');
        expect(styles.alignItems).toContain('center');
    });
});

describe('MermaidDiagram Error Handling', () => {
    it('handles invalid mermaid syntax', async () => {
        const invalidContent = 'invalid mermaid syntax !!!';

        // Should not throw
        expect(() => {
            render(<MermaidDiagram content={invalidContent} zoom={1} />);
        }).not.toThrow();

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('handles very long diagram content', () => {
        const longContent = 'graph TD\n' + Array(100).fill('A-->B\n').join('');

        render(<MermaidDiagram content={longContent} zoom={1} />);
        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });
});

describe('MermaidDiagram Accessibility', () => {
    it('has proper test id for testing', () => {
        render(<MermaidDiagram content={mockContent} zoom={1} />);

        expect(screen.getByTestId('mermaid-diagram')).toBeInTheDocument();
    });

    it('renders SVG content that is accessible', () => {
        render(<MermaidDiagram content={mockContent} zoom={1} />);

        const container = screen.getByTestId('mermaid-diagram');
        expect(container).toBeInTheDocument();

        // SVG will be injected by mermaid.render in real usage
    });
});
