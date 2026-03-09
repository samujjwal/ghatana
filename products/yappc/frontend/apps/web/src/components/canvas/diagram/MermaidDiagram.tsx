/**
 * Mermaid Diagram Renderer
 * 
 * Renders Mermaid diagrams with live editing support
 * 
 * @doc.type component
 * @doc.purpose Render Mermaid diagrams in canvas
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React, { useEffect, useRef, useState } from 'react';
import { Box, Surface as Paper, Alert, Spinner as CircularProgress } from '@ghatana/ui';
import mermaid from 'mermaid';

export interface MermaidDiagramProps {
    /** Mermaid diagram definition */
    content: string;

    /** Callback when diagram is clicked */
    onClick?: () => void;

    /** Diagram theme */
    theme?: 'default' | 'dark' | 'forest' | 'neutral';

    /** Width of diagram container */
    width?: number | string;

    /** Height of diagram container */
    height?: number | string;

    /** Show loading state */
    isLoading?: boolean;
}

/**
 * Initialize Mermaid configuration
 */
mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    securityLevel: 'loose',
    fontFamily: 'Inter, -apple-system, sans-serif',
});

/**
 * MermaidDiagram Component
 */
export const MermaidDiagram: React.FC<MermaidDiagramProps> = ({
    content,
    onClick,
    theme = 'default',
    width = '100%',
    height = 'auto',
    isLoading = false,
}) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const [error, setError] = useState<string | null>(null);
    const [isRendering, setIsRendering] = useState(false);

    useEffect(() => {
        const renderDiagram = async () => {
            if (!containerRef.current || !content.trim()) return;

            setIsRendering(true);
            setError(null);

            try {
                // Clear previous content
                containerRef.current.innerHTML = '';

                // Generate unique ID for this diagram
                const id = `mermaid-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

                // Update theme
                mermaid.initialize({ theme });

                // Render the diagram
                const { svg } = await mermaid.render(id, content);

                if (containerRef.current) {
                    containerRef.current.innerHTML = svg;

                    // Make SVG responsive
                    const svgElement = containerRef.current.querySelector('svg');
                    if (svgElement) {
                        svgElement.style.maxWidth = '100%';
                        svgElement.style.height = 'auto';
                    }
                }
            } catch (err: unknown) {
                console.error('Mermaid rendering error:', err);
                setError(err.message || 'Failed to render diagram');
            } finally {
                setIsRendering(false);
            }
        };

        renderDiagram();
    }, [content, theme]);

    if (isLoading || isRendering) {
        return (
            <Box
                style={{ width, height: height === 'auto' ? 200 : height }}
                className="flex items-center justify-center"
            >
                <CircularProgress />
            </Box>
        );
    }

    if (error) {
        return (
            <Alert severity="error" className="m-4">
                <strong>Diagram Error:</strong> {error}
            </Alert>
        );
    }

    return (
        <Paper
            variant="flat"
            style={{ width, height, padding: 16, cursor: onClick ? 'pointer' : 'default', transition: 'box-shadow 0.2s' }}
            className={onClick ? 'hover:shadow-sm' : ''}
            onClick={onClick}
        >
            <Box
                ref={containerRef}
                className="w-full h-full overflow-auto max-w-full h-auto"
            />
        </Paper>
    );
};

/**
 * Common Mermaid diagram templates
 */
export const MERMAID_TEMPLATES = {
    flowchart: `graph TD
    A[Start] --> B{Decision}
    B -->|Yes| C[Process 1]
    B -->|No| D[Process 2]
    C --> E[End]
    D --> E`,

    sequence: `sequenceDiagram
    participant A as Client
    participant B as Server
    participant C as Database
    A->>B: Request
    B->>C: Query
    C-->>B: Response
    B-->>A: Data`,

    class: `classDiagram
    class User {
        +String id
        +String name
        +String email
        +login()
        +logout()
    }
    class Order {
        +String id
        +Date date
        +total()
    }
    User "1" --> "*" Order : places`,

    state: `stateDiagram-v2
    [*] --> Draft
    Draft --> Review
    Review --> Approved
    Review --> Rejected
    Approved --> [*]
    Rejected --> Draft`,

    gantt: `gantt
    title Project Timeline
    dateFormat YYYY-MM-DD
    section Phase 1
    Task 1      :a1, 2026-01-17, 7d
    Task 2      :a2, after a1, 5d
    section Phase 2
    Task 3      :a3, 2026-02-01, 10d`,

    erDiagram: `erDiagram
    USER ||--o{ ORDER : places
    ORDER ||--|{ LINE_ITEM : contains
    PRODUCT ||--o{ LINE_ITEM : "ordered in"
    USER {
        string id
        string name
        string email
    }
    ORDER {
        string id
        date orderDate
        float total
    }`,
};
