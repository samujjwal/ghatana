/**
 * Canvas Utilities Usage Examples
 * 
 * Practical examples of using canvasPersistence, canvasHistory, and canvasExport utilities.
 * 
 * @doc.type documentation
 * @doc.purpose Usage examples for canvas utilities
 * @doc.layer product
 */

// ============================================================================
// 1. Canvas Persistence - Save/Load State
// ============================================================================

import { useCanvasPersistence } from '../utils/canvasPersistence';
import { useCanvasHistory } from '../utils/canvasHistory';
import { useCanvasExport } from '../utils/canvasExport';

/**
 * EXAMPLE 1: Basic Persistence
 * Automatically saves and loads canvas state from localStorage
 */
export const BasicPersistenceExample = () => {
    const [notes, setNotes] = useState<string[]>([]);

    // Initialize persistence for this canvas mode/level
    const { save, load, clear } = useCanvasPersistence<string[]>(
        'brainstorm',  // mode
        'system'       // level
    );

    // Load persisted state on mount
    useEffect(() => {
        const savedNotes = load();
        if (savedNotes) {
            setNotes(savedNotes);
        }
    }, [load]);

    // Auto-save whenever notes change
    useEffect(() => {
        if (notes.length > 0) {
            save(notes);
        }
    }, [notes, save]);

    return (
        <div>
            <button onClick={() => clear()}>Clear Saved Data</button>
            {/* Your canvas UI */}
        </div>
    );
};

/**
 * EXAMPLE 2: Custom Persistence Options
 * Configure storage key, expiration, and compression
 */
export const CustomPersistenceExample = () => {
    const [diagramData, setDiagramData] = useState(null);

    const { save, load } = useCanvasPersistence(
        'diagram',
        'component',
        {
            storageKey: 'my-custom-canvas',     // Custom key prefix
            maxAge: 24 * 60 * 60 * 1000,        // 24 hours
            enableCompression: true              // Compress large data
        }
    );

    return <div>/* Your canvas */</div>;
};

// ============================================================================
// 2. Canvas History - Undo/Redo
// ============================================================================

/**
 * EXAMPLE 3: Basic Undo/Redo
 * Add undo/redo functionality to any canvas
 */
export const BasicHistoryExample = () => {
    const [nodes, setNodes] = useState<Node[]>([]);

    const history = useCanvasHistory<Node[]>(nodes);

    const addNode = (node: Node) => {
        const newNodes = history.execute({
            execute: (state) => [...state, node],
            undo: (state) => state.slice(0, -1),
            description: 'Add node'
        });
        setNodes(newNodes);
    };

    const deleteNode = (id: string) => {
        const newNodes = history.execute({
            execute: (state) => state.filter(n => n.id !== id),
            undo: (state) => [...state, deletedNode], // Store reference
            description: 'Delete node'
        });
        setNodes(newNodes);
    };

    return (
        <div>
            <button onClick={() => setNodes(history.undo())} disabled={!history.canUndo()}>
                ↶ Undo
            </button>
            <button onClick={() => setNodes(history.redo())} disabled={!history.canRedo()}>
                ↷ Redo
            </button>
            <button onClick={() => history.clear()}>
                Clear History
            </button>
        </div>
    );
};

/**
 * EXAMPLE 4: Using createCommand Helper
 * Simplified command creation for common operations
 */
import { createCommand } from '../utils/canvasHistory';

export const SimplifiedHistoryExample = () => {
    const [text, setText] = useState('');
    const history = useCanvasHistory<string>(text);

    const updateText = (newText: string) => {
        const cmd = createCommand<string>(
            text,           // oldState
            newText,        // newState
            'Update text'   // description
        );
        const result = history.execute(cmd);
        setText(result);
    };

    return <textarea value={text} onChange={(e) => updateText(e.target.value)} />;
};

/**
 * EXAMPLE 5: Combined Persistence + History
 * Best of both worlds - undo/redo with automatic saving
 */
export const CombinedExample = () => {
    const [canvas, setCanvas] = useState<CanvasState>(initialState);

    // Persistence
    const { save, load } = useCanvasPersistence<CanvasState>('code', 'file');

    // History
    const history = useCanvasHistory<CanvasState>(canvas, {
        maxHistorySize: 100,      // Keep more history
        enableGrouping: true,     // Group rapid changes
        groupingDelay: 1000       // 1 second grouping
    });

    // Load on mount
    useEffect(() => {
        const saved = load();
        if (saved) {
            setCanvas(saved);
            history.reset(saved); // Reset history to loaded state
        }
    }, []);

    // Save whenever canvas changes
    useEffect(() => {
        save(canvas);
    }, [canvas]);

    const updateCanvas = (changes: Partial<CanvasState>) => {
        const newState = history.execute({
            execute: (state) => ({ ...state, ...changes }),
            undo: (state) => canvas, // Capture current state
            description: 'Update canvas'
        });
        setCanvas(newState);
    };

    return (
        <div>
            <button onClick={() => setCanvas(history.undo())}>Undo</button>
            <button onClick={() => setCanvas(history.redo())}>Redo</button>
        </div>
    );
};

// ============================================================================
// 3. Canvas Export - PNG/SVG/PDF
// ============================================================================

/**
 * EXAMPLE 6: Basic Export
 * Export canvas as image or PDF
 */
