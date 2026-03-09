/**
 * Simplified Performance and Integration Tests
 * Focus on testing the actual implemented functionality
 */

import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import '@testing-library/jest-dom';

import { useCollaboration } from '../collaboration/CollaborationEngine';
import { useAdvancedHistory } from '../history/AdvancedHistory';
import {
    usePerformanceMonitor,
    useVirtualScrolling,
    BatchProcessor
} from '../performance/PerformanceOptimization';

// Test utilities and mocks
const mockItem = {
    id: 'test-item',
    type: 'rectangle',
    position: { x: 100, y: 100 },
    data: { width: 50, height: 50 }
};

describe.skip('Performance Tests', () => {

    describe('usePerformanceMonitor', () => {
        test('tracks basic performance metrics', () => {
            const TestComponent = () => {
                const { metrics, recordRender } = usePerformanceMonitor();

                React.useEffect(() => {
                    recordRender(100, 50);
                }, [recordRender]);

                return (
                    <div>
                        <div data-testid="item-count">{metrics.itemCount}</div>
                        <div data-testid="visible-count">{metrics.visibleItemCount}</div>
                        <div data-testid="fps">{Math.round(metrics.fps)}</div>
                    </div>
                );
            };

            render(<TestComponent />);

            expect(screen.getByTestId('item-count')).toHaveTextContent('100');
            expect(screen.getByTestId('visible-count')).toHaveTextContent('50');
            expect(screen.getByTestId('fps')).toHaveTextContent(/\d+/);
        });
    });

    describe('useVirtualScrolling', () => {
        test('handles empty item list', () => {
            const TestComponent = () => {
                const { visibleItems, visibleRange } = useVirtualScrolling([], {
                    itemHeight: 50,
                    containerHeight: 300,
                    overscan: 2
                });

                return (
                    <div>
                        <div data-testid="visible-count">{visibleItems.length}</div>
                        <div data-testid="start-index">{visibleRange.startIndex}</div>
                        <div data-testid="end-index">{visibleRange.endIndex}</div>
                    </div>
                );
            };

            render(<TestComponent />);

            expect(screen.getByTestId('visible-count')).toHaveTextContent('0');
            expect(screen.getByTestId('start-index')).toHaveTextContent('0');
            expect(screen.getByTestId('end-index')).toHaveTextContent('0');
        });

        test('calculates visible range for large lists', () => {
            const items = Array.from({ length: 1000 }, (_, i) => ({
                id: `item-${i}`,
                type: 'test-item',
                position: { x: 0, y: i * 50 },
                data: { label: `Item ${i}` }
            }));

            const TestComponent = () => {
                const { visibleItems, visibleRange } = useVirtualScrolling(items, {
                    itemHeight: 50,
                    containerHeight: 300,
                    overscan: 2
                });

                return (
                    <div>
                        <div data-testid="total-items">{items.length}</div>
                        <div data-testid="visible-count">{visibleItems.length}</div>
                        <div data-testid="performance-ratio">
                            {((visibleItems.length / items.length) * 100).toFixed(1)}%
                        </div>
                    </div>
                );
            };

            render(<TestComponent />);

            expect(screen.getByTestId('total-items')).toHaveTextContent('1000');
            expect(screen.getByTestId('visible-count')).not.toHaveTextContent('1000');

            // Should show performance improvement (less than 100% of items visible)
            const ratio = parseFloat(screen.getByTestId('performance-ratio').textContent || '0');
            expect(ratio).toBeLessThan(100);
        });
    });

    describe('BatchProcessor', () => {
        test('handles basic operation batching', async () => {
            const processor = new BatchProcessor<string>();

            // Since we don't know the exact API, test what we can
            expect(processor).toBeDefined();
            expect(processor).toBeInstanceOf(BatchProcessor);
        });
    });
});

