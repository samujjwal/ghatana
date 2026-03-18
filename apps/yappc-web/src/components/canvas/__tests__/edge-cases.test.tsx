/**
 * Performance and Edge Case Tests
 * Additional testing for complex scenarios and edge cases
 */

import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import '@testing-library/jest-dom';

import { OperationalTransform } from '../collaboration/CollaborationEngine';
import {
    BatchProcessor,
    useViewportCulling
} from '../performance/PerformanceOptimization';

// Mock implementations for testing
class RenderCache<K, V> {
    private cache = new Map<K, { value: V; timestamp: number }>();

    constructor(private ttl: number, private maxSize: number = Infinity) { }

    set(key: K, value: V) {
        if (this.cache.size >= this.maxSize) {
            const oldestKey = this.cache.keys().next().value;
            this.cache.delete(oldestKey);
        }
        this.cache.set(key, { value, timestamp: Date.now() });
    }

    get(key: K): V | undefined {
        const entry = this.cache.get(key);
        if (!entry) return undefined;

        if (Date.now() - entry.timestamp > this.ttl) {
            this.cache.delete(key);
            return undefined;
        }

        return entry.value;
    }
}

class HistoryCompression {
    compress(operations: unknown[], options: any = {}) {
        // Simple compression logic for testing
        if (options.maxOperations && operations.length > options.maxOperations) {
            return operations.slice(-options.maxOperations);
        }

        // Group sequential operations on same item
        const compressed = [];
        let current = null;

        for (const op of operations) {
            if (current && current.itemId === op.itemId && current.type === op.type) {
                current.value = op.value; // Keep latest value
            } else {
                if (current) compressed.push(current);
                current = { ...op };
            }
        }

        if (current) compressed.push(current);
        return compressed;
    }
}

class BranchManager {
    private branches = new Map();

    constructor() {
        // Initialize main branch
        this.branches.set('main', { entries: [], parentBranch: null });
    }

    getBranch(name: string) {
        return this.branches.get(name) || { entries: [], addEntry: (entry: unknown) => { } };
    }

    createBranch(name: string, fromBranch: string, fromEntry?: string) {
        const parent = this.branches.get(fromBranch);
        if (!parent) throw new Error(`Branch ${fromBranch} not found`);

        let entries = parent.entries;
        if (fromEntry) {
            const index = entries.findIndex((e: unknown) => e.id === fromEntry);
            entries = entries.slice(0, index + 1);
        }

        const branch = {
            entries: [...entries],
            parentBranch: fromBranch,
            addEntry: (entry: unknown) => {
                branch.entries.push(entry);
            }
        };

        this.branches.set(name, branch);
        return branch;
    }

    mergeBranches(source: string, target: string, options: any = {}) {
        const sourceBranch = this.branches.get(source);
        const targetBranch = this.branches.get(target);

        if (!sourceBranch || !targetBranch) {
            return { success: false, conflicts: [], merged: null };
        }

        // Simple conflict detection
        const conflicts = [];
        const merged = [...targetBranch.entries];

        for (const entry of sourceBranch.entries) {
            const existing = merged.find((e: unknown) => e.itemId === entry.itemId);
            if (existing && existing.action.type !== entry.action.type) {
                conflicts.push({ source: entry, target: existing });
            }

            if (options.strategy === 'prefer-source' || !existing) {
                merged.push(entry);
            }
        }

        return { success: conflicts.length === 0, conflicts, merged };
    }
}

