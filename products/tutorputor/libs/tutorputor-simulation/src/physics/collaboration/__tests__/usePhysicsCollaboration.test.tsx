// @vitest-environment jsdom

import React, { type ReactNode } from 'react';
import { Provider, createStore } from 'jotai';
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { MockYDoc, MockWebsocketProvider, MockIndexeddbPersistence } = vi.hoisted(() => {
    class MockYArray<T> {
        private readonly observers = new Set<() => void>();

        private values: T[] = [];

        toArray(): T[] {
            return [...this.values];
        }

        observe(listener: () => void): void {
            this.observers.add(listener);
        }

        delete(start: number, length: number): void {
            this.values.splice(start, length);
        }

        push(items: T[]): void {
            this.values.push(...items);
        }

        emit(): void {
            for (const observer of this.observers) {
                observer();
            }
        }

        get length(): number {
            return this.values.length;
        }
    }

    class MockYMap<T> {
        private readonly observers = new Set<() => void>();

        private readonly values = new Map<string, T>();

        set(key: string, value: T): void {
            this.values.set(key, value);
        }

        toJSON(): Record<string, T> {
            return Object.fromEntries(this.values);
        }

        observe(listener: () => void): void {
            this.observers.add(listener);
        }

        emit(): void {
            for (const observer of this.observers) {
                observer();
            }
        }
    }

    class MockYDoc {
        private readonly arrays = new Map<string, MockYArray<unknown>>();

        private readonly maps = new Map<string, MockYMap<unknown>>();

        destroyed = false;

        getArray<T>(name: string): MockYArray<T> {
            if (!this.arrays.has(name)) {
                this.arrays.set(name, new MockYArray<T>() as MockYArray<unknown>);
            }

            return this.arrays.get(name) as MockYArray<T>;
        }

        getMap<T>(name: string): MockYMap<T> {
            if (!this.maps.has(name)) {
                this.maps.set(name, new MockYMap<T>() as MockYMap<unknown>);
            }

            return this.maps.get(name) as MockYMap<T>;
        }

        transact(operation: () => void): void {
            operation();
        }

        destroy(): void {
            this.destroyed = true;
        }
    }

    class MockAwareness {
        private readonly listeners = new Set<() => void>();

        private readonly states = new Map<number, Record<string, unknown>>();

        setLocalStateField(key: string, value: unknown): void {
            const currentState = this.states.get(1) ?? {};
            this.states.set(1, { ...currentState, [key]: value });
        }

        setRemoteState(clientId: number, state: Record<string, unknown>): void {
            this.states.set(clientId, state);
        }

        getStates(): Map<number, Record<string, unknown>> {
            return this.states;
        }

        on(event: string, listener: () => void): void {
            if (event === 'change') {
                this.listeners.add(listener);
            }
        }

        emitChange(): void {
            for (const listener of this.listeners) {
                listener();
            }
        }
    }

    class MockWebsocketProvider {
        static instances: MockWebsocketProvider[] = [];

        readonly awareness = new MockAwareness();

        readonly listeners = new Map<string, Set<(payload: unknown) => void>>();

        disconnected = false;

        destroyed = false;

        constructor(
            readonly serverUrl: string,
            readonly roomName: string,
            readonly doc: MockYDoc,
        ) {
            MockWebsocketProvider.instances.push(this);
        }

        on(event: string, listener: (payload: unknown) => void): void {
            const listeners = this.listeners.get(event) ?? new Set<(payload: unknown) => void>();
            listeners.add(listener);
            this.listeners.set(event, listeners);
        }

        emit(event: string, payload: unknown): void {
            for (const listener of this.listeners.get(event) ?? []) {
                listener(payload);
            }
        }

        disconnect(): void {
            this.disconnected = true;
        }

        destroy(): void {
            this.destroyed = true;
        }

        static reset(): void {
            MockWebsocketProvider.instances = [];
        }
    }

    class MockIndexeddbPersistence {
        static instances: MockIndexeddbPersistence[] = [];

        readonly listeners = new Map<string, Set<() => void>>();

        destroyed = false;

        constructor(readonly roomName: string, readonly doc: MockYDoc) {
            MockIndexeddbPersistence.instances.push(this);
        }

        on(event: string, listener: () => void): void {
            const listeners = this.listeners.get(event) ?? new Set<() => void>();
            listeners.add(listener);
            this.listeners.set(event, listeners);
        }

        emit(event: string): void {
            for (const listener of this.listeners.get(event) ?? []) {
                listener();
            }
        }

        destroy(): void {
            this.destroyed = true;
        }

        static reset(): void {
            MockIndexeddbPersistence.instances = [];
        }
    }

    return { MockYDoc, MockWebsocketProvider, MockIndexeddbPersistence };
});

vi.mock('yjs', () => ({
    Doc: MockYDoc,
}));