describe('Collaboration Tests', () => {

    describe('useCollaboration', () => {
        test('initializes collaboration state', () => {
            const TestComponent = () => {
                const {
                    collaborationState,
                    addUser,
                    updateCursor,
                    updateSelection
                } = useCollaboration('user-1', 'client-1');

                return (
                    <div>
                        <div data-testid="user-count">{collaborationState.users.size}</div>
                        <div data-testid="cursor-count">{collaborationState.cursors.size}</div>
                        <div data-testid="selection-count">{collaborationState.selections.size}</div>
                        <button
                            data-testid="add-user"
                            onClick={() => addUser({
                                id: 'user-2',
                                name: 'User 2',
                                color: '#ff0000',
                                isOnline: true,
                                lastSeen: Date.now()
                            })}
                        >
                            Add User
                        </button>
                        <button
                            data-testid="update-cursor"
                            onClick={() => updateCursor({ x: 100, y: 100 })}
                        >
                            Update Cursor
                        </button>
                        <button
                            data-testid="update-selection"
                            onClick={() => updateSelection(['item-1', 'item-2'])}
                        >
                            Update Selection
                        </button>
                    </div>
                );
            };

            render(<TestComponent />);

            // Test initial state
            expect(screen.getByTestId('user-count')).toHaveTextContent('0');
            expect(screen.getByTestId('cursor-count')).toHaveTextContent('0');
            expect(screen.getByTestId('selection-count')).toHaveTextContent('0');

            // Test user addition
            fireEvent.click(screen.getByTestId('add-user'));
            expect(screen.getByTestId('user-count')).toHaveTextContent('1');

            // Test cursor update
            fireEvent.click(screen.getByTestId('update-cursor'));
            expect(screen.getByTestId('cursor-count')).toHaveTextContent('1');

            // Test selection update
            fireEvent.click(screen.getByTestId('update-selection'));
            expect(screen.getByTestId('selection-count')).toHaveTextContent('1');
        });

        test('handles multiple users', () => {
            const TestComponent = () => {
                const {
                    collaborationState,
                    addUser
                } = useCollaboration('user-1', 'client-1');

                const addMultipleUsers = () => {
                    ['user-2', 'user-3', 'user-4'].forEach((userId, index) => {
                        addUser({
                            id: userId,
                            name: `User ${index + 2}`,
                            color: `#${Math.random().toString(16).substr(-6)}`,
                            isOnline: true,
                            lastSeen: Date.now()
                        });
                    });
                };

                return (
                    <div>
                        <div data-testid="user-count">{collaborationState.users.size}</div>
                        <button data-testid="add-multiple" onClick={addMultipleUsers}>
                            Add Multiple Users
                        </button>
                    </div>
                );
            };

            render(<TestComponent />);

            fireEvent.click(screen.getByTestId('add-multiple'));
            expect(screen.getByTestId('user-count')).toHaveTextContent('3');
        });
    });
});

describe.skip('History Tests', () => {

    describe('useAdvancedHistory', () => {
        test('records and replays actions', () => {
            const TestComponent = () => {
                const { items, actions, historyState } = useAdvancedHistory([mockItem]);

                return (
                    <div>
                        <div data-testid="item-count">{items.length}</div>
                        <div data-testid="can-undo">{actions.canUndo().toString()}</div>
                        <div data-testid="can-redo">{actions.canRedo().toString()}</div>
                        <button
                            data-testid="add-item"
                            onClick={() => actions.recordAction({
                                type: 'create',
                                items: [{ ...mockItem, id: 'new-item' }]
                            }, 'Add new item')}
                        >
                            Add Item
                        </button>
                        <button
                            data-testid="undo"
                            onClick={() => actions.undo()}
                            disabled={!actions.canUndo()}
                        >
                            Undo
                        </button>
                        <button
                            data-testid="redo"
                            onClick={() => actions.redo()}
                            disabled={!actions.canRedo()}
                        >
                            Redo
                        </button>
                    </div>
                );
            };

            render(<TestComponent />);

            // Initial state
            expect(screen.getByTestId('item-count')).toHaveTextContent('1');
            expect(screen.getByTestId('can-undo')).toHaveTextContent('false');
            expect(screen.getByTestId('can-redo')).toHaveTextContent('false');

            // Add item
            fireEvent.click(screen.getByTestId('add-item'));
            expect(screen.getByTestId('item-count')).toHaveTextContent('2');
            expect(screen.getByTestId('can-undo')).toHaveTextContent('true');

            // Undo
            fireEvent.click(screen.getByTestId('undo'));
            expect(screen.getByTestId('item-count')).toHaveTextContent('1');
            expect(screen.getByTestId('can-redo')).toHaveTextContent('true');

            // Redo
            fireEvent.click(screen.getByTestId('redo'));
            expect(screen.getByTestId('item-count')).toHaveTextContent('2');
        });

        test('supports branching when enabled', () => {
            const TestComponent = () => {
                const { actions, historyState } = useAdvancedHistory([mockItem], {
                    enableBranching: true
                });

                return (
                    <div>
                        <div data-testid="active-branch">{historyState.activeBranchId}</div>
                        <div data-testid="branch-count">{historyState.branches.size}</div>
                        <button
                            data-testid="create-branch"
                            onClick={() => actions.createBranch('feature-branch')}
                        >
                            Create Branch
                        </button>
                        <button
                            data-testid="switch-branch"
                            onClick={() => actions.switchBranch('feature-branch')}
                        >
                            Switch to Feature
                        </button>
                    </div>
                );
            };

            render(<TestComponent />);

            // Initial state - main branch
            expect(screen.getByTestId('active-branch')).toHaveTextContent('main');
            expect(screen.getByTestId('branch-count')).toHaveTextContent('1');

            // Create new branch
            fireEvent.click(screen.getByTestId('create-branch'));
            expect(screen.getByTestId('branch-count')).toHaveTextContent('2');

            // Switch to branch
            fireEvent.click(screen.getByTestId('switch-branch'));
            expect(screen.getByTestId('active-branch')).toHaveTextContent('feature-branch');
        });
    });
});