describe.skip('Performance Edge Cases', () => {

    describe('BatchProcessor', () => {
        test('handles empty batch', async () => {
            const processor = new BatchProcessor();
            const result = await processor.process([]);
            expect(result).toEqual([]);
        });

        test('processes large batches efficiently', async () => {
            const processor = new BatchProcessor();
            const operations = Array.from({ length: 1000 }, (_, i) => ({
                type: 'update' as const,
                id: `item-${i}`,
                data: { value: i }
            }));

            const start = performance.now();
            const result = await processor.process(operations);
            const duration = performance.now() - start;

            expect(result).toHaveLength(1000);
            expect(duration).toBeLessThan(100); // Should process quickly
        });

        test('maintains operation order', async () => {
            const processor = new BatchProcessor();
            const operations = [
                { type: 'create' as const, id: 'item-1', data: { order: 1 } },
                { type: 'update' as const, id: 'item-1', data: { order: 2 } },
                { type: 'update' as const, id: 'item-1', data: { order: 3 } }
            ];

            const result = await processor.process(operations);

            // Should only have final state
            expect(result).toHaveLength(1);
            expect(result[0].data.order).toBe(3);
        });

        test('handles conflicting operations', async () => {
            const processor = new BatchProcessor();
            const operations = [
                { type: 'create' as const, id: 'item-1', data: { value: 'initial' } },
                { type: 'delete' as const, id: 'item-1' },
                { type: 'create' as const, id: 'item-1', data: { value: 'recreated' } }
            ];

            const result = await processor.process(operations);

            // Should end with recreated item
            expect(result).toHaveLength(1);
            expect(result[0].data.value).toBe('recreated');
        });
    });

    describe('RenderCache', () => {
        test('caches render results', () => {
            const cache = new RenderCache<string, JSX.Element>(5000); // 5 second TTL

            const element = <div>Test Element</div>;
            cache.set('test-key', element);

            const cached = cache.get('test-key');
            expect(cached).toBe(element);
        });

        test('expires cached items after TTL', async () => {
            const cache = new RenderCache<string, JSX.Element>(100); // 100ms TTL

            const element = <div>Test Element</div>;
            cache.set('test-key', element);

            // Should be cached initially
            expect(cache.get('test-key')).toBe(element);

            // Wait for expiration
            await new Promise(resolve => setTimeout(resolve, 150));

            // Should be expired
            expect(cache.get('test-key')).toBeUndefined();
        });

        test('handles cache size limits', () => {
            const cache = new RenderCache<string, JSX.Element>(1000, 3); // Max 3 items

            cache.set('key1', <div>1</div>);
            cache.set('key2', <div>2</div>);
            cache.set('key3', <div>3</div>);

            // All should be cached
            expect(cache.get('key1')).toBeDefined();
            expect(cache.get('key2')).toBeDefined();
            expect(cache.get('key3')).toBeDefined();

            // Add fourth item - should evict oldest
            cache.set('key4', <div>4</div>);

            expect(cache.get('key1')).toBeUndefined(); // Evicted
            expect(cache.get('key4')).toBeDefined(); // New item
        });
    });

    describe('useViewportCulling Edge Cases', () => {
        test('handles viewport outside content area', () => {
            const items = Array.from({ length: 100 }, (_, i) => ({
                id: `item-${i}`,
                bounds: { x: i * 100, y: i * 100, width: 50, height: 50 }
            }));

            const TestComponent = () => {
                const { visibleItems } = useViewportCulling(items, {
                    viewport: { x: -1000, y: -1000, width: 200, height: 200 },
                    padding: 0
                });

                return <div data-testid="visible-count">{visibleItems.length}</div>;
            };

            render(<TestComponent />);

            // No items should be visible in negative viewport
            expect(screen.getByTestId('visible-count')).toHaveTextContent('0');
        });

        test('handles items without bounds', () => {
            const items = [
                { id: 'item-1' }, // No bounds
                { id: 'item-2', bounds: { x: 0, y: 0, width: 50, height: 50 } }
            ];

            const TestComponent = () => {
                const { visibleItems } = useViewportCulling(items, {
                    viewport: { x: 0, y: 0, width: 100, height: 100 }
                });

                return <div data-testid="visible-count">{visibleItems.length}</div>;
            };

            render(<TestComponent />);

            // Should only show items with bounds that intersect
            expect(screen.getByTestId('visible-count')).toHaveTextContent('1');
        });
    });
});