vi.mock('y-websocket', () => ({
    WebsocketProvider: MockWebsocketProvider,
}));

vi.mock('y-indexeddb', () => ({
    IndexeddbPersistence: MockIndexeddbPersistence,
}));

import { simulationEntitiesAtom, simulationPhysicsConfigAtom } from '../../state';
import { EntityType, type PhysicsEntity } from '../../types';
import { DEFAULT_PHYSICS_CONFIG } from '../../entities';
import { usePhysicsCollaboration } from '../usePhysicsCollaboration';

function createEntity(id: string, x: number, y: number): PhysicsEntity {
    return {
        id,
        type: EntityType.BALL,
        x,
        y,
        radius: 24,
        appearance: {
            color: '#3b82f6',
        },
        physics: {
            mass: 1,
            friction: 0.5,
            restitution: 0.7,
            isStatic: false,
        },
    };
}

function createWrapper(store: ReturnType<typeof createStore>) {
    return function Wrapper({ children }: { children: ReactNode }) {
        return <Provider store={store}>{children}</Provider>;
    };
}

describe('usePhysicsCollaboration', () => {
    beforeEach(() => {
        MockWebsocketProvider.reset();
        MockIndexeddbPersistence.reset();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('tracks connection state and publishes local awareness updates', async () => {
        const store = createStore();
        const wrapper = createWrapper(store);

        const { result, unmount } = renderHook(
            () =>
                usePhysicsCollaboration('room-a', 'user-1', 'Ada', {
                    serverUrl: 'ws://collab.test',
                    enablePersistence: true,
                }),
            { wrapper },
        );

        const provider = MockWebsocketProvider.instances[0];
        const persistence = MockIndexeddbPersistence.instances[0];

        expect(provider.serverUrl).toBe('ws://collab.test');
        expect(provider.roomName).toBe('physics-room-a');
        expect(provider.awareness.getStates().get(1)?.user).toEqual({
            id: 'user-1',
            name: 'Ada',
            color: result.current.currentUser.color,
        });

        act(() => {
            provider.emit('status', { status: 'connected' });
        });

        await waitFor(() => {
            expect(result.current.isConnected).toBe(true);
            expect(result.current.syncStatus).toBe('syncing');
        });

        act(() => {
            result.current.updateCursor(12, 24);
            result.current.updateSelection('entity-1');
        });

        expect(provider.awareness.getStates().get(1)?.cursor).toEqual({ x: 12, y: 24 });
        expect(provider.awareness.getStates().get(1)?.selectedEntityId).toBe('entity-1');

        act(() => {
            persistence.emit('synced');
        });

        await waitFor(() => {
            expect(result.current.syncStatus).toBe('synced');
        });

        unmount();

        expect(provider.disconnected).toBe(true);
        expect(provider.destroyed).toBe(true);
        expect(persistence.destroyed).toBe(true);
    });

    it('syncs remote awareness and yjs entity updates back into local jotai state', async () => {
        const store = createStore();
        const wrapper = createWrapper(store);

        renderHook(
            () =>
                usePhysicsCollaboration('room-b', 'user-1', 'Ada', {
                    enablePersistence: false,
                }),
            { wrapper },
        );

        const provider = MockWebsocketProvider.instances[0];
        const remoteEntity = createEntity('entity-remote', 80, 120);

        act(() => {
            provider.awareness.setRemoteState(2, {
                user: {
                    id: 'user-2',
                    name: 'Grace',
                    color: '#ef4444',
                },
                cursor: { x: 300, y: 180 },
                selectedEntityId: 'entity-remote',
            });
            provider.awareness.emitChange();
        });

        const entitiesArray = provider.doc.getArray<PhysicsEntity>('entities');
        const physicsConfigMap = provider.doc.getMap<number | boolean>('physicsConfig');

        act(() => {
            provider.doc.transact(() => {
                entitiesArray.delete(0, entitiesArray.length);
                entitiesArray.push([remoteEntity]);
                physicsConfigMap.set('gravity', 3.7);
                physicsConfigMap.set('friction', 0.2);
                physicsConfigMap.set('timeScale', 0.5);
                physicsConfigMap.set('collisionEnabled', false);
                physicsConfigMap.set('airResistance', 0.03);
                physicsConfigMap.set('debugMode', true);
            });
            entitiesArray.emit();
            physicsConfigMap.emit();
        });

        await waitFor(() => {
            expect(store.get(simulationEntitiesAtom)).toEqual([remoteEntity]);
        });

        expect(store.get(simulationPhysicsConfigAtom)).toEqual({
            ...DEFAULT_PHYSICS_CONFIG,
            gravity: 3.7,
            friction: 0.2,
            timeScale: 0.5,
            collisionEnabled: false,
            airResistance: 0.03,
            debugMode: true,
        });
    });
});