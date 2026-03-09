/**
 * Unit tests for PluginRegistry (Phase 1)
 *
 * Tests validate plugin registration, lifecycle, lazy loading, and event system.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PluginRegistry, type PluginLoader, type RegisteredPlugin } from '@/lib/persona/PluginRegistry';
import type { PluginManifest } from '@/schemas/persona.schema';

describe('PluginRegistry', () => {
    let registry: PluginRegistry;

    beforeEach(() => {
        registry = new PluginRegistry();
    });

    describe('register()', () => {
        it('should register plugin with valid manifest', () => {
            const manifest: PluginManifest = {
                id: 'test-plugin',
                name: 'Test Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: ['metrics.read'],
                enabled: true,
                priority: 0,
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            const plugin = registry.get('test-plugin');
            expect(plugin).toBeDefined();
            expect(plugin?.id).toBe('test-plugin');
            expect(plugin?.name).toBe('Test Plugin');
        });

        it('should emit "registered" event on successful registration', () => {
            const manifest: PluginManifest = {
                id: 'event-test-plugin',
                name: 'Event Test',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const eventHandler = vi.fn();
            registry.on(eventHandler);

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(eventHandler).toHaveBeenCalledWith(
                expect.objectContaining({ id: 'event-test-plugin' }),
                'registered'
            );
        });

        it('should default enabled to true if not specified', () => {
            const manifest: PluginManifest = {
                id: 'default-enabled-plugin',
                name: 'Default Enabled',
                version: '1.0.0',
                type: 'action',
                slot: 'toolbar',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            const plugin = registry.get('default-enabled-plugin');
            expect(plugin?.enabled).toBe(true);
        });

        it('should overwrite duplicate plugin ID', () => {
            const manifest1: PluginManifest = {
                id: 'duplicate-plugin',
                name: 'Original',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const manifest2: PluginManifest = {
                ...manifest1,
                name: 'Updated',
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest1, loader);
            registry.register(manifest2, loader);

            const plugin = registry.get('duplicate-plugin');
            expect(plugin?.name).toBe('Updated');
        });
    });

    describe('get()', () => {
        it('should retrieve registered plugin by ID', () => {
            const manifest: PluginManifest = {
                id: 'get-test-plugin',
                name: 'Get Test',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            const plugin = registry.get('get-test-plugin');
            expect(plugin).toBeDefined();
            expect(plugin?.id).toBe('get-test-plugin');
        });

        it('should return undefined for unknown plugin ID', () => {
            const plugin = registry.get('unknown-plugin');
            expect(plugin).toBeUndefined();
        });
    });

    describe('getBySlot()', () => {
        beforeEach(() => {
            const dashboardPlugin: PluginManifest = {
                id: 'dashboard-plugin',
                name: 'Dashboard Widget',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.metrics',
                permissions: ['dashboard.read'],
                enabled: true,
                priority: 0,
            };

            const sidebarPlugin: PluginManifest = {
                id: 'sidebar-plugin',
                name: 'Sidebar Widget',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(dashboardPlugin, loader);
            registry.register(sidebarPlugin, loader);
        });

        it('should return plugins for specified slot', () => {
            const plugins = registry.getBySlot('dashboard.metrics');
            expect(plugins).toHaveLength(1);
            expect(plugins[0].id).toBe('dashboard-plugin');
        });

        it('should return multiple plugins if registered for same slot', () => {
            const anotherDashboardPlugin: PluginManifest = {
                id: 'another-dashboard-plugin',
                name: 'Another Dashboard',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(anotherDashboardPlugin, loader);

            const plugins = registry.getBySlot('dashboard.metrics');
            expect(plugins).toHaveLength(2);
        });

        it('should return empty array for unknown slot', () => {
            const plugins = registry.getBySlot('unknown.slot');
            expect(plugins).toHaveLength(0);
        });
    });

    describe('getEnabled()', () => {
        beforeEach(() => {
            const enabledPlugin: PluginManifest = {
                id: 'enabled-plugin',
                name: 'Enabled Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: ['metrics:read'],
                enabled: true,
                priority: 0,
                config: {},
            };

            const disabledPlugin: PluginManifest = {
                id: 'disabled-plugin',
                name: 'Disabled Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: ['metrics:admin'],
                enabled: false,
                priority: 0,
                config: {},
            };

            const loader: PluginLoader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(enabledPlugin, loader);
            registry.register(disabledPlugin, loader);
        });

        it('should return only enabled plugins', () => {
            const plugins = registry.getEnabled(['metrics:read'], 'metric');
            expect(plugins).toHaveLength(1);
            expect(plugins[0].id).toBe('enabled-plugin');
        });

        it('should filter by permissions', () => {
            const plugins = registry.getEnabled(['dashboard:read'], 'metric'); // No metrics:read permission
            expect(plugins).toHaveLength(0);
        });

        it('should include plugins without required permissions', () => {
            const noPermPlugin: PluginManifest = {
                id: 'no-perm-plugin',
                name: 'No Permission',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [], // No permissions required
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(noPermPlugin, loader);

            const plugins = registry.getEnabled(['dashboard:read'], 'widget');
            expect(plugins).toHaveLength(1); // Should include plugin with no permissions
        });
    });

    describe('enable() / disable()', () => {
        beforeEach(() => {
            const manifest: PluginManifest = {
                id: 'toggle-plugin',
                name: 'Toggle Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);
        });

        it('should disable plugin and emit "disabled" event', () => {
            const eventHandler = vi.fn();
            registry.on(eventHandler);

            registry.disable('toggle-plugin');

            const plugin = registry.get('toggle-plugin');
            expect(plugin?.enabled).toBe(false);
            expect(eventHandler).toHaveBeenCalledWith(expect.objectContaining({ id: 'toggle-plugin' }), 'disabled');
        });

        it('should enable disabled plugin and emit "enabled" event', () => {
            registry.disable('toggle-plugin');

            const eventHandler = vi.fn();
            registry.on(eventHandler);

            registry.enable('toggle-plugin');

            const plugin = registry.get('toggle-plugin');
            expect(plugin?.enabled).toBe(true);
            expect(eventHandler).toHaveBeenCalledWith(expect.objectContaining({ id: 'toggle-plugin' }), 'enabled');
        });

        it('should not throw for unknown plugin ID', () => {
            expect(() => registry.disable('unknown-plugin')).not.toThrow();
            expect(() => registry.enable('unknown-plugin')).not.toThrow();
        });
    });

    describe('loadComponent()', () => {
        it('should lazy load plugin component', async () => {
            const MockComponent = () => null;
            const manifest: PluginManifest = {
                id: 'lazy-plugin',
                name: 'Lazy Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: MockComponent }));
            registry.register(manifest, loader);

            const Component = await registry.loadComponent('lazy-plugin');
            expect(Component).toBe(MockComponent);
            expect(loader).toHaveBeenCalledOnce();
        });

        it('should throw error for unknown plugin', async () => {
            await expect(registry.loadComponent('unknown-plugin')).rejects.toThrow('Plugin not found: unknown-plugin');
        });

        it('should cache loaded component (not reload)', async () => {
            const MockComponent = () => null;
            const manifest: PluginManifest = {
                id: 'cache-plugin',
                name: 'Cache Plugin',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: MockComponent }));
            registry.register(manifest, loader);

            await registry.loadComponent('cache-plugin');
            await registry.loadComponent('cache-plugin'); // Second call

            expect(loader).toHaveBeenCalledOnce(); // Should not reload
        });
    });

    describe('Event System', () => {
        it('should support multiple listeners for same event', () => {
            const handler1 = vi.fn();
            const handler2 = vi.fn();

            registry.on(handler1);
            registry.on(handler2);

            const manifest: PluginManifest = {
                id: 'multi-listener-plugin',
                name: 'Multi Listener',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(handler1).toHaveBeenCalledWith(expect.objectContaining({ id: 'multi-listener-plugin' }), 'registered');
            expect(handler2).toHaveBeenCalledWith(expect.objectContaining({ id: 'multi-listener-plugin' }), 'registered');
        });

        it('should support off() to remove event listener', () => {
            const handler = vi.fn();
            registry.on(handler);
            registry.off(handler);

            const manifest: PluginManifest = {
                id: 'remove-listener-plugin',
                name: 'Remove Listener',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(handler).not.toHaveBeenCalled();
        });

        it('should handle errors in event listeners gracefully', () => {
            const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
            const faultyHandler = vi.fn(() => {
                throw new Error('Handler error');
            });
            const goodHandler = vi.fn();

            registry.on(faultyHandler);
            registry.on(goodHandler);

            const manifest: PluginManifest = {
                id: 'error-listener-plugin',
                name: 'Error Listener',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
                config: {},
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            // Both should be called despite error in first
            expect(faultyHandler).toHaveBeenCalled();
            expect(goodHandler).toHaveBeenCalled();
            expect(consoleErrorSpy).toHaveBeenCalledWith('Plugin event listener error:', expect.any(Error));

            consoleErrorSpy.mockRestore();
        });
    });

    // ========================================
    // Additional Coverage Tests
    // ========================================

    describe('unregister()', () => {
        it('should remove plugin from registry', () => {
            const manifest: PluginManifest = {
                id: 'unregister-test-plugin',
                name: 'Unregister Test',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(registry.get('unregister-test-plugin')).toBeDefined();

            const result = registry.unregister('unregister-test-plugin');
            expect(result).toBe(true);
            expect(registry.get('unregister-test-plugin')).toBeUndefined();
        });

        it('should return false for non-existent plugin', () => {
            const result = registry.unregister('non-existent-plugin');
            expect(result).toBe(false);
        });
    });

    describe('getAll()', () => {
        beforeEach(() => {
            const plugin1: PluginManifest = {
                id: 'plugin-1',
                name: 'Plugin 1',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const plugin2: PluginManifest = {
                id: 'plugin-2',
                name: 'Plugin 2',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: false,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(plugin1, loader);
            registry.register(plugin2, loader);
        });

        it('should return all plugins without filter', () => {
            const plugins = registry.getAll();
            expect(plugins).toHaveLength(2);
        });

        it('should return filtered plugins when filter provided', () => {
            const plugins = registry.getAll((p) => p.enabled);
            expect(plugins).toHaveLength(1);
            expect(plugins[0].id).toBe('plugin-1');
        });
    });

    describe('getByType()', () => {
        beforeEach(() => {
            const metricPlugin: PluginManifest = {
                id: 'metric-plugin',
                name: 'Metric Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const widgetPlugin: PluginManifest = {
                id: 'widget-plugin',
                name: 'Widget Plugin',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(metricPlugin, loader);
            registry.register(widgetPlugin, loader);
        });

        it('should return plugins of specified type', () => {
            const metrics = registry.getByType('metric');
            expect(metrics).toHaveLength(1);
            expect(metrics[0].type).toBe('metric');

            const widgets = registry.getByType('widget');
            expect(widgets).toHaveLength(1);
            expect(widgets[0].type).toBe('widget');
        });

        it('should return empty array for unknown type', () => {
            const plugins = registry.getByType('action' as any);
            expect(plugins).toHaveLength(0);
        });
    });

    describe('getBySlot() - priority sorting', () => {
        it('should sort plugins by priority (highest first)', () => {
            const lowPriority: PluginManifest = {
                id: 'low-priority',
                name: 'Low Priority',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 1,
            };

            const highPriority: PluginManifest = {
                id: 'high-priority',
                name: 'High Priority',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 10,
            };

            const mediumPriority: PluginManifest = {
                id: 'medium-priority',
                name: 'Medium Priority',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 5,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(lowPriority, loader);
            registry.register(highPriority, loader);
            registry.register(mediumPriority, loader);

            const plugins = registry.getBySlot('dashboard.metrics');
            expect(plugins).toHaveLength(3);
            expect(plugins[0].id).toBe('high-priority');
            expect(plugins[1].id).toBe('medium-priority');
            expect(plugins[2].id).toBe('low-priority');
        });
    });

    describe('getEnabled() - advanced filtering', () => {
        it('should filter by type when provided', () => {
            const metricPlugin: PluginManifest = {
                id: 'metric-enabled',
                name: 'Metric Enabled',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const widgetPlugin: PluginManifest = {
                id: 'widget-enabled',
                name: 'Widget Enabled',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(metricPlugin, loader);
            registry.register(widgetPlugin, loader);

            const metrics = registry.getEnabled([], 'metric');
            expect(metrics).toHaveLength(1);
            expect(metrics[0].type).toBe('metric');
        });

        it('should exclude plugins user lacks permissions for', () => {
            const restrictedPlugin: PluginManifest = {
                id: 'restricted',
                name: 'Restricted',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: ['admin:write'],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(restrictedPlugin, loader);

            const plugins = registry.getEnabled(['user:read']);
            expect(plugins).toHaveLength(0);
        });
    });

    describe('loadComponent() - error handling', () => {
        it('should throw error if plugin has no component loader', async () => {
            const manifest: PluginManifest = {
                id: 'no-loader-plugin',
                name: 'No Loader',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            // Register without loader
            registry.register(manifest);

            await expect(registry.loadComponent('no-loader-plugin')).rejects.toThrow(
                'No component loader for plugin: no-loader-plugin'
            );
        });

        it('should handle component load errors and emit error event', async () => {
            const manifest: PluginManifest = {
                id: 'error-plugin',
                name: 'Error Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const errorHandler = vi.fn();
            registry.on(errorHandler);

            const loader = vi.fn(() => Promise.reject(new Error('Load failed')));
            registry.register(manifest, loader);

            await expect(registry.loadComponent('error-plugin')).rejects.toThrow(
                'Failed to load plugin error-plugin:'
            );

            expect(errorHandler).toHaveBeenCalledWith(
                expect.objectContaining({ id: 'error-plugin', loadError: expect.any(Error) }),
                'error'
            );
        });

        it('should wait for concurrent load attempts', async () => {
            const MockComponent = () => null;
            const manifest: PluginManifest = {
                id: 'concurrent-plugin',
                name: 'Concurrent Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            let loadResolve: (value: any) => void;
            const loadPromise = new Promise((resolve) => {
                loadResolve = resolve;
            });

            const loader = vi.fn(() => loadPromise);
            registry.register(manifest, loader);

            // Start first load (won't complete yet)
            const load1 = registry.loadComponent('concurrent-plugin');

            // Start second load while first is in progress
            const load2 = registry.loadComponent('concurrent-plugin');

            // Complete the load
            loadResolve!({ default: MockComponent });

            const [component1, component2] = await Promise.all([load1, load2]);

            expect(component1).toBe(MockComponent);
            expect(component2).toBe(MockComponent);
            expect(loader).toHaveBeenCalledOnce(); // Should only load once
        });

        it('should handle concurrent load with error', async () => {
            const manifest: PluginManifest = {
                id: 'concurrent-error-plugin',
                name: 'Concurrent Error Plugin',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            let loadReject: (error: any) => void;
            const loadPromise = new Promise((resolve, reject) => {
                loadReject = reject;
            });

            const loader = vi.fn(() => loadPromise);
            registry.register(manifest, loader);

            // Start first load (won't complete yet)
            const load1 = registry.loadComponent('concurrent-error-plugin');

            // Start second load while first is in progress
            const load2 = registry.loadComponent('concurrent-error-plugin');

            // Reject the load
            const error = new Error('Load failed');
            loadReject!(error);

            // Both should reject with same error
            await expect(load1).rejects.toThrow('Failed to load plugin');
            await expect(load2).rejects.toThrow('Load failed');

            expect(loader).toHaveBeenCalledOnce();
        });

        it('should emit "loaded" event on successful load', async () => {
            const MockComponent = () => null;
            const manifest: PluginManifest = {
                id: 'loaded-event-plugin',
                name: 'Loaded Event',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const eventHandler = vi.fn();
            registry.on(eventHandler);

            const loader = vi.fn(() => Promise.resolve({ default: MockComponent }));
            registry.register(manifest, loader);

            await registry.loadComponent('loaded-event-plugin');

            expect(eventHandler).toHaveBeenCalledWith(
                expect.objectContaining({ id: 'loaded-event-plugin', loadedComponent: MockComponent }),
                'loaded'
            );
        });
    });

    describe('enable() / disable() - edge cases', () => {
        it('should not emit event if plugin already enabled', () => {
            const manifest: PluginManifest = {
                id: 'already-enabled',
                name: 'Already Enabled',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            const eventHandler = vi.fn();
            registry.on(eventHandler);

            registry.enable('already-enabled');

            // Should not emit "enabled" event since already enabled
            expect(eventHandler).not.toHaveBeenCalled();
        });

        it('should not emit event if plugin already disabled', () => {
            const manifest: PluginManifest = {
                id: 'already-disabled',
                name: 'Already Disabled',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: false,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            const eventHandler = vi.fn();
            registry.on(eventHandler);

            registry.disable('already-disabled');

            // Should not emit "disabled" event since already disabled
            expect(eventHandler).not.toHaveBeenCalled();
        });
    });

    describe('isEnabled()', () => {
        it('should return true for enabled plugin', () => {
            const manifest: PluginManifest = {
                id: 'enabled-check',
                name: 'Enabled Check',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(registry.isEnabled('enabled-check')).toBe(true);
        });

        it('should return false for disabled plugin', () => {
            const manifest: PluginManifest = {
                id: 'disabled-check',
                name: 'Disabled Check',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: false,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(registry.isEnabled('disabled-check')).toBe(false);
        });

        it('should return false for non-existent plugin', () => {
            expect(registry.isEnabled('non-existent')).toBe(false);
        });
    });

    describe('hasPermission()', () => {
        it('should grant access if plugin has no required permissions', () => {
            const plugin: RegisteredPlugin = {
                id: 'no-perms',
                name: 'No Permissions',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            expect(registry.hasPermission(plugin, ['user:read'])).toBe(true);
        });

        it('should grant access if user has wildcard permission', () => {
            const plugin: RegisteredPlugin = {
                id: 'restricted',
                name: 'Restricted',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: ['admin:write', 'admin:delete'],
                enabled: true,
                priority: 0,
            };

            expect(registry.hasPermission(plugin, ['*'])).toBe(true);
        });

        it('should grant access if user has exact permission match', () => {
            const plugin: RegisteredPlugin = {
                id: 'exact-match',
                name: 'Exact Match',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: ['metrics:read'],
                enabled: true,
                priority: 0,
            };

            expect(registry.hasPermission(plugin, ['metrics:read', 'user:read'])).toBe(true);
        });

        it('should grant access if user has wildcard pattern permission', () => {
            const plugin: RegisteredPlugin = {
                id: 'wildcard-match',
                name: 'Wildcard Match',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: ['metrics:read', 'metrics:write'],
                enabled: true,
                priority: 0,
            };

            expect(registry.hasPermission(plugin, ['metrics:*'])).toBe(true);
        });

        it('should deny access if user lacks required permissions', () => {
            const plugin: RegisteredPlugin = {
                id: 'denied',
                name: 'Denied',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: ['admin:write'],
                enabled: true,
                priority: 0,
            };

            expect(registry.hasPermission(plugin, ['user:read'])).toBe(false);
        });
    });

    describe('clear()', () => {
        it('should remove all plugins and listeners', () => {
            const manifest: PluginManifest = {
                id: 'clear-test',
                name: 'Clear Test',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(manifest, loader);

            expect(registry.getAll()).toHaveLength(1);

            registry.clear();

            expect(registry.getAll()).toHaveLength(0);
        });

        it('should remove all event listeners on clear', () => {
            const eventHandler = vi.fn();
            registry.on(eventHandler);

            const manifest: PluginManifest = {
                id: 'listener-clear-test',
                name: 'Listener Clear Test',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));

            // First registration - listener should be called
            registry.register(manifest, loader);
            expect(eventHandler).toHaveBeenCalledTimes(1);

            // Clear registry (removes plugins AND listeners)
            registry.clear();

            // Reset mock
            eventHandler.mockClear();

            // Register again - listener should NOT be called (was cleared)
            registry.register(manifest, loader);
            expect(eventHandler).not.toHaveBeenCalled();
        });
    });

    describe('getStats()', () => {
        it('should return accurate statistics', () => {
            const enabledMetric: PluginManifest = {
                id: 'enabled-metric',
                name: 'Enabled Metric',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const disabledWidget: PluginManifest = {
                id: 'disabled-widget',
                name: 'Disabled Widget',
                version: '1.0.0',
                type: 'widget',
                slot: 'dashboard.sidebar',
                permissions: [],
                enabled: false,
                priority: 0,
            };

            const anotherMetric: PluginManifest = {
                id: 'another-metric',
                name: 'Another Metric',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: () => null }));
            registry.register(enabledMetric, loader);
            registry.register(disabledWidget, loader);
            registry.register(anotherMetric, loader);

            const stats = registry.getStats();

            expect(stats.total).toBe(3);
            expect(stats.enabled).toBe(2);
            expect(stats.disabled).toBe(1);
            expect(stats.loaded).toBe(0); // No components loaded yet
            expect(stats.byType).toEqual({
                metric: 2,
                widget: 1,
            });
        });

        it('should track loaded components in stats', async () => {
            const MockComponent = () => null;
            const manifest: PluginManifest = {
                id: 'stats-loaded',
                name: 'Stats Loaded',
                version: '1.0.0',
                type: 'metric',
                slot: 'dashboard.metrics',
                permissions: [],
                enabled: true,
                priority: 0,
            };

            const loader = vi.fn(() => Promise.resolve({ default: MockComponent }));
            registry.register(manifest, loader);

            let stats = registry.getStats();
            expect(stats.loaded).toBe(0);

            await registry.loadComponent('stats-loaded');

            stats = registry.getStats();
            expect(stats.loaded).toBe(1);
        });
    });
});