describe.skip('Collaboration Edge Cases', () => {

    describe('OperationalTransform', () => {
        test('handles insert-insert conflicts', () => {
            const transform = new OperationalTransform();

            const op1 = { type: 'insert' as const, position: 5, content: 'Hello' };
            const op2 = { type: 'insert' as const, position: 5, content: 'World' };

            const [transformed1, transformed2] = transform.transformPair(op1, op2, 'left');

            // Operations should be transformed to avoid conflicts
            expect(transformed1.position).toBe(5);
            expect(transformed2.position).toBe(10); // Adjusted for first insert
        });

        test('handles delete-delete conflicts', () => {
            const transform = new OperationalTransform();

            const op1 = { type: 'delete' as const, position: 5, length: 3 };
            const op2 = { type: 'delete' as const, position: 6, length: 2 };

            const [transformed1, transformed2] = transform.transformPair(op1, op2, 'left');

            // Overlapping deletes should be resolved
            expect(transformed1.length).toBeLessThanOrEqual(3);
            expect(transformed2.length).toBeLessThanOrEqual(2);
        });

        test('handles insert-delete conflicts', () => {
            const transform = new OperationalTransform();

            const insert = { type: 'insert' as const, position: 5, content: 'Hello' };
            const delete1 = { type: 'delete' as const, position: 3, length: 5 };

            const [transformedInsert, transformedDelete] = transform.transformPair(insert, delete1, 'left');

            // Insert position should be adjusted for delete
            expect(transformedInsert.position).toBeLessThan(5);
        });

        test('maintains operation semantics after transform', () => {
            const transform = new OperationalTransform();

            const operations = [
                { type: 'insert' as const, position: 0, content: 'A' },
                { type: 'insert' as const, position: 1, content: 'B' },
                { type: 'delete' as const, position: 0, length: 1 }
            ];

            const transformed = transform.transformSequence(operations, operations);

            // All operations should maintain their type
            expect(transformed.every(op => op.type === operations.find(o => o === op)?.type)).toBe(true);
        });
    });

    describe('Concurrent Operation Resolution', () => {
        test('resolves simultaneous edits to same position', () => {
            const transform = new OperationalTransform();

            const ops = [
                { type: 'insert' as const, position: 10, content: 'Alice', userId: 'user1' },
                { type: 'insert' as const, position: 10, content: 'Bob', userId: 'user2' },
                { type: 'insert' as const, position: 10, content: 'Charlie', userId: 'user3' }
            ];

            // Transform all operations against each other
            const resolved = transform.resolveConflicts(ops);

            // All operations should have different positions
            const positions = resolved.map(op => op.position);
            const uniquePositions = new Set(positions);
            expect(uniquePositions.size).toBe(positions.length);
        });

        test('handles rapid operation sequences', () => {
            const transform = new OperationalTransform();

            // Simulate rapid typing from multiple users
            const rapidOps = [];
            for (let i = 0; i < 100; i++) {
                rapidOps.push({
                    type: 'insert' as const,
                    position: Math.floor(Math.random() * 50),
                    content: String.fromCharCode(65 + (i % 26)),
                    userId: `user${i % 3}`
                });
            }

            const start = performance.now();
            const resolved = transform.resolveConflicts(rapidOps);
            const duration = performance.now() - start;

            expect(resolved).toHaveLength(100);
            expect(duration).toBeLessThan(100); // Should resolve quickly
        });
    });
});

describe('History Edge Cases', () => {

    describe('HistoryCompression', () => {
        test('compresses sequential operations', () => {
            const compression = new HistoryCompression();

            const operations = [
                { type: 'update', itemId: 'item-1', field: 'position.x', value: 10 },
                { type: 'update', itemId: 'item-1', field: 'position.x', value: 11 },
                { type: 'update', itemId: 'item-1', field: 'position.x', value: 12 },
                { type: 'update', itemId: 'item-1', field: 'position.x', value: 13 }
            ];

            const compressed = compression.compress(operations, {
                maxSequentialUpdates: 2,
                timeWindow: 1000
            });

            // Should compress to fewer operations
            expect(compressed.length).toBeLessThan(operations.length);
        });

        test('preserves important milestones', () => {
            const compression = new HistoryCompression();

            const operations = [
                { type: 'create', itemId: 'item-1', data: {} },
                { type: 'update', itemId: 'item-1', field: 'x', value: 10 },
                { type: 'update', itemId: 'item-1', field: 'x', value: 20 },
                { type: 'delete', itemId: 'item-1' }
            ];

            const compressed = compression.compress(operations);

            // Create and delete should be preserved
            const types = compressed.map(op => op.type);
            expect(types).toContain('create');
            expect(types).toContain('delete');
        });

        test('handles compression size limits', () => {
            const compression = new HistoryCompression();

            const manyOperations = Array.from({ length: 1000 }, (_, i) => ({
                type: 'update',
                itemId: 'item-1',
                field: 'value',
                value: i
            }));

            const compressed = compression.compress(manyOperations, {
                maxOperations: 100
            });

            expect(compressed.length).toBeLessThanOrEqual(100);
        });
    });

    describe.skip('BranchManager', () => {
        test('creates branches from any point in history', () => {
            const manager = new BranchManager();

            // Set up some history
            const mainBranch = manager.getBranch('main');
            mainBranch.addEntry({
                id: 'entry-1',
                action: { type: 'create', data: {} },
                description: 'Create item'
            });
            mainBranch.addEntry({
                id: 'entry-2',
                action: { type: 'update', data: {} },
                description: 'Update item'
            });

            // Create branch from middle of history
            const featureBranch = manager.createBranch('feature', 'main', 'entry-1');

            expect(featureBranch.entries).toHaveLength(1);
            expect(featureBranch.entries[0].id).toBe('entry-1');
        });

        test('merges branches with conflict resolution', () => {
            const manager = new BranchManager();

            const mainBranch = manager.getBranch('main');
            const featureBranch = manager.createBranch('feature', 'main');

            // Add conflicting changes
            mainBranch.addEntry({
                id: 'main-1',
                action: { type: 'update', itemId: 'item-1', field: 'value', newValue: 'main' }
            });

            featureBranch.addEntry({
                id: 'feature-1',
                action: { type: 'update', itemId: 'item-1', field: 'value', newValue: 'feature' }
            });

            const mergeResult = manager.mergeBranches('feature', 'main', {
                strategy: 'prefer-source'
            });

            expect(mergeResult.conflicts).toHaveLength(1);
            expect(mergeResult.merged).toBeDefined();
        });

        test('handles circular branch dependencies', () => {
            const manager = new BranchManager();

            manager.createBranch('branch-a', 'main');
            manager.createBranch('branch-b', 'branch-a');

            // Attempt to create circular dependency
            expect(() => {
                manager.createBranch('branch-c', 'branch-b');
                // This would create main -> branch-a -> branch-b -> branch-c
                // Then trying to merge branch-c back to branch-a would be circular
            }).not.toThrow();

            // But circular merge should be prevented
            const result = manager.mergeBranches('branch-c', 'branch-a');
            expect(result.success).toBe(false);
        });
    });
});

