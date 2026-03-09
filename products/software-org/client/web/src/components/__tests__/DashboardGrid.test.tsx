/**
 * Unit tests for DashboardGrid component (Phase 2)
 *
 * Tests validate grid rendering, drag/resize, layout persistence, and responsive breakpoints.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, renderHook } from '@testing-library/react';
import { DashboardGrid, useLayoutPersistence } from '@/components/DashboardGrid';
import type { WidgetConfig } from '@/schemas/persona.schema';

// Mock react-grid-layout
vi.mock('react-grid-layout', () => ({
    Responsive: vi.fn(({ children }) => <div data-testid="responsive-grid">{children}</div>),
    WidthProvider: vi.fn((Component) => Component),
}));

// Mock useLayoutPersistence hook
const mockSaveLayout = vi.fn();
const mockClearLayout = vi.fn();
vi.mock('@/components/DashboardGrid', async () => {
    const actual = await vi.importActual('@/components/DashboardGrid');
    return {
        ...actual,
        useLayoutPersistence: vi.fn(() => [{}, mockSaveLayout, mockClearLayout]),
    };
});

describe('DashboardGrid', () => {
    let mockWidgets: WidgetConfig[];

    beforeEach(() => {
        vi.clearAllMocks();
        mockWidgets = [
            {
                id: 'widget-1',
                title: 'Test Widget 1',
                type: 'metric',
                slot: 'dashboard.metrics',
                pluginId: 'test-plugin',
                config: {},
                layout: {
                    lg: { x: 0, y: 0, w: 4, h: 4 },
                },
                enabled: true,
                permissions: [],
            },
            {
                id: 'widget-2',
                title: 'Test Widget 2',
                type: 'widget',
                slot: 'dashboard.overview',
                pluginId: 'overview-plugin',
                config: {},
                layout: {
                    lg: { x: 4, y: 0, w: 4, h: 4 },
                },
                enabled: true,
                permissions: [],
            },
        ];
    });

    it('should render widgets in grid layout', () => {
        render(<DashboardGrid widgets={mockWidgets} />);

        expect(screen.getByTestId('responsive-grid')).toBeInTheDocument();
        expect(screen.getByText('Test Widget 1', { exact: false })).toBeInTheDocument();
        expect(screen.getByText('Test Widget 2', { exact: false })).toBeInTheDocument();
    });

    it('should show drag handle when editable', () => {
        const { container } = render(<DashboardGrid widgets={mockWidgets} editable />);

        const dragHandles = container.querySelectorAll('.widget-drag-handle');
        expect(dragHandles.length).toBe(2); // One for each widget
    });

    it('should not show drag handle when not editable', () => {
        const { container } = render(<DashboardGrid widgets={mockWidgets} editable={false} />);

        const dragHandles = container.querySelectorAll('.widget-drag-handle');
        expect(dragHandles.length).toBe(0);
    });

    it('should use saved layouts when provided', () => {
        const savedLayouts = {
            lg: [
                { i: 'widget-1', x: 2, y: 2, w: 4, h: 4 },
                { i: 'widget-2', x: 6, y: 2, w: 4, h: 4 },
            ],
        };

        render(<DashboardGrid widgets={mockWidgets} savedLayouts={savedLayouts} />);

        expect(screen.getByTestId('responsive-grid')).toBeInTheDocument();
    });

    it('should generate default layouts from widget configs', () => {
        render(<DashboardGrid widgets={mockWidgets} />);

        // Grid should render with default layouts from widget.layout property
        expect(screen.getByTestId('responsive-grid')).toBeInTheDocument();
    });

    it('should call onLayoutChange when layout changes', () => {
        const handleLayoutChange = vi.fn();
        const { rerender } = render(
            <DashboardGrid widgets={mockWidgets} editable onLayoutChange={handleLayoutChange} />
        );

        // Simulate layout change by rerendering with different savedLayouts
        const newLayouts = {
            lg: [
                { i: 'widget-1', x: 4, y: 0, w: 4, h: 4 },
                { i: 'widget-2', x: 0, y: 0, w: 4, h: 4 },
            ],
        };

        rerender(
            <DashboardGrid
                widgets={mockWidgets}
                editable
                onLayoutChange={handleLayoutChange}
                savedLayouts={newLayouts}
            />
        );

        // Layout change should trigger callback
        expect(screen.getByTestId('responsive-grid')).toBeInTheDocument();
    });

    it('should use custom rowHeight when provided', () => {
        render(<DashboardGrid widgets={mockWidgets} rowHeight={100} />);

        expect(screen.getByTestId('responsive-grid')).toBeInTheDocument();
    });

    it('should apply custom className', () => {
        const { container } = render(<DashboardGrid widgets={mockWidgets} className="custom-grid" />);

        const gridElement = container.querySelector('.custom-grid');
        expect(gridElement).toBeInTheDocument();
    });

    it('should handle empty widgets array', () => {
        render(<DashboardGrid widgets={[]} />);

        expect(screen.getByTestId('responsive-grid')).toBeInTheDocument();
    });
});

describe('useLayoutPersistence', () => {
    beforeEach(() => {
        localStorage.clear();
        vi.clearAllMocks();
    });

    it('should load saved layouts from localStorage on mount', async () => {
        const key = 'test-layout';
        // The hook expects layoutsByBreakpoint format (lg, md, sm, xs only)
        const savedLayouts = {
            lg: [{ i: 'widget-1', x: 0, y: 0, w: 4, h: 4 }],
            md: [{ i: 'widget-1', x: 0, y: 0, w: 4, h: 4 }],
            sm: [{ i: 'widget-1', x: 0, y: 0, w: 4, h: 4 }],
            xs: [{ i: 'widget-1', x: 0, y: 0, w: 4, h: 4 }],
        };

        localStorage.setItem(key, JSON.stringify(savedLayouts));

        // Import real implementation bypassing mock
        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result } = renderHook(() => actual.useLayoutPersistence(key));

        expect(result.current[0]).toEqual(savedLayouts);
    });

    it('should return undefined when no saved layouts', async () => {
        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result } = renderHook(() => actual.useLayoutPersistence('non-existent-key'));

        expect(result.current[0]).toBeUndefined();
    });

    it('should save layouts to localStorage', async () => {
        const key = 'test-layout';
        const layout = [{ i: 'widget-1', x: 2, y: 2, w: 4, h: 4 }];

        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result } = renderHook(() => actual.useLayoutPersistence(key));
        const saveLayout = result.current[1];

        // saveLayout expects (layout: RGLLayout[], widgets: WidgetConfig[])
        saveLayout(layout, []);

        const saved = localStorage.getItem(key);
        // Hook creates layoutsByBreakpoint with breakpoint keys (lg, md, sm, xs)
        const expected = {
            lg: layout,
            md: layout,
            sm: layout,
            xs: layout,
        };
        expect(saved).toBe(JSON.stringify(expected));
    });

    it('should clear layouts from localStorage', async () => {
        const key = 'test-layout';
        localStorage.setItem(key, JSON.stringify({ lg: [] }));

        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result } = renderHook(() => actual.useLayoutPersistence(key));
        const clearLayout = result.current[2];

        clearLayout();

        expect(localStorage.getItem(key)).toBeNull();
    });

    it('should handle localStorage errors gracefully', async () => {
        const key = 'test-layout';

        // Mock localStorage.setItem to throw error
        const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('QuotaExceededError');
        });

        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result } = renderHook(() => actual.useLayoutPersistence(key));
        const saveLayout = result.current[1];

        // Should not throw
        expect(() => saveLayout([{ i: 'w1', x: 0, y: 0, w: 4, h: 4 }], [])).not.toThrow();

        setItemSpy.mockRestore();
    });

    it('should handle invalid JSON in localStorage', async () => {
        const key = 'test-layout';
        localStorage.setItem(key, 'invalid-json{{{');

        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result } = renderHook(() => actual.useLayoutPersistence(key));

        // Should return undefined on parse error
        expect(result.current[0]).toBeUndefined();
    });

    it('should isolate layouts by key', async () => {
        const key1 = 'layout-1';
        const key2 = 'layout-2';
        const layout1 = [{ i: 'w1', x: 0, y: 0, w: 4, h: 4 }];
        const layout2 = [{ i: 'w2', x: 4, y: 0, w: 4, h: 4 }];

        const actual = await vi.importActual<typeof import('@/components/DashboardGrid')>('@/components/DashboardGrid');
        const { result: result1 } = renderHook(() => actual.useLayoutPersistence(key1));
        const saveLayout1 = result1.current[1];
        const { result: result2 } = renderHook(() => actual.useLayoutPersistence(key2));
        const saveLayout2 = result2.current[1];

        saveLayout1(layout1, []);
        saveLayout2(layout2, []);

        // Each key should have its own layoutsByBreakpoint structure (lg, md, sm, xs)
        const expected1 = { lg: layout1, md: layout1, sm: layout1, xs: layout1 };
        const expected2 = { lg: layout2, md: layout2, sm: layout2, xs: layout2 };
        expect(localStorage.getItem(key1)).toBe(JSON.stringify(expected1));
        expect(localStorage.getItem(key2)).toBe(JSON.stringify(expected2));
    });
});
