/**
 * useMobileCanvas Tests
 * 
 * Tests for mobile canvas hook
 * 
 * @doc.type test
 * @doc.purpose useMobileCanvas hook tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useMobileCanvas } from '../useMobileCanvas';
import type { MobilePlatform, MobileComponentType } from '../useMobileCanvas';

describe('useMobileCanvas', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useMobileCanvas());

            expect(result.current.platform).toBe('ios');
            expect(result.current.device).toBe('iphone-14');
            expect(result.current.components).toEqual([]);
            expect(result.current.selectedComponent).toBeNull();
            expect(result.current.deviceFrame).toMatchObject({
                name: 'iPhone 14',
                width: 390,
                height: 844,
            });
        });

        it('should initialize with custom platform', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialPlatform: 'android' }));

            expect(result.current.platform).toBe('android');
            expect(result.current.device).toBe('iphone-14'); // Not auto-switched yet
        });

        it('should initialize with custom device', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialDevice: 'pixel-7' }));

            expect(result.current.device).toBe('pixel-7');
            expect(result.current.deviceFrame.name).toBe('Google Pixel 7');
        });
    });

    describe('Platform Management', () => {
        it('should change platform', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.setPlatform('android');
            });

            expect(result.current.platform).toBe('android');
        });

        it('should auto-switch device when platform changes to Android', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialDevice: 'iphone-14' }));

            act(() => {
                result.current.setPlatform('android');
            });

            expect(result.current.platform).toBe('android');
            expect(result.current.device).toBe('pixel-7');
        });

        it('should auto-switch device when platform changes to iOS', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialDevice: 'pixel-7' }));

            act(() => {
                result.current.setPlatform('ios');
            });

            expect(result.current.platform).toBe('ios');
            expect(result.current.device).toBe('iphone-14');
        });

        it('should set split view mode', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.setPlatform('both');
            });

            expect(result.current.platform).toBe('both');
        });
    });

    describe('Component Management', () => {
        it('should add component', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('navigation-bar');
            });

            expect(result.current.components).toHaveLength(1);
            expect(result.current.components[0]).toMatchObject({
                type: 'navigation-bar',
                label: 'Navigation Bar',
                props: { title: 'Screen Title' },
            });
            expect(result.current.selectedComponent).toBe(result.current.components[0].id);
        });

        it('should add component with custom props', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button', { title: 'Custom Button', variant: 'outlined' });
            });

            expect(result.current.components[0].props).toMatchObject({
                title: 'Custom Button',
                variant: 'outlined',
            });
        });

        it('should add multiple components', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('navigation-bar');
                result.current.addComponent('toggle-switch');
                result.current.addComponent('button');
            });

            expect(result.current.components).toHaveLength(3);
            expect(result.current.components.map((c) => c.type)).toEqual([
                'navigation-bar',
                'toggle-switch',
                'button',
            ]);
        });

        it('should update component', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button');
            });

            const componentId = result.current.components[0].id;

            act(() => {
                result.current.updateComponent(componentId, { props: { title: 'Updated Button' } });
            });

            expect(result.current.components[0].props.title).toBe('Updated Button');
        });

        it('should delete component', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button');
                result.current.addComponent('slider');
            });

            const componentId = result.current.components[0].id;

            act(() => {
                result.current.deleteComponent(componentId);
            });

            expect(result.current.components).toHaveLength(1);
            expect(result.current.components[0].type).toBe('slider');
        });

        it('should clear selection when deleting selected component', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button');
            });

            const componentId = result.current.components[0].id;
            expect(result.current.selectedComponent).toBe(componentId);

            act(() => {
                result.current.deleteComponent(componentId);
            });

            expect(result.current.selectedComponent).toBeNull();
        });

        it('should select component', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button');
                result.current.addComponent('slider');
            });

            const secondComponentId = result.current.components[1].id;

            act(() => {
                result.current.selectComponent(secondComponentId);
            });

            expect(result.current.selectedComponent).toBe(secondComponentId);
        });

        it('should clear component selection', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button');
            });

            act(() => {
                result.current.selectComponent(null);
            });

            expect(result.current.selectedComponent).toBeNull();
        });

        it('should clear all components', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('navigation-bar');
                result.current.addComponent('button');
                result.current.addComponent('slider');
            });

            act(() => {
                result.current.clearComponents();
            });

            expect(result.current.components).toHaveLength(0);
            expect(result.current.selectedComponent).toBeNull();
        });
    });

    describe('Code Generation', () => {
        it('should generate React Native code', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('navigation-bar', { title: 'My App' });
                result.current.addComponent('button', { title: 'Submit' });
            });

            const code = result.current.generateReactNativeCode();

            expect(code).toContain("import React, { useState } from 'react';");
            expect(code).toContain('import { View, Text, StyleSheet, Platform');
            expect(code).toContain('const MobileScreen = () => {');
            expect(code).toContain('<View style={styles.navBar}>');
            expect(code).toContain('<Text style={styles.navTitle}>My App</Text>');
            expect(code).toContain('<TouchableOpacity style={styles.button}>');
            expect(code).toContain('<Text style={styles.buttonText}>Submit</Text>');
            expect(code).toContain('const styles = StyleSheet.create({');
            expect(code).toContain('export default MobileScreen;');
        });

        it('should generate code with Platform.select for styles', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('navigation-bar');
            });

            const code = result.current.generateReactNativeCode();

            expect(code).toContain('Platform.select({ ios: 44, android: 56 })');
            expect(code).toContain('Platform.select({ ios: \'#007AFF\', android: \'#2196F3\' })');
        });

        it('should generate code with state for interactive components', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('toggle-switch');
            });

            const code = result.current.generateReactNativeCode();
            const componentId = result.current.components[0].id;

            expect(code).toContain(`const [${componentId}Value, set${componentId}Value] = useState(false);`);
            expect(code).toContain(`value={${componentId}Value}`);
            expect(code).toContain(`onValueChange={set${componentId}Value}`);
        });

        it('should generate code with slider state', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('slider', { min: 0, max: 100, value: 50 });
            });

            const code = result.current.generateReactNativeCode();

            expect(code).toContain('minimumValue={0}');
            expect(code).toContain('maximumValue={100}');
        });

        it('should generate code with list data', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('list', { items: ['Apple', 'Banana', 'Orange'] });
            });

            const code = result.current.generateReactNativeCode();

            expect(code).toContain('["Apple","Banana","Orange"]');
            expect(code).toContain('<FlatList');
        });

        it('should use node label as component name', () => {
            const mockNode = {
                id: 'node-1',
                type: 'mobile',
                position: { x: 0, y: 0 },
                data: { label: 'SettingsScreen' },
            };

            const { result } = renderHook(() => useMobileCanvas({ node: mockNode }));

            act(() => {
                result.current.addComponent('button');
            });

            const code = result.current.generateReactNativeCode();

            expect(code).toContain('const SettingsScreen = () => {');
            expect(code).toContain('export default SettingsScreen;');
        });
    });

    describe('Utility Functions', () => {
        it('should get component count', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button');
                result.current.addComponent('button');
                result.current.addComponent('slider');
            });

            expect(result.current.getComponentCount()).toBe(3);
            expect(result.current.getComponentCount('button')).toBe(2);
            expect(result.current.getComponentCount('slider')).toBe(1);
            expect(result.current.getComponentCount('list')).toBe(0);
        });

        it('should get component by ID', () => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent('button', { title: 'Test Button' });
            });

            const componentId = result.current.components[0].id;
            const component = result.current.getComponentById(componentId);

            expect(component).toBeDefined();
            expect(component?.type).toBe('button');
            expect(component?.props.title).toBe('Test Button');
        });

        it('should return undefined for non-existent component', () => {
            const { result } = renderHook(() => useMobileCanvas());

            const component = result.current.getComponentById('non-existent');

            expect(component).toBeUndefined();
        });
    });

    describe('Device Frames', () => {
        it('should provide iPhone 14 frame', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialDevice: 'iphone-14' }));

            expect(result.current.deviceFrame).toMatchObject({
                name: 'iPhone 14',
                width: 390,
                height: 844,
                statusBarHeight: 54,
                notchHeight: 30,
            });
        });

        it('should provide Pixel 7 frame', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialDevice: 'pixel-7' }));

            expect(result.current.deviceFrame).toMatchObject({
                name: 'Google Pixel 7',
                width: 412,
                height: 915,
                statusBarHeight: 48,
            });
        });

        it('should fallback to default device frame', () => {
            const { result } = renderHook(() => useMobileCanvas({ initialDevice: 'unknown-device' }));

            expect(result.current.deviceFrame.name).toBe('iPhone 14');
        });
    });

    describe('All Component Types', () => {
        const componentTypes: MobileComponentType[] = [
            'navigation-bar',
            'toggle-switch',
            'slider',
            'list',
            'button',
            'text-input',
            'image',
            'card',
        ];

        it.each(componentTypes)('should add %s component', (type) => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent(type);
            });

            expect(result.current.components).toHaveLength(1);
            expect(result.current.components[0].type).toBe(type);
        });

        it.each(componentTypes)('should generate code for %s component', (type) => {
            const { result } = renderHook(() => useMobileCanvas());

            act(() => {
                result.current.addComponent(type);
            });

            const code = result.current.generateReactNativeCode();

            expect(code).toBeTruthy();
            expect(code).toContain('import React');
            expect(code).toContain('StyleSheet');
        });
    });
});