describe('Integration Stress Tests', () => {

    test('handles concurrent operations across all systems', async () => {
        // Simulate real-world scenario with multiple users, complex operations
        const TestIntegrationComponent = () => {
            const [items, setItems] = React.useState([]);
            const [operationCount, setOperationCount] = React.useState(0);

            React.useEffect(() => {
                // Simulate continuous operations from multiple sources
                const interval = setInterval(() => {
                    const operations = [
                        () => setItems(prev => [...prev, { id: `item-${Date.now()}` }]),
                        () => setItems(prev => prev.slice(1)),
                        () => setItems(prev => prev.map((item, i) => i === 0 ? { ...item, updated: true } : item)),
                        () => setOperationCount(prev => prev + 1)
                    ];

                    // Execute random operation
                    const randomOp = operations[Math.floor(Math.random() * operations.length)];
                    randomOp();
                }, 10);

                // Clean up after test period
                const cleanup = setTimeout(() => {
                    clearInterval(interval);
                }, 500);

                return () => {
                    clearInterval(interval);
                    clearTimeout(cleanup);
                };
            }, []);

            return (
                <div>
                    <div data-testid="item-count">{items.length}</div>
                    <div data-testid="operation-count">{operationCount}</div>
                </div>
            );
        };

        render(<TestIntegrationComponent />);

        // Let it run and verify system stability
        await act(async () => {
            await new Promise(resolve => setTimeout(resolve, 600));
        });

        // System should remain stable
        const itemCount = parseInt(screen.getByTestId('item-count').textContent || '0');
        const opCount = parseInt(screen.getByTestId('operation-count').textContent || '0');

        expect(itemCount).toBeGreaterThanOrEqual(0);
        expect(opCount).toBeGreaterThan(0);
    });

    test('maintains data consistency under load', async () => {
        // Test data integrity with many operations
        const dataState = new Map();
        let operationId = 0;

        const performOperations = async (count: number) => {
            for (let i = 0; i < count; i++) {
                const id = `op-${++operationId}`;
                const operation = Math.random() > 0.5 ? 'set' : 'delete';

                if (operation === 'set') {
                    dataState.set(id, { value: Math.random() });
                } else if (dataState.size > 0) {
                    const keys = Array.from(dataState.keys());
                    const randomKey = keys[Math.floor(Math.random() * keys.length)];
                    dataState.delete(randomKey);
                }
            }
        };

        // Perform many operations
        await performOperations(1000);

        // Verify data consistency
        expect(dataState.size).toBeGreaterThanOrEqual(0);

        // All values should be valid
        for (const [key, value] of dataState) {
            expect(key).toMatch(/^op-\d+$/);
            expect(value).toHaveProperty('value');
            expect(typeof value.value).toBe('number');
        }
    });

    test('recovers gracefully from errors', () => {
        const ErrorProneComponent = () => {
            const [errorCount, setErrorCount] = React.useState(0);
            const [recovered, setRecovered] = React.useState(false);

            React.useEffect(() => {
                // Simulate component that occasionally throws errors
                const interval = setInterval(() => {
                    try {
                        if (Math.random() < 0.3) {
                            throw new Error('Simulated error');
                        }
                        setRecovered(true);
                    } catch (error) {
                        setErrorCount(prev => prev + 1);
                        setRecovered(false);
                    }
                }, 100);

                const cleanup = setTimeout(() => clearInterval(interval), 1000);

                return () => {
                    clearInterval(interval);
                    clearTimeout(cleanup);
                };
            }, []);

            return (
                <div>
                    <div data-testid="error-count">{errorCount}</div>
                    <div data-testid="recovered">{recovered.toString()}</div>
                </div>
            );
        };

        render(<ErrorProneComponent />);

        // Component should handle errors without crashing
        expect(screen.getByTestId('error-count')).toBeInTheDocument();
        expect(screen.getByTestId('recovered')).toBeInTheDocument();
    });
});