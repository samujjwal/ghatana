/**
 * Canvas Atoms Unit Tests
 * 
 * Tests for Jotai state atoms managing canvas interaction modes,
 * sketch tools, and diagram configuration.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import {
    canvasInteractionModeAtom,
    sketchToolAtom,
    sketchColorAtom,
    sketchStrokeWidthAtom,
    diagramTypeAtom,
    diagramContentAtom,
    diagramZoomAtom,
} from '../canvasAtoms';

describe('Canvas Interaction Mode Atom', () => {
    let store: ReturnType<typeof createStore>;

    beforeEach(() => {
        store = createStore();
    });

    it('defaults to navigate mode', () => {
        const mode = store.get(canvasInteractionModeAtom);
        expect(mode).toBe('navigate');
    });

    it('can be set to sketch mode', () => {
        store.set(canvasInteractionModeAtom, 'sketch');
        expect(store.get(canvasInteractionModeAtom)).toBe('sketch');
    });

    it('can be set to code mode', () => {
        store.set(canvasInteractionModeAtom, 'code');
        expect(store.get(canvasInteractionModeAtom)).toBe('code');
    });

    it('can be set to diagram mode', () => {
        store.set(canvasInteractionModeAtom, 'diagram');
        expect(store.get(canvasInteractionModeAtom)).toBe('diagram');
    });

    it('can switch between modes', () => {
        store.set(canvasInteractionModeAtom, 'sketch');
        expect(store.get(canvasInteractionModeAtom)).toBe('sketch');

        store.set(canvasInteractionModeAtom, 'diagram');
        expect(store.get(canvasInteractionModeAtom)).toBe('diagram');

        store.set(canvasInteractionModeAtom, 'navigate');
        expect(store.get(canvasInteractionModeAtom)).toBe('navigate');
    });
});

describe('Sketch Tool Atoms', () => {
    let store: ReturnType<typeof createStore>;

    beforeEach(() => {
        store = createStore();
    });

    describe('sketchToolAtom', () => {
        it('defaults to pen tool', () => {
            const tool = store.get(sketchToolAtom);
            expect(tool).toBe('pen');
        });

        it('can be set to rect tool', () => {
            store.set(sketchToolAtom, 'rect');
            expect(store.get(sketchToolAtom)).toBe('rect');
        });

        it('can be set to ellipse tool', () => {
            store.set(sketchToolAtom, 'ellipse');
            expect(store.get(sketchToolAtom)).toBe('ellipse');
        });

        it('can be set to eraser tool', () => {
            store.set(sketchToolAtom, 'eraser');
            expect(store.get(sketchToolAtom)).toBe('eraser');
        });

        it('can switch between tools', () => {
            store.set(sketchToolAtom, 'pen');
            expect(store.get(sketchToolAtom)).toBe('pen');

            store.set(sketchToolAtom, 'rect');
            expect(store.get(sketchToolAtom)).toBe('rect');

            store.set(sketchToolAtom, 'ellipse');
            expect(store.get(sketchToolAtom)).toBe('ellipse');
        });
    });

    describe('sketchColorAtom', () => {
        it('defaults to black', () => {
            const color = store.get(sketchColorAtom);
            expect(color).toBe('#000000');
        });

        it('can be set to custom color', () => {
            store.set(sketchColorAtom, '#FF0000');
            expect(store.get(sketchColorAtom)).toBe('#FF0000');
        });

        it('supports hex color format', () => {
            const colors = ['#FF0000', '#00FF00', '#0000FF', '#FFFF00'];

            colors.forEach(color => {
                store.set(sketchColorAtom, color);
                expect(store.get(sketchColorAtom)).toBe(color);
            });
        });

        it('can switch between colors', () => {
            store.set(sketchColorAtom, '#FF0000');
            expect(store.get(sketchColorAtom)).toBe('#FF0000');

            store.set(sketchColorAtom, '#0000FF');
            expect(store.get(sketchColorAtom)).toBe('#0000FF');
        });
    });

    describe('sketchStrokeWidthAtom', () => {
        it('defaults to 2', () => {
            const width = store.get(sketchStrokeWidthAtom);
            expect(width).toBe(2);
        });

        it('can be set to minimum value (1)', () => {
            store.set(sketchStrokeWidthAtom, 1);
            expect(store.get(sketchStrokeWidthAtom)).toBe(1);
        });

        it('can be set to maximum value (20)', () => {
            store.set(sketchStrokeWidthAtom, 20);
            expect(store.get(sketchStrokeWidthAtom)).toBe(20);
        });

        it('can be set to intermediate values', () => {
            const widths = [5, 10, 15];

            widths.forEach(width => {
                store.set(sketchStrokeWidthAtom, width);
                expect(store.get(sketchStrokeWidthAtom)).toBe(width);
            });
        });

        it('can be incremented', () => {
            store.set(sketchStrokeWidthAtom, 5);
            store.set(sketchStrokeWidthAtom, store.get(sketchStrokeWidthAtom) + 1);
            expect(store.get(sketchStrokeWidthAtom)).toBe(6);
        });

        it('can be decremented', () => {
            store.set(sketchStrokeWidthAtom, 5);
            store.set(sketchStrokeWidthAtom, store.get(sketchStrokeWidthAtom) - 1);
            expect(store.get(sketchStrokeWidthAtom)).toBe(4);
        });
    });
});

describe('Diagram Atoms', () => {
    let store: ReturnType<typeof createStore>;

    beforeEach(() => {
        store = createStore();
    });

    describe('diagramTypeAtom', () => {
        it('defaults to mermaid', () => {
            const type = store.get(diagramTypeAtom);
            expect(type).toBe('mermaid');
        });

        it('can be set to sequence diagram', () => {
            store.set(diagramTypeAtom, 'sequence');
            expect(store.get(diagramTypeAtom)).toBe('sequence');
        });

        it('can be set to class diagram', () => {
            store.set(diagramTypeAtom, 'class');
            expect(store.get(diagramTypeAtom)).toBe('class');
        });

        it('can be set to state diagram', () => {
            store.set(diagramTypeAtom, 'state');
            expect(store.get(diagramTypeAtom)).toBe('state');
        });

        it('can be set to gantt chart', () => {
            store.set(diagramTypeAtom, 'gantt');
            expect(store.get(diagramTypeAtom)).toBe('gantt');
        });

        it('can be set to ER diagram', () => {
            store.set(diagramTypeAtom, 'er');
            expect(store.get(diagramTypeAtom)).toBe('er');
        });

        it('can switch between diagram types', () => {
            store.set(diagramTypeAtom, 'flowchart');
            expect(store.get(diagramTypeAtom)).toBe('flowchart');

            store.set(diagramTypeAtom, 'sequence');
            expect(store.get(diagramTypeAtom)).toBe('sequence');
        });
    });

    describe('diagramContentAtom', () => {
        it('defaults to sample diagram', () => {
            const content = store.get(diagramContentAtom);
            expect(content).toBe('graph TD\n  A[Start] --> B[End]');
        });

        it('can be set to flowchart syntax', () => {
            const flowchart = 'graph TD\n  A-->B';
            store.set(diagramContentAtom, flowchart);
            expect(store.get(diagramContentAtom)).toBe(flowchart);
        });

        it('can be set to sequence diagram syntax', () => {
            const sequence = 'sequenceDiagram\n  Alice->>Bob: Hello';
            store.set(diagramContentAtom, sequence);
            expect(store.get(diagramContentAtom)).toBe(sequence);
        });

        it('can be updated incrementally', () => {
            store.set(diagramContentAtom, 'graph TD');
            expect(store.get(diagramContentAtom)).toBe('graph TD');

            store.set(diagramContentAtom, 'graph TD\n  A-->B');
            expect(store.get(diagramContentAtom)).toBe('graph TD\n  A-->B');
        });

        it('can be cleared', () => {
            store.set(diagramContentAtom, 'graph TD\n  A-->B');
            store.set(diagramContentAtom, '');
            expect(store.get(diagramContentAtom)).toBe('');
        });
    });

    describe('diagramZoomAtom', () => {
        it('defaults to 1 (100%)', () => {
            const zoom = store.get(diagramZoomAtom);
            expect(zoom).toBe(1);
        });

        it('can be set to 0.5 (50%)', () => {
            store.set(diagramZoomAtom, 0.5);
            expect(store.get(diagramZoomAtom)).toBe(0.5);
        });

        it('can be set to 2 (200%)', () => {
            store.set(diagramZoomAtom, 2);
            expect(store.get(diagramZoomAtom)).toBe(2);
        });

        it('can be set to intermediate values', () => {
            const zooms = [0.75, 1.25, 1.5];

            zooms.forEach(zoom => {
                store.set(diagramZoomAtom, zoom);
                expect(store.get(diagramZoomAtom)).toBe(zoom);
            });
        });

        it('can zoom in', () => {
            store.set(diagramZoomAtom, 1);
            store.set(diagramZoomAtom, store.get(diagramZoomAtom) + 0.1);
            expect(store.get(diagramZoomAtom)).toBeCloseTo(1.1, 1);
        });

        it('can zoom out', () => {
            store.set(diagramZoomAtom, 1);
            store.set(diagramZoomAtom, store.get(diagramZoomAtom) - 0.1);
            expect(store.get(diagramZoomAtom)).toBeCloseTo(0.9, 1);
        });
    });
});

describe('State Persistence', () => {
    it('maintains state across multiple reads', () => {
        const store = createStore();

        store.set(canvasInteractionModeAtom, 'sketch');
        expect(store.get(canvasInteractionModeAtom)).toBe('sketch');
        expect(store.get(canvasInteractionModeAtom)).toBe('sketch');
        expect(store.get(canvasInteractionModeAtom)).toBe('sketch');
    });

    it('maintains state across different atoms', () => {
        const store = createStore();

        store.set(sketchToolAtom, 'rect');
        store.set(sketchColorAtom, '#FF0000');
        store.set(sketchStrokeWidthAtom, 10);

        expect(store.get(sketchToolAtom)).toBe('rect');
        expect(store.get(sketchColorAtom)).toBe('#FF0000');
        expect(store.get(sketchStrokeWidthAtom)).toBe(10);
    });
});

describe('Atom Independence', () => {
    it('changing one atom does not affect others', () => {
        const store = createStore();

        store.set(canvasInteractionModeAtom, 'sketch');
        store.set(sketchToolAtom, 'rect');

        store.set(canvasInteractionModeAtom, 'diagram');

        // Sketch tool should remain unchanged
        expect(store.get(sketchToolAtom)).toBe('rect');
    });

    it('diagram atoms independent from sketch atoms', () => {
        const store = createStore();

        store.set(sketchToolAtom, 'ellipse');
        store.set(sketchColorAtom, '#00FF00');

        store.set(diagramTypeAtom, 'sequence');
        store.set(diagramZoomAtom, 1.5);

        // Both should maintain their values
        expect(store.get(sketchToolAtom)).toBe('ellipse');
        expect(store.get(sketchColorAtom)).toBe('#00FF00');
        expect(store.get(diagramTypeAtom)).toBe('sequence');
        expect(store.get(diagramZoomAtom)).toBe(1.5);
    });
});