export const BasicExportExample = () => {
    const canvasRef = useRef<HTMLDivElement>(null);
    const { exportAs } = useCanvasExport('diagram', 'system', canvasRef);

    const handleExport = async (format: 'png' | 'svg' | 'pdf') => {
        try {
            await exportAs(format);
            console.log('Export successful!');
        } catch (error) {
            console.error('Export failed:', error);
            // Show user-friendly error if dependencies not installed
        }
    };

    return (
        <div>
            <div ref={canvasRef}>
                {/* Your canvas content */}
            </div>

            <button onClick={() => handleExport('png')}>📸 Export PNG</button>
            <button onClick={() => handleExport('svg')}>🎨 Export SVG</button>
            <button onClick={() => handleExport('pdf')}>📄 Export PDF</button>
        </div>
    );
};

/**
 * EXAMPLE 7: Custom Export Options
 * Configure quality, scale, background, etc.
 */
export const CustomExportExample = () => {
    const canvasRef = useRef<HTMLDivElement>(null);
    const { exportAs } = useCanvasExport('design', 'component', canvasRef);

    const handleHighQualityExport = async () => {
        await exportAs('png', {
            quality: 1.0,              // Maximum quality
            scale: 3,                  // 3x resolution
            backgroundColor: '#ffffff', // White background
            fileName: 'my-design-hq',
            includeMetadata: true
        });
    };

    return <button onClick={handleHighQualityExport}>Export High Quality</button>;
};

/**
 * EXAMPLE 8: SVG Export for Diagrams
 * Best for vector-based diagrams (lossless)
 */
export const SVGExportExample = () => {
    const diagramRef = useRef<HTMLDivElement>(null);
    const { exportAs } = useCanvasExport('diagram', 'system', diagramRef);

    return (
        <div>
            <div ref={diagramRef}>
                <svg width="800" height="600">
                    {/* Your SVG diagram */}
                </svg>
            </div>

            <button onClick={() => exportAs('svg')}>
                Export as Vector (SVG)
            </button>
        </div>
    );
};

// ============================================================================
// 4. Real-World Integration Example
// ============================================================================

/**
 * EXAMPLE 9: Complete Canvas with All Features
 * Production-ready canvas with persistence, history, and export
 */
export const ProductionCanvasExample = () => {
    // State
    const [elements, setElements] = useState<Element[]>([]);
    const canvasRef = useRef<HTMLDivElement>(null);

    // Utilities
    const { save, load } = useCanvasPersistence<Element[]>('design', 'component');
    const history = useCanvasHistory<Element[]>(elements);
    const { exportAs } = useCanvasExport('design', 'component', canvasRef);

    // Load on mount
    useEffect(() => {
        const saved = load();
        if (saved) {
            setElements(saved);
            history.reset(saved);
        }
    }, []);

    // Auto-save
    useEffect(() => {
        if (elements.length > 0) {
            save(elements);
        }
    }, [elements]);

    // Operations with history
    const addElement = (element: Element) => {
        const newElements = history.execute(
            createCommand(elements, [...elements, element], 'Add element')
        );
        setElements(newElements);
    };

    const deleteElement = (id: string) => {
        const newElements = history.execute(
            createCommand(
                elements,
                elements.filter(e => e.id !== id),
                'Delete element'
            )
        );
        setElements(newElements);
    };

    return (
        <div>
            {/* Toolbar */}
            <div className="toolbar">
                <button
                    onClick={() => setElements(history.undo())}
                    disabled={!history.canUndo()}
                >
                    ↶ Undo
                </button>
                <button
                    onClick={() => setElements(history.redo())}
                    disabled={!history.canRedo()}
                >
                    ↷ Redo
                </button>

                <div className="export-menu">
                    <button onClick={() => exportAs('png')}>📸 PNG</button>
                    <button onClick={() => exportAs('pdf')}>📄 PDF</button>
                </div>
            </div>

            {/* Canvas */}
            <div ref={canvasRef} className="canvas-content">
                {elements.map(element => (
                    <ElementComponent
                        key={element.id}
                        element={element}
                        onDelete={() => deleteElement(element.id)}
                    />
                ))}
            </div>

            {/* Add button */}
            <button onClick={() => addElement(createNewElement())}>
                + Add Element
            </button>
        </div>
    );
};

// ============================================================================
// 5. Error Handling Examples
// ============================================================================

/**
 * EXAMPLE 10: Graceful Degradation
 * Handle missing dependencies gracefully
 */
export const ErrorHandlingExample = () => {
    const canvasRef = useRef<HTMLDivElement>(null);
    const { exportAs } = useCanvasExport('code', 'code', canvasRef);
    const [exportError, setExportError] = useState<string | null>(null);

    const handleExportWithFallback = async (format: 'png' | 'svg' | 'pdf') => {
        try {
            setExportError(null);
            await exportAs(format);
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Export failed';

            if (message.includes('html2canvas') || message.includes('jspdf')) {
                setExportError(
                    'Export dependencies not installed. ' +
                    'Install with: npm install html2canvas jspdf'
                );
            } else {
                setExportError(message);
            }
        }
    };

    return (
        <div>
            <button onClick={() => handleExportWithFallback('png')}>Export</button>
            {exportError && (
                <div className="error-message">
                    ⚠️ {exportError}
                </div>
            )}
        </div>
    );
};

// ============================================================================
// Type Definitions for Examples
// ============================================================================

interface Node {
    id: string;
    label: string;
    position: { x: number; y: number };
}

interface Element {
    id: string;
    type: string;
    props: Record<string, unknown>;
}

interface CanvasState {
    nodes: Node[];
    edges: Edge[];
    viewport: { zoom: number; x: number; y: number };
}

interface Edge {
    id: string;
    source: string;
    target: string;
}