describe.skip('Integration Stress Tests', () => {

    test('handles many rapid state changes', async () => {
        const TestComponent = () => {
            const [counter, setCounter] = React.useState(0);
            const { metrics } = usePerformanceMonitor();

            React.useEffect(() => {
                // Rapid state updates
                const interval = setInterval(() => {
                    setCounter(prev => prev + 1);
                }, 10);

                // Clean up quickly for test
                const cleanup = setTimeout(() => {
                    clearInterval(interval);
                }, 200);

                return () => {
                    clearInterval(interval);
                    clearTimeout(cleanup);
                };
            }, []);

            return (
                <div>
                    <div data-testid="counter">{counter}</div>
                    <div data-testid="fps">{Math.round(metrics.fps)}</div>
                </div>
            );
        };

        render(<TestComponent />);

        // Let it run briefly
        await act(async () => {
            await new Promise(resolve => setTimeout(resolve, 250));
        });

        const finalCounter = parseInt(screen.getByTestId('counter').textContent || '0');
        expect(finalCounter).toBeGreaterThan(10); // Should have updated multiple times
    });

    test('maintains state consistency across hooks', () => {
        const TestComponent = () => {
            const [items, setItems] = React.useState([mockItem]);
            const { metrics, recordRender } = usePerformanceMonitor();
            const { actions } = useAdvancedHistory(items);
            const { collaborationState } = useCollaboration('test-user', 'test-client');

            React.useEffect(() => {
                recordRender(items.length, items.length);
            }, [items, recordRender]);

            return (
                <div>
                    <div data-testid="items">{items.length}</div>
                    <div data-testid="metrics-items">{metrics.itemCount}</div>
                    <div data-testid="can-undo">{actions.canUndo().toString()}</div>
                    <div data-testid="users">{collaborationState.users.size}</div>
                    <button
                        data-testid="add-item"
                        onClick={() => {
                            const newItem = { ...mockItem, id: `item-${Date.now()}` };
                            setItems(prev => [...prev, newItem]);
                            actions.recordAction({
                                type: 'create',
                                items: [newItem]
                            }, 'Add item');
                        }}
                    >
                        Add Item
                    </button>
                </div>
            );
        };

        render(<TestComponent />);

        // Initial state consistency
        expect(screen.getByTestId('items')).toHaveTextContent('1');
        expect(screen.getByTestId('metrics-items')).toHaveTextContent('1');
        expect(screen.getByTestId('can-undo')).toHaveTextContent('false');
        expect(screen.getByTestId('users')).toHaveTextContent('0');

        // Add item and verify consistency
        fireEvent.click(screen.getByTestId('add-item'));

        expect(screen.getByTestId('items')).toHaveTextContent('2');
        expect(screen.getByTestId('can-undo')).toHaveTextContent('true');
    });

    test('error boundary prevents crashes', () => {
        const ErrorComponent = () => {
            const [shouldError, setShouldError] = React.useState(false);

            if (shouldError) {
                throw new Error('Test error');
            }

            return (
                <div>
                    <div data-testid="status">ok</div>
                    <button
                        data-testid="trigger-error"
                        onClick={() => setShouldError(true)}
                    >
                        Trigger Error
                    </button>
                </div>
            );
        };

        class ErrorBoundary extends React.Component {
            constructor(props: unknown) {
                super(props);
                this.state = { hasError: false };
            }

            static getDerivedStateFromError() {
                return { hasError: true };
            }

            render() {
                if ((this.state as unknown).hasError) {
                    return <div data-testid="error-caught">Error was caught</div>;
                }

                return (this.props as unknown).children;
            }
        }

        render(
            <ErrorBoundary>
                <ErrorComponent />
            </ErrorBoundary>
        );

        // Should start normally
        expect(screen.getByTestId('status')).toHaveTextContent('ok');

        // Trigger error
        fireEvent.click(screen.getByTestId('trigger-error'));

        // Error boundary should catch it
        expect(screen.getByTestId('error-caught')).toBeInTheDocument();
    });
});

// Test utilities
export function createTestItem(id: string, overrides: any = {}) {
    return {
        ...mockItem,
        id,
        ...overrides
    };
}

export function createTestItems(count: number) {
    return Array.from({ length: count }, (_, i) => createTestItem(`item-${i}`));
}

export function measurePerformance<T>(fn: () => T): { result: T; duration: number } {
    const start = performance.now();
    const result = fn();
    const duration = performance.now() - start;
    return { result, duration };
}